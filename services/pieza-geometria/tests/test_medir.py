"""Test del script de medición: corre de punta a punta y escribe los dos CSV."""
import csv
import json
import os
import subprocess
import sys

from conftest import RAIZ
from test_planificador import ASIMETRICO, RECTANGULO, ROJO, _item, _pieza, _preparacion


def test_medir_escribe_los_csv_por_corte_y_por_corrida(tmp_path):
    preparacion = _preparacion([_pieza(1, RECTANGULO, True), _pieza(2, ASIMETRICO, False)],
                               [_item(1, {ROJO: 3}), _item(2, {ROJO: 4})])
    entrada = tmp_path / "preparacion.json"
    entrada.write_text(json.dumps(preparacion), encoding="utf-8")
    prefijo = tmp_path / "salida"
    salida = subprocess.run(
        [sys.executable, str(RAIZ / "scripts" / "medir.py"), str(entrada), "--presupuesto", "1",
         "--semilla", "3", "--csv", str(prefijo)],
        capture_output=True, text=True, encoding="utf-8", timeout=120,
        env={**os.environ, "PYTHONIOENCODING": "utf-8"})
    assert salida.returncode == 0, salida.stderr
    with open(f"{prefijo}_cortes.csv", encoding="utf-8") as archivo:
        cortes = list(csv.DictReader(archivo))
    with open(f"{prefijo}_corridas.csv", encoding="utf-8") as archivo:
        corridas = list(csv.DictReader(archivo))
    assert len(cortes) == 1 and cortes[0]["verificacion_ok"] == "True"
    assert float(cortes[0]["largo_final_cm"]) <= float(cortes[0]["largo_peor_cm"]) + 1e-6
    assert 0 < float(cortes[0]["eficiencia_real"]) <= 1
    assert corridas and corridas[0]["semilla"] == "3"
    assert cortes[0]["reinicios"] == str(len(corridas))
    capas = int(cortes[0]["capas_totales"])
    assert float(cortes[0]["tela_final_cm"]) == round(float(cortes[0]["largo_final_cm"]) * capas, 1) or \
        abs(float(cortes[0]["tela_final_cm"]) - float(cortes[0]["largo_final_cm"]) * capas) < 0.2
    assert corridas[0]["motivo_fin"] in ("GENERACIONES", "LIMITE_DE_TIEMPO")
    assert "Tanda:" in salida.stdout and "Tela (largo × capas)" in salida.stdout
    assert "Cortes que llegaron al límite de tiempo" in salida.stdout


def test_comparar_estancamiento_corre_cada_variante_con_las_mismas_semillas(tmp_path):
    preparacion = _preparacion([_pieza(1, RECTANGULO, True)], [_item(1, {ROJO: 3})])
    entrada = tmp_path / "preparacion.json"
    entrada.write_text(json.dumps(preparacion), encoding="utf-8")
    prefijo = tmp_path / "salida"
    salida = subprocess.run(
        [sys.executable, str(RAIZ / "scripts" / "medir.py"), str(entrada), "--presupuesto", "2",
         "--semillas", "3", "7", "--comparar-estancamiento", "50", "--csv", str(prefijo)],
        capture_output=True, text=True, encoding="utf-8", timeout=180,
        env={**os.environ, "PYTHONIOENCODING": "utf-8"})
    assert salida.returncode == 0, salida.stderr
    with open(f"{prefijo}_estancamiento.csv", encoding="utf-8") as archivo:
        filas = list(csv.DictReader(archivo))
    assert [(f["semilla"], f["variante"]) for f in filas] == [("3", "sin"), ("3", "N=50"), ("7", "sin"), ("7", "N=50")]
    assert all(f["diferencia_vs_sin"] == "0.0" for f in filas if f["variante"] == "sin")
    assert all(float(f["segundos_por_generacion"]) > 0 for f in filas)
    assert "| corte | variante |" in salida.stdout
    # Si se vuelve a correr (medición cortada), saltea lo ya medido y no repite filas.
    otra = subprocess.run(
        [sys.executable, str(RAIZ / "scripts" / "medir.py"), str(entrada), "--presupuesto", "2",
         "--semillas", "3", "7", "--comparar-estancamiento", "50", "--csv", str(prefijo)],
        capture_output=True, text=True, encoding="utf-8", timeout=180,
        env={**os.environ, "PYTHONIOENCODING": "utf-8"})
    assert otra.returncode == 0, otra.stderr
    assert "Retomando: 4 mediciones" in otra.stdout
    with open(f"{prefijo}_estancamiento.csv", encoding="utf-8") as archivo:
        assert len(list(csv.DictReader(archivo))) == 4
