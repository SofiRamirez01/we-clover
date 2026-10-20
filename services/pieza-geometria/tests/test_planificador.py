"""Tests del planificador de cortes (sin algoritmo genético)."""
import pytest
from shapely.geometry import Polygon

from app.corte.parametros import ParametrosCorte
from app.corte.piezas import desvio_simetria, preparar_piezas
from app.corte.planificador import ItemDemanda, planificar, unidades_por_capa
from app.corte.residuo import optimizar_residuo

ROJO, AZUL = 1, 2
RECTANGULO = [[0, 0], [40, 0], [40, 50], [0, 50]]  # simétrico
ASIMETRICO = [[0, 0], [30, 0], [36, 38], [4, 40], [0, 25]]
ANCHO_100 = [[0, 0], [100, 0], [100, 14], [0, 14]]  # no entra entero en 90 cm
# Como el Bolsillo T1-2 antes de corregirlo: un costado más alto que el otro.
CASI_SIMETRICO = [[0, 0], [47, 0], [47, 6], [43, 7], [43, 24], [4, 24], [4, 7], [0, 12]]  # desvío 2,4 %


def _pieza(id_pieza, coordenadas, simetrica, talles=((1, "2"),), nombre=None):
    return {"id": id_pieza, "nombre": nombre or f"pieza{id_pieza}", "simetrica": simetrica,
            "talles": [{"idTalle": id_talle, "talle": talle, "coordenadas": coordenadas}
                       for id_talle, talle in talles]}


def _item(id_pieza, por_color, id_talle=1, talle="2"):
    return {"idPieza": id_pieza, "idTalle": id_talle, "talle": talle,
            "porColor": [{"idPaletaColor": c, "nombreColor": f"color{c}", "cantidad": n} for c, n in por_color.items()],
            "cantidadTotal": sum(por_color.values()), "origen": []}


def _preparacion(piezas, items, id_tela=1):
    return {"idTanda": 1, "nombreTanda": "T", "parametros": {},
            "piezas": piezas,
            "grupos": [{"idTipoTela": id_tela, "codigoTipoTela": "FRIZA", "nombreTipoTela": "Frisa", "items": items}]}


def _item_demanda(coordenadas, simetrica, demanda, **parametros):
    p = ParametrosCorte(**parametros)
    geos, _ = preparar_piezas([_pieza(1, coordenadas, simetrica)], p)
    return ItemDemanda(geos[(1, 1)], demanda, {c: f"color{c}" for c in demanda}), p


# --- Aritmética de capas y excedente ---------------------------------------------------------

def test_simetrica_con_demanda_impar_usa_una_mitad_y_no_sobra_nada():
    item, p = _item_demanda(RECTANGULO, True, {ROJO: 3}, capas_maximas=1)
    assert unidades_por_capa(item, {ROJO: 1}, p) == 3
    plan = planificar(_preparacion([_pieza(1, RECTANGULO, True)], [_item(1, {ROJO: 3})]), p)
    (ic,) = plan.cortes[0].items
    assert (ic.ranuras, ic.enteras, ic.mitades) == (1, 0, 1)
    assert ic.produccion == {ROJO: 3} and ic.excedente == {ROJO: 0}


def test_asimetrica_sale_de_a_pares_y_el_excedente_impar_es_inevitable():
    item, p = _item_demanda(ASIMETRICO, False, {ROJO: 3}, excedente_maximo_por_item=0, capas_maximas=1)
    assert unidades_por_capa(item, {ROJO: 1}, p) == 4  # aunque el tope sea 0
    plan = planificar(_preparacion([_pieza(1, ASIMETRICO, False)], [_item(1, {ROJO: 3})]), p)
    (ic,) = plan.cortes[0].items
    assert ic.enteras == 2 and ic.excedente == {ROJO: 1} and ic.excedente_inevitable == {ROJO: 1}


def test_a_igual_tela_y_cortes_prefiere_menos_capas():
    # 1 capa × 3 unidades o 3 capas × 1 unidad gastan la misma tela: gana 1 capa.
    plan = planificar(_preparacion([_pieza(1, RECTANGULO, True)], [_item(1, {ROJO: 3})]), ParametrosCorte())
    (corte,) = plan.cortes
    assert corte.capas == {ROJO: 1} and corte.items[0].unidades_por_capa == 3 and plan.excedente_total == 0


def test_el_tope_de_excedente_limita_las_capas():
    # Asimétrica con demanda 6: con 2 capas produce 8 (2 de más).
    item, p = _item_demanda(ASIMETRICO, False, {ROJO: 6}, excedente_maximo_por_item=1)
    assert unidades_por_capa(item, {ROJO: 2}, p) is None
    item, p = _item_demanda(ASIMETRICO, False, {ROJO: 6}, excedente_maximo_por_item=2)
    assert unidades_por_capa(item, {ROJO: 2}, p) == 4
    assert unidades_por_capa(item, {ROJO: 3}, p) == 2  # 2 × 3 = 6, exacto


def test_unidades_por_capa_cubren_todos_los_colores():
    item, p = _item_demanda(RECTANGULO, True, {ROJO: 6, AZUL: 10})
    assert unidades_por_capa(item, {ROJO: 3, AZUL: 5}, p) == 2  # 6 y 10 exactos
    assert unidades_por_capa(item, {ROJO: 1, AZUL: 1}, p) is None  # 10 de rojo: 4 de más
    item, p = _item_demanda(RECTANGULO, True, {ROJO: 6, AZUL: 10}, excedente_maximo_por_item=4)
    assert unidades_por_capa(item, {ROJO: 1, AZUL: 1}, p) == 10


def test_un_plan_con_colores_que_el_item_no_pide_cuenta_como_excedente():
    item, p = _item_demanda(RECTANGULO, True, {ROJO: 2}, excedente_maximo_por_item=0)
    assert unidades_por_capa(item, {ROJO: 1, AZUL: 1}, p) is None
    item, p = _item_demanda(RECTANGULO, True, {ROJO: 2}, excedente_maximo_por_item=2)
    assert unidades_por_capa(item, {ROJO: 1, AZUL: 1}, p) == 2


def test_nunca_produce_de_menos_y_respeta_el_tope_de_capas():
    piezas = [_pieza(1, RECTANGULO, True), _pieza(2, ASIMETRICO, False)]
    items = [_item(1, {ROJO: 37}), _item(2, {ROJO: 29})]
    p = ParametrosCorte(capas_maximas=5, excedente_maximo_por_item=3)
    plan = planificar(_preparacion(piezas, items), p)
    producido = {}
    for corte in plan.cortes:
        assert corte.capas_totales <= 5
        for ic in corte.items:
            producido[ic.item.clave] = producido.get(ic.item.clave, 0) + ic.produccion[ROJO]
            assert all(e <= max(3, ic.excedente_inevitable[c]) for c, e in ic.excedente.items())
    assert producido[(1, 1)] >= 37 and producido[(2, 1)] >= 29


# --- Compatibilidad de colores ------------------------------------------------------------

def test_items_con_distinto_conjunto_de_colores_no_comparten_corte():
    piezas = [_pieza(1, RECTANGULO, True), _pieza(2, RECTANGULO, True)]
    items = [_item(1, {ROJO: 2}), _item(2, {ROJO: 2, AZUL: 2})]
    plan = planificar(_preparacion(piezas, items), ParametrosCorte())
    for corte in plan.cortes:
        conjuntos = {ic.item.colores for ic in corte.items}
        assert len(conjuntos) == 1
        assert set(corte.capas) == next(iter(conjuntos))


def test_items_con_el_mismo_conjunto_de_colores_comparten_corte():
    piezas = [_pieza(1, RECTANGULO, True, talles=((1, "2"), (2, "3"))), _pieza(2, ASIMETRICO, False)]
    items = [_item(1, {ROJO: 3, AZUL: 6}), _item(1, {ROJO: 2, AZUL: 4}, id_talle=2, talle="3"),
             _item(2, {ROJO: 2, AZUL: 4})]
    plan = planificar(_preparacion(piezas, items), ParametrosCorte())
    assert len(plan.cortes) == 1
    assert plan.cortes[0].capas == {ROJO: 1, AZUL: 2}
    assert plan.excedente_total == 0


# --- Piezas que no entran, simetría y largo --------------------------------------------------

def test_simetrica_mas_ancha_que_el_lienzo_va_solo_como_mitad():
    plan = planificar(_preparacion([_pieza(1, ANCHO_100, True)], [_item(1, {ROJO: 2})]),
                      ParametrosCorte(capas_maximas=1))
    (ic,) = plan.cortes[0].items
    assert (ic.ranuras, ic.enteras, ic.mitades) == (0, 0, 2)
    assert plan.no_resueltas == []


def test_pieza_que_no_entra_queda_no_resuelta_sin_frenar_al_resto():
    piezas = [_pieza(1, ANCHO_100, False), _pieza(2, RECTANGULO, True)]
    plan = planificar(_preparacion(piezas, [_item(1, {ROJO: 2}), _item(2, {ROJO: 2})]), ParametrosCorte())
    assert [(nr["idPieza"], nr["motivo"]) for nr in plan.no_resueltas] == [(1, "NO_ENTRA_EN_EL_LIENZO")]
    assert "no es simétrica" in plan.no_resueltas[0]["detalle"]
    assert [ic.item.geo.id_pieza for c in plan.cortes for ic in c.items] == [2]


def test_marcada_simetrica_fuera_de_tolerancia_se_ofrece_solo_entera_con_advertencia():
    assert 0.01 < desvio_simetria(Polygon(CASI_SIMETRICO)) < 0.05
    plan = planificar(_preparacion([_pieza(1, CASI_SIMETRICO, True, nombre="Bolsillo")], [_item(1, {ROJO: 3})]),
                      ParametrosCorte(capas_maximas=1))
    (ic,) = plan.cortes[0].items
    assert ic.mitades == 0 and ic.ranuras == 0 and ic.enteras == 2
    assert any("Bolsillo" in a and "solo entera" in a for a in plan.advertencias)


def test_la_tolerancia_de_simetria_es_configurable():
    plan = planificar(_preparacion([_pieza(1, CASI_SIMETRICO, True)], [_item(1, {ROJO: 3})]),
                      ParametrosCorte(tolerancia_simetria=0.05, capas_maximas=1))
    assert plan.cortes[0].items[0].mitades == 1 and plan.advertencias == []


def test_lo_que_no_entra_en_800_cm_se_parte_en_varios_cortes():
    plan = planificar(_preparacion([_pieza(1, RECTANGULO, True)], [_item(1, {ROJO: 400})]),
                      ParametrosCorte(capas_maximas=1))
    assert len(plan.cortes) > 1
    assert all(c.largo_estimado_cm <= 800 for c in plan.cortes)
    assert sum(ic.produccion[ROJO] for c in plan.cortes for ic in c.items) == 400


# --- Caso de 8 buzos de un color (como la tanda A) ------------------------------------------

def test_ocho_buzos_de_un_color_se_cortan_sin_excedente():
    talles = ((2, "2"), (3, "3"), (5, "5"), (6, "6"))
    piezas = [_pieza(1, RECTANGULO, True, talles), _pieza(2, ASIMETRICO, False, talles)]
    unidades = {2: 2, 3: 3, 5: 2, 6: 1}
    items = ([_item(1, {ROJO: n}, id_talle=t, talle=str(t)) for t, n in unidades.items()]
             + [_item(2, {ROJO: 2 * n}, id_talle=t, talle=str(t)) for t, n in unidades.items()])
    plan = planificar(_preparacion(piezas, items), ParametrosCorte())
    assert plan.excedente_total == 0
    producido = sum(ic.produccion[ROJO] for c in plan.cortes for ic in c.items)
    assert producido == 8 + 16


# --- Parámetros y optimizador de residuo ----------------------------------------------------

def test_parametros_aceptan_camel_case_del_backend():
    p = ParametrosCorte.desde_dict({"anchoUtilCm": 80, "capasMaximas": 12, "desconocido": 1},
                                   excedente_maximo_por_item=2)
    assert (p.ancho_util_cm, p.capas_maximas, p.excedente_maximo_por_item) == (80, 12, 2)
    assert (p.minutos_por_corte_adicional, p.minutos_por_recorte, p.aprovechamiento_minimo) == (120, 10, 0.85)
    assert p.sustituciones_habilitadas is False and p.pares_prohibidos == ()


def test_optimizador_de_residuo_no_hace_nada():
    preparacion = _preparacion([_pieza(1, RECTANGULO, True)], [_item(1, {ROJO: 3})])
    p = ParametrosCorte()
    plan = planificar(preparacion, p)
    antes = [(c.capas, [(ic.item.clave, ic.unidades_por_capa) for ic in c.items]) for c in plan.cortes]
    resultado = optimizar_residuo(plan, p)
    assert resultado is plan
    assert [(c.capas, [(ic.item.clave, ic.unidades_por_capa) for ic in c.items]) for c in plan.cortes] == antes
    assert all(c.sustituciones == [] for c in plan.cortes) and plan.advertencias == []


def test_optimizador_de_residuo_habilitado_avisa_que_no_esta_implementado():
    plan = planificar(_preparacion([_pieza(1, RECTANGULO, True)], [_item(1, {ROJO: 3})]), ParametrosCorte())
    optimizar_residuo(plan, ParametrosCorte(sustituciones_habilitadas=True))
    assert any("no está implementado" in a for a in plan.advertencias)
    assert all(c.sustituciones == [] for c in plan.cortes)


def _tanda_dos_pedidos():
    """Como la tanda A con los dos pedidos: 8 buzos Natural (T2, T3, T5, T6) y 23 Verde (T1-T7)."""
    talles = tuple((t, str(t)) for t in range(1, 8))
    piezas = [_pieza(1, RECTANGULO, True, talles), _pieza(2, ASIMETRICO, False, talles),
              _pieza(3, [[0, 0], [58, 0], [58, 65], [0, 65]], True, talles)]
    natural = {2: 2, 3: 3, 5: 2, 6: 1}
    verde = {1: 3, 2: 2, 3: 2, 4: 5, 5: 5, 6: 4, 7: 2}
    items = []
    for t in range(1, 8):
        una = {c: n for c, n in ((ROJO, natural.get(t, 0)), (AZUL, verde.get(t, 0))) if n}
        items += [_item(1, una, t, str(t)), _item(3, una, t, str(t)),
                  _item(2, {c: 2 * n for c, n in una.items()}, t, str(t))]
    return _preparacion(piezas, items)


def test_en_modo_costo_subir_el_tope_nunca_empeora_el_plan():
    preparacion = _tanda_dos_pedidos()
    claves = []
    for tope in range(5):
        plan = planificar(preparacion, ParametrosCorte(excedente_maximo_por_item=tope, modo_objetivo="COSTO"))
        claves.append((len(plan.cortes), round(plan.tela_estimada_cm, 3)))
    for menor, mayor in zip(claves, claves[1:]):
        assert mayor <= menor, claves


def test_en_modo_tela_un_tope_mayor_no_cambia_el_plan_porque_sobrar_gasta_tela():
    preparacion = _tanda_dos_pedidos()
    planes = [planificar(preparacion, ParametrosCorte(excedente_maximo_por_item=t)) for t in (0, 2, 4)]
    assert all(plan.excedente_total == 0 for plan in planes)
    assert len({(len(plan.cortes), round(plan.tela_estimada_cm, 3)) for plan in planes}) == 1


def test_el_plan_siempre_cumple_el_tope_de_la_solicitud():
    preparacion = _tanda_dos_pedidos()
    for modo in ("TELA", "COSTO"):
        for tope in (0, 1, 2, 4):
            plan = planificar(preparacion, ParametrosCorte(excedente_maximo_por_item=tope, modo_objetivo=modo))
            for corte in plan.cortes:
                for ic in corte.items:
                    assert all(e <= max(tope, ic.excedente_inevitable[c]) for c, e in ic.excedente.items())


def test_modo_costo_ahorra_cortes_con_excedente_dentro_del_tope():
    preparacion = _tanda_dos_pedidos()
    tela = planificar(preparacion, ParametrosCorte(excedente_maximo_por_item=4))
    costo = planificar(preparacion, ParametrosCorte(excedente_maximo_por_item=4, modo_objetivo="COSTO"))
    assert len(costo.cortes) < len(tela.cortes)
    assert costo.excedente_total > 0 and costo.tela_estimada_cm > tela.tela_estimada_cm


def test_modo_objetivo_invalido():
    with pytest.raises(ValueError):
        ParametrosCorte(modo_objetivo="RAPIDO")


def test_prendas_partidas_cuenta_producto_y_talle_en_mas_de_un_corte():
    # Producto 7, talle 2: la pieza 1 (simétrica, 3) y la 2 (asimétrica, 6). Con 1 capa máxima y
    # un largo máximo chico no entran juntas: la prenda queda partida.
    piezas = [_pieza(1, RECTANGULO, True), _pieza(2, ASIMETRICO, False)]
    items = [_item(1, {ROJO: 3}), _item(2, {ROJO: 6})]
    for item in items:
        item["origen"] = [{"idProducto": 7, "idPaletaColor": ROJO, "cantidad": item["cantidadTotal"]}]
    plan = planificar(_preparacion(piezas, items), ParametrosCorte(capas_maximas=1, largo_maximo_cm=90))
    assert len(plan.cortes) >= 2
    assert plan.prendas_partidas == [(1, 7, 1)]
    juntas = planificar(_preparacion(piezas, items), ParametrosCorte(capas_maximas=1))
    assert len(juntas.cortes) == 1 and juntas.prendas_partidas == []



def test_el_tope_de_excedente_se_puede_mandar_por_tela():
    preparacion = _tanda_dos_pedidos()
    for grupo in preparacion["grupos"]:
        grupo["codigoTipoTela"] = "RIBB"
    general = ParametrosCorte(modo_objetivo="COSTO", excedente_maximo_por_item=0)
    por_tela = ParametrosCorte.desde_dict({"modoObjetivo": "COSTO", "excedenteMaximoPorTela": {"RIBB": 4}})
    otra_tela = ParametrosCorte(modo_objetivo="COSTO", excedente_maximo_por_tela={"FRIZA": 4})
    assert por_tela.para_tela("RIBB").excedente_maximo_por_item == 4
    assert por_tela.para_tela("FRIZA").excedente_maximo_por_item == 0
    sin, con, ajeno = (planificar(preparacion, p) for p in (general, por_tela, otra_tela))
    assert sin.excedente_total == 0 and ajeno.excedente_total == 0
    assert con.excedente_total > 0 and len(con.cortes) < len(sin.cortes)
