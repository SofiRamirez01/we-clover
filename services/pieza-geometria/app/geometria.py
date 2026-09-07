import math

from shapely.affinity import scale
from shapely.geometry import Polygon

from app.schemas import Punto, Segmento, SegmentoArco, SegmentoCirculo, SegmentoRecta

# Esta entrega no usa nada del vendor (vendor/OptimizacionCorteTextil): el margen de costura
# ya viene incluido en las coordenadas medidas de la moldería, y el margen de navegación de
# corte (polyorder.create_margin_polygons) pertenece a la futura fase de optimización de
# layout (algorpatronsnap.py), todavía no integrada. Cuando se encare esa fase, ahí se vuelve
# a agregar el sys.path.insert + import polyorder que hacía falta acá (ver git history).

DENSIDAD_CM_POR_PUNTO = 1.0
MINIMO_PUNTOS_ARCO = 8
EPSILON_CM = 1e-6


def _distancia(a: Punto, b: Punto) -> float:
    return math.hypot(a[0] - b[0], a[1] - b[1])


def _cantidad_puntos(longitud_arco_cm: float) -> int:
    return max(MINIMO_PUNTOS_ARCO, math.ceil(longitud_arco_cm / DENSIDAD_CM_POR_PUNTO) + 1)


def _tesselar_arco_por_centro(centro: Punto, radio: float, angulo_inicial_rad: float, angulo_final_rad: float) -> list[Punto]:
    longitud_arco = radio * abs(angulo_final_rad - angulo_inicial_rad)
    cantidad = _cantidad_puntos(longitud_arco)
    puntos = []
    for i in range(cantidad):
        t = i / (cantidad - 1)
        angulo = angulo_inicial_rad + t * (angulo_final_rad - angulo_inicial_rad)
        puntos.append((centro[0] + radio * math.cos(angulo), centro[1] + radio * math.sin(angulo)))
    return puntos


def _tesselar_recta(segmento: SegmentoRecta) -> list[Punto]:
    return [tuple(segmento.puntoInicial), tuple(segmento.puntoFinal)]


def _tesselar_arco(segmento: SegmentoArco) -> list[Punto]:
    """
    Arma un arco a partir de dos extremos y un radio (sin centro ni ángulos explícitos).
    Dados dos puntos y un radio hay dos centros posibles (a cada lado de la cuerda); `lado`
    elige cuál, tomando como referencia el sentido de avance puntoInicial -> puntoFinal:
    IZQUIERDA = el centro queda a la izquierda de ese sentido, DERECHA = a la derecha.
    Siempre se tesela el arco menor (<= 180°) entre ambos puntos alrededor del centro elegido.
    """
    p0, p1 = segmento.puntoInicial, segmento.puntoFinal
    radio = segmento.radioCm
    distancia = _distancia(p0, p1)

    if distancia < EPSILON_CM:
        raise ValueError("Un segmento ARCO no puede tener puntoInicial igual a puntoFinal")
    if radio < distancia / 2 - EPSILON_CM:
        raise ValueError(
            f"El radio ({radio}cm) es menor que la mitad de la distancia entre los extremos "
            f"del arco ({distancia}cm); no hay un arco posible con ese radio"
        )

    medio = ((p0[0] + p1[0]) / 2, (p0[1] + p1[1]) / 2)
    ux, uy = (p1[0] - p0[0]) / distancia, (p1[1] - p0[1]) / distancia
    a = math.sqrt(max(radio * radio - (distancia / 2) ** 2, 0.0))

    perp_izquierda = (-uy, ux)
    perp_derecha = (uy, -ux)
    perp = perp_izquierda if segmento.lado == "IZQUIERDA" else perp_derecha
    centro = (medio[0] + perp[0] * a, medio[1] + perp[1] * a)

    angulo_p0 = math.atan2(p0[1] - centro[1], p0[0] - centro[0])
    angulo_p1 = math.atan2(p1[1] - centro[1], p1[0] - centro[0])

    diferencia = angulo_p1 - angulo_p0
    # Normalizamos la diferencia a (-pi, pi] para quedarnos siempre con el arco menor.
    while diferencia <= -math.pi:
        diferencia += 2 * math.pi
    while diferencia > math.pi:
        diferencia -= 2 * math.pi

    return _tesselar_arco_por_centro(centro, radio, angulo_p0, angulo_p0 + diferencia)


def _tesselar_circulo(segmento: SegmentoCirculo) -> list[Punto]:
    angulo_inicial_rad = math.radians(segmento.anguloInicial)
    angulo_final_rad = math.radians(segmento.anguloFinal)
    return _tesselar_arco_por_centro(tuple(segmento.centro), segmento.radioCm, angulo_inicial_rad, angulo_final_rad)


def _tesselar_segmento(segmento: Segmento) -> list[Punto]:
    if isinstance(segmento, SegmentoRecta):
        return _tesselar_recta(segmento)
    if isinstance(segmento, SegmentoArco):
        return _tesselar_arco(segmento)
    return _tesselar_circulo(segmento)


def _extremos_segmento(segmento: Segmento) -> tuple[Punto, Punto]:
    """Los dos puntos "de enganche" de un tramo, sin tesselar (para coserlos entre sí)."""
    if isinstance(segmento, SegmentoCirculo):
        angulo_inicial_rad = math.radians(segmento.anguloInicial)
        angulo_final_rad = math.radians(segmento.anguloFinal)
        centro = tuple(segmento.centro)
        radio = segmento.radioCm
        p1 = (centro[0] + radio * math.cos(angulo_inicial_rad), centro[1] + radio * math.sin(angulo_inicial_rad))
        p2 = (centro[0] + radio * math.cos(angulo_final_rad), centro[1] + radio * math.sin(angulo_final_rad))
        return (p1, p2)
    return (tuple(segmento.puntoInicial), tuple(segmento.puntoFinal))


class _Cadena:
    __slots__ = ("tramos", "extremo_inicio", "extremo_fin", "cerrada")

    def __init__(self, indice: int, a: Punto, b: Punto):
        self.tramos: list[tuple[int, bool]] = [(indice, False)]
        self.extremo_inicio = a
        self.extremo_fin = b
        self.cerrada = _distancia(a, b) < EPSILON_CM


def _invertir_tramos(tramos: list[tuple[int, bool]]) -> list[tuple[int, bool]]:
    return [(indice, not invertido) for indice, invertido in reversed(tramos)]


def _construir_cadenas(segmentos: list[Segmento]) -> list[_Cadena]:
    """
    Cose los tramos entre sí solo donde sus extremos coinciden (dentro de EPSILON_CM) — nunca
    por el orden en que se cargaron. Un conjunto de tramos puede terminar en varias cadenas
    (algunas cerradas, otras no) si no todos los tramos se conectan entre sí.
    """
    cadenas = [_Cadena(i, *_extremos_segmento(segmento)) for i, segmento in enumerate(segmentos)]

    cambiado = True
    while cambiado:
        cambiado = False
        for i, c1 in enumerate(cadenas):
            if c1.cerrada:
                continue
            for j, c2 in enumerate(cadenas):
                if i == j or c2.cerrada:
                    continue

                if _distancia(c1.extremo_fin, c2.extremo_inicio) < EPSILON_CM:
                    c1.tramos = c1.tramos + c2.tramos
                    c1.extremo_fin = c2.extremo_fin
                elif _distancia(c1.extremo_fin, c2.extremo_fin) < EPSILON_CM:
                    c1.tramos = c1.tramos + _invertir_tramos(c2.tramos)
                    c1.extremo_fin = c2.extremo_inicio
                elif _distancia(c1.extremo_inicio, c2.extremo_fin) < EPSILON_CM:
                    c1.tramos = c2.tramos + c1.tramos
                    c1.extremo_inicio = c2.extremo_inicio
                elif _distancia(c1.extremo_inicio, c2.extremo_inicio) < EPSILON_CM:
                    c1.tramos = _invertir_tramos(c2.tramos) + c1.tramos
                    c1.extremo_inicio = c2.extremo_fin
                else:
                    continue

                del cadenas[j]
                if _distancia(c1.extremo_inicio, c1.extremo_fin) < EPSILON_CM:
                    c1.cerrada = True
                cambiado = True
                break
            if cambiado:
                break

    return cadenas


def construir_contorno(segmentos: list[Segmento]) -> list[Punto]:
    """
    Cose los tramos por coincidencia de extremos (ver _construir_cadenas) y exige que el
    resultado sea una única cadena cerrada que use todos los tramos — si sobran tramos sueltos
    o el contorno no vuelve al punto de partida, es un error de datos, no algo para adivinar.
    """
    if not segmentos:
        raise ValueError("Debe indicar al menos un segmento")

    cadenas = _construir_cadenas(segmentos)

    if len(cadenas) != 1:
        raise ValueError(
            f"Los tramos no forman un único contorno: hay {len(cadenas)} grupos de tramos "
            "sin conectar entre sí (revisá que los extremos coincidan exactamente)"
        )

    cadena = cadenas[0]
    if not cadena.cerrada:
        raise ValueError(
            "El contorno no cierra: el último tramo no vuelve a coincidir con el punto de partida"
        )

    puntos: list[Punto] = []
    for indice, invertido in cadena.tramos:
        tramo = _tesselar_segmento(segmentos[indice])
        if invertido:
            tramo = list(reversed(tramo))
        if puntos and _distancia(puntos[-1], tramo[0]) < EPSILON_CM:
            tramo = tramo[1:]
        puntos.extend(tramo)

    if len(puntos) > 1 and _distancia(puntos[0], puntos[-1]) < EPSILON_CM:
        puntos.pop()

    if len(puntos) < 3:
        raise ValueError("El contorno resultante no tiene suficientes puntos para formar un polígono")

    return puntos


def polygon_a_coordenadas(polygon: Polygon) -> list[Punto]:
    coords = [(float(x), float(y)) for x, y in polygon.exterior.coords]
    if len(coords) > 1 and _distancia(coords[0], coords[-1]) < EPSILON_CM:
        coords.pop()
    return coords


def escalar_contorno(
    coordenadas_base: list[Punto],
    ancho_base_cm: float,
    largo_base_cm: float,
    ancho_objetivo_cm: float,
    largo_objetivo_cm: float,
) -> Polygon:
    polygon = Polygon(coordenadas_base)
    minx, miny, _, _ = polygon.bounds
    return scale(
        polygon,
        xfact=ancho_objetivo_cm / ancho_base_cm,
        yfact=largo_objetivo_cm / largo_base_cm,
        origin=(minx, miny),
    )
