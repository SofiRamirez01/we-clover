from typing import Annotated, Literal, Union

from pydantic import BaseModel, Field

Punto = tuple[float, float]


class SegmentoRecta(BaseModel):
    tipo: Literal["RECTA"]
    puntoInicial: Punto
    puntoFinal: Punto


class SegmentoArco(BaseModel):
    tipo: Literal["ARCO"]
    puntoInicial: Punto
    puntoFinal: Punto
    radioCm: float
    lado: Literal["IZQUIERDA", "DERECHA"]


class SegmentoCirculo(BaseModel):
    tipo: Literal["CIRCULO"]
    centro: Punto
    radioCm: float
    anguloInicial: float
    anguloFinal: float


Segmento = Annotated[
    Union[SegmentoRecta, SegmentoArco, SegmentoCirculo],
    Field(discriminator="tipo"),
]


class CalcularBaseRequest(BaseModel):
    segmentos: list[Segmento]
    # No afecta ningún cálculo de este endpoint (área/ancho/largo/perímetro no dependen de
    # esto) — viaja en el contrato para la futura fase de optimización de layout de corte.
    simetrica: bool = False


class CalcularBaseResponse(BaseModel):
    coordenadas: list[Punto]
    areaCm2: float
    anchoCm: float
    largoCm: float
    perimetroCm: float


class EscalarRequest(BaseModel):
    coordenadasBase: list[Punto]
    anchoBaseCm: float
    largoBaseCm: float
    anchoObjetivoCm: float
    largoObjetivoCm: float


class EscalarResponse(BaseModel):
    coordenadas: list[Punto]
