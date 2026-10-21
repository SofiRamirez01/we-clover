"""Medición del algoritmo sobre los cortes del plan de una tanda (Módulo 4, paso 3).

Uso:
  python scripts/medir.py preparacion.json [--modo COMPLETO|RAPIDO] [--presupuesto S]
                          [--semilla 1] [--csv salida] [--cortes 1 4] [--comparar-v100 N]
  python scripts/medir.py preparacion.json --cortes 3 4 --semillas 1 101 --comparar-estancamiento 50 80
                          [--umbral-estancamiento-cm 0.1]
    Corte por estancamiento: cada corte, con cada semilla, sin estancamiento y con cada N, de a
    una corrida por vez; escribe <salida>_estancamiento.csv y una tabla con la diferencia de tela.

Reproducible: la semilla base es fija (por defecto 1) y cada reinicio usa semilla + n. Escribe
dos CSV: <salida>_cortes.csv (una fila por corte) y <salida>_corridas.csv (una fila por
reinicio), por defecto en scripts/resultados/. Por pantalla: por corte, la primera corrida (lo que daba una sola corrida), la mejor
y la peor, el largo final después de compactar, la eficiencia real (área de piezas / (ancho ×
largo)) frente a la supuesta y el largo estimado del planificador, y los tiempos.
"""
import argparse
import csv
import json
import subprocess
import sys
import time
from pathlib import Path

RAIZ = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(RAIZ))

from app.corte.parametros import ParametrosCorte  # noqa: E402
from app.corte.planificador import planificar  # noqa: E402
from app.corte.residuo import buscar_candidatas  # noqa: E402
from app.corte.tizada import NoSeUbica, armar_entradas, ga, limites_mm, ubicar_corte  # noqa: E402

REFERENCIA_MANUAL = "T6+T7 por un lado y T5+T4+T3 por otro"
RESULTADOS = RAIZ / "scripts" / "resultados"
COLUMNAS_CORTE = [
    "corte", "tela", "capas", "talles", "entradas", "presupuesto_s", "estado", "reinicios",
    "semilla_ganadora", "largo_estimado_cm", "largo_primera_cm", "largo_mejor_cm", "largo_peor_cm",
    "largo_sin_compactar_cm", "largo_final_cm", "eficiencia_real", "eficiencia_supuesta",
    "largo_final_vs_estimado", "capas_totales", "tela_estimada_cm", "tela_primera_cm", "tela_final_cm",
    "segundos", "verificacion_ok",
]
COLUMNAS_CORRIDA = ["corte", "reinicio", "semilla", "largo_cm", "generaciones", "motivo_fin", "segundos",
                    "estado", "verificacion_ok"]


def _orden(talle):
    return (0, int(talle)) if str(talle).isdigit() else (1, str(talle))


def _talles(corte):
    return "+".join("T" + t for t in sorted({ic.item.geo.talle for ic in corte.items}, key=_orden))


def comparar_v100(corte, p):
    """Validación de v1.0.0 frente a la actual, con las mismas piezas (todas enteras: v1.0.0 no
    conoce las mitades), la misma semilla y una sola corrida: inválidos, tiempo y largo final."""
    import contextlib
    import importlib.util
    import io
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
        for nombre, modulo, extra in (("v1.0.0", v100, {}), ("actual", ga, {"verbose": False, "mutation_rate_modo": 0})):
            cuenta = {"total": 0, "invalidas": 0}
            original = modulo.score_margin_positions

            def contar(*args, _original=original, **kwargs):
                valor = _original(*args, **kwargs)
                cuenta["total"] += 1
                cuenta["invalidas"] += valor <= -30000000000
                return valor

            modulo.score_margin_positions = contar
            random.seed(p.semilla or 0)
            inicio = time.perf_counter()
            with contextlib.redirect_stdout(io.StringIO()):
                mejor, _, _ = modulo.genetic_algorithm_margin_placement(
                    enteras, x_bounds=x_bounds, y_bounds=y_bounds, population_size=p.poblacion,
                    generations=p.generaciones, mutation_rate_x=p.tasa_mutacion_x, mutation_rate_y=p.tasa_mutacion_y,
                    mutation_scale_x=p.escala_mutacion_x_mm, mutation_scale_y_pos=p.escala_mutacion_y_pos_mm,
                    mutation_scale_y_neg=p.escala_mutacion_y_neg_mm, tournament_size=min(p.tamano_torneo, p.poblacion),
                    crossover_prob=p.probabilidad_cruce, mutation_rate_flip=p.tasa_rotacion, **extra)
            segundos = time.perf_counter() - inicio
            modulo.score_margin_positions = original
            filas.append(f"  {nombre:7} | {len(enteras)} piezas | {segundos:6.1f} s | individuos inválidos "
                         f"{cuenta['invalidas']}/{cuenta['total']} ({cuenta['invalidas'] / max(cuenta['total'], 1):.1%})"
                         f" | largo final {modulo._individual_max_y(mejor) / 10:.0f} cm")
        return filas
    finally:
        destino.unlink(missing_ok=True)


COLUMNAS_ESTANCAMIENTO = ["corte", "tela", "capas", "entradas", "semilla", "variante", "corridas", "motivos",
                          "estado", "largo_cm", "tela_cm", "segundos", "segundos_por_generacion", "verificacion_ok",
                          "diferencia_vs_sin"]


def _motivos(tz):
    abreviado = {"GENERACIONES": "GENE", "LIMITE_DE_TIEMPO": "LIMI", "ESTANCAMIENTO": "ESTA"}
    return " ".join(f"{abreviado.get(c.motivo_fin, c.motivo_fin)}@{c.generaciones}" for c in tz.corridas)


def comparar_estancamiento(plan, p, args, colores):
    """Cada corte elegido, con cada semilla, sin estancamiento y con cada N pedido (umbral
    --umbral-estancamiento-cm), de a una corrida por vez. Escribe <csv>_estancamiento.csv; si ya
    existe, saltea lo medido y sigue (para retomar una medición cortada)."""
    from dataclasses import replace

    variantes = [("sin", 0)] + [(f"N={n}", n) for n in args.comparar_estancamiento]
    ruta = f"{args.csv}_estancamiento.csv"
    filas = []
    if Path(ruta).exists():
        with open(ruta, encoding="utf-8") as archivo:
            filas = list(csv.DictReader(archivo))
        for fila in filas:
            fila["corte"], fila["semilla"], fila["corridas"] = int(fila["corte"]), int(fila["semilla"]), int(fila["corridas"])
            for clave in ("largo_cm", "tela_cm", "segundos", "diferencia_vs_sin"):
                fila[clave] = float(fila[clave])
        print(f"Retomando: {len(filas)} mediciones ya hechas en {ruta}", flush=True)
    hechas = {(f["corte"], f["semilla"], f["variante"]): f for f in filas}
    with open(ruta, "a", newline="", encoding="utf-8") as archivo:
        escritor = csv.DictWriter(archivo, fieldnames=COLUMNAS_ESTANCAMIENTO)
        if not filas:
            escritor.writeheader()
        for numero, corte in enumerate(plan.cortes, start=1):
            if args.cortes and numero not in args.cortes:
                continue
            capas = ",".join(f"{colores[c]}={l}" for c, l in corte.capas.items())
            entradas, _ = armar_entradas(corte, p)
            for semilla in args.semillas:
                previa = hechas.get((numero, semilla, "sin"))
                base = previa["tela_cm"] if previa else None
                for nombre, n in variantes:
                    if (numero, semilla, nombre) in hechas:
                        continue
                    pv = replace(p, semilla=semilla, generaciones_estancamiento=n,
                                 umbral_estancamiento_cm=args.umbral_estancamiento_cm)
                    tz = ubicar_corte(corte, pv)
                    tela = tz.largo_usado_cm * corte.capas_totales
                    base = tela if base is None else base
                    generaciones = sum(c.generaciones for c in tz.corridas)
                    fila = {"corte": numero, "tela": corte.codigo_tela, "capas": capas, "entradas": len(entradas),
                            "semilla": semilla, "variante": nombre, "corridas": tz.reinicios, "motivos": _motivos(tz),
                            "estado": tz.estado, "largo_cm": round(tz.largo_usado_cm, 2), "tela_cm": round(tela, 2),
                            "segundos": round(tz.segundos, 1),
                            "segundos_por_generacion": round(sum(c.segundos for c in tz.corridas) / max(generaciones, 1), 3),
                            "verificacion_ok": tz.verificacion_ok,
                            "diferencia_vs_sin": round(tela / base - 1, 4)}
                    escritor.writerow(fila)
                    archivo.flush()
                    filas.append(fila)
                    print(f"corte {numero} {corte.codigo_tela:6} [{capas}] semilla {semilla:>4} {nombre:5} | {tz.reinicios:2}"
                          f" corrida(s) {fila['motivos']:44} | largo {tz.largo_usado_cm:6.1f} cm, tela {tela:7.1f} cm"
                          f" ({fila['diferencia_vs_sin']:+.1%}) | {tz.segundos:4.0f} s"
                          f" ({fila['segundos_por_generacion']:.2f} s/gen) | {tz.estado}"
                          f" | verif {'ok' if tz.verificacion_ok else 'FALLÓ'}", flush=True)
    print(f"\nCSV: {ruta}")
    print("\n| corte | variante | corridas | mejor largo (cm) | tela (cm) | tiempo (s) | vs sin |")
    print("|---|---|---|---|---|---|---|")
    for numero in sorted({f["corte"] for f in filas}):
        for nombre, _ in variantes:
            grupo = [f for f in filas if f["corte"] == numero and f["variante"] == nombre]
            print(f"| {numero} | {nombre} | " + " / ".join(str(f["corridas"]) for f in grupo)
                  + " | " + " / ".join(f"{f['largo_cm']:.1f}" for f in grupo)
                  + " | " + " / ".join(f"{f['tela_cm']:.1f}" for f in grupo)
                  + " | " + " / ".join(f"{f['segundos']:.0f}" for f in grupo)
                  + " | " + " / ".join(f"{f['diferencia_vs_sin']:+.1%}" for f in grupo) + " |")


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("preparacion")
    parser.add_argument("--modo", default="COMPLETO", choices=["COMPLETO", "RAPIDO"])
    parser.add_argument("--presupuesto", type=float, help="segundos por corte (manda sobre --modo)")
    parser.add_argument("--semilla", type=int, default=1)
    parser.add_argument("--csv", help="prefijo de los CSV (default: scripts/resultados/medicion_tanda<id>_<fecha>)")
    parser.add_argument("--cortes", type=int, nargs="*", help="números de corte a medir (default: todos)")
    parser.add_argument("--comparar-v100", type=int, metavar="N", help="número de corte para comparar con v1.0.0")
    parser.add_argument("--comparar-estancamiento", type=int, nargs="+", metavar="N",
                        help="compara cada corte sin estancamiento y con cada N (mismas semillas)")
    parser.add_argument("--umbral-estancamiento-cm", type=float, default=0.1,
                        help="umbral del estancamiento para --comparar-estancamiento (default 0,1 cm = 1 mm)")
    parser.add_argument("--semillas", type=int, nargs="+", help="semillas base para --comparar-estancamiento"
                        " (default: --semilla)")
    args = parser.parse_args()

    preparacion = json.loads(Path(args.preparacion).read_text(encoding="utf-8"))
    if args.csv is None:
        RESULTADOS.mkdir(parents=True, exist_ok=True)
        args.csv = str(RESULTADOS / f"medicion_tanda{preparacion['idTanda']}_{time.strftime('%Y%m%d_%H%M')}")
    p = ParametrosCorte.desde_dict(preparacion["parametros"], modo_tiempo=args.modo, semilla=args.semilla,
                                   presupuesto_segundos_por_corte=args.presupuesto)
    plan = planificar(preparacion, p)
    colores = {c["idPaletaColor"]: c["nombreColor"]
               for g in preparacion["grupos"] for it in g["items"] for c in it["porColor"]}
    print(f"Tanda {preparacion['nombreTanda']}: {len(plan.cortes)} cortes | modo {p.modo_tiempo}, presupuesto"
          f" {p.presupuesto_segundos:.0f} s por corte, semilla base {p.semilla}, población {p.poblacion},"
          f" {p.generaciones} generaciones por corrida, corte anticipado tras {p.reinicios_sin_mejora}"
          f" reinicios sin mejorar más de {p.mejora_minima_reinicio:.0%}", flush=True)
    if args.comparar_estancamiento:
        args.semillas = args.semillas or [args.semilla]
        comparar_estancamiento(plan, p, args, colores)
        return

    filas_corte, filas_corrida = [], []
    inicio_tanda = time.perf_counter()
    for numero, corte in enumerate(plan.cortes, start=1):
        if args.cortes and numero not in args.cortes:
            continue
        capas = ",".join(f"{colores[c]}={l}" for c, l in corte.capas.items())
        entradas, _ = armar_entradas(corte, p)
        try:
            tz = ubicar_corte(corte, p)
        except NoSeUbica as error:
            print(f"corte {numero}: NO SE UBICA ({', '.join(ic.item.geo.nombre for ic in error.items[:3])})", flush=True)
            continue
        largos = [c.largo_cm for c in tz.corridas]
        fila = {
            "corte": numero, "tela": corte.codigo_tela, "capas": capas, "talles": _talles(corte),
            "entradas": len(entradas), "presupuesto_s": tz.presupuesto_segundos, "estado": tz.estado,
            "reinicios": tz.reinicios, "semilla_ganadora": tz.semilla_ganadora,
            "largo_estimado_cm": round(corte.largo_estimado_cm, 1), "largo_primera_cm": round(largos[0], 1),
            "largo_mejor_cm": round(min(largos), 1), "largo_peor_cm": round(max(largos), 1),
            "largo_sin_compactar_cm": round(tz.largo_sin_compactar_cm, 1), "largo_final_cm": round(tz.largo_usado_cm, 1),
            "eficiencia_real": round(tz.eficiencia, 4), "eficiencia_supuesta": p.eficiencia_estimada,
            "largo_final_vs_estimado": round(tz.largo_usado_cm / corte.largo_estimado_cm - 1, 4),
            "capas_totales": corte.capas_totales,
            "tela_estimada_cm": round(corte.largo_estimado_cm * corte.capas_totales, 1),
            "tela_primera_cm": round(largos[0] * corte.capas_totales, 1),
            "tela_final_cm": round(tz.largo_usado_cm * corte.capas_totales, 1),
            "segundos": round(tz.segundos, 1), "verificacion_ok": tz.verificacion_ok,
        }
        filas_corte.append(fila)
        for indice, c in enumerate(tz.corridas, start=1):
            filas_corrida.append({"corte": numero, "reinicio": indice, "semilla": c.semilla,
                                  "largo_cm": round(c.largo_cm, 1), "generaciones": c.generaciones,
                                  "motivo_fin": c.motivo_fin, "segundos": round(c.segundos, 1), "estado": c.estado,
                                  "verificacion_ok": c.verificacion_ok})
        print(f"corte {numero}: {corte.codigo_tela:6} [{capas}] {fila['talles']:22} {len(entradas):2} entradas"
              f" | {tz.estado:16} {tz.reinicios} corrida(s), gana semilla {tz.semilla_ganadora}"
              f" | primera {largos[0]:5.0f} · mejor {min(largos):5.0f} · peor {max(largos):5.0f}"
              f" · final {tz.largo_usado_cm:5.0f} cm (est. {corte.largo_estimado_cm:4.0f})"
              f" × {corte.capas_totales} capa(s) = tela {fila['tela_final_cm']:6.0f} cm"
              f" | efic. real {tz.eficiencia:5.1%} | {tz.segundos:5.0f} s | verif. {'ok' if tz.verificacion_ok else 'FALLÓ'}",
              flush=True)
        for advertencia in tz.advertencias:
            print(f"   ADVERTENCIA: {advertencia}", flush=True)
    total = time.perf_counter() - inicio_tanda

    with open(f"{args.csv}_cortes.csv", "w", newline="", encoding="utf-8") as archivo:
        escritor = csv.DictWriter(archivo, fieldnames=COLUMNAS_CORTE)
        escritor.writeheader()
        escritor.writerows(filas_corte)
    with open(f"{args.csv}_corridas.csv", "w", newline="", encoding="utf-8") as archivo:
        escritor = csv.DictWriter(archivo, fieldnames=COLUMNAS_CORRIDA)
        escritor.writeheader()
        escritor.writerows(filas_corrida)

    if filas_corte:
        efic = [f["eficiencia_real"] for f in filas_corte]
        print(f"\nTanda: {total:.0f} s ({total / 60:.1f} min) | eficiencia real de {min(efic):.1%} a {max(efic):.1%}"
              f" (supuesta {p.eficiencia_estimada:.0%}) | largo total final"
              f" {sum(f['largo_final_cm'] for f in filas_corte):.0f} cm frente a"
              f" {sum(f['largo_primera_cm'] for f in filas_corte):.0f} cm de una sola corrida"
              f" y {sum(f['largo_estimado_cm'] for f in filas_corte):.0f} cm estimados")
        print(f"Tela (largo × capas): final {sum(f['tela_final_cm'] for f in filas_corte):.0f} cm frente a"
              f" {sum(f['tela_primera_cm'] for f in filas_corte):.0f} cm de una sola corrida"
              f" y {sum(f['tela_estimada_cm'] for f in filas_corte):.0f} cm estimados")
        al_limite = [str(f["corte"]) for f in filas_corte if f["estado"] == "LIMITE_DE_TIEMPO"]
        print(f"Cortes que llegaron al límite de tiempo ({p.presupuesto_segundos:.0f} s): "
              + (", ".join(al_limite) if al_limite else "ninguno"))
    print(f"CSV: {args.csv}_cortes.csv y {args.csv}_corridas.csv")

    print(f"\nAgrupación de talles del planificador (referencia manual: {REFERENCIA_MANUAL}):")
    for numero, corte in enumerate(plan.cortes, start=1):
        print(f"  corte {numero}: {corte.codigo_tela:6} {_talles(corte)}")
    print("\nSustituciones candidatas sobre el residuo (NO aplicadas):")
    candidatas = buscar_candidatas(plan, p)
    for c in candidatas:
        print(f"  {c['tipo']:17} {c['nombreOrigen']} (corte {c['corteOrigen']}) con el molde de {c['nombreUsada']}"
              f" (corte {c['corteUsado']}) | aprovechamiento {c['aprovechamiento']:.1%} | desperdicio"
              f" {c['desperdicioCm2']:.0f} cm² | recorte {c['largoRecorteCm']:.0f} cm")
    if not candidatas:
        print("  ninguna")
    if args.comparar_v100:
        print(f"\nValidación v1.0.0 frente a la actual (corte {args.comparar_v100}, todas enteras, una corrida):")
        for fila in comparar_v100(plan.cortes[args.comparar_v100 - 1], p):
            print(fila, flush=True)


if __name__ == "__main__":
    main()
