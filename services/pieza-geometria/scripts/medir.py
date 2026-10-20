"""Medición del algoritmo genético sobre los cortes del plan (Parte 3, punto de control).

Uso: python scripts/medir.py preparacion.json [--poblacion 100] [--generaciones 200]
                             [--limite 120] [--ampliar] [--comparar-v100 N]

Por cada corte: entradas, tiempo, generaciones, largo usado frente al estimado, eficiencia y
verificación. Con --limite, un corte que pasa esos segundos se cancela y se reporta la
proyección a todas las generaciones. Con --ampliar, si un corte no arma la población inicial en
800 cm se mide con el lienzo al doble (solo para ver hasta dónde compacta). Al final: talles
agrupados frente a la referencia manual y sustituciones candidatas (sin aplicar).
"""
import argparse
import dataclasses
import json
import subprocess
import sys
import threading
import time
from pathlib import Path

RAIZ = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(RAIZ))

from app.corte.parametros import ParametrosCorte  # noqa: E402
from app.corte.planificador import planificar  # noqa: E402
from app.corte.residuo import buscar_candidatas  # noqa: E402
from app.corte.tizada import NoSeUbica, armar_entradas, ga, limites_mm, ubicar_corte  # noqa: E402

REFERENCIA_MANUAL = "T6+T7 por un lado y T5+T4+T3 por otro"


def _orden(talle):
    return (0, int(talle)) if str(talle).isdigit() else (1, str(talle))


def medir_corte(corte, p, limite):
    cancelado = threading.Event()
    avance = {"gen": 0, "largo": None, "t_gen0": None}
    inicio = time.perf_counter()

    def on_generation(gen, total, fitness, largo_cm):
        ahora = time.perf_counter()
        if avance["t_gen0"] is None:
            avance["t_gen0"] = ahora  # fin de la población inicial + generación 0
        avance.update(gen=gen + 1, largo=largo_cm)
        if limite and ahora - inicio > limite:
            cancelado.set()

    try:
        tizada = ubicar_corte(corte, p, on_generation=on_generation, cancelado=cancelado)
        return {"estado": "OK", "tizada": tizada, "segundos": tizada.segundos, "avance": avance}
    except ga.CorteCancelado:
        return {"estado": "CANCELADO", "segundos": time.perf_counter() - inicio, "avance": avance}
    except NoSeUbica as error:
        return {"estado": "NO_SE_UBICA", "segundos": time.perf_counter() - inicio, "avance": avance,
                "items": [f"{ic.item.geo.nombre} T{ic.item.geo.talle}" for ic in error.items[:3]]}


def _linea(numero, corte, p, medicion, colores):
    capas = ",".join(f"{colores[c]}={l}" for c, l in corte.capas.items())
    talles = "+".join("T" + t for t in sorted({ic.item.geo.talle for ic in corte.items}, key=_orden))
    entradas, _ = armar_entradas(corte, p)
    cabeza = (f"corte {numero}: {corte.codigo_tela:6} [{capas}] {talles:22} {len(entradas):3} entradas"
              f" | est. {corte.largo_estimado_cm:5.0f} cm")
    av = medicion["avance"]
    if medicion["estado"] == "OK":
        tz = medicion["tizada"]
        return (cabeza + f" | {tz.segundos:6.1f} s ({tz.generaciones} gen) | largo {tz.largo_usado_cm:5.0f} cm"
                f" | efic. {tz.eficiencia:5.1%} | verif. {'ok' if tz.verificacion_ok else 'FALLÓ'}"
                f" (intentos {tz.intentos})")
    if medicion["estado"] == "CANCELADO":
        por_gen = medicion["segundos"] / max(av["gen"], 1)
        area = sum(ic.area_por_capa_cm2 for ic in corte.items)
        return (cabeza + f" | CANCELADO a los {medicion['segundos']:.0f} s en gen {av['gen']}/{p.generaciones}"
                f" (~{por_gen:.1f} s/gen → ~{por_gen * p.generaciones / 60:.0f} min las {p.generaciones})"
                f" | mejor largo hasta ahí {av['largo'] or 0:5.0f} cm")
    return cabeza + (f" | NO ARMA LA POBLACIÓN INICIAL ({medicion['segundos']:.0f} s); fallan sobre todo:"
                     f" {', '.join(medicion['items'])}")


def comparar_v100(corte, p):
    """Validación de v1.0.0 frente a la nueva, con las mismas piezas (todas enteras: v1.0.0 no
    conoce las mitades) y la misma semilla: individuos inválidos, tiempo y largo final."""
    import importlib.util
    import random

    codigo = subprocess.run(["git", "show", "c703f7b:./algorpatronsnap.py"], cwd=RAIZ / "vendor/OptimizacionCorteTextil",
                            capture_output=True, text=True, encoding="utf-8", check=True).stdout
    destino = RAIZ / ".v100_algorpatronsnap.py"
    destino.write_text(codigo, encoding="utf-8")
    try:
        spec = importlib.util.spec_from_file_location("v100", destino)
        v100 = importlib.util.module_from_spec(spec)
        sys.modules["v100"] = v100
        spec.loader.exec_module(v100)
        entradas, _ = armar_entradas(corte, p)
        enteras = [{"name": e["name"], "polygon": e["polygon"], "simetrico": e["simetrico"]}
                   for e in entradas if "polygon" in e]
        x_bounds, y_bounds, _ = limites_mm(enteras, p)
        filas = []
        for nombre, modulo, extra in (("v1.0.0", v100, {}), ("nueva", ga, {"verbose": False, "mutation_rate_modo": 0})):
            evaluaciones = {"total": 0, "invalidas": 0}
            original = modulo.score_margin_positions

            def contar(*args, _original=original, **kwargs):
                valor = _original(*args, **kwargs)
                evaluaciones["total"] += 1
                evaluaciones["invalidas"] += valor <= -30000000000
                return valor

            modulo.score_margin_positions = contar
            random.seed(p.semilla or 0)
            inicio = time.perf_counter()
            import contextlib
            import io
            with contextlib.redirect_stdout(io.StringIO()):
                mejor, _, _ = modulo.genetic_algorithm_margin_placement(
                    enteras, x_bounds=x_bounds, y_bounds=y_bounds, population_size=p.poblacion,
                    generations=p.generaciones, mutation_rate_x=p.tasa_mutacion_x, mutation_rate_y=p.tasa_mutacion_y,
                    mutation_scale_x=p.escala_mutacion_x_mm, mutation_scale_y_pos=p.escala_mutacion_y_pos_mm,
                    mutation_scale_y_neg=p.escala_mutacion_y_neg_mm, tournament_size=min(p.tamano_torneo, p.poblacion),
                    crossover_prob=p.probabilidad_cruce, mutation_rate_flip=p.tasa_rotacion, **extra)
            segundos = time.perf_counter() - inicio
            modulo.score_margin_positions = original
            largo = modulo._individual_max_y(mejor) / 10
            filas.append(f"  {nombre:7} | {len(enteras)} piezas | {segundos:6.1f} s | individuos inválidos "
                         f"{evaluaciones['invalidas']}/{evaluaciones['total']} "
                         f"({evaluaciones['invalidas'] / max(evaluaciones['total'], 1):.1%}) | largo final {largo:.0f} cm")
        return filas
    finally:
        destino.unlink(missing_ok=True)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("preparacion")
    parser.add_argument("--poblacion", type=int, default=100)
    parser.add_argument("--generaciones", type=int, default=200)
    parser.add_argument("--limite", type=float, default=0, help="segundos máximos por corte (0 = sin límite)")
    parser.add_argument("--ampliar", action="store_true")
    parser.add_argument("--cortes", type=int, nargs="*", help="números de corte a medir (default: todos)")
    parser.add_argument("--comparar-v100", type=int, metavar="N", help="número de corte para comparar con v1.0.0")
    parser.add_argument("--semilla", type=int, default=1)
    args = parser.parse_args()

    preparacion = json.loads(Path(args.preparacion).read_text(encoding="utf-8"))
    p = ParametrosCorte.desde_dict(preparacion["parametros"], poblacion=args.poblacion,
                                   generaciones=args.generaciones, semilla=args.semilla)
    plan = planificar(preparacion, p)
    colores = {c["idPaletaColor"]: c["nombreColor"]
               for g in preparacion["grupos"] for it in g["items"] for c in it["porColor"]}
    print(f"Tanda {preparacion['nombreTanda']}: {len(plan.cortes)} cortes | población {p.poblacion},"
          f" {p.generaciones} generaciones, límite {args.limite or 'sin'} s por corte", flush=True)
    for numero, corte in enumerate(plan.cortes, start=1):
        if args.cortes and numero not in args.cortes:
            continue
        medicion = medir_corte(corte, p, args.limite)
        print(_linea(numero, corte, p, medicion, colores), flush=True)
        if medicion["estado"] == "NO_SE_UBICA" and args.ampliar:
            ampliado = dataclasses.replace(p, largo_maximo_cm=p.largo_maximo_cm * 2)
            medicion = medir_corte(corte, ampliado, args.limite)
            print("   con lienzo de 1600 cm → " + _linea(numero, corte, ampliado, medicion, colores).split(" | ", 2)[-1],
                  flush=True)

    print(f"\nAgrupación de talles del planificador (referencia manual: {REFERENCIA_MANUAL}):")
    for numero, corte in enumerate(plan.cortes, start=1):
        talles = "+".join("T" + t for t in sorted({ic.item.geo.talle for ic in corte.items}, key=_orden))
        print(f"  corte {numero}: {corte.codigo_tela:6} {talles}")

    print("\nSustituciones candidatas sobre el residuo (NO aplicadas):")
    candidatas = buscar_candidatas(plan, p)
    for c in candidatas:
        print(f"  {c['tipo']:17} {c['nombreOrigen']} (corte {c['corteOrigen']}) con el molde de {c['nombreUsada']}"
              f" (corte {c['corteUsado']}) | aprovechamiento {c['aprovechamiento']:.1%} | desperdicio"
              f" {c['desperdicioCm2']:.0f} cm² | recorte {c['largoRecorteCm']:.0f} cm")
    if not candidatas:
        print("  ninguna")

    if args.comparar_v100:
        print(f"\nValidación v1.0.0 frente a la nueva (corte {args.comparar_v100}, todas enteras, misma semilla):")
        for fila in comparar_v100(plan.cortes[args.comparar_v100 - 1], p):
            print(fila, flush=True)


if __name__ == "__main__":
    main()
