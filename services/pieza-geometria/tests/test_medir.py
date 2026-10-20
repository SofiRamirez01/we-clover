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
