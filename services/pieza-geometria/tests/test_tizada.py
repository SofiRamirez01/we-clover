"""Tests de la integración planificador + algoritmo genético (con un algoritmo mínimo)."""
import pytest
from shapely.affinity import translate
from shapely.geometry import Polygon

from app.corte.parametros import ParametrosCorte
from app.corte.planificador import partir_corte, planificar
from app.corte.residuo import PIEZA_EQUIVALENTE, SOBRE_TALLE, buscar_candidatas
from app.corte.tizada import ENTERA, MITAD_AL_DOBLEZ, PiezaUbicada, optimizar, ubicar_corte, verificar
from test_planificador import ASIMETRICO, RECTANGULO, ROJO, AZUL, _item, _pieza, _preparacion

ANCHO_100 = [[0, 0], [100, 0], [100, 14], [0, 14]]


def _p(**extra):
    return ParametrosCorte(poblacion=6, generaciones=3, tamano_torneo=3, semilla=5, **extra)


def _tizada(piezas, items, **extra):
    p = _p(**extra)
    plan = planificar(_preparacion(piezas, items), p)
    return ubicar_corte(plan.cortes[0], p), p


def test_las_piezas_salen_en_cm_sin_margen_y_con_su_area_original():
    tizada, p = _tizada([_pieza(1, ASIMETRICO, False)], [_item(1, {ROJO: 4})], capas_maximas=1)
    assert len(tizada.piezas) == 2  # 4 unidades por capa = 2 enteras (cada una rinde 2)
    original = Polygon(ASIMETRICO).area
    for pieza in tizada.piezas:
        assert pieza.modo == ENTERA and pieza.rotacion in (0, 180)
        assert pieza.poligono.area == pytest.approx(original)
        minx, miny, maxx, maxy = pieza.poligono.bounds
        assert minx >= p.margen_borde_cm - 1e-6 and maxx <= p.ancho_util_cm - p.margen_borde_cm + 1e-6
        assert miny >= p.margen_borde_cm - 1e-6
    assert [pz.copia for pz in tizada.piezas] == [1, 2]
    assert tizada.verificacion_ok and verificar(tizada.piezas, p) == []


def test_las_mitades_quedan_con_el_eje_sobre_el_doblez():
    # 100 cm de ancho: no entra entera, va como mitad (50 cm) pegada a un doblez.
    tizada, p = _tizada([_pieza(1, ANCHO_100, True)], [_item(1, {ROJO: 3})], capas_maximas=1)
    assert len(tizada.piezas) == 3
    for pieza in tizada.piezas:
        assert pieza.modo == MITAD_AL_DOBLEZ
        minx, _, maxx, _ = pieza.poligono.bounds
        assert maxx - minx == pytest.approx(50)
        borde = minx if pieza.rotacion == 0 else maxx - p.ancho_util_cm
        assert abs(borde) < 1e-6
    assert tizada.verificacion_ok


def test_entre_piezas_queda_el_margen_de_corte():
    tizada, p = _tizada([_pieza(1, RECTANGULO, True)], [_item(1, {ROJO: 8})], capas_maximas=1)
    poligonos = [pz.poligono for pz in tizada.piezas]
    for i, a in enumerate(poligonos):
        for b in poligonos[i + 1:]:
            assert a.distance(b) >= p.margen_corte_cm - 1e-6
    assert tizada.eficiencia == pytest.approx(
        sum(pg.area for pg in poligonos) / (p.ancho_util_cm * tizada.largo_usado_cm))


def test_la_verificacion_es_estricta_con_superposiciones_y_bordes():
    p = _p()
    base = Polygon(RECTANGULO)
    a = PiezaUbicada(1, 1, "2", "A", 1, ENTERA, 0, translate(base, 1, 1))
    b = PiezaUbicada(1, 1, "2", "A", 2, ENTERA, 0, translate(base, 41, 1))  # toca a A (x = 41)
    c = PiezaUbicada(1, 1, "2", "A", 3, ENTERA, 0, translate(base, 1, 51.3))  # a 0,3 cm: menos del margen
    d = PiezaUbicada(1, 1, "2", "A", 4, ENTERA, 0, translate(base, 0.1, 200))  # pegada al doblez
    e = PiezaUbicada(1, 1, "2", "A", 5, MITAD_AL_DOBLEZ, 0, translate(base, 0.01, 300))  # mitad corrida
    errores = verificar([a, b, c, d, e], p)
    assert any("se superponen o se tocan" in x for x in errores)
    assert any("menos del margen" in x for x in errores)
    assert any("margen al doblez" in x for x in errores)
    assert any("eje sobre el doblez" in x for x in errores)
    assert verificar([a], p) == []


def test_un_corte_que_no_se_ubica_se_parte_y_una_pieza_imposible_queda_no_resuelta(monkeypatch):
    import app.corte.tizada as tizada_mod

    piezas = [_pieza(1, RECTANGULO, True), _pieza(2, ASIMETRICO, False)]
    preparacion = _preparacion(piezas, [_item(1, {ROJO: 2}), _item(2, {ROJO: 2})])
    original = tizada_mod.ubicar_corte

    def falla_si_hay_mas_de_un_item(corte, p, *args, **kwargs):
        if len(corte.items) > 1 or corte.items[0].item.geo.id_pieza == 2:
            raise tizada_mod.NoSeUbica(corte.items)
        return original(corte, p, *args, **kwargs)

    monkeypatch.setattr(tizada_mod, "ubicar_corte", falla_si_hay_mas_de_un_item)
    resultado = optimizar(preparacion, _p(capas_maximas=1))
    assert [tz.corte.items[0].item.geo.id_pieza for tz in resultado.tizadas] == [1]
    assert [(nr["idPieza"], nr["motivo"]) for nr in resultado.no_resueltas] == [(2, "NO_SE_PUDO_UBICAR")]
    assert any("se partió" in a for a in resultado.advertencias)


def test_partir_corte_reparte_items_o_unidades_sin_perder_demanda():
    p = _p(capas_maximas=1)
    plan = planificar(_preparacion([_pieza(1, RECTANGULO, True), _pieza(2, ASIMETRICO, False)],
                                   [_item(1, {ROJO: 5}), _item(2, {ROJO: 4})]), p)
    (corte,) = plan.cortes
    partes = partir_corte(corte, p)
    assert len(partes) == 2 and all(len(parte.items) == 1 for parte in partes)
    (solo,) = [parte for parte in partes if parte.items[0].item.geo.id_pieza == 1]
    mitades = partir_corte(solo, p)
    assert sorted(parte.items[0].unidades_por_capa for parte in mitades) == [2, 3]
    assert sum(parte.items[0].produccion[ROJO] for parte in mitades) == 5
    asimetrica = [parte for parte in partes if parte.items[0].item.geo.id_pieza == 2][0]
    assert [parte.items[0].unidades_por_capa for parte in partir_corte(asimetrica, p)] == [2, 2]


def _plan_a_mano(cortes, p):
    """Plan con cortes armados a mano: [(capas, [(idPieza, idTalle, coordenadas, demanda)])]."""
    from app.corte.piezas import preparar_piezas
    from app.corte.planificador import Corte, ItemDemanda, Plan, _item_en_corte, _largo_estimado

    lista = []
    for capas, items in cortes:
        en_corte = []
        for id_pieza, id_talle, coordenadas, demanda in items:
            geos, _ = preparar_piezas([_pieza(id_pieza, coordenadas, True, talles=((id_talle, str(id_talle)),))], p)
            item = ItemDemanda(geos[(id_pieza, id_talle)], demanda, {c: f"color{c}" for c in demanda})
            u = max(-(-n // capas[c]) for c, n in demanda.items())
            en_corte.append(_item_en_corte(item, u, capas, demanda, p))
        lista.append(Corte(1, "FRIZA", "Frisa", capas, en_corte, _largo_estimado(en_corte, p)))
    return Plan(lista, [], [], {})


def test_sustituciones_candidatas_respetan_tela_color_y_aprovechamiento():
    chico = [[0, 0], [38, 0], [38, 48], [0, 48]]
    equivalente = [[0, 0], [40, 0], [40, 50], [20, 49], [0, 50]]  # como un frente: escote poco profundo
    p = _p()
    plan = _plan_a_mano([
        ({ROJO: 1}, [(1, 1, chico, {ROJO: 1})]),                     # corte de una capa: residuo
        ({ROJO: 3}, [(1, 2, RECTANGULO, {ROJO: 6})]),                # mismo molde, talle mayor
        ({ROJO: 2}, [(3, 2, equivalente, {ROJO: 4})]),               # otra pieza que la contiene
        ({AZUL: 2}, [(4, 2, RECTANGULO, {AZUL: 4})]),                # la contiene pero es de otro color
        ({ROJO: 2}, [(5, 2, [[0, 0], [80, 0], [80, 80], [0, 80]], {ROJO: 4})]),  # aprovechamiento bajo
    ], p)
    candidatas = [c for c in buscar_candidatas(plan, p) if c["piezaOrigen"] == 1 and c["talleOrigen"] == 1]
    assert len(candidatas) == 1
    (c,) = candidatas
    # Entra en el rectángulo 40×50 (desperdicio 176) y en el equivalente (156): gana el de menos
    # desperdicio. La pieza 4 es de otro color y la 5 no llega al aprovechamiento mínimo.
    assert (c["piezaUsada"], c["tipo"]) == (3, PIEZA_EQUIVALENTE)
    assert c["desperdicioCm2"] == pytest.approx(Polygon(equivalente).area - Polygon(chico).area, abs=0.1)
    assert c["aprovechamiento"] >= p.aprovechamiento_minimo


def test_sobre_talle_y_pieza_equivalente_se_distinguen():
    chico = [[0, 0], [38, 0], [38, 48], [0, 48]]
    p = _p()
    for usada, tipo in ((1, SOBRE_TALLE), (3, PIEZA_EQUIVALENTE)):
        plan = _plan_a_mano([({ROJO: 1}, [(1, 1, chico, {ROJO: 1})]),
                             ({ROJO: 2}, [(usada, 2, RECTANGULO, {ROJO: 4})])], p)
        (c,) = [c for c in buscar_candidatas(plan, p) if c["piezaOrigen"] == 1]
        assert c["tipo"] == tipo and c["largoRecorteCm"] > 0


def test_la_simplificacion_contiene_a_la_original_y_respeta_el_eje():
    from app.corte.tizada import simplificar_conteniendo, ga as vendor

    curva = Polygon([(x, (x - 300) ** 2 / 300) for x in range(0, 601, 5)] + [(600, 700), (0, 700)])
    simple = simplificar_conteniendo(curva, 1.0)
    assert simple.buffer(1e-9).contains(curva)
    assert len(simple.exterior.coords) < len(curva.exterior.coords)
    assert simple.area - curva.area < curva.length * 2.0  # a lo sumo dos tolerancias de borde
    mitad_base, mitad_margen = vendor.create_half_polygons(curva, 2.5)
    simple_mitad = simplificar_conteniendo(mitad_margen, 1.0, eje_x=mitad_base.bounds[0])
    assert simple_mitad.buffer(1e-9).contains(mitad_margen)
    assert simple_mitad.bounds[0] == pytest.approx(mitad_base.bounds[0])  # sin margen sobre el doblez
    assert simplificar_conteniendo(curva, 0) is curva
