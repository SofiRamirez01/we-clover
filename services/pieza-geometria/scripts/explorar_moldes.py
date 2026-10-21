"""Exploración (no es código de producción): inventario de moldes idénticos o casi idénticos.

Uso: python scripts/explorar_moldes.py [--csv scripts/resultados/moldes]

Lee pieza_talles de la base (SOLO LECTURA, PyMySQL) y compara los polígonos de cada (pieza,
talle) contra todos los demás:
- Normalización: se prueba alinear por la esquina inferior izquierda del rectángulo envolvente y
  por el centroide, con giro de 0° y de 180°; se queda la alineación de menor diferencia.
  Sin espejo: las piezas marcadas simétricas lo son (desvío verificado en el planificador), así
  que el espejo coincide con la pieza; las no simétricas no se pueden espejar (derecho/revés).
- Diferencia: área de la diferencia simétrica / área media de los dos, y distancia máxima entre
  bordes (Hausdorff de los contornos, cm).
- Inclusión (para sustitución con recorte): fracción de B que queda fuera de A en la mejor
  alineación, y recorte = (área A − área B) / área B.

Escribe <csv>_pares.csv (todos los pares) y <csv>_clases.csv (clases por tolerancia) y muestra
las clases con tolerancias 0,1 cm / 0,5 cm (Hausdorff) y 1 % (diferencia simétrica).
"""
import argparse
import csv
import itertools
import json
from pathlib import Path

import pymysql
from shapely.affinity import rotate, translate
from shapely.geometry import Polygon

RESULTADOS = Path(__file__).resolve().parent / "resultados"


def leer_moldes():
    conexion = pymysql.connect(host="localhost", user="root", password="root", database="weclover_db")
    try:
        with conexion.cursor() as cur:
            cur.execute("SELECT p.id, p.nombre, p.simetrica, pt.id_talle, pt.coordenadas_json "
                        "FROM pieza_talles pt JOIN piezas p ON p.id = pt.id_pieza "
                        "WHERE p.activo = 1 ORDER BY p.id, pt.id_talle")
            filas = cur.fetchall()
    finally:
        conexion.close()
    moldes = []
    for id_pieza, nombre, simetrica, id_talle, coordenadas in filas:
        poligono = Polygon(json.loads(coordenadas))
        if not poligono.is_valid:
            poligono = poligono.buffer(0)
        moldes.append({"id_pieza": id_pieza, "nombre": nombre, "simetrica": bool(simetrica and simetrica != b"\x00"),
                       "id_talle": id_talle, "poligono": poligono})
    return moldes


def _alineaciones(a: Polygon, b: Polygon):
    """b movido sobre a: esquina y centroide, con 0° y 180°."""
    for angulo in (0, 180):
        bb = rotate(b, angulo, origin="centroid") if angulo else b
        yield angulo, "esquina", translate(bb, a.bounds[0] - bb.bounds[0], a.bounds[1] - bb.bounds[1])
        yield angulo, "centroide", translate(bb, a.centroid.x - bb.centroid.x, a.centroid.y - bb.centroid.y)


def comparar(a: Polygon, b: Polygon):
    mejor = None
    for angulo, modo, bb in _alineaciones(a, b):
        dif = a.symmetric_difference(bb).area / ((a.area + b.area) / 2)
        if mejor is None or dif < mejor["dif_simetrica"]:
            mejor = {"dif_simetrica": dif, "hausdorff_cm": a.exterior.hausdorff_distance(bb.exterior),
                     "giro": angulo, "alineacion": modo,
                     # Qué parte de cada uno queda fuera del otro (para ver quién contiene a quién).
                     "b_fuera_de_a": bb.difference(a).area / b.area, "a_fuera_de_b": a.difference(bb).area / a.area}
    return mejor


def _clases(moldes, pares, condicion):
    padre = list(range(len(moldes)))

    def raiz(i):
        while padre[i] != i:
            padre[i] = padre[padre[i]]
            i = padre[i]
        return i

    for (i, j), r in pares.items():
        if condicion(r):
            padre[raiz(i)] = raiz(j)
    grupos = {}
    for i in range(len(moldes)):
        grupos.setdefault(raiz(i), []).append(i)
    clases = []
    for miembros in grupos.values():
        desvio_h = max((pares[tuple(sorted(p))]["hausdorff_cm"] for p in itertools.combinations(miembros, 2)), default=0)
        desvio_d = max((pares[tuple(sorted(p))]["dif_simetrica"] for p in itertools.combinations(miembros, 2)), default=0)
        clases.append((miembros, desvio_h, desvio_d))
    return sorted(clases, key=lambda c: (moldes[c[0][0]]["id_pieza"], moldes[c[0][0]]["id_talle"]))


def _etiqueta(moldes, miembros):
    por_pieza = {}
    for i in miembros:
        por_pieza.setdefault(moldes[i]["nombre"], []).append(str(moldes[i]["id_talle"]))
    return " + ".join(f"{nombre} T{'/'.join(talles)}" for nombre, talles in por_pieza.items())


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--csv", default=str(RESULTADOS / "moldes"))
    args = parser.parse_args()

    moldes = leer_moldes()
    pares = {}
    for i, j in itertools.combinations(range(len(moldes)), 2):
        pares[(i, j)] = comparar(moldes[i]["poligono"], moldes[j]["poligono"])

    with open(f"{args.csv}_pares.csv", "w", newline="", encoding="utf-8") as archivo:
        escritor = csv.writer(archivo)
        escritor.writerow(["pieza_a", "talle_a", "pieza_b", "talle_b", "area_a_cm2", "area_b_cm2", "dif_simetrica",
                           "hausdorff_cm", "giro", "alineacion", "b_fuera_de_a", "a_fuera_de_b"])
        for (i, j), r in pares.items():
            a, b = moldes[i], moldes[j]
            escritor.writerow([a["nombre"], a["id_talle"], b["nombre"], b["id_talle"], round(a["poligono"].area, 2),
                               round(b["poligono"].area, 2), round(r["dif_simetrica"], 6), round(r["hausdorff_cm"], 4),
                               r["giro"], r["alineacion"], round(r["b_fuera_de_a"], 6), round(r["a_fuera_de_b"], 6)])

    tolerancias = {
        "0,1 cm": lambda r: r["hausdorff_cm"] <= 0.1,
        "0,5 cm": lambda r: r["hausdorff_cm"] <= 0.5,
        "1 %": lambda r: r["dif_simetrica"] <= 0.01,
    }
    filas_clases = []
    for nombre, condicion in tolerancias.items():
        clases = _clases(moldes, pares, condicion)
        print(f"\n== Tolerancia {nombre}: {len(clases)} clases para {len(moldes)} moldes ==")
        for numero, (miembros, desvio_h, desvio_d) in enumerate(clases, start=1):
            if len(miembros) > 1:
                print(f"  clase {numero:2}: {_etiqueta(moldes, miembros):45} desvío máx {desvio_h:.3f} cm / {desvio_d:.2%}")
            filas_clases.append([nombre, numero, _etiqueta(moldes, miembros), len(miembros), round(desvio_h, 4),
                                 round(desvio_d, 6)])
    with open(f"{args.csv}_clases.csv", "w", newline="", encoding="utf-8") as archivo:
        escritor = csv.writer(archivo)
        escritor.writerow(["tolerancia", "clase", "moldes", "cantidad", "hausdorff_max_cm", "dif_simetrica_max"])
        escritor.writerows(filas_clases)

    # Casi idénticos y candidatos a sustitución con recorte, dentro de una misma pieza: B entra en A
    # (menos de 0,5 % de B afuera) y recortar A hasta B tira menos del 15 % de B.
    print("\n== Talles consecutivos de cada pieza (diferencia y si el chico entra en el grande) ==")
    for (i, j), r in pares.items():
        a, b = moldes[i], moldes[j]
        if a["id_pieza"] != b["id_pieza"] or b["id_talle"] != a["id_talle"] + 1:
            continue
        print(f"  {a['nombre']:26} T{a['id_talle']}→T{b['id_talle']}: dif {r['dif_simetrica']:6.2%}, "
              f"Hausdorff {r['hausdorff_cm']:5.2f} cm, T{a['id_talle']} fuera de T{b['id_talle']} {r['a_fuera_de_b']:6.2%}")
    print("\n== Sustitución con recorte (misma pieza): el talle chico entra en el grande ==")
    for (i, j), r in pares.items():
        a, b = moldes[i], moldes[j]
        if a["id_pieza"] != b["id_pieza"] or r["hausdorff_cm"] <= 0.5:
            continue
        chico, grande, fuera = (a, b, r["a_fuera_de_b"]) if a["poligono"].area < b["poligono"].area else (b, a, r["b_fuera_de_a"])
        recorte = grande["poligono"].area / chico["poligono"].area - 1
        if fuera <= 0.005 and recorte <= 0.15:
            print(f"  {chico['nombre']:26} T{chico['id_talle']} sale de T{grande['id_talle']}: recorte {recorte:5.1%} del área"
                  f" (afuera {fuera:.2%})")
    print(f"\nCSV: {args.csv}_pares.csv y {args.csv}_clases.csv")


if __name__ == "__main__":
    main()
