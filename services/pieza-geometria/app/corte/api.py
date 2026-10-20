"""API de trabajos de corte: POST /corte/trabajos, GET y DELETE /corte/trabajos/{id}."""
from fastapi import APIRouter, Depends, HTTPException
from pydantic import BaseModel, Field

from app.corte.parametros import ParametrosCorte
from app.corte.trabajos import GestorTrabajos, TrabajoNoEncontrado, TrabajoYaTerminado

router = APIRouter(prefix="/corte", tags=["corte"])
_gestor: GestorTrabajos | None = None


def obtener_gestor() -> GestorTrabajos:
    global _gestor
    if _gestor is None:
        _gestor = GestorTrabajos()
    return _gestor


class CrearTrabajoRequest(BaseModel):
    # El contrato de GET /api/tandas/{id}/preparacion-corte del backend, tal cual.
    preparacion: dict
    # Pisan los de la preparación y los defaults (camelCase): modoTiempo, presupuestoSegundosPorCorte,
    # excedenteMaximoPorItem, excedenteMaximoPorTela, modoObjetivo, semilla, etc.
    parametros: dict = Field(default_factory=dict)


class CrearTrabajoResponse(BaseModel):
    idTrabajo: str
    estado: str
    posicionEnCola: int | None


def _validar_preparacion(preparacion: dict):
    for clave in ("piezas", "grupos"):
        if not isinstance(preparacion.get(clave), list):
            raise HTTPException(422, f"La preparación no tiene '{clave}'")
    if not any(grupo.get("items") for grupo in preparacion["grupos"]):
        raise HTTPException(422, "No hay demanda para cortar en esta preparación")


@router.post("/trabajos", response_model=CrearTrabajoResponse, status_code=202)
def crear_trabajo(pedido: CrearTrabajoRequest, gestor: GestorTrabajos = Depends(obtener_gestor)):
    _validar_preparacion(pedido.preparacion)
    try:
        parametros = ParametrosCorte.desde_dict({**(pedido.preparacion.get("parametros") or {}), **pedido.parametros})
    except (TypeError, ValueError) as error:
        raise HTTPException(422, f"Parámetros inválidos: {error}") from error
    trabajo = gestor.crear(pedido.preparacion, parametros)
    vista = gestor.consultar(trabajo.id)
    return CrearTrabajoResponse(idTrabajo=trabajo.id, estado=vista["estado"], posicionEnCola=vista["posicionEnCola"])


@router.get("/trabajos/{id_trabajo}")
def consultar_trabajo(id_trabajo: str, gestor: GestorTrabajos = Depends(obtener_gestor)):
    try:
        return gestor.consultar(id_trabajo)
    except TrabajoNoEncontrado as error:
        raise HTTPException(404, "No existe el trabajo (o ya venció)") from error


@router.delete("/trabajos/{id_trabajo}")
def cancelar_trabajo(id_trabajo: str, gestor: GestorTrabajos = Depends(obtener_gestor)):
    try:
        return gestor.cancelar(id_trabajo)
    except TrabajoNoEncontrado as error:
        raise HTTPException(404, "No existe el trabajo (o ya venció)") from error
    except TrabajoYaTerminado as error:
        raise HTTPException(409, f"El trabajo ya terminó ({error.estado}): no se puede cancelar") from error
