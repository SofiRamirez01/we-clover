"""Exploración (no es código de producción): impacto de fusionar ítems con molde idéntico.

Uso: python scripts/explorar_fusion.py preparacion.json [otra.json ...] [--tolerancia-cm 0.1] [--tope 0]

"Demanda efectiva": dentro de cada tela, los (pieza, talle) cuyo molde es idéntico (distancia
máxima entre bordes <= tolerancia, con giro de 0° o 180°) pasan a ser un solo ítem con la demanda
sumada por color. El molde que se usa es el de mayor área de la clase (si no son exactamente
iguales, el sustituto nunca es más chico). Cada `origen` conserva su talle original
(`idTalleOriginal`) para la trazabilidad. Después corre el planificador ACTUAL (app/, sin tocarlo)
con la demanda tal cual y con la fusionada, y compara por tela: ítems, cortes, capas, tela
estimada (Σ largo × capas) y excedente. Todo es estimación por área: no corre el algoritmo.
"""
import argparse
import copy
import json
import sys
from pathlib import Path

RAIZ = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(RAIZ))

from shapely.affinity import rotate, translate  # noqa: E402
from shapely.geometry import Polygon  # noqa: E402

from app.corte.parametros import ParametrosCorte  # noqa: E402
from app.corte.planificador import planificar  # noqa: E402


def _distancia(a: Polygon, b: Polygon) -> float:
    """Menor distancia de Hausdorff entre contornos alineando por esquina y centroide, 0° y 180°."""
    mejor = float("inf")
    for angulo in (0, 180):
        bb = rotate(b, angulo, origin="centroid") if angulo else b
        for movido in (translate(bb, a.bounds[0] - bb.bounds[0], a.bounds[1] - bb.bounds[1]),
                       translate(bb, a.centroid.x - bb.centroid.x, a.centroid.y - bb.centroid.y)):
            mejor = min(mejor, a.exterior.hausdorff_distance(movido.exterior))
    return mejor


def clases_de_molde(preparacion, tolerancia_cm):
    """{(idPieza, idTalle): representante (idPieza, idTalle)}; clases por unión de pares cercanos."""
    poligonos = {(pieza["id"], talle["idTalle"]): Polygon(talle["coordenadas"])
                 for pieza in preparacion["piezas"] for talle in pieza["talles"]}
    claves = list(poligonos)
    padre = {k: k for k in claves}

    def raiz(k):
        while padre[k] != k:
            k = padre[k]
        return k

    for i, a in enumerate(claves):
        for b in claves[i + 1:]:
            if _distancia(poligonos[a], poligonos[b]) <= tolerancia_cm:
                padre[raiz(a)] = raiz(b)
    miembros = {}
    for k in claves:
        miembros.setdefault(raiz(k), []).append(k)
    representante = {}
    for grupo in miembros.values():
        mayor = max(grupo, key=lambda k: (poligonos[k].area, -k[1]))
        for k in grupo:
            representante[k] = mayor
    return representante, poligonos


def fusionar(preparacion, representante):
    """Copia de la preparación con los ítems de molde idéntico fusionados dentro de cada tela."""
    nueva = copy.deepcopy(preparacion)
    talle_de = {(pieza["id"], talle["idTalle"]): talle["talle"]
                for pieza in preparacion["piezas"] for talle in pieza["talles"]}
    for grupo in nueva["grupos"]:
        fusionados = {}
        for it in grupo["items"]:
            clave = representante.get((it["idPieza"], it["idTalle"]), (it["idPieza"], it["idTalle"]))
            destino = fusionados.get(clave)
            origen = [{**o, "idTalleOriginal": it["idTalle"], "talleOriginal": it["talle"]} for o in it.get("origen", [])]
            if destino is None:
                destino = {**it, "idPieza": clave[0], "idTalle": clave[1], "talle": talle_de[clave],
                           "porColor": [dict(c) for c in it["porColor"]], "origen": origen,
                           "tallesFusionados": [it["talle"]]}
                fusionados[clave] = destino
                continue
            destino["tallesFusionados"].append(it["talle"])
            destino["origen"].extend(origen)
            destino["cantidadTotal"] += it["cantidadTotal"]
            por_color = {c["idPaletaColor"]: c for c in destino["porColor"]}
            for c in it["porColor"]:
                if c["idPaletaColor"] in por_color:
                    por_color[c["idPaletaColor"]]["cantidad"] += c["cantidad"]
                else:
                    destino["porColor"].append(dict(c))
        grupo["items"] = list(fusionados.values())
    return nueva


def resumen(plan, preparacion):
    nombres = {p["id"]: p["nombre"] for p in preparacion["piezas"]}
    colores = {c["idPaletaColor"]: c["nombreColor"] for g in preparacion["grupos"] for it in g["items"] for c in it["porColor"]}
    por_tela = {}
    for g in preparacion["grupos"]:
        por_tela[g["codigoTipoTela"]] = {"items": len(g["items"]), "cortes": [], "capas": 0, "tela": 0.0, "excedente": 0}
    for corte in plan.cortes:
        r = por_tela[corte.codigo_tela]
        r["capas"] += corte.capas_totales
        r["tela"] += corte.tela_estimada_cm
        r["excedente"] += sum(sum(ic.excedente.values()) for ic in corte.items)
        talles = sorted({ic.item.geo.talle for ic in corte.items}, key=lambda t: (len(t), t))
        r["cortes"].append(f"[{','.join(f'{colores[c]}={l}' for c, l in corte.capas.items())}] "
                           f"T{'+'.join(talles)} est {corte.largo_estimado_cm:.0f} cm")
    return por_tela, nombres


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("preparaciones", nargs="+")
    parser.add_argument("--tolerancia-cm", type=float, default=0.1)
    parser.add_argument("--tope", type=int, default=0)
    parser.add_argument("--guardar", help="carpeta donde guardar las preparaciones fusionadas")
    args = parser.parse_args()

    for ruta in args.preparaciones:
        preparacion = json.loads(Path(ruta).read_text(encoding="utf-8"))
        representante, _ = clases_de_molde(preparacion, args.tolerancia_cm)
        fusionada = fusionar(preparacion, representante)
        if args.guardar:
            destino = Path(args.guardar) / (Path(ruta).stem + "_fusionada.json")
            destino.write_text(json.dumps(fusionada, ensure_ascii=False), encoding="utf-8")
        p = ParametrosCorte.desde_dict(preparacion["parametros"], excedente_maximo_por_item=args.tope)
        antes = planificar(preparacion, p)
        despues = planificar(fusionada, p)
        r_antes, _ = resumen(antes, preparacion)
        r_despues, _ = resumen(despues, fusionada)
        print(f"\n### {Path(ruta).name} (tope {args.tope}, tolerancia {args.tolerancia_cm} cm)")
        print(f"| tela | ítems | cortes | capas | tela est. (cm) | excedente |")
        print(f"|---|---|---|---|---|---|")
        for tela in r_antes:
            a, d = r_antes[tela], r_despues[tela]
            print(f"| {tela} | {a['items']} → {d['items']} | {len(a['cortes'])} → {len(d['cortes'])} | {a['capas']} → {d['capas']}"
                  f" | {a['tela']:.0f} → {d['tela']:.0f} | {a['excedente']} → {d['excedente']} |")
        print(f"| **total** | {sum(r['items'] for r in r_antes.values())} → {sum(r['items'] for r in r_despues.values())}"
              f" | {len(antes.cortes)} → {len(despues.cortes)} | {sum(r['capas'] for r in r_antes.values())} →"
              f" {sum(r['capas'] for r in r_despues.values())} | {antes.tela_estimada_cm:.0f} → {despues.tela_estimada_cm:.0f}"
              f" | {antes.excedente_total} → {despues.excedente_total} |")
        for tela in r_antes:
            print(f"  {tela} antes:   " + " · ".join(r_antes[tela]["cortes"]))
            print(f"  {tela} después: " + " · ".join(r_despues[tela]["cortes"]))
        print(f"  prendas partidas: {len(antes.prendas_partidas)} → {len(despues.prendas_partidas)}"
              f" | estrategias: {antes.estrategias} → {despues.estrategias}")


if __name__ == "__main__":
    main()
