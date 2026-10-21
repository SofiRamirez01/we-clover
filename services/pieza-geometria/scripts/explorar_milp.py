"""Exploración (no es código de producción): planificación por TELA con programación entera (MILP).

Uso (con un Python que tenga scipy, NO el del servicio):
  python scripts/explorar_milp.py preparacion.json [--fusionar] [--tope 0] [--lambda-cm-por-min 0]
                                  [--h-cm 0] [--eficiencia FRIZA=0.66 ...] [--tiempo 120] [--telas FRIZA]

Por cada tela se elige el conjunto de cortes que cubre toda la demanda:
- Corte k = un plan de capas L^k por color de la tela (puede mezclar colores, Σ L <= capas máximas),
  tomado de una lista de candidatos (cada uno se puede usar hasta R veces).
- u[k,i] = unidades por capa del ítem i en el corte k (entero; par si la pieza no admite mitad).
- Cobertura: Σ_k u[k,i]·L^k_c >= demanda[i,c]; excedente <= max(tope, inevitable) por ítem y color.
- Largo estimado del corte: Σ_i u[k,i]·a_i / (ancho × η) + h (h = pérdida fija por tizada, opcional),
  con a_i = área con margen por unidad (mitad de la entera) y largo <= largo máximo × (1 + tolerancia).
- Objetivo: Σ_k |L^k|·largo_k (tela) + λ · minutos_por_corte_adicional · (cortes) + ε · capas.
Con h = 0 y λ = 0 la tela estimada de cualquier cobertura sin excedente es la misma (la estimación es
lineal en el área), así que el resultado depende de los desempates: por eso existen h y λ.
"""
import argparse
import json
import sys
import time
from math import ceil
from pathlib import Path

import numpy as np
from scipy.optimize import Bounds, LinearConstraint, milp
from scipy.sparse import lil_matrix

RAIZ = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(RAIZ))
sys.path.insert(0, str(RAIZ / "scripts"))

from app.corte.parametros import ParametrosCorte  # noqa: E402
from app.corte.piezas import preparar_piezas  # noqa: E402
from explorar_fusion import clases_de_molde, fusionar  # noqa: E402


def candidatos_capas(items, colores, p, maximo_multicolor=60):
    """Planes de capas: cada color solo de 1 a capas máximas y, si hay ítems de varios colores, los
    vectores que cubren exacto a algún ítem (como el planificador), hasta `maximo_multicolor`."""
    planes = set()
    for c in colores:
        for l in range(1, p.capas_maximas + 1):
            planes.add(tuple(l if x == c else 0 for x in colores))
    multicolor = set()
    for item in items:
        presentes = [c for c in colores if item["demanda"].get(c, 0) > 0]
        if len(presentes) < 2:
            continue
        for u in range(1, max(item["demanda"].values()) + 1):
            vector = tuple(max(1, ceil(item["demanda"][c] / u)) if c in presentes else 0 for c in colores)
            if sum(vector) <= p.capas_maximas:
                multicolor.add(vector)
    planes |= set(sorted(multicolor, key=sum)[:maximo_multicolor])
    return sorted(planes, key=lambda v: (sum(1 for x in v if x), sum(v), v))


def resolver_tela(items, colores, p, eta, h, lam, tope, repeticiones, tiempo):
    planes = candidatos_capas(items, colores, p)
    largo_max = p.largo_maximo_cm * (1 + p.tolerancia_estimacion_largo)
    cortes = [plan for plan in planes for _ in range(repeticiones)]
    n_i, n_k = len(items), len(cortes)
    # Variables: u[k,i] (n_k*n_i, enteras; w = u/2 si solo entera), y[k] (binaria), largo[k] (continua).
    iu = lambda k, i: k * n_i + i  # noqa: E731
    iy = lambda k: n_k * n_i + k  # noqa: E731
    il = lambda k: n_k * n_i + n_k + k  # noqa: E731
    n = n_k * n_i + 2 * n_k
    paso = [1 if it["mitad"] else 2 for it in items]  # unidades por variable
    c = np.zeros(n)
    integral = np.zeros(n)
    lb, ub = np.zeros(n), np.full(n, np.inf)
    for k, plan in enumerate(cortes):
        capas = sum(plan)
        c[il(k)] = capas  # tela = capas × largo
        c[iy(k)] = lam * p.minutos_por_corte_adicional + 1e-3 * capas + 1e-4 * k  # corte + desempates
        integral[iy(k)] = 1
        ub[iy(k)] = 1
        ub[il(k)] = largo_max
        for i, it in enumerate(items):
            integral[iu(k, i)] = 1
            # Cota: ningún color del plan puede producir más que demanda + tope permitido.
            limite = min(((it["demanda"].get(col, 0) + it["tope"].get(col, tope)) // (l * paso[i])
                          for col, l in zip(colores, plan) if l), default=0)
            ub[iu(k, i)] = max(0, limite)
    filas, lo, hi = [], [], []
    A = lil_matrix((n_i * len(colores) + 2 * n_k + n_k * n_i, n))
    r = 0
    for i, it in enumerate(items):
        for ci, col in enumerate(colores):
            d = it["demanda"].get(col, 0)
            for k, plan in enumerate(cortes):
                if plan[ci]:
                    A[r, iu(k, i)] = plan[ci] * paso[i]
            lo.append(d)
            hi.append(d + it["tope"].get(col, tope))
            r += 1
    for k in range(n_k):
        # largo_k - Σ u a /(ancho η) - h y_k >= 0
        A[r, il(k)] = 1
        for i, it in enumerate(items):
            A[r, iu(k, i)] = -it["area_unidad"] * paso[i] / (p.ancho_util_cm * eta)
        A[r, iy(k)] = -h
        lo.append(0)
        hi.append(np.inf)
        r += 1
        # largo_k <= largo_max · y_k
        A[r, il(k)] = 1
        A[r, iy(k)] = -largo_max
        lo.append(-np.inf)
        hi.append(0)
        r += 1
        for i in range(n_i):  # u[k,i] solo si el corte existe
            A[r, iu(k, i)] = 1
            A[r, iy(k)] = -ub[iu(k, i)]
            lo.append(-np.inf)
            hi.append(0)
            r += 1
    A = A[:r].tocsr()
    inicio = time.perf_counter()
    res = milp(c, constraints=LinearConstraint(A, lo, hi), integrality=integral, bounds=Bounds(lb, ub),
               options={"time_limit": tiempo, "disp": False, "mip_rel_gap": 1e-4})
    segundos = time.perf_counter() - inicio
    if res.x is None:
        return None, res, segundos
    elegidos = []
    for k, plan in enumerate(cortes):
        if res.x[iy(k)] < 0.5:
            continue
        u = {i: int(round(res.x[iu(k, i)])) * paso[i] for i in range(n_i) if res.x[iu(k, i)] > 0.5}
        if not u:
            continue
        area = sum(items[i]["area_unidad"] * ui for i, ui in u.items())
        elegidos.append({"capas": dict(zip(colores, plan)), "u": u, "area_por_capa": area,
                         "largo_est": area / (p.ancho_util_cm * eta), "figuras": sum(
                             ui // 2 + ui % 2 if items[i]["mitad"] else ui // 2 for i, ui in u.items())})
    return elegidos, res, segundos


def preparar_items(grupo, geos, p):
    items = []
    for it in grupo["items"]:
        geo = geos[(it["idPieza"], it["idTalle"])]
        demanda = {c["idPaletaColor"]: c["cantidad"] for c in it["porColor"] if c["cantidad"] > 0}
        tope = {}
        for c in demanda:
            inevitable = 0 if geo.permite_mitad else demanda[c] % 2
            tope[c] = max(p.excedente_maximo_por_item, inevitable)
        items.append({"nombre": f"{geo.nombre.split()[0]} T{geo.talle}", "demanda": demanda, "tope": tope,
                      "mitad": geo.permite_mitad, "area_unidad": geo.area_entera_cm2 / 2})
    return items


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("preparacion")
    parser.add_argument("--fusionar", action="store_true")
    parser.add_argument("--tope", type=int, default=0)
    parser.add_argument("--lambda-cm-por-min", type=float, default=0.0)
    parser.add_argument("--h-cm", type=float, default=0.0)
    parser.add_argument("--eficiencia", nargs="*", default=[])
    parser.add_argument("--repeticiones", type=int, default=2)
    parser.add_argument("--tiempo", type=float, default=120)
    parser.add_argument("--telas", nargs="*")
    parser.add_argument("--json", help="guardar la solución")
    args = parser.parse_args()

    preparacion = json.loads(Path(args.preparacion).read_text(encoding="utf-8"))
    if args.fusionar:
        representante, _ = clases_de_molde(preparacion, 0.1)
        preparacion = fusionar(preparacion, representante)
    eficiencias = {k: float(v) for k, v in (e.split("=") for e in args.eficiencia)}
    general = ParametrosCorte.desde_dict(preparacion["parametros"], excedente_maximo_por_item=args.tope)
    geos, _ = preparar_piezas(preparacion["piezas"], general)
    salida = []
    total = {"cortes": 0, "capas": 0, "tela": 0.0}
    print(f"MILP {'con' if args.fusionar else 'sin'} fusión | tope {args.tope} | λ {args.lambda_cm_por_min} cm/min"
          f" | h {args.h_cm} cm | R {args.repeticiones}")
    for grupo in preparacion["grupos"]:
        tela = grupo["codigoTipoTela"]
        if args.telas and tela not in args.telas:
            continue
        p = general.para_tela(tela)
        eta = eficiencias.get(tela, p.eficiencia_estimada)
        items = preparar_items(grupo, geos, p)
        colores = sorted({c for it in items for c in it["demanda"]})
        nombres = {c["idPaletaColor"]: c["nombreColor"] for it in grupo["items"] for c in it["porColor"]}
        elegidos, res, segundos = resolver_tela(items, colores, p, eta, args.h_cm, args.lambda_cm_por_min, args.tope,
                                                args.repeticiones, args.tiempo)
        if elegidos is None:
            print(f"  {tela}: sin solución ({res.message})")
            continue
        tela_est = sum((c["largo_est"] + args.h_cm) * sum(c["capas"].values()) for c in elegidos)
        capas = sum(sum(c["capas"].values()) for c in elegidos)
        excedente = 0
        for i, it in enumerate(items):
            for col, d in it["demanda"].items():
                excedente += sum(c["u"].get(i, 0) * c["capas"].get(col, 0) for c in elegidos) - d
        print(f"  {tela}: {len(elegidos)} cortes, {capas} capas, tela est. {tela_est:.0f} cm (h incluido), excedente"
              f" {excedente} | {res.message.split('.')[0]} en {segundos:.0f} s, gap {getattr(res, 'mip_gap', float('nan')):.2%}")
        for c in sorted(elegidos, key=lambda c: -c["largo_est"]):
            print(f"     [{','.join(f'{nombres[col]}={l}' for col, l in c['capas'].items() if l)}] {c['figuras']:2} figuras,"
                  f" largo est {c['largo_est']:6.1f} cm: " + " ".join(f"{items[i]['nombre']}×{u}" for i, u in sorted(c["u"].items())))
        total["cortes"] += len(elegidos)
        total["capas"] += capas
        total["tela"] += tela_est
        salida.append({"tela": tela, "cortes": [{**c, "u": {items[i]["nombre"]: u for i, u in c["u"].items()},
                                                 "capas": {nombres[col]: l for col, l in c["capas"].items() if l}}
                                                for c in elegidos]})
    print(f"  TOTAL: {total['cortes']} cortes, {total['capas']} capas, tela est. {total['tela']:.0f} cm")
    if args.json:
        Path(args.json).write_text(json.dumps(salida, ensure_ascii=False, indent=1), encoding="utf-8")


if __name__ == "__main__":
    main()
