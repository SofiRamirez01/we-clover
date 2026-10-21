"""Exploración (no es código de producción): eficiencia real según figuras, tela, tamaño y capas.

Uso: python scripts/explorar_eficiencia.py [CSV de medir.py ...] [--ga scripts/resultados/explorar_ga.csv]

Junta las tizadas medidas (una fila por corrida de ubicar_corte) y:
1. Tabla de eficiencia real por cantidad de figuras y por tela.
2. Prueba la hipótesis "pocas figuras (muchas capas) => menos eficiencia por capa" con el
   experimento controlado de explorar_ga.py (misma demanda en 1, 2, 3 o 6 capas).
3. Compara estimaciones del largo contra el largo real:
   M0 (la actual): largo = A / (ancho · η_tela), η fijo por tela.
   M1: largo = A / (ancho · η) + h, con h = pérdida fija por tizada (el borde irregular del final).
   M2: η = a + b · ln(figuras).
   A = área con margen por capa, la misma que usa el planificador (se reconstruye del largo
   estimado que guardó cada corrida: A = largo_estimado · ancho · η_supuesta).
Los ajustes son por mínimos cuadrados sobre todas las corridas (FRIZA, que es la que tiene datos).
"""
import argparse
import csv
import glob
import math
from pathlib import Path

import numpy as np

RESULTADOS = Path(__file__).resolve().parent / "resultados"
ANCHO = 90.0
ETA_ACTUAL = {"FRIZA": 0.66, "JERSEY": 0.65, "RIBB": 0.80}


def leer(medir_csvs, ga_csv):
    filas = []
    for ruta in medir_csvs:
        for f in csv.DictReader(open(ruta, encoding="utf-8")):
            capas = sum(int(x.split("=")[1]) for x in f["capas"].split(","))
            eta_sup = float(f["eficiencia_supuesta"])
            filas.append({"origen": Path(ruta).stem, "variante": f"corte {f['corte']}", "tela": f["tela"],
                          "figuras": int(f["entradas"]), "capas": capas, "largo": float(f["largo_final_cm"]),
                          "eficiencia": float(f["eficiencia_real"]),
                          "area_margen": float(f["largo_estimado_cm"]) * ANCHO * eta_sup})
    if ga_csv and Path(ga_csv).exists():
        for f in csv.DictReader(open(ga_csv, encoding="utf-8")):
            tela = "JERSEY" if "jersey" in f["experimento"] else "FRIZA"
            filas.append({"origen": f["experimento"], "variante": f["variante"], "tela": tela,
                          "figuras": int(f["entradas"]), "capas": int(f["capas"]), "largo": float(f["largo_final_cm"]),
                          "eficiencia": float(f["eficiencia"]),
                          "area_margen": float(f["largo_estimado_cm"]) * ANCHO * ETA_ACTUAL[tela]})
    return filas


def error(pred, real):
    pred, real = np.array(pred), np.array(real)
    rel = (pred - real) / real
    return f"error medio {np.mean(np.abs(rel)):5.1%}, máx {np.max(np.abs(rel)):5.1%}, sesgo {np.mean(rel):+5.1%}"


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("medir", nargs="*")
    parser.add_argument("--ga", default=str(RESULTADOS / "explorar_ga.csv"))
    args = parser.parse_args()
    medir = args.medir or sorted(glob.glob(str(RESULTADOS / "medicion_*_cortes.csv")))
    filas = leer(medir, args.ga)
    print(f"{len(filas)} tizadas medidas ({len(medir)} CSV de medir.py + {args.ga})\n")

    print("1) Eficiencia real por figuras (todas las corridas):")
    print("| tela | figuras | tizadas | eficiencia media | mín | máx |")
    print("|---|---|---|---|---|---|")
    for tela in sorted({f["tela"] for f in filas}):
        for desde, hasta in ((1, 6), (7, 10), (11, 20), (21, 35), (36, 99)):
            grupo = [f["eficiencia"] for f in filas if f["tela"] == tela and desde <= f["figuras"] <= hasta]
            if grupo:
                print(f"| {tela} | {desde}–{hasta} | {len(grupo)} | {np.mean(grupo):.1%} | {min(grupo):.1%} | {max(grupo):.1%} |")

    print("\n2) Experimento controlado (misma demanda, distinta cantidad de capas):")
    for origen in sorted({f["origen"] for f in filas if f["origen"].startswith(("eficiencia", "corte3", "excedente"))}):
        variantes = {}
        for f in filas:
            if f["origen"] == origen:
                variantes.setdefault(f["variante"], []).append(f)
        print(f"  {origen}:")
        for variante, grupo in variantes.items():
            tela = [g["largo"] * g["capas"] for g in grupo]
            print(f"    {variante:40} {grupo[0]['figuras']:2} figuras × {grupo[0]['capas']:2} capas | eficiencia"
                  f" {np.mean([g['eficiencia'] for g in grupo]):5.1%} | tela media {np.mean(tela):7.1f} cm"
                  f" (mín {min(tela):7.1f}, máx {max(tela):7.1f}, n={len(grupo)})")

    print("\n3) Estimación del largo (por corrida):")
    for tela in sorted({f["tela"] for f in filas}):
        grupo = [f for f in filas if f["tela"] == tela]
        A = np.array([f["area_margen"] for f in grupo])
        real = np.array([f["largo"] for f in grupo])
        capas = np.array([f["capas"] for f in grupo])
        figuras = np.array([f["figuras"] for f in grupo])
        print(f"  {tela} ({len(grupo)} corridas):")
        m0 = A / (ANCHO * ETA_ACTUAL[tela])
        print(f"    M0 actual  η={ETA_ACTUAL[tela]:.2f}          largo: {error(m0, real)} | tela: {error(m0 * capas, real * capas)}")
        if len(grupo) >= 4:
            X = np.column_stack([A / ANCHO, np.ones_like(A)])
            (pend, h), *_ = np.linalg.lstsq(X, real, rcond=None)
            m1 = A / ANCHO * pend + h
            print(f"    M1 η={1 / pend:.3f}, h={h:5.1f} cm  largo: {error(m1, real)} | tela: {error(m1 * capas, real * capas)}")
            eta_real = A / (ANCHO * real)
            X2 = np.column_stack([np.log(figuras), np.ones_like(A)])
            (b, a), *_ = np.linalg.lstsq(X2, eta_real, rcond=None)
            m2 = A / (ANCHO * (a + b * np.log(figuras)))
            print(f"    M2 η = {a:.3f} + {b:.3f}·ln(figuras)  largo: {error(m2, real)} | tela: {error(m2 * capas, real * capas)}")
            print(f"       (η aparente con margen: de {eta_real.min():.1%} a {eta_real.max():.1%};"
                  f" correlación η–ln(figuras) {np.corrcoef(np.log(figuras), eta_real)[0, 1]:+.2f})")


if __name__ == "__main__":
    main()
