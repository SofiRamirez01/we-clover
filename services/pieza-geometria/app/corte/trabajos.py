"""Trabajos de optimización en memoria: uno por vez, con cola, avance, cancelación y vencimiento.

El servidor es de 1 vCPU y 2 GB: un único hilo trabajador corre los trabajos de a uno, en orden de
llegada. Los trabajos viven en memoria (se pierden si se reinicia el proceso), por eso Uvicorn
tiene que correr con un solo worker. Un trabajo terminado, con error o cancelado se borra
`vencimiento_segundos` después de terminar.
"""
import logging
import os
import queue
import threading
import time
import traceback
import uuid
from dataclasses import dataclass, field

from app.corte.parametros import ParametrosCorte
from app.corte.planificador import planificar
from app.corte.residuo import optimizar_residuo
from app.corte.serializacion import resultado_a_dict
from app.corte.tizada import ga, optimizar

log = logging.getLogger(__name__)

EN_COLA = "EN_COLA"
EJECUTANDO = "EJECUTANDO"
TERMINADO = "TERMINADO"
ERROR = "ERROR"
CANCELADO = "CANCELADO"
FINALES = (TERMINADO, ERROR, CANCELADO)

VENCIMIENTO_SEGUNDOS = float(os.environ.get("CORTE_VENCIMIENTO_MINUTOS", "60")) * 60


class TrabajoNoEncontrado(KeyError):
    pass


class TrabajoYaTerminado(Exception):
    def __init__(self, estado):
        super().__init__(estado)
        self.estado = estado


@dataclass
class Trabajo:
    id: str
    preparacion: dict
    parametros: ParametrosCorte
    estado: str = EN_COLA
    creado: float = 0.0
    iniciado: float | None = None
    terminado: float | None = None
    progreso: dict = field(default_factory=dict)
    resultado: dict | None = None
    error: str | None = None
    cancelado: threading.Event = field(default_factory=threading.Event)
    id_tanda: int | None = None  # de la preparación: el backend lo usa para no mezclar tandas


class GestorTrabajos:
    def __init__(self, vencimiento_segundos: float = VENCIMIENTO_SEGUNDOS, reloj=time.time):
        self._vencimiento = vencimiento_segundos
        self._reloj = reloj
        self._trabajos: dict[str, Trabajo] = {}
        self._cola: queue.Queue = queue.Queue()
        self._lock = threading.Lock()
        self._hilo: threading.Thread | None = None
        self._apagando = threading.Event()

    # --- API pública ----------------------------------------------------------------------

    def crear(self, preparacion: dict, parametros: ParametrosCorte) -> Trabajo:
        trabajo = Trabajo(uuid.uuid4().hex, preparacion, parametros, creado=self._reloj(),
                          id_tanda=preparacion.get("idTanda"))
        trabajo.progreso = {"corteActual": 0, "cortesTotales": None, "corridaActual": 0, "semillaActual": None,
                            "generacionActual": 0, "generacionesTotales": parametros.generaciones,
                            "mejorLargoCm": None, "mejorEficiencia": None, "segundosTranscurridos": 0.0}
        with self._lock:
            self._purgar()
            self._trabajos[trabajo.id] = trabajo
        self._cola.put(trabajo.id)
        self._asegurar_hilo()
        return trabajo

    def consultar(self, id_trabajo: str) -> dict:
        with self._lock:
            self._purgar()
            trabajo = self._trabajos.get(id_trabajo)
            if trabajo is None:
                raise TrabajoNoEncontrado(id_trabajo)
            return self._vista(trabajo)

    def cancelar(self, id_trabajo: str) -> dict:
        with self._lock:
            self._purgar()
            trabajo = self._trabajos.get(id_trabajo)
            if trabajo is None:
                raise TrabajoNoEncontrado(id_trabajo)
            if trabajo.estado in FINALES:
                raise TrabajoYaTerminado(trabajo.estado)
            trabajo.cancelado.set()
            if trabajo.estado == EN_COLA:  # nunca arrancó: queda cancelado ya
                trabajo.estado = CANCELADO
                trabajo.terminado = self._reloj()
            return self._vista(trabajo)

    def apagar(self, espera: float = 5.0):
        """Cancela lo que esté corriendo y frena el hilo trabajador (al apagar el servicio)."""
        self._apagando.set()
        with self._lock:
            for trabajo in self._trabajos.values():
                if trabajo.estado not in FINALES:
                    trabajo.cancelado.set()
        self._cola.put(None)
        if self._hilo is not None:
            self._hilo.join(espera)

    # --- Internos -------------------------------------------------------------------------

    def _posicion(self, trabajo):
        if trabajo.estado != EN_COLA:
            return None
        en_cola = sorted((t for t in self._trabajos.values() if t.estado == EN_COLA), key=lambda t: t.creado)
        return 1 + [t.id for t in en_cola].index(trabajo.id)

    def _vista(self, trabajo: Trabajo) -> dict:
        ahora = self._reloj()
        progreso = dict(trabajo.progreso)
        if trabajo.iniciado is not None:
            progreso["segundosTranscurridos"] = round((trabajo.terminado or ahora) - trabajo.iniciado, 1)
        vista = {"idTrabajo": trabajo.id, "idTanda": trabajo.id_tanda, "estado": trabajo.estado,
                 "posicionEnCola": self._posicion(trabajo),
                 "progreso": progreso, "creado": trabajo.creado, "iniciado": trabajo.iniciado,
                 "terminado": trabajo.terminado,
                 "venceEn": trabajo.terminado + self._vencimiento if trabajo.terminado is not None else None}
        if trabajo.estado == TERMINADO:
            vista["resultado"] = trabajo.resultado
        if trabajo.estado == ERROR:
            vista["error"] = trabajo.error
        return vista

    def _purgar(self):
        ahora = self._reloj()
        vencidos = [t.id for t in self._trabajos.values()
                    if t.terminado is not None and ahora - t.terminado > self._vencimiento]
        for id_trabajo in vencidos:
            del self._trabajos[id_trabajo]

    def _asegurar_hilo(self):
        with self._lock:
            if self._hilo is None or not self._hilo.is_alive():
                self._hilo = threading.Thread(target=self._trabajar, name="corte-trabajos", daemon=True)
                self._hilo.start()

    def _trabajar(self):
        while not self._apagando.is_set():
            id_trabajo = self._cola.get()
            if id_trabajo is None:
                return
            with self._lock:
                trabajo = self._trabajos.get(id_trabajo)
                if trabajo is None or trabajo.estado != EN_COLA:  # cancelado o vencido en la cola
                    continue
                trabajo.estado = EJECUTANDO
                trabajo.iniciado = self._reloj()
            self._ejecutar(trabajo)

    def _actualizar(self, trabajo, **valores):
        with self._lock:
            trabajo.progreso.update(valores)

    def _ejecutar(self, trabajo: Trabajo):
        p = trabajo.parametros
        inicio = time.monotonic()
        area_por_corte = {}

        def on_corte(numero, total, corte):
            area = sum((ic.ranuras + ic.enteras) * ic.item.geo.poligono.area
                       + ic.mitades * ic.item.geo.poligono.area / 2 for ic in corte.items)
            area_por_corte["actual"] = (area, p.ancho_util_cm)
            self._actualizar(trabajo, corteActual=numero, cortesTotales=total, corridaActual=0,
                             generacionActual=0, mejorLargoCm=None, mejorEficiencia=None)

        def on_corrida(numero, semilla):
            self._actualizar(trabajo, corridaActual=numero, semillaActual=semilla, generacionActual=0)

        def on_generation(gen, total, mejor_fitness, mejor_largo_cm):
            area, ancho = area_por_corte.get("actual", (0.0, p.ancho_util_cm))
            self._actualizar(trabajo, generacionActual=gen + 1, generacionesTotales=total,
                             mejorLargoCm=round(mejor_largo_cm, 1),
                             mejorEficiencia=round(area / (ancho * mejor_largo_cm), 4) if mejor_largo_cm > 0 else None)

        try:
            plan = optimizar_residuo(planificar(trabajo.preparacion, p), p)
            resultado = optimizar(trabajo.preparacion, p, plan=plan, on_corte=on_corte,
                                  on_generation=on_generation, cancelado=trabajo.cancelado, on_corrida=on_corrida)
            datos = resultado_a_dict(resultado, p, time.monotonic() - inicio)
            estado, error = TERMINADO, None
        except ga.CorteCancelado:
            datos, estado, error = None, CANCELADO, None
        except Exception as excepcion:  # noqa: BLE001 - el trabajo queda en ERROR con el detalle
            log.error("Trabajo %s falló:\n%s", trabajo.id, traceback.format_exc())
            datos, estado, error = None, ERROR, f"{type(excepcion).__name__}: {excepcion}"
        with self._lock:
            trabajo.estado, trabajo.resultado, trabajo.error = estado, datos, error
            trabajo.terminado = self._reloj()
            trabajo.preparacion = None  # ya no hace falta: libera memoria
