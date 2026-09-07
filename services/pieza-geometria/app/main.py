from fastapi import FastAPI, HTTPException
from shapely.geometry import Polygon

from app.geometria import construir_contorno, escalar_contorno, polygon_a_coordenadas
from app.schemas import CalcularBaseRequest, CalcularBaseResponse, EscalarRequest, EscalarResponse

app = FastAPI(title="Servicio de Geometría de Piezas")


@app.post("/piezas/calcular-base", response_model=CalcularBaseResponse)
def calcular_base(request: CalcularBaseRequest) -> CalcularBaseResponse:
    """
    Área/ancho/largo/perímetro se calculan directamente sobre el polígono base, tal cual lo
    cargó el usuario — sin ningún buffer/margen. El margen de costura ya está físicamente
    incluido en las coordenadas medidas de la moldería; no hay nada más que sumarle acá.
    """
    try:
        puntos = construir_contorno(request.segmentos)
        polygon = Polygon(puntos)
    except ValueError as error:
        raise HTTPException(status_code=422, detail=str(error)) from error

    minx, miny, maxx, maxy = polygon.bounds
    return CalcularBaseResponse(
        coordenadas=polygon_a_coordenadas(polygon),
        areaCm2=polygon.area,
        anchoCm=maxx - minx,
        largoCm=maxy - miny,
        perimetroCm=polygon.length,
    )


@app.post("/piezas/escalar", response_model=EscalarResponse)
def escalar(request: EscalarRequest) -> EscalarResponse:
    try:
        escalado = escalar_contorno(
            request.coordenadasBase,
            request.anchoBaseCm,
            request.largoBaseCm,
            request.anchoObjetivoCm,
            request.largoObjetivoCm,
        )
    except ValueError as error:
        raise HTTPException(status_code=422, detail=str(error)) from error

    return EscalarResponse(coordenadas=polygon_a_coordenadas(escalado))
