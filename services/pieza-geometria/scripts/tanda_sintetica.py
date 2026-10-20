"""Genera una preparación de corte sintética de N pedidos de buzos y la planifica (sin algoritmo).

Uso: python scripts/tanda_sintetica.py preparacion_real.json --pedidos 15 30 [--semilla 1] [--guardar dir]

Toma la geometría (piezas por talle) y las telas de una preparación real y arma pedidos con estos
SUPUESTOS (no salen de datos reales salvo donde se indica):
- Cada pedido es un buzo con las 8 piezas reales: de 1 pin Bolsillo, Cintura, Espalda y Frente;
  de 2 pines Capucha, Capucha interior, Manga y Puño (confirmado para la moldería actual).
- Unidades por pedido: al azar entre 8 y 35 (los dos pedidos reales tienen 8 y 23).
- Talles: cada unidad se sortea con la distribución de los dos pedidos reales juntos
  (T1..T7 = 3, 4, 5, 5, 7, 5, 2 unidades).
- Colores: cada pedido elige 1 de 8 colores de Frisa, 1 de 4 de Jersey y 1 de 4 de Ribb.
"""
import argparse
import json
import random
import sys
from pathlib import Path

RAIZ = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(RAIZ))

from app.corte.parametros import ParametrosCorte  # noqa: E402
from app.corte.planificador import planificar  # noqa: E402
from app.corte.tizada import armar_entradas  # noqa: E402

PINES = {"Bolsillo": 1, "Cintura": 1, "Espalda": 1, "Frente": 1,
         "Capucha interior": 2, "Capucha": 2, "Manga": 2, "Puño": 2, "Pu": 2}
PESOS_TALLE = {"1": 3, "2": 4, "3": 5, "4": 5, "5": 7, "6": 5, "7": 2}
COLORES_POR_TELA = {"FRIZA": 8, "JERSEY": 4, "RIBB": 4}


def _pines(nombre):
    for prefijo, pines in PINES.items():  # "Capucha interior" antes que "Capucha"
        if nombre.startswith(prefijo):
            return pines
    raise ValueError(f"No sé cuántos pines tiene {nombre}")


def generar(real, pedidos, semilla):
    azar = random.Random(semilla)
    tela_de_pieza = {it["idPieza"]: (g["idTipoTela"], g["codigoTipoTela"], g["nombreTipoTela"])
                     for g in real["grupos"] for it in g["items"]}
    piezas = [p for p in real["piezas"] if p["id"] in tela_de_pieza]
    talles = {t["talle"]: t["idTalle"] for p in piezas for t in p["talles"]}
    colores = {codigo: [(10000 + 100 * i + j, f"{codigo.title()} {j + 1}") for j in range(n)]
               for i, (codigo, n) in enumerate(COLORES_POR_TELA.items())}
    demanda = {}  # (idTela, idPieza, talle) -> {idColor: cantidad}
    for numero in range(1, pedidos + 1):
        unidades = azar.randint(8, 35)
        color = {codigo: azar.choice(lista) for codigo, lista in colores.items()}
        for talle in azar.choices(list(PESOS_TALLE), weights=list(PESOS_TALLE.values()), k=unidades):
            for pieza in piezas:
                id_tela, codigo, _ = tela_de_pieza[pieza["id"]]
                clave = (id_tela, pieza["id"], talle)
                id_color = color[codigo][0]
                demanda.setdefault(clave, {}).setdefault(id_color, {"cantidad": 0, "pedidos": set()})
                demanda[clave][id_color]["cantidad"] += _pines(pieza["nombre"])
                demanda[clave][id_color]["pedidos"].add(numero)
    nombres = {id_color: nombre for lista in colores.values() for id_color, nombre in lista}
    grupos = []
    for id_tela, codigo, nombre in sorted(set(tela_de_pieza.values())):
        items = []
        for (tela, id_pieza, talle), por_color in sorted(demanda.items(), key=lambda kv: (kv[0][1], int(kv[0][2]))):
            if tela != id_tela:
                continue
            items.append({
                "idPieza": id_pieza, "idTalle": talles[talle], "talle": talle,
                "porColor": [{"idPaletaColor": c, "nombreColor": nombres[c], "hex": None, "cantidad": d["cantidad"]}
                             for c, d in sorted(por_color.items())],
                "cantidadTotal": sum(d["cantidad"] for d in por_color.values()),
                "origen": [{"idPedido": n, "codigoInternoPedido": f"SINT-{n:02d}", "idProducto": n,
                            "idPaletaColor": c, "cantidad": d["cantidad"], "idProductoInsumo": None}
                           for c, d in por_color.items() for n in sorted(d["pedidos"])]})
        grupos.append({"idTipoTela": id_tela, "codigoTipoTela": codigo, "nombreTipoTela": nombre, "items": items})
    return {"idTanda": -pedidos, "nombreTanda": f"Sintética {pedidos} pedidos (semilla {semilla})",
            "parametros": real["parametros"], "piezas": piezas, "grupos": grupos, "pendientes": [], "resumen": {}}


def reportar(preparacion):
    p = ParametrosCorte.desde_dict(preparacion["parametros"])
    plan = planificar(preparacion, p)
    print(f"\n=== {preparacion['nombreTanda']} | modo {p.modo_objetivo}, tope {p.excedente_maximo_por_item},"
          f" eficiencia por tela {p.eficiencia_estimada_por_tela} (preliminar)")
    por_tela = {}
    for corte in plan.cortes:
        entradas, _ = armar_entradas(corte, p)
        por_tela.setdefault(corte.codigo_tela, []).append((len(entradas), corte.capas_totales, corte.largo_estimado_cm))
    total_entradas = []
    for tela, cortes in por_tela.items():
        piezas = sorted(n for n, _, _ in cortes)
        total_entradas += piezas
        print(f"  {tela:7} {len(cortes):3} cortes | piezas por corte: min {piezas[0]}, mediana {piezas[len(piezas) // 2]},"
              f" max {piezas[-1]} | capas: {sorted({c for _, c, _ in cortes})} | largo est. total"
              f" {sum(l for _, _, l in cortes):.0f} cm (máx. {max(l for _, _, l in cortes):.0f})")
    grandes = sum(1 for n in total_entradas if n >= 20)
    cortes = len(plan.cortes)
    print(f"  TOTAL {cortes} cortes ({grandes} con 20 piezas o más) | excedente {plan.excedente_total}"
          f" | prendas partidas {len(plan.prendas_partidas)} | no resueltas {len(plan.no_resueltas)}")
    print(f"  Tiempo de algoritmo (cota con el presupuesto entero en cada corte): RAPIDO {cortes * 60 / 60:.0f} min,"
          f" COMPLETO {cortes * 200 / 60:.0f} min")
    return plan


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("preparacion_real")
    parser.add_argument("--pedidos", type=int, nargs="+", default=[15, 30])
    parser.add_argument("--semilla", type=int, default=1)
    parser.add_argument("--guardar", help="carpeta donde guardar los JSON generados")
    args = parser.parse_args()
    real = json.loads(Path(args.preparacion_real).read_text(encoding="utf-8"))
    for pedidos in args.pedidos:
        preparacion = generar(real, pedidos, args.semilla)
        if args.guardar:
            destino = Path(args.guardar) / f"tanda_sintetica_{pedidos}_pedidos.json"
            destino.write_text(json.dumps(preparacion, ensure_ascii=False), encoding="utf-8")
        reportar(preparacion)


if __name__ == "__main__":
    main()
