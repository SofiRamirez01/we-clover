"""Planificador de cortes: decide qué piezas van juntas en cada tizada y cuántas capas lleva cada
color, antes de ubicar nada (el algoritmo genético corre después, una vez por corte).

Restricciones duras: nunca producir de menos; excedente <= `excedente_maximo_por_item` por pieza,
talle y color (el excedente inevitable, una pieza que sale de a pares con demanda impar, se acepta
igual y se reporta como tal); largo <= `largo_maximo_cm`; capas <= `capas_maximas`.

Objetivo según `modo_objetivo`:
- TELA (por defecto): minimizar Σ(largo × capas). Desempates: menos cortes, menos prendas
  partidas, menos capas.
- COSTO (apagado por defecto): minimizar minutos = cortes × `minutos_por_corte_adicional` +
  prendas partidas × `costo_prenda_partida`. Desempates: menos tela, menos capas.

Una prenda partida es un par producto × talle cuyas piezas de una misma tela quedan en más de
un corte (está permitido; se cuenta como métrica).

Un corte tiene un plan de capas L (capas por color). Un ítem (pieza × talle) lleva u unidades
por capa y produce u · L[color] de cada color; u es el mínimo que cubre la demanda en todos sus
colores. Solo comparten corte los ítems con el mismo conjunto de colores. El largo se estima por
área (área con margen / (ancho × eficiencia_estimada)): es solo para planificar.
"""
from dataclasses import dataclass, field, replace
from math import ceil

from app.corte.parametros import ParametrosCorte
from app.corte.piezas import PiezaTalleGeo, preparar_piezas

TELA = "TELA"
COSTO = "COSTO"

POR_CONJUNTO = "POR_CONJUNTO_DE_COLORES"
POR_COLOR = "POR_COLOR"
MEZCLA = "MEZCLA"


@dataclass
class ItemDemanda:
    geo: PiezaTalleGeo
    demanda: dict  # idColor -> piezas
    nombres_color: dict  # idColor -> nombre
    origen: list = field(default_factory=list)

    @property
    def clave(self):
        return (self.geo.id_pieza, self.geo.id_talle)

    @property
    def colores(self) -> frozenset:
        return frozenset(self.demanda)


@dataclass
class ItemEnCorte:
    item: ItemDemanda
    unidades_por_capa: int
    # Composición por capa: ranuras (1 entera o 2 mitades, elige el algoritmo), enteras solas
    # (piezas sin mitad posible) y mitades solas (unidad impar o pieza que no entra entera).
    ranuras: int
    enteras: int
    mitades: int
    demanda: dict  # parte de la demanda del ítem que cubre este corte
    produccion: dict
    excedente: dict
    excedente_inevitable: dict

    @property
    def area_por_capa_cm2(self) -> float:
        geo = self.item.geo
        return (self.ranuras + self.enteras) * geo.area_entera_cm2 + self.mitades * geo.area_mitad_cm2


@dataclass
class Corte:
    id_tipo_tela: int
    codigo_tela: str
    nombre_tela: str
    capas: dict  # idColor -> capas
    items: list
    largo_estimado_cm: float
    # Lo completa el optimizador de residuo (todavía no implementado): siempre vacío.
    sustituciones: list = field(default_factory=list)

    @property
    def capas_totales(self) -> int:
        return sum(self.capas.values())

    @property
    def tela_estimada_cm(self) -> float:
        return self.largo_estimado_cm * self.capas_totales


@dataclass
class Plan:
    cortes: list
    no_resueltas: list
    advertencias: list
    estrategias: dict  # idTipoTela -> estrategia elegida
    # (idTipoTela, idProducto, idTalle) de cada prenda partida entre cortes de una misma tela.
    prendas_partidas: list = field(default_factory=list)

    @property
    def tela_estimada_cm(self) -> float:
        return sum(corte.tela_estimada_cm for corte in self.cortes)

    @property
    def excedente_total(self) -> int:
        return sum(sum(ic.excedente.values()) for corte in self.cortes for ic in corte.items)


# --- Unidades por capa y excedente ---------------------------------------------------------

def _excedente_inevitable(geo: PiezaTalleGeo, demanda: int, p: ParametrosCorte) -> int:
    """Una pieza que solo va entera sale de a `telas_por_capa` copias: el resto es inevitable."""
    return 0 if geo.permite_mitad else (-demanda) % p.unidades_por_entera


def unidades_por_capa(item: ItemDemanda, capas: dict, p: ParametrosCorte):
    """Mínimas unidades por capa que cubren la demanda sin pasar el tope; None si no hay."""
    if not item.colores <= set(capas):
        return None
    u = max(ceil(cantidad / capas[color]) for color, cantidad in item.demanda.items())
    if not item.geo.permite_mitad:
        u = ceil(u / p.unidades_por_entera) * p.unidades_por_entera
    for color, l in capas.items():
        demanda = item.demanda.get(color, 0)
        tope = max(p.excedente_maximo_por_item, _excedente_inevitable(item.geo, demanda, p))
        if u * l - demanda > tope:
            return None
    return u


def _composicion(geo: PiezaTalleGeo, u: int, p: ParametrosCorte):
    """(ranuras, enteras, mitades) que rinden u unidades por capa."""
    if geo.permite_entera and geo.permite_mitad:
        return u // 2, 0, u % 2
    if geo.permite_entera:
        return 0, u // p.unidades_por_entera, 0
    return 0, 0, u


def _item_en_corte(item, u, capas, demanda, p):
    ranuras, enteras, mitades = _composicion(item.geo, u, p)
    produccion = {color: u * l for color, l in capas.items()}
    excedente = {color: produccion[color] - demanda.get(color, 0) for color in capas}
    inevitable = {color: min(excedente[color], _excedente_inevitable(item.geo, demanda.get(color, 0), p))
                  for color in capas}
    return ItemEnCorte(item, u, ranuras, enteras, mitades, dict(demanda), produccion, excedente, inevitable)


# --- Reparto en cortes (bin packing por área) -------------------------------------------------

def _capacidad_cm2(p: ParametrosCorte) -> float:
    return p.largo_maximo_cm * p.ancho_util_cm * p.eficiencia_estimada


def _largo_estimado(items, p: ParametrosCorte) -> float:
    return sum(ic.area_por_capa_cm2 for ic in items) / (p.ancho_util_cm * p.eficiencia_estimada)


def _partir(item, u, capas, p):
    """Parte un ítem que solo no entra en un corte en trozos de unidades que sí entran."""
    paso = 1 if item.geo.permite_mitad else p.unidades_por_entera
    entero = _item_en_corte(item, u, capas, item.demanda, p)
    area_unidad = entero.area_por_capa_cm2 / u
    por_trozo = max(paso, int(_capacidad_cm2(p) // area_unidad) // paso * paso)
    trozos = []
    restante = dict(item.demanda)
    while u > 0:
        cantidad = min(por_trozo, u)
        demanda = {color: min(restante.get(color, 0), cantidad * l) for color, l in capas.items()}
        restante = {color: restante.get(color, 0) - demanda[color] for color in capas}
        u -= cantidad
        trozos.append(_item_en_corte(item, cantidad, capas, demanda, p))
    return trozos


def partir_corte(corte, p: ParametrosCorte):
    """Parte en dos un corte que el algoritmo no pudo ubicar: reparte los ítems por área o, si
    hay uno solo, sus unidades por capa. Devuelve [] si ya es una sola unidad."""
    def nuevo(items):
        return Corte(corte.id_tipo_tela, corte.codigo_tela, corte.nombre_tela, dict(corte.capas), items,
                     _largo_estimado(items, p))

    if len(corte.items) > 1:
        mitades = ([], [])
        for ic in sorted(corte.items, key=lambda ic: ic.area_por_capa_cm2, reverse=True):
            min(mitades, key=lambda m: sum(x.area_por_capa_cm2 for x in m)).append(ic)
        return [nuevo(items) for items in mitades]
    (ic,) = corte.items
    paso = 1 if ic.item.geo.permite_mitad else p.unidades_por_entera
    if ic.unidades_por_capa <= paso:
        return []
    primera = max(paso, ic.unidades_por_capa // 2 // paso * paso)
    partes, restante = [], dict(ic.demanda)
    for u in (primera, ic.unidades_por_capa - primera):
        demanda = {color: min(restante.get(color, 0), u * l) for color, l in corte.capas.items()}
        restante = {color: restante.get(color, 0) - demanda[color] for color in corte.capas}
        partes.append(nuevo([_item_en_corte(ic.item, u, corte.capas, demanda, p)]))
    return partes


def empacar(asignaciones, capas, p: ParametrosCorte):
    """First-fit decreasing por área por capa. Devuelve una lista de cortes (listas de ItemEnCorte)."""
    capacidad = _capacidad_cm2(p)
    piezas = []
    for item, u in asignaciones:
        entero = _item_en_corte(item, u, capas, item.demanda, p)
        piezas.extend(_partir(item, u, capas, p) if entero.area_por_capa_cm2 > capacidad else [entero])
    piezas.sort(key=lambda ic: ic.area_por_capa_cm2, reverse=True)
    cajas = []
    for ic in piezas:
        for caja in cajas:
            if caja["area"] + ic.area_por_capa_cm2 <= capacidad:
                caja["items"].append(ic)
                caja["area"] += ic.area_por_capa_cm2
                break
        else:
            cajas.append({"items": [ic], "area": ic.area_por_capa_cm2})
    return [caja["items"] for caja in cajas]


# --- Planes de capas candidatos y resolución de un grupo ------------------------------------

def _candidatos(items, colores, p: ParametrosCorte):
    if len(colores) == 1:
        return [{colores[0]: l} for l in range(1, p.capas_maximas + 1)]
    candidatos = {tuple(1 for _ in colores)}
    for item in items:
        for u in range(1, max(item.demanda.values()) + 1):
            candidatos.add(tuple(max(1, ceil(item.demanda[c] / u)) for c in colores))
    return [dict(zip(colores, l)) for l in sorted(candidatos) if sum(l) <= p.capas_maximas]


def _area_excedente(cortes_items):
    return sum(ic.item.geo.area_entera_cm2 / 2 * sum(ic.excedente.values())
               for items in cortes_items for ic in items)


def resolver_grupo(items, p: ParametrosCorte, orden=None):
    """Cubre los ítems de un grupo (mismo conjunto de colores) con cortes, por cobertura voraz.

    En cada paso elige un plan de capas. En modo TELA: el de menos excedente por ítem, después el
    que cubre más ítems por corte y después el de menos capas. En modo COSTO primero cuenta los
    ítems por corte y después el excedente. Devuelve (cortes como (capas, items), no_asignados).
    """
    colores = sorted(items[0].colores)
    candidatos = _candidatos(items, colores, p)
    restantes = list(items)
    resultado = []
    while restantes:
        mejor = None
        for capas in candidatos:
            factibles = [(item, u) for item in restantes
                         if (u := unidades_por_capa(item, capas, p)) is not None]
            if not factibles:
                continue
            cajas = empacar(factibles, capas, p)
            por_corte = -len(factibles) / len(cajas)
            excedente = _area_excedente(cajas) / len(factibles)
            clave = ((por_corte, excedente) if (orden or p.modo_objetivo) == COSTO else (excedente, por_corte))                 + (sum(capas.values()),)
            if mejor is None or clave < mejor[0]:
                mejor = (clave, capas, cajas, {id(item) for item, _ in factibles})
        if mejor is None:
            break
        _, capas, cajas, cubiertos = mejor
        resultado.extend((capas, caja) for caja in cajas)
        restantes = [item for item in restantes if id(item) not in cubiertos]
    return resultado, restantes


# --- Descomposiciones de una tela ------------------------------------------------------------

def _por_color(item: ItemDemanda):
    return [ItemDemanda(item.geo, {color: cantidad}, {color: item.nombres_color[color]},
                        [o for o in item.origen if o.get("idPaletaColor") == color])
            for color, cantidad in item.demanda.items()]


def _agrupar(items):
    grupos = {}
    for item in items:
        grupos.setdefault(item.colores, []).append(item)
    return list(grupos.values())


def _descomponer(items, estrategia):
    if estrategia == POR_COLOR:
        return _agrupar([parte for item in items for parte in _por_color(item)])
    if estrategia == MEZCLA:
        grupos = _agrupar(items)
        sueltos = [i for g in grupos if len(g) == 1 and len(g[0].colores) > 1 for i in g]
        conservados = [i for g in grupos if not (len(g) == 1 and len(g[0].colores) > 1) for i in g]
        return _agrupar(conservados + [parte for item in sueltos for parte in _por_color(item)])
    return _agrupar(items)


def _resolver_tela(items, estrategia, p, orden=None):
    """Resuelve una tela con una estrategia; los ítems multicolor sin plan posible se parten por color."""
    cortes = []
    pendientes = []
    for grupo in _descomponer(items, estrategia):
        resueltos, sin_plan = resolver_grupo(grupo, p, orden)
        cortes.extend(resueltos)
        pendientes.extend(sin_plan)
    if pendientes:
        for grupo in _agrupar([parte for item in pendientes for parte in _por_color(item)]):
            resueltos, sin_plan = resolver_grupo(grupo, p, orden)
            cortes.extend(resueltos)
            assert not sin_plan, "un ítem de un solo color siempre tiene plan (capas = 1)"
    return cortes

def prendas_partidas(cortes_items):
    """Pares (idProducto, idTalle) con piezas en más de un corte, sobre los cortes de una tela."""
    cortes_por_prenda = {}
    for indice, items in enumerate(cortes_items):
        for ic in items:
            for origen in ic.item.origen:
                prenda = (origen.get("idProducto"), ic.item.geo.id_talle)
                cortes_por_prenda.setdefault(prenda, set()).add(indice)
    return sorted(prenda for prenda, cortes in cortes_por_prenda.items() if len(cortes) > 1)


def _evaluar(cortes, p):
    tela = round(sum(_largo_estimado(items, p) * sum(capas.values()) for capas, items in cortes), 3)
    capas_totales = sum(sum(capas.values()) for capas, _ in cortes)
    partidas = len(prendas_partidas([items for _, items in cortes]))
    if p.modo_objetivo == COSTO:
        minutos = len(cortes) * p.minutos_por_corte_adicional + partidas * p.costo_prenda_partida
        return (minutos, tela, capas_totales)
    return (tela, len(cortes), partidas, capas_totales)


def _topes_de_busqueda(tope):
    return sorted(set(range(tope + 1)) | {1, 2, 4})


def _cumple_tope(cortes, tope):
    return all(excedente <= max(tope, ic.excedente_inevitable[color])
               for _, items in cortes for ic in items for color, excedente in ic.excedente.items())


def planificar(preparacion: dict, parametros: ParametrosCorte | None = None) -> Plan:
    """Planifica los cortes de una preparación (contrato de GET /api/tandas/{id}/preparacion-corte)."""
    p = parametros or ParametrosCorte.desde_dict(preparacion.get("parametros"))
    geometrias, advertencias = preparar_piezas(preparacion["piezas"], p)
    cortes, no_resueltas, estrategias, partidas = [], [], {}, []

    general = p
    for grupo_tela in preparacion["grupos"]:
        p = general.para_tela(grupo_tela["codigoTipoTela"])
        items = []
        for it in grupo_tela["items"]:
            geo = geometrias.get((it["idPieza"], it["idTalle"]))
            demanda = {c["idPaletaColor"]: c["cantidad"] for c in it["porColor"] if c["cantidad"] > 0}
            if geo is None or not (geo.permite_entera or geo.permite_mitad):
                no_resueltas.append(_no_resuelta(it, geo, grupo_tela, p))
                continue
            items.append(ItemDemanda(geo, demanda, {c["idPaletaColor"]: c["nombreColor"] for c in it["porColor"]},
                                     it.get("origen", [])))
        if not items:
            continue

        # La cobertura es voraz y depende de qué planes de capas ve: se la corre con varios topes
        # de búsqueda y los dos órdenes de selección, y se queda el mejor resultado que cumple el
        # tope de la solicitud (un plan buscado con tope 1 puede no tener ningún excedente). El
        # tope aplicado es siempre `excedente_maximo_por_item`.
        mejor = None
        for tope in _topes_de_busqueda(p.excedente_maximo_por_item):
            p_tope = replace(p, excedente_maximo_por_item=tope)
            for orden in (TELA, COSTO):
                for estrategia in (POR_CONJUNTO, POR_COLOR, MEZCLA):
                    resultado = _resolver_tela(items, estrategia, p_tope, orden)
                    if not _cumple_tope(resultado, p.excedente_maximo_por_item):
                        continue
                    clave = _evaluar(resultado, p)
                    if mejor is None or clave < mejor[0]:
                        mejor = (clave, estrategia, resultado)
        _, estrategia, resultado = mejor
        estrategias[grupo_tela["idTipoTela"]] = estrategia
        partidas.extend((grupo_tela["idTipoTela"], *prenda)
                        for prenda in prendas_partidas([items for _, items in resultado]))
        for capas, items_corte in resultado:
            cortes.append(Corte(grupo_tela["idTipoTela"], grupo_tela["codigoTipoTela"], grupo_tela["nombreTipoTela"],
                                capas, items_corte, _largo_estimado(items_corte, p)))
    return Plan(cortes, no_resueltas, advertencias, estrategias, partidas)


def _no_resuelta(it, geo, grupo_tela, p):
    if geo is None:
        motivo, detalle = "SIN_GEOMETRIA", "La preparación no trae la geometría de este talle"
    else:
        motivo = "NO_ENTRA_EN_EL_LIENZO"
        detalle = (f"{geo.nombre} T{geo.talle} mide {geo.ancho_cm:.1f} × {geo.largo_cm:.1f} cm y el lienzo "
                   f"útil es de {p.ancho_util_cm - 2 * p.margen_borde_cm:.1f} × {p.largo_maximo_cm:.0f} cm"
                   + ("" if geo.simetria_verificada else "; no se puede ir al doblez porque no es simétrica"))
    return {"idPieza": it["idPieza"], "idTalle": it["idTalle"], "talle": it.get("talle"),
            "idTipoTela": grupo_tela["idTipoTela"], "cantidad": it["cantidadTotal"],
            "motivo": motivo, "detalle": detalle}
