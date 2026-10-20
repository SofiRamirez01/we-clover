"""Geometría de cada pieza en cada talle: simetría verificada, modos posibles y áreas con margen."""
import logging
from dataclasses import dataclass

from shapely.affinity import scale
from shapely.geometry import Polygon, box
from shapely.validation import make_valid

from app.corte.parametros import ParametrosCorte

log = logging.getLogger(__name__)


@dataclass(frozen=True)
class PiezaTalleGeo:
    id_pieza: int
    nombre: str
    id_talle: int
    talle: str
    poligono: Polygon
    ancho_cm: float
    largo_cm: float
    simetrica_marcada: bool
    desvio_simetria: float
    # Marcada simétrica y con desvío dentro de la tolerancia.
    simetria_verificada: bool
    permite_entera: bool
    permite_mitad: bool
    # Área con margen (buffer de margen/2) de una entera y de una mitad, en cm².
    area_entera_cm2: float
    area_mitad_cm2: float


def desvio_simetria(poligono: Polygon) -> float:
    """Área de la diferencia simétrica con su espejo sobre el eje vertical central, sobre el área."""
    minx, _, maxx, _ = poligono.bounds
    espejo = scale(poligono, xfact=-1, yfact=1, origin=((minx + maxx) / 2, 0))
    return poligono.symmetric_difference(espejo).area / poligono.area


def mitad_derecha(poligono: Polygon) -> Polygon:
    minx, miny, maxx, maxy = poligono.bounds
    return poligono.intersection(box((minx + maxx) / 2, miny - 1, maxx + 1, maxy + 1))


def preparar_piezas(piezas_json: list[dict], parametros: ParametrosCorte):
    """Arma la geometría de cada (pieza, talle) de la preparación.

    Devuelve (geometrias por (idPieza, idTalle), advertencias). Una pieza marcada simétrica
    que no pasa la tolerancia se ofrece solo entera, con advertencia (también en el log).
    """
    geometrias = {}
    advertencias = []
    margen = parametros.margen_corte_cm / 2
    for pieza in piezas_json:
        for talle in pieza["talles"]:
            poligono = Polygon(talle["coordenadas"])
            if not poligono.is_valid:
                poligono = max(getattr(make_valid(poligono), "geoms", [poligono]), key=lambda g: g.area)
            minx, miny, maxx, maxy = poligono.bounds
            ancho, largo = maxx - minx, maxy - miny
            marcada = bool(pieza["simetrica"])
            desvio = desvio_simetria(poligono)
            simetria_ok = marcada and desvio <= parametros.tolerancia_simetria
            if marcada and not simetria_ok:
                texto = (f"{pieza['nombre']} T{talle['talle']}: está marcada simétrica pero su desvío de "
                         f"simetría es {desvio:.1%} (tolerancia {parametros.tolerancia_simetria:.1%}); "
                         f"se ofrece solo entera")
                advertencias.append(texto)
                log.warning(texto)

            ancho_lienzo = parametros.ancho_util_cm
            entra_largo = largo + parametros.margen_borde_cm <= parametros.largo_maximo_cm
            permite_entera = entra_largo and ancho + 2 * parametros.margen_borde_cm <= ancho_lienzo
            permite_mitad = (simetria_ok and parametros.mitad_disponible and entra_largo
                             and ancho / 2 + parametros.margen_borde_cm <= ancho_lienzo)
            mitad = mitad_derecha(poligono)
            geometrias[(pieza["id"], talle["idTalle"])] = PiezaTalleGeo(
                id_pieza=pieza["id"], nombre=pieza["nombre"], id_talle=talle["idTalle"],
                talle=talle["talle"], poligono=poligono, ancho_cm=ancho, largo_cm=largo,
                simetrica_marcada=marcada, desvio_simetria=desvio, simetria_verificada=simetria_ok,
                permite_entera=permite_entera, permite_mitad=permite_mitad,
                area_entera_cm2=poligono.buffer(margen, join_style=2).area,
                area_mitad_cm2=mitad.buffer(margen, join_style=2).intersection(
                    box(mitad.bounds[0], miny - 10, maxx + 10, maxy + 10)).area,
            )
    return geometrias, advertencias
