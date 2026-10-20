"""Tests de la API de trabajos de corte (cola, avance, cancelación y vencimiento)."""
import time

import pytest
from fastapi.testclient import TestClient

from app.corte import api as corte_api
from app.corte.trabajos import GestorTrabajos
from app.main import app
from test_planificador import ASIMETRICO, RECTANGULO, ROJO, _item, _pieza, _preparacion

RAPIDO = {"poblacion": 6, "generaciones": 3, "tamanoTorneo": 3, "semilla": 1, "presupuestoSegundosPorCorte": 2}
LARGO = {"poblacion": 20, "generaciones": 100000, "tamanoTorneo": 3, "semilla": 1, "presupuestoSegundosPorCorte": 30}


class Reloj:
    def __init__(self):
        self.desfase = 0.0

    def __call__(self):
        return time.time() + self.desfase


@pytest.fixture
def entorno():
    reloj = Reloj()
    gestor = GestorTrabajos(vencimiento_segundos=600, reloj=reloj)
    app.dependency_overrides[corte_api.obtener_gestor] = lambda: gestor
    with TestClient(app) as cliente:
        yield cliente, gestor, reloj
    gestor.apagar()
    app.dependency_overrides.clear()


def _preparacion_simple():
    return _preparacion([_pieza(1, RECTANGULO, True), _pieza(2, ASIMETRICO, False)],
                        [_item(1, {ROJO: 3}), _item(2, {ROJO: 4})])


def _crear(cliente, parametros, preparacion=None):
    respuesta = cliente.post("/corte/trabajos", json={"preparacion": preparacion or _preparacion_simple(),
                                                      "parametros": parametros})
    assert respuesta.status_code == 202, respuesta.text
    return respuesta.json()


def _esperar(cliente, id_trabajo, estados, limite=60):
    fin = time.monotonic() + limite
    while time.monotonic() < fin:
        vista = cliente.get(f"/corte/trabajos/{id_trabajo}").json()
        if vista["estado"] in estados:
            return vista
        time.sleep(0.05)
    raise AssertionError(f"el trabajo no llegó a {estados}: {vista}")


def test_ciclo_completo_y_resultado(entorno):
    cliente, _, _ = entorno
    creado = _crear(cliente, RAPIDO)
    assert creado["estado"] in ("EN_COLA", "EJECUTANDO")
    vista = _esperar(cliente, creado["idTrabajo"], ("TERMINADO", "ERROR"))
    assert vista["estado"] == "TERMINADO", vista.get("error")
    resultado = vista["resultado"]
    (corte,) = resultado["cortes"]
    assert corte["anchoCm"] == 90 and corte["largoUsadoCm"] > 0 and 0 < corte["eficiencia"] <= 1
    assert corte["estado"] in ("COMPLETO", "LIMITE_DE_TIEMPO") and corte["verificacionOk"]
    assert corte["capasPorColor"][0]["idPaletaColor"] == ROJO and corte["sustituciones"] == []
    pieza = corte["piezas"][0]
    assert {"idPieza", "idTalle", "copia", "modo", "rotacion", "poligonoCm"} <= set(pieza)
    assert pieza["modo"] in ("ENTERA", "MITAD_AL_DOBLEZ") and pieza["rotacion"] in (0, 180)
    item = next(it for it in corte["items"] if it["idPieza"] == 1)
    assert item["demanda"][0]["cantidad"] == 3 and item["produccion"][0]["cantidad"] >= 3
    assert corte["corridas"] and corte["corridas"][0]["motivoFin"] in ("GENERACIONES", "LIMITE_DE_TIEMPO")
    assert resultado["noResueltas"] == [] and resultado["resumen"]["cortes"] == 1
    assert vista["progreso"]["cortesTotales"] == 1 and vista["venceEn"] is not None


def test_uno_por_vez_y_cancelar_el_que_espera_en_cola(entorno):
    cliente, _, _ = entorno
    primero = _crear(cliente, LARGO)
    segundo = _crear(cliente, RAPIDO)
    _esperar(cliente, primero["idTrabajo"], ("EJECUTANDO",))
    vista = cliente.get(f"/corte/trabajos/{segundo['idTrabajo']}").json()
    assert vista["estado"] == "EN_COLA" and vista["posicionEnCola"] == 1
    cancelado = cliente.delete(f"/corte/trabajos/{segundo['idTrabajo']}").json()
    assert cancelado["estado"] == "CANCELADO" and cancelado["iniciado"] is None
    cliente.delete(f"/corte/trabajos/{primero['idTrabajo']}")
    _esperar(cliente, primero["idTrabajo"], ("CANCELADO",))
    time.sleep(0.3)  # el trabajador ya no lo toma
    assert cliente.get(f"/corte/trabajos/{segundo['idTrabajo']}").json()["iniciado"] is None


def test_cancelar_el_que_esta_corriendo_y_el_avance(entorno):
    cliente, _, _ = entorno
    creado = _crear(cliente, LARGO)
    fin = time.monotonic() + 30
    while time.monotonic() < fin:
        vista = cliente.get(f"/corte/trabajos/{creado['idTrabajo']}").json()
        if vista["progreso"]["generacionActual"] > 0:
            break
        time.sleep(0.05)
    progreso = vista["progreso"]
    assert vista["estado"] == "EJECUTANDO"
    assert progreso["corteActual"] == 1 and progreso["corridaActual"] == 1 and progreso["semillaActual"] == 1
    assert progreso["mejorLargoCm"] > 0 and 0 < progreso["mejorEficiencia"] <= 1
    assert progreso["segundosTranscurridos"] >= 0
    pedido = time.monotonic()
    assert cliente.delete(f"/corte/trabajos/{creado['idTrabajo']}").status_code == 200
    vista = _esperar(cliente, creado["idTrabajo"], ("CANCELADO",), limite=10)
    assert time.monotonic() - pedido < 10 and "resultado" not in vista


def test_no_se_cancela_un_trabajo_terminado_y_404_si_no_existe(entorno):
    cliente, _, _ = entorno
    creado = _crear(cliente, RAPIDO)
    _esperar(cliente, creado["idTrabajo"], ("TERMINADO",))
    assert cliente.delete(f"/corte/trabajos/{creado['idTrabajo']}").status_code == 409
    assert cliente.get("/corte/trabajos/no-existe").status_code == 404
    assert cliente.delete("/corte/trabajos/no-existe").status_code == 404


def test_errores_de_la_solicitud_dan_422(entorno):
    cliente, _, _ = entorno
    vacia = _preparacion([_pieza(1, RECTANGULO, True)], [])
    assert cliente.post("/corte/trabajos", json={"preparacion": vacia}).status_code == 422
    assert cliente.post("/corte/trabajos", json={"preparacion": {"grupos": []}}).status_code == 422
    malos = cliente.post("/corte/trabajos", json={"preparacion": _preparacion_simple(),
                                                  "parametros": {"modoTiempo": "LENTO"}})
    assert malos.status_code == 422 and "modoTiempo" in malos.json()["detail"]


def test_un_trabajo_que_falla_queda_en_error_con_el_motivo(entorno):
    cliente, _, _ = entorno
    rota = _preparacion_simple()
    rota["piezas"][0]["talles"][0]["coordenadas"] = [[0, 0], [1, 1]]  # no es un polígono
    creado = _crear(cliente, RAPIDO, rota)
    vista = _esperar(cliente, creado["idTrabajo"], ("ERROR", "TERMINADO"))
    assert vista["estado"] == "ERROR" and vista["error"]


def test_los_resultados_vencen(entorno):
    cliente, gestor, reloj = entorno
    creado = _crear(cliente, RAPIDO)
    _esperar(cliente, creado["idTrabajo"], ("TERMINADO",))
    reloj.desfase = 599
    assert cliente.get(f"/corte/trabajos/{creado['idTrabajo']}").status_code == 200
    reloj.desfase = 601
    assert cliente.get(f"/corte/trabajos/{creado['idTrabajo']}").status_code == 404


def test_en_rapido_el_presupuesto_no_pasa_de_60_segundos(entorno):
    cliente, _, _ = entorno
    creado = _crear(cliente, {**RAPIDO, "modoTiempo": "RAPIDO", "presupuestoSegundosPorCorte": 150})
    vista = _esperar(cliente, creado["idTrabajo"], ("TERMINADO",))
    assert vista["resultado"]["parametros"]["presupuestoSegundosPorCorte"] == 60
    assert vista["resultado"]["cortes"][0]["presupuestoSegundos"] == 60
