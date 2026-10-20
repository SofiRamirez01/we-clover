"""Ubica las piezas de cada corte con el algoritmo genético del vendor y arma el resultado en cm.

Frontera de unidades: el plan y el resultado están en cm; el vendor trabaja en mm (sus
parámetros están afinados para un lienzo de 900), así que acá se multiplica por 10 al entrar y se
divide al salir. El margen entre piezas es un buffer de margen/2 por pieza (en inglete); las
enteras quedan a `margen_borde_cm` del doblez y del borde inferior, y las mitades con el eje sobre
el doblez.
"""
import random
import sys
import time
from dataclasses import dataclass, field
from pathlib import Path

from shapely.affinity import scale, translate
from shapely.geometry import box
from shapely.geometry.polygon import orient
from shapely.strtree import STRtree

from app.corte.parametros import ParametrosCorte
from app.corte.planificador import Corte, Plan, partir_corte, planificar

VENDOR = Path(__file__).resolve().parents[2] / "vendor" / "OptimizacionCorteTextil"
if str(VENDOR) not in sys.path:
    sys.path.insert(0, str(VENDOR))
import algorpatronsnap as ga  # noqa: E402

MM_POR_CM = 10.0
ENTERA = "ENTERA"
MITAD_AL_DOBLEZ = "MITAD_AL_DOBLEZ"
_TOLERANCIA_CM = 1e-6


@dataclass
class PiezaUbicada:
    id_pieza: int
    id_talle: int
    talle: str
    nombre: str
    copia: int
    modo: str  # ENTERA o MITAD_AL_DOBLEZ
    rotacion: int
    poligono: object  # shapely Polygon en cm, ya rotado y trasladado, sin margen


@dataclass
class Tizada:
    corte: Corte
    piezas: list
    largo_usado_cm: float
    eficiencia: float
    generaciones: int
    segundos: float
    verificacion_ok: bool
    intentos: int
    advertencias: list = field(default_factory=list)


@dataclass
class ResultadoOptimizacion:
    tizadas: list
    no_resueltas: list
    advertencias: list
    plan: Plan


class NoSeUbica(Exception):
    """El algoritmo no pudo armar una población inicial con todas las piezas del corte."""

    def __init__(self, items):
        super().__init__("no se pudieron ubicar todas las piezas del corte")
        self.items = items


# --- Entradas del algoritmo -----------------------------------------------------------------

def _a_mm(poligono_cm):
    minx, miny, _, _ = poligono_cm.bounds
    return scale(translate(poligono_cm, -minx, -miny), MM_POR_CM, MM_POR_CM, origin=(0, 0))


def _a_cm(poligono_mm):
    return scale(poligono_mm, 1 / MM_POR_CM, 1 / MM_POR_CM, origin=(0, 0))


def simplificar_conteniendo(poligono, tolerancia, eje_x=None):
    """Versión simplificada para el chequeo de choques que siempre CONTIENE a la original:
    simplifica con la tolerancia y después agranda esa misma tolerancia (en inglete). En una
    mitad se vuelve a recortar por el eje, para que siga sin margen sobre el doblez. Si por algún
    caso raro no la contiene, devuelve la original."""
    if tolerancia <= 0:
        return poligono
    simple = poligono.simplify(tolerancia, preserve_topology=True).buffer(tolerancia, join_style=2)
    if eje_x is not None:
        minx, miny, maxx, maxy = simple.bounds
        simple = simple.intersection(box(eje_x, miny - 1, maxx + 1, maxy + 1))
    simple = ga._poligono_mayor(simple)
    if not simple.buffer(1e-9).contains(poligono) or len(simple.exterior.coords) >= len(poligono.exterior.coords):
        return poligono
    return orient(simple)


def armar_entradas(corte: Corte, p: ParametrosCorte):
    """Entradas del vendor (en mm) y, por cada una, cómo volver a la pieza sin margen."""
    offset = p.margen_corte_cm / 2 * MM_POR_CM
    tolerancia = p.tolerancia_simplificacion_cm * MM_POR_CM
    entradas, meta = [], []
    for ic in corte.items:
        geo = ic.item.geo
        base = _a_mm(geo.poligono)
        margen = simplificar_conteniendo(ga._poligono_mayor(base.buffer(offset, join_style=2)), tolerancia)
        mitad_base = mitad_margen = None
        if ic.ranuras or ic.mitades:
            mitad_base, mitad_margen = ga.create_half_polygons(base, offset)
            mitad_margen = simplificar_conteniendo(mitad_margen, tolerancia, eje_x=mitad_base.bounds[0])
        datos = {"ic": ic, "base": {ga.ENTERA: base, ga.MITAD: mitad_base},
                 "referencia": {ga.ENTERA: margen, ga.MITAD: mitad_margen}}
        nombre = f"{geo.nombre} T{geo.talle}"
        for _ in range(ic.ranuras):
            entradas.append({"name": nombre, "polygon": margen, "mitad": mitad_margen,
                             "simetrico": True, "modo": ga.RANURA})
            meta.append(datos)
        for _ in range(ic.enteras):
            entradas.append({"name": nombre, "polygon": margen, "simetrico": geo.simetria_verificada,
                             "modo": ga.ENTERA})
            meta.append(datos)
        for _ in range(ic.mitades):
            entradas.append({"name": nombre, "mitad": mitad_margen, "simetrico": True, "modo": ga.MITAD})
            meta.append(datos)
    return entradas, meta


def limites_mm(entradas, p: ParametrosCorte):
    """(x_bounds, y_bounds, doblez) del vendor, en mm, para los polígonos con margen."""
    ancho = p.ancho_util_cm * MM_POR_CM
    offset = p.margen_corte_cm / 2 * MM_POR_CM
    borde = p.margen_borde_cm * MM_POR_CM - offset
    # Apilar todo es siempre una solución, pero ubicando al azar casi nunca se la encuentra justa:
    # se da el doble de holgura (v1.0.0 usaba la suma de perímetros), sin pasar el máximo.
    alto = 0.0
    for entrada in entradas:
        alto_entera = entrada["polygon"].bounds[3] - entrada["polygon"].bounds[1] if "polygon" in entrada else 0
        alto_mitad = entrada["mitad"].bounds[3] - entrada["mitad"].bounds[1] if entrada.get("mitad") else 0
        factor = 2 if entrada.get("modo") == ga.RANURA else 1
        alto += max(alto_entera, factor * alto_mitad)
    largo = min(p.largo_maximo_cm * MM_POR_CM + offset, borde + 2 * alto)
    return (borde, ancho - borde), (borde, largo), (0.0, ancho)


# --- Verificación final con polígonos exactos -----------------------------------------------

def verificar(piezas, p: ParametrosCorte):
    """Errores de la tizada con los polígonos exactos (sin simplificar). Criterio estricto: dos
    piezas no pueden tocarse; el margen entre piezas, el del doblez y los bordes se chequean
    con una tolerancia de 1e-6 cm, independiente de la del algoritmo."""
    errores = []
    ancho, largo_maximo = p.ancho_util_cm, p.largo_maximo_cm
    for pieza in piezas:
        etiqueta = f"{pieza.nombre} T{pieza.talle} copia {pieza.copia}"
        minx, miny, maxx, maxy = pieza.poligono.bounds
        if not pieza.poligono.is_valid:
            errores.append(f"{etiqueta}: polígono inválido")
        if miny < p.margen_borde_cm - _TOLERANCIA_CM or maxy > largo_maximo + _TOLERANCIA_CM:
            errores.append(f"{etiqueta}: sale del lienzo a lo largo (y de {miny:.3f} a {maxy:.3f} cm)")
        if pieza.modo == ENTERA:
            if minx < p.margen_borde_cm - _TOLERANCIA_CM or maxx > ancho - p.margen_borde_cm + _TOLERANCIA_CM:
                errores.append(f"{etiqueta}: no respeta el margen al doblez (x de {minx:.3f} a {maxx:.3f} cm)")
        else:
            borde = abs(minx) if pieza.rotacion == 0 else abs(maxx - ancho)
            if borde > _TOLERANCIA_CM or minx < -_TOLERANCIA_CM or maxx > ancho + _TOLERANCIA_CM:
                errores.append(f"{etiqueta}: la mitad no queda con el eje sobre el doblez")
    poligonos = [pieza.poligono for pieza in piezas]
    arbol = STRtree(poligonos)
    for i, poligono in enumerate(poligonos):
        for j in arbol.query(poligono.buffer(p.margen_corte_cm)):
            if j <= i:
                continue
            otro = poligonos[j]
            if poligono.intersects(otro):
                errores.append(f"{piezas[i].nombre} copia {piezas[i].copia} y {piezas[j].nombre} "
                               f"copia {piezas[j].copia}: se superponen o se tocan")
            elif poligono.distance(otro) < p.margen_corte_cm - _TOLERANCIA_CM:
                errores.append(f"{piezas[i].nombre} copia {piezas[i].copia} y {piezas[j].nombre} "
                               f"copia {piezas[j].copia}: quedan a menos del margen")
    return errores


# --- Un corte -------------------------------------------------------------------------------

def _piezas_ubicadas(mejor, meta):
    piezas, copias = [], {}
    for gen in mejor:
        if not gen.get("activo", True):
            continue
        datos = meta[gen["indice_entrada"]]
        geo = datos["ic"].item.geo
        poligono_mm = ga.place_polygon(datos["base"][gen["modo"]], datos["referencia"][gen["modo"]],
                                       gen["rotacion"], gen["x"], gen["y"])
        clave = (geo.id_pieza, geo.id_talle)
        copias[clave] = copias.get(clave, 0) + 1
        piezas.append(PiezaUbicada(geo.id_pieza, geo.id_talle, geo.talle, geo.nombre, copias[clave],
                                   ENTERA if gen["modo"] == ga.ENTERA else MITAD_AL_DOBLEZ,
                                   gen["rotacion"], _a_cm(poligono_mm)))
    return piezas


def ubicar_corte(corte: Corte, p: ParametrosCorte, on_generation=None, cancelado=None) -> Tizada:
    """Corre el algoritmo sobre un corte. Lanza NoSeUbica si no puede ubicar todo, y
    ga.CorteCancelado si se cancela. Si la verificación falla, reintenta con otra semilla."""
    entradas, meta = armar_entradas(corte, p)
    x_bounds, y_bounds, doblez = limites_mm(entradas, p)
    advertencias = []
    inicio = time.perf_counter()
    generaciones = [0]

    def avance(gen, total, mejor_fitness, mejor_largo_mm):
        generaciones[0] = gen + 1
        if on_generation is not None:
            on_generation(gen, total, mejor_fitness, mejor_largo_mm / MM_POR_CM)

    for intento in range(1, p.reintentos_verificacion + 2):
        if p.semilla is not None:
            random.seed(p.semilla + intento - 1)
        try:
            mejor, _, _ = ga.genetic_algorithm_margin_placement(
                entradas, x_bounds=x_bounds, y_bounds=y_bounds,
                population_size=p.poblacion, generations=p.generaciones,
                mutation_rate_x=p.tasa_mutacion_x, mutation_rate_y=p.tasa_mutacion_y,
                mutation_scale_x=p.escala_mutacion_x_mm, mutation_scale_y_pos=p.escala_mutacion_y_pos_mm,
                mutation_scale_y_neg=p.escala_mutacion_y_neg_mm,
                tournament_size=min(p.tamano_torneo, p.poblacion), crossover_prob=p.probabilidad_cruce,
                mutation_rate_flip=p.tasa_rotacion, mutation_rate_modo=p.tasa_cambio_modo,
                doblez=doblez, on_generation=avance, cancelado=cancelado, verbose=False)
        except ga.SinUbicacionValida as error:
            raise NoSeUbica([meta[i]["ic"] for i in error.indices_entrada]) from error
        piezas = _piezas_ubicadas(mejor, meta)
        errores = verificar(piezas, p)
        if not errores:
            break
        advertencias.append(f"Intento {intento}: la verificación con polígonos exactos falló: "
                            + "; ".join(errores[:5]) + (f" (y {len(errores) - 5} más)" if len(errores) > 5 else ""))
    largo = max(pieza.poligono.bounds[3] for pieza in piezas) + p.margen_borde_cm
    area = sum(pieza.poligono.area for pieza in piezas)
    return Tizada(corte, piezas, largo, area / (p.ancho_util_cm * largo), generaciones[0],
                  time.perf_counter() - inicio, not errores, intento, advertencias)


# --- Todos los cortes de un plan ------------------------------------------------------------

def optimizar(preparacion: dict, p: ParametrosCorte, plan: Plan | None = None,
              on_corte=None, on_generation=None, cancelado=None) -> ResultadoOptimizacion:
    """Planifica (si no viene el plan) y ubica cada corte. Un corte que no se puede ubicar se
    parte en dos y se reintenta; una unidad sola que no entra va a no resueltas con el motivo."""
    plan = plan or planificar(preparacion, p)
    pendientes = list(plan.cortes)
    tizadas, no_resueltas, advertencias = [], list(plan.no_resueltas), list(plan.advertencias)
    total = len(pendientes)
    while pendientes:
        corte = pendientes.pop(0)
        if on_corte is not None:
            on_corte(len(tizadas) + 1, total, corte)
        try:
            tizada = ubicar_corte(corte, p, on_generation, cancelado)
        except NoSeUbica as error:
            partes = partir_corte(corte, p)
            if partes:
                advertencias.append(f"Un corte de {corte.codigo_tela} no se pudo ubicar completo y se partió en "
                                    f"{len(partes)}")
                pendientes[:0] = partes
                total += len(partes) - 1
                continue
            for ic in (error.items or corte.items):
                no_resueltas.append({
                    "idPieza": ic.item.geo.id_pieza, "idTalle": ic.item.geo.id_talle, "talle": ic.item.geo.talle,
                    "idTipoTela": corte.id_tipo_tela, "cantidad": sum(ic.demanda.values()),
                    "motivo": "NO_SE_PUDO_UBICAR",
                    "detalle": f"{ic.item.geo.nombre} T{ic.item.geo.talle}: el algoritmo no encontró lugar "
                               f"para la pieza sola en el lienzo"})
            continue
        if not tizada.verificacion_ok:
            advertencias.extend(tizada.advertencias)
        tizadas.append(tizada)
    return ResultadoOptimizacion(tizadas, no_resueltas, advertencias, plan)
