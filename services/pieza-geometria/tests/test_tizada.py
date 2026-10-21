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
    valores = {"poblacion": 6, "generaciones": 3, "tamano_torneo": 3, "semilla": 5,
               "presupuesto_segundos_por_corte": 10, **extra}
    return ParametrosCorte(**valores)


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


# --- Reinicios, presupuesto de tiempo y compactación ----------------------------------------

def _corte(piezas, items, **extra):
    p = _p(**extra)
    return planificar(_preparacion(piezas, items), p).cortes[0], p


def test_cada_reinicio_usa_otra_semilla_y_gana_la_tizada_mas_corta():
    corte, p = _corte([_pieza(1, ASIMETRICO, False)], [_item(1, {ROJO: 8})], capas_maximas=1,
                      mejora_minima_reinicio=1.0, reinicios_sin_mejora=3, compactar=False)
    tizada = ubicar_corte(corte, p)
    assert [c.semilla for c in tizada.corridas] == [5, 6, 7, 8]  # la primera y 3 sin mejora
    verificadas = [c for c in tizada.corridas if c.verificacion_ok]
    mejor = min(verificadas, key=lambda c: c.largo_cm)
    assert tizada.semilla_ganadora == mejor.semilla
    assert tizada.largo_usado_cm == pytest.approx(mejor.largo_cm)
    assert tizada.estado == "COMPLETO" and tizada.reinicios == 4


def test_corte_anticipado_sin_mejora():
    corte, p = _corte([_pieza(1, RECTANGULO, True)], [_item(1, {ROJO: 4})], capas_maximas=1,
                      mejora_minima_reinicio=1.0, reinicios_sin_mejora=2)
    assert ubicar_corte(corte, p).reinicios == 3
    corte, p = _corte([_pieza(1, RECTANGULO, True)], [_item(1, {ROJO: 4})], capas_maximas=1,
                      reinicios_sin_mejora=0)
    assert ubicar_corte(corte, p).reinicios == 1


def test_si_el_presupuesto_no_alcanza_usa_una_sola_corrida_y_la_devuelve_verificada():
    corte, p = _corte([_pieza(1, RECTANGULO, True), _pieza(2, ASIMETRICO, False)],
                      [_item(1, {ROJO: 6}), _item(2, {ROJO: 6})], capas_maximas=1,
                      generaciones=100000, presupuesto_segundos_por_corte=1.0)
    tizada = ubicar_corte(corte, p)
    assert tizada.reinicios == 1 and tizada.estado == "LIMITE_DE_TIEMPO"
    assert 1 <= tizada.corridas[0].generaciones < 100000
    assert tizada.verificacion_ok and len(tizada.piezas) > 0
    assert tizada.segundos < 5


def test_la_cancelacion_no_devuelve_tizada_y_es_distinta_del_limite_de_tiempo():
    import threading
    from app.corte.tizada import ga as vendor

    corte, p = _corte([_pieza(1, RECTANGULO, True)], [_item(1, {ROJO: 4})], capas_maximas=1)
    cancelado = threading.Event()
    cancelado.set()
    with pytest.raises(vendor.CorteCancelado):
        ubicar_corte(corte, p, cancelado=cancelado)


def test_presupuesto_por_modo_por_solicitud_y_con_tope():
    assert ParametrosCorte().presupuesto_segundos == 200
    assert ParametrosCorte(modo_tiempo="RAPIDO").presupuesto_segundos == 60
    assert ParametrosCorte(presupuesto_segundos_por_corte=30).presupuesto_segundos == 30
    assert ParametrosCorte(presupuesto_segundos_por_corte=500).presupuesto_segundos == 200
    assert ParametrosCorte(presupuesto_segundos_por_corte=500, presupuesto_maximo_segundos=300).presupuesto_segundos == 300
    assert ParametrosCorte(modo_tiempo="RAPIDO", presupuesto_maximo_segundos=300).presupuesto_segundos == 60
    assert ParametrosCorte(modo_tiempo="RAPIDO", presupuesto_segundos_por_corte=150).presupuesto_segundos == 60
    assert ParametrosCorte(modo_tiempo="RAPIDO", presupuesto_segundos_por_corte=30).presupuesto_segundos == 30
    assert ParametrosCorte.desde_dict({"presupuestoSegundosPorCorte": 45}).presupuesto_segundos == 45
    with pytest.raises(ValueError):
        ParametrosCorte(modo_tiempo="LENTO")


def test_la_compactacion_baja_y_corre_las_enteras_y_deja_las_mitades_en_el_doblez():
    from app.corte.tizada import armar_entradas, compactar, limites_mm, ga as vendor

    corte, p = _corte([_pieza(1, RECTANGULO, False), _pieza(2, ANCHO_100, True)],
                      [_item(1, {ROJO: 2}), _item(2, {ROJO: 1})], capas_maximas=1)
    entradas, _ = armar_entradas(corte, p)
    limites = limites_mm(entradas, p)
    plantillas = vendor._preparar_entradas(entradas)
    entera = next(t for t in plantillas if t["modo_entrada"] == vendor.ENTERA)
    mitad = next(t for t in plantillas if t["modo_entrada"] == vendor.MITAD)
    alto = limites[1][1]
    g_entera = vendor._nuevo_gen(entera, vendor.ENTERA, 0, x=600, y=alto * 0.5)  # suelta, arriba
    g_mitad = vendor._nuevo_gen(mitad, vendor.MITAD, 180, y=alto * 0.85)
    g_mitad["x"] = vendor._x_mitad(g_mitad["polygon"], 180, limites[2])
    compacto = compactar([g_entera, g_mitad], limites, p)
    nueva_entera, nueva_mitad = compacto
    assert nueva_entera["y"] < alto * 0.5 and nueva_entera["x"] < 600
    assert nueva_mitad["y"] < alto * 0.85 and nueva_mitad["x"] == g_mitad["x"]
    movidos = [vendor._move_margin_entry(g) for g in compacto]
    assert movidos[0][0].intersection(movidos[1][0]).area < 1e-8
    assert min(b[1] for _, b in movidos) == pytest.approx(limites[1][0], abs=p.paso_minimo_compactacion_cm * 10)


def test_la_compactacion_nunca_alarga_la_tizada():
    corte, p = _corte([_pieza(1, ASIMETRICO, False), _pieza(2, RECTANGULO, True)],
                      [_item(1, {ROJO: 6}), _item(2, {ROJO: 5})], capas_maximas=1, reinicios_sin_mejora=0)
    tizada = ubicar_corte(corte, p)
    assert tizada.largo_usado_cm <= tizada.largo_sin_compactar_cm + 1e-9
    assert tizada.verificacion_ok


def test_eficiencia_supuesta_por_tela():
    p = ParametrosCorte()
    assert p.eficiencia_para("FRIZA") == 0.66 and p.eficiencia_para("RIBB") == 0.80
    assert p.eficiencia_para("OTRA") == 0.70
    assert p.para_tela("JERSEY").eficiencia_estimada == 0.65
    assert ParametrosCorte.desde_dict({"eficienciaEstimadaPorTela": {"FRIZA": 0.5}}).eficiencia_para("FRIZA") == 0.5


def test_el_planificador_estima_el_largo_con_la_eficiencia_de_cada_tela():
    preparacion = _preparacion([_pieza(1, RECTANGULO, True)], [_item(1, {ROJO: 10})])
    largos = {}
    for eficiencia in (0.5, 0.8):
        p = ParametrosCorte(capas_maximas=1, eficiencia_estimada_por_tela={"FRIZA": eficiencia})
        largos[eficiencia] = planificar(preparacion, p).cortes[0].largo_estimado_cm
    assert largos[0.5] == pytest.approx(largos[0.8] * 0.8 / 0.5)



def test_el_largo_usado_es_el_punto_mas_alto_de_las_piezas():
    corte, p = _corte([_pieza(1, RECTANGULO, True)], [_item(1, {ROJO: 4})], capas_maximas=1, reinicios_sin_mejora=0)
    tizada = ubicar_corte(corte, p)
    assert tizada.largo_usado_cm == pytest.approx(max(pz.poligono.bounds[3] for pz in tizada.piezas))
    assert min(pz.poligono.bounds[1] for pz in tizada.piezas) >= p.margen_borde_cm - 1e-6


def test_cada_corrida_registra_por_que_termino():
    corte, p = _corte([_pieza(1, RECTANGULO, True)], [_item(1, {ROJO: 2})], capas_maximas=1, reinicios_sin_mejora=0)
    (corrida,) = ubicar_corte(corte, p).corridas
    assert (corrida.motivo_fin, corrida.generaciones) == ("GENERACIONES", 3)
    corte, p = _corte([_pieza(1, RECTANGULO, True)], [_item(1, {ROJO: 2})], capas_maximas=1,
                      generaciones=100000, presupuesto_segundos_por_corte=0.5)
    (corrida,) = ubicar_corte(corte, p).corridas
    assert corrida.motivo_fin == "LIMITE_DE_TIEMPO" and corrida.generaciones < 100000


def test_un_corte_estimado_hasta_10_por_ciento_de_mas_se_intenta_entero():
    # Rectángulos de 40 × 50 cm: el estimado por corte crece con la cantidad.
    def cortes_con(unidades):
        preparacion = _preparacion([_pieza(1, RECTANGULO, True)], [_item(1, {ROJO: unidades})])
        return planificar(preparacion, ParametrosCorte(capas_maximas=1, eficiencia_estimada_por_tela={"FRIZA": 0.7}))

    estimados = {u: cortes_con(u).cortes for u in range(20, 80, 2)}
    entre_800_y_880 = [u for u, cortes in estimados.items() if len(cortes) == 1 and 800 < cortes[0].largo_estimado_cm <= 880]
    assert entre_800_y_880, "hace falta algún caso estimado entre 800 y 880 cm"
    for u, cortes in estimados.items():
        assert all(c.largo_estimado_cm <= 880 + 1e-6 for c in cortes)
        if sum(c.largo_estimado_cm for c in cortes) > 880:
            assert len(cortes) > 1


def test_una_tizada_de_mas_de_800_cm_se_parte_y_se_vuelve_a_correr(monkeypatch):
    import app.corte.tizada as tizada_mod

    original = tizada_mod.ubicar_corte

    def largo_de_mas_si_hay_varios_items(corte, p, *args, **kwargs):
        tizada = original(corte, p, *args, **kwargs)
        if len(corte.items) > 1:
            tizada.largo_usado_cm = p.largo_maximo_cm + 50
        return tizada

    monkeypatch.setattr(tizada_mod, "ubicar_corte", largo_de_mas_si_hay_varios_items)
    preparacion = _preparacion([_pieza(1, RECTANGULO, True), _pieza(2, ASIMETRICO, False)],
                               [_item(1, {ROJO: 2}), _item(2, {ROJO: 2})])
    resultado = optimizar(preparacion, _p(capas_maximas=1))
    assert sorted(tz.corte.items[0].item.geo.id_pieza for tz in resultado.tizadas) == [1, 2]
    assert all(tz.largo_usado_cm <= 800 for tz in resultado.tizadas)
    assert any("más de 800 cm" in a and "se partió" in a for a in resultado.advertencias)



def test_estancamiento_apagado_por_defecto_en_los_dos_modos_y_validado():
    for modo in ("RAPIDO", "COMPLETO"):
        assert ParametrosCorte(modo_tiempo=modo).estancamiento_generaciones is None
    p = ParametrosCorte(generaciones_estancamiento=80)
    assert (p.estancamiento_generaciones, p.estancamiento_umbral_cm, p.generaciones_minimas,
            p.limite_estancamiento_mutacion) == (80, 0.1, 60, 40)
    with pytest.raises(ValueError):  # N <= 40 cortaría antes del empujón de mutación del vendor
        ParametrosCorte(generaciones_estancamiento=40)
    with pytest.raises(ValueError):
        ParametrosCorte(generaciones_estancamiento_por_modo={"COMPLETO": 30})
    with pytest.raises(ValueError):
        ParametrosCorte(generaciones_estancamiento=80, umbral_estancamiento_cm=-1)


def test_estancamiento_por_modo_con_el_valor_de_la_solicitud_por_encima_y_0_apagado():
    por_modo = {"RAPIDO": 50, "COMPLETO": 80}
    umbrales = {"RAPIDO": 0.2, "COMPLETO": 0.1}
    rapido = ParametrosCorte(modo_tiempo="RAPIDO", generaciones_estancamiento_por_modo=por_modo,
                             umbral_estancamiento_cm_por_modo=umbrales)
    completo = ParametrosCorte(generaciones_estancamiento_por_modo=por_modo, umbral_estancamiento_cm_por_modo=umbrales)
    assert (rapido.estancamiento_generaciones, rapido.estancamiento_umbral_cm) == (50, 0.2)
    assert (completo.estancamiento_generaciones, completo.estancamiento_umbral_cm) == (80, 0.1)
    explicito = ParametrosCorte(generaciones_estancamiento=120, umbral_estancamiento_cm=0.3,
                                generaciones_estancamiento_por_modo=por_modo)
    assert (explicito.estancamiento_generaciones, explicito.estancamiento_umbral_cm) == (120, 0.3)
    assert ParametrosCorte(generaciones_estancamiento=0, generaciones_estancamiento_por_modo=por_modo) \
        .estancamiento_generaciones is None
    desde_api = ParametrosCorte.desde_dict({"modoTiempo": "RAPIDO", "generacionesEstancamientoPorModo": por_modo,
                                            "umbralEstancamientoCm": 0.05})
    assert (desde_api.estancamiento_generaciones, desde_api.estancamiento_umbral_cm) == (50, 0.05)


def test_con_estancamiento_encendido_la_corrida_registra_el_corte_y_cuenta_como_terminada():
    corte, p = _corte([_pieza(1, RECTANGULO, True)], [_item(1, {ROJO: 2})], capas_maximas=1,
                      generaciones=400, generaciones_estancamiento=45, generaciones_minimas=60,
                      reinicios_sin_mejora=0)
    tizada = ubicar_corte(corte, p)
    (corrida,) = tizada.corridas
    assert corrida.motivo_fin == "ESTANCAMIENTO" and 60 <= corrida.generaciones < 400
    assert corrida.estado == "COMPLETO" and tizada.estado == "COMPLETO"


def test_despues_de_una_corrida_cortada_por_estancamiento_siguen_los_reinicios():
    corte, p = _corte([_pieza(1, RECTANGULO, True)], [_item(1, {ROJO: 2})], capas_maximas=1,
                      generaciones=400, generaciones_estancamiento=45, generaciones_minimas=60,
                      reinicios_sin_mejora=3, mejora_minima_reinicio=1.0, presupuesto_segundos_por_corte=60)
    tizada = ubicar_corte(corte, p)
    assert tizada.reinicios == 4  # la primera y 3 sin mejora: el estancamiento no frenó los reinicios
    assert all(c.motivo_fin == "ESTANCAMIENTO" and c.estado == "COMPLETO" for c in tizada.corridas)


def test_estancamiento_apagado_da_los_mismos_resultados_con_la_misma_semilla():
    def correr(**extra):
        corte, p = _corte([_pieza(1, RECTANGULO, True), _pieza(2, ASIMETRICO, False)],
                          [_item(1, {ROJO: 3}), _item(2, {ROJO: 4})], capas_maximas=1, generaciones=30,
                          reinicios_sin_mejora=1, **extra)
        tizada = ubicar_corte(corte, p)
        return ([(c.semilla, c.generaciones, c.motivo_fin, round(c.largo_cm, 6)) for c in tizada.corridas],
                [(pz.id_pieza, pz.copia, pz.modo, pz.rotacion, pz.poligono.wkt) for pz in tizada.piezas])

    base = correr()
    assert correr(generaciones_estancamiento=0) == base
    assert correr(generaciones_estancamiento_por_modo={"RAPIDO": None, "COMPLETO": None}) == base
    # Encendido pero sin llegar a cortar (N mayor que las generaciones): tampoco cambia nada.
    assert correr(generaciones_estancamiento=1000) == base
