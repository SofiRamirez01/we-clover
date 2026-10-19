"""Tests de los cambios hechos al vendor (algorpatronsnap) respecto de v1.0.0."""
import random
import subprocess
import sys
import threading

import pytest
from shapely.affinity import rotate, scale, translate
from shapely.geometry import Polygon

import algorpatronsnap as ga
from conftest import VENDOR

ANCHO = 900
LARGO = 3000
OFFSET = 2.5

# Pieza asimétrica (tipo capucha): un espejo no coincide con ella ni con su rotación.
ASIMETRICA = Polygon([(0, 0), (200, 0), (260, 300), (40, 380), (0, 250)])
# Pieza simétrica respecto de x = 100 (tipo frente).
SIMETRICA = Polygon([(0, 0), (200, 0), (200, 300), (140, 360), (60, 360), (0, 300)])


def _margen(polygon):
    return polygon.buffer(OFFSET, join_style=2)


def _correr(entradas, generations=6, population_size=8, **kwargs):
    random.seed(1234)
    parametros = dict(
        x_bounds=(0, ANCHO), y_bounds=(0, LARGO),
        population_size=population_size, generations=generations,
        mutation_rate_x=0.3, mutation_rate_y=0.99,
        mutation_scale_x=200, mutation_scale_y_pos=1, mutation_scale_y_neg=500,
        tournament_size=4, crossover_prob=0.9, mutation_rate_flip=0.5,
        verbose=False,
    )
    parametros.update(kwargs)
    return ga.genetic_algorithm_margin_placement(entradas, **parametros)


def _entrada_ranura(nombre="frente"):
    _, mitad_margen = ga.create_half_polygons(SIMETRICA, OFFSET)
    return {"name": nombre, "polygon": _margen(SIMETRICA), "mitad": mitad_margen,
            "simetrico": True, "modo": ga.RANURA}


def _movidos(individuo):
    return [ga._move_margin_entry(gen)[0] for gen in ga._activos(individuo)]


# --- Rotación de 180° -------------------------------------------------------------------

def test_rotacion_180_mantiene_el_hilo_y_no_espeja():
    plantilla = ga._preparar_entradas([{"name": "capucha", "polygon": ASIMETRICA}])[0]
    original = plantilla["formas"][(ga.ENTERA, 0)]
    rotada = plantilla["formas"][(ga.ENTERA, 180)]

    # El largo (eje vertical, sentido del hilo) y el ancho no cambian: el hilo sigue vertical.
    ox0, oy0, ox1, oy1 = original.bounds
    rx0, ry0, rx1, ry1 = rotada.bounds
    assert rx1 - rx0 == pytest.approx(ox1 - ox0)
    assert ry1 - ry0 == pytest.approx(oy1 - oy0)
    # Es una rotación (dos veces vuelve al original) y no un espejo de ningún eje.
    assert rotate(rotada, 180, origin="centroid").symmetric_difference(original).area < 1e-6
    centro = original.centroid
    for espejo in (scale(original, -1, 1, origin=centro), scale(original, 1, -1, origin=centro)):
        assert rotada.symmetric_difference(espejo).area > 1.0


def test_el_algoritmo_solo_usa_orientaciones_0_y_180():
    entradas = [{"name": f"capucha{i}", "polygon": _margen(ASIMETRICA)} for i in range(3)]
    mejor, _, _ = _correr(entradas, generations=10, mutation_rate_flip=0.9)
    for gen in mejor:
        assert gen["rotacion"] in (0, 180)
        assert gen["polygon"] is gen["formas"][(gen["modo"], gen["rotacion"])]


def test_place_polygon_aplica_la_misma_transformacion_que_el_algoritmo():
    base = ASIMETRICA
    margen = _margen(base)
    gen = {"polygon": rotate(margen, 180, origin="centroid"), "x": 400.0, "y": 700.0}
    margen_movido, _ = ga._move_margin_entry(gen)
    base_movida = ga.place_polygon(base, margen, 180, 400.0, 700.0)
    # La pieza sin margen queda adentro de su margen ya ubicado, a OFFSET del borde.
    assert margen_movido.buffer(1e-6).contains(base_movida)
    assert base_movida.distance(margen_movido.exterior) == pytest.approx(OFFSET, abs=1e-6)


# --- Mitad al doblez --------------------------------------------------------------------

def test_mitad_se_corta_por_el_eje_y_no_tiene_margen_sobre_el_doblez():
    mitad_base, mitad_margen = ga.create_half_polygons(SIMETRICA, OFFSET)
    assert mitad_base.area == pytest.approx(SIMETRICA.area / 2)
    assert mitad_base.bounds[0] == pytest.approx(100)
    # El margen agrega OFFSET a los costados libres pero no del lado del eje.
    assert mitad_margen.bounds[0] == pytest.approx(100)
    assert mitad_margen.bounds[2] == pytest.approx(200 + OFFSET)
    assert mitad_margen.bounds[3] == pytest.approx(360 + OFFSET)


def test_mitad_queda_con_el_eje_sobre_el_doblez():
    _, mitad_margen = ga.create_half_polygons(SIMETRICA, OFFSET)
    entradas = [{"name": f"m{i}", "mitad": mitad_margen, "simetrico": True, "modo": ga.MITAD}
                for i in range(4)]
    mejor, fitness, _ = _correr(entradas, generations=10, mutation_rate_flip=0.5)
    assert fitness > -30000000000
    for gen in mejor:
        minx, _, maxx, _ = ga._move_margin_entry(gen)[1]
        if gen["rotacion"] == 0:
            assert minx == pytest.approx(0, abs=1e-6)
        else:
            assert maxx == pytest.approx(ANCHO, abs=1e-6)


def test_mitad_usa_el_doblez_y_la_entera_los_limites_internos():
    _, mitad_margen = ga.create_half_polygons(SIMETRICA, OFFSET)
    entradas = [
        {"name": "mitad", "mitad": mitad_margen, "simetrico": True, "modo": ga.MITAD},
        {"name": "entera", "polygon": _margen(ASIMETRICA)},
    ]
    mejor, fitness, _ = _correr(entradas, x_bounds=(10, ANCHO - 10), doblez=(0, ANCHO))
    assert fitness > -30000000000
    mitad, entera = mejor
    minx, _, maxx, _ = ga._move_margin_entry(mitad)[1]
    assert minx == pytest.approx(0, abs=1e-6) or maxx == pytest.approx(ANCHO, abs=1e-6)
    ex0, _, ex1, _ = ga._move_margin_entry(entera)[1]
    assert ex0 >= 10 - 1e-6 and ex1 <= ANCHO - 10 + 1e-6


def test_ranura_rinde_siempre_una_entera_o_dos_mitades():
    entradas = [_entrada_ranura(f"f{i}") for i in range(3)]
    mejor, fitness, _ = _correr(entradas, generations=15, mutation_rate_modo=0.8)
    assert fitness > -30000000000
    assert len(mejor) == 6
    for i in range(0, 6, 2):
        g0, g1 = mejor[i], mejor[i + 1]
        assert g0["ranura"] == g1["ranura"]
        if g0["modo"] == ga.ENTERA:
            assert g0["activo"] and not g1["activo"]
        else:
            assert g0["activo"] and g1["activo"] and g1["modo"] == ga.MITAD


def test_alternar_modo_pasa_de_entera_a_dos_mitades_y_vuelve():
    random.seed(7)
    plantillas = ga._preparar_entradas([_entrada_ranura()])
    individuo = [ga._nuevo_gen(plantillas[0], ga.ENTERA, 0, x=450, y=400),
                 ga._nuevo_gen(plantillas[1], ga.ENTERA, 0, activo=False)]
    movidos = [ga._move_margin_entry(individuo[0]), None]
    ga._alternar_modo_ranura(individuo, movidos, 0, 1, (0, ANCHO), (0, LARGO), (0, ANCHO))
    assert [g["modo"] for g in individuo] == [ga.MITAD, ga.MITAD]
    assert all(g["activo"] for g in individuo)
    ga._alternar_modo_ranura(individuo, movidos, 0, 1, (0, ANCHO), (0, LARGO), (0, ANCHO))
    assert individuo[0]["modo"] == ga.ENTERA and not individuo[1]["activo"]


# --- Lienzo, simétrico, progreso y cancelación ------------------------------------------

def test_nada_se_superpone_ni_sale_del_lienzo():
    entradas = ([{"name": f"c{i}", "polygon": _margen(ASIMETRICA)} for i in range(3)]
                + [_entrada_ranura(f"f{i}") for i in range(2)])
    mejor, fitness, _ = _correr(entradas, generations=10)
    assert fitness > -30000000000
    movidos = _movidos(mejor)
    for poligono in movidos:
        x0, y0, x1, y1 = poligono.bounds
        assert x0 >= -1e-6 and y0 >= -1e-6 and x1 <= ANCHO + 1e-6 and y1 <= LARGO + 1e-6
    for i, a in enumerate(movidos):
        for b in movidos[i + 1:]:
            assert a.intersection(b).area < 1e-6


def test_los_genes_llevan_la_marca_de_simetrico():
    entradas = [_entrada_ranura(), {"name": "c", "polygon": _margen(ASIMETRICA)}]
    mejor, _, _ = _correr(entradas, generations=2)
    assert [g["simetrico"] for g in mejor] == [True, True, False]


def test_on_generation_informa_cada_generacion():
    llamadas = []
    entradas = [{"name": f"c{i}", "polygon": _margen(ASIMETRICA)} for i in range(2)]
    _correr(entradas, generations=5, on_generation=lambda *args: llamadas.append(args))
    assert [(g, total) for g, total, _, _ in llamadas] == [(i, 5) for i in range(5)]
    fitness = [f for _, _, f, _ in llamadas]
    assert fitness == sorted(fitness)
    assert all(largo > 0 for _, _, _, largo in llamadas)


def test_cancelacion_corta_al_inicio_de_la_generacion_siguiente():
    cancelado = threading.Event()
    vistas = []

    def on_generation(gen, total, mejor, largo):
        vistas.append(gen)
        if gen == 2:
            cancelado.set()

    entradas = [{"name": f"c{i}", "polygon": _margen(ASIMETRICA)} for i in range(2)]
    with pytest.raises(ga.CorteCancelado):
        _correr(entradas, generations=50, on_generation=on_generation, cancelado=cancelado)
    assert vistas == [0, 1, 2]


def test_cancelacion_previa_no_arranca():
    cancelado = threading.Event()
    cancelado.set()
    with pytest.raises(ga.CorteCancelado):
        _correr([{"name": "c", "polygon": _margen(ASIMETRICA)}], cancelado=cancelado)


def test_importar_el_algoritmo_no_carga_matplotlib_ni_polyorder():
    # numpy no se chequea: lo carga shapely 2, que depende de él.
    codigo = (
        "import sys; import algorpatronsnap; "
        "print(any(m in sys.modules for m in ('matplotlib', 'polyorder', 'shapely.plotting')))"
    )
    salida = subprocess.run([sys.executable, "-c", codigo], cwd=VENDOR,
                            capture_output=True, text=True, check=True)
    assert salida.stdout.strip() == "False"


# --- Comportamiento distinto de v1.0.0 (un test por cambio, ver README) -----------------

CUADRADO = Polygon([(0, 0), (100, 0), (100, 100), (0, 100)])


def translate_cuadrado(dx, dy):
    return translate(CUADRADO, xoff=dx, yoff=dy)


def _gen_entero(nombre, x, y, polygon=CUADRADO):
    plantilla = ga._preparar_entradas([{"name": nombre, "polygon": polygon}])[0]
    return ga._nuevo_gen(plantilla, ga.ENTERA, 0, x=x, y=y)


def test_cambio1_dos_piezas_que_mutan_en_la_misma_generacion_no_quedan_superpuestas(monkeypatch):
    # A (x=100) y B (x=500) proponen moverse las dos a x=300, que está libre en la posición
    # anterior. En v1.0.0 B se validaba contra la posición vieja de A y quedaban encimadas.
    individuo = [_gen_entero("A", 100, 50), _gen_entero("B", 500, 50)]
    desplazamientos = iter([200.0, -200.0])
    monkeypatch.setattr(ga.random, "uniform", lambda a, b: next(desplazamientos))
    mutado = ga.mutate_margin_individual(
        individuo, mutation_rate_x=1.0, mutation_rate_y=0.0, x_bounds=(0, ANCHO),
        y_bounds=(0, LARGO), mutation_scale_x=300, mutation_rate_flip=0.0)
    assert mutado[0]["x"] == 300
    assert mutado[1]["x"] == 500  # rechazada: chocaría con A ya movida
    a, b = (ga._move_margin_entry(g)[0] for g in mutado)
    assert a.intersection(b).area == 0


def test_cambio2_la_poblacion_inicial_usa_las_dos_rotaciones():
    random.seed(3)
    entradas = [{"name": "capucha", "polygon": _margen(ASIMETRICA)}]
    poblacion = ga.create_margin_population(40, entradas, (0, ANCHO), (0, LARGO))
    assert {individuo[0]["rotacion"] for individuo in poblacion} == {0, 180}


def test_cambio3_si_la_mitad_no_entra_la_ranura_prueba_entera(monkeypatch):
    # El doblez se achica a 50 mm: la mitad (100 mm de ancho) no entra, la entera sí. El sorteo
    # siempre da MITAD primero; sin el respaldo, volver a sortear el individuo no alcanzaría.
    monkeypatch.setattr(ga.random, "sample", lambda opciones, k: [ga.MITAD, ga.ENTERA])
    monkeypatch.setattr(ga.random, "choice",
                        lambda opciones: ga.MITAD if ga.MITAD in opciones else opciones[0])
    poblacion = ga.create_margin_population(
        3, [_entrada_ranura()], (0, ANCHO), (0, LARGO), doblez=(0, 50), max_individual_attempts=3)
    for g0, g1 in poblacion:
        assert g0["modo"] == ga.ENTERA and g0["activo"] and not g1["activo"]


def test_cambio4_mitad_rotada_queda_exactamente_sobre_el_doblez():
    # Con esta forma, rotada 180°, el redondeo la deja 1,1e-13 mm afuera del doblez derecho:
    # sin la tolerancia de límites la mitad se rechazaría aunque esté bien ubicada.
    pieza = Polygon([(0, 0), (373.183, 0), (373.183, 513.353), (186.5915, 585.207), (0, 513.353)])
    _, mitad_margen = ga.create_half_polygons(pieza, OFFSET)
    plantilla = ga._preparar_entradas([{"name": "m", "mitad": mitad_margen, "modo": ga.MITAD}])[0]
    for rotacion in ga.ROTACIONES:
        gen = ga._nuevo_gen(plantilla, ga.MITAD, rotacion, y=500.0)
        gen["x"] = ga._x_mitad(gen["polygon"], rotacion, (0, ANCHO))
        poligono, (minx, _, maxx, _) = ga._move_margin_entry(gen)
        borde = minx if rotacion == 0 else maxx
        assert abs(borde - (0 if rotacion == 0 else ANCHO)) < 1e-9
        assert ga._placement_is_valid_moved(poligono, poligono.bounds, [], (0, ANCHO), (0, LARGO))
        assert ga.score_margin_positions([gen], (2.5, ANCHO - 2.5), (0, LARGO), (0, ANCHO)) > -3e10



def test_cambio4_la_tolerancia_es_solo_de_limites_y_no_de_superposicion():
    a = translate_cuadrado(0, 0)
    # Sale del lienzo 1e-10: se acepta. Sale 1e-7: se rechaza.
    assert ga._placement_is_valid_moved(a, (-1e-10, 0, 100, 100), [], (0, ANCHO), (0, LARGO))
    assert not ga._placement_is_valid_moved(a, (-1e-7, 0, 100, 100), [], (0, ANCHO), (0, LARGO))
    # Una superposición de 1e-7 mm de profundidad (área 1e-5) se rechaza igual que en v1.0.0.
    b = translate_cuadrado(100 - 1e-7, 0)
    assert not ga._placement_is_valid_moved(b, b.bounds, [(a, a.bounds)], (0, ANCHO), (0, LARGO))
    # Tocarse sin superponerse está permitido.
    c = translate_cuadrado(100, 0)
    assert ga._placement_is_valid_moved(c, c.bounds, [(a, a.bounds)], (0, ANCHO), (0, LARGO))


def test_cambio5_la_cancelacion_se_revisa_mientras_se_arma_la_poblacion():
    class EventoQueSeActiva:
        """Se activa recién en la tercera consulta: después de armar dos individuos."""
        def __init__(self):
            self.consultas = 0

        def is_set(self):
            self.consultas += 1
            return self.consultas >= 3

    evento = EventoQueSeActiva()
    with pytest.raises(ga.CorteCancelado):
        ga.create_margin_population(10, [{"name": "c", "polygon": _margen(ASIMETRICA)}],
                                    (0, ANCHO), (0, LARGO), cancelado=evento)
    assert evento.consultas == 3


def test_cambio6_mutation_rate_modo_vale_0_1_por_defecto_y_0_no_cambia_modos():
    import inspect
    firma = inspect.signature(ga.genetic_algorithm_margin_placement)
    assert firma.parameters["mutation_rate_modo"].default == 0.1
    assert inspect.signature(ga.mutate_margin_individual).parameters["mutation_rate_modo"].default == 0.0

    random.seed(11)
    plantillas = ga._preparar_entradas([_entrada_ranura(f"f{i}") for i in range(3)])
    individuo = ga.create_margin_population(1, plantillas, (0, ANCHO), (0, LARGO))[0]
    modos = [g["modo"] for g in individuo]
    for _ in range(30):
        individuo = ga.mutate_margin_individual(individuo, 0.3, 0.99, (0, ANCHO), (0, LARGO),
                                                mutation_rate_modo=0.0)
        assert [g["modo"] for g in individuo] == modos


def test_sin_ubicacion_valida_indica_que_pieza_no_entra():
    gigante = Polygon([(0, 0), (1000, 0), (1000, 100), (0, 100)])  # más ancha que el lienzo
    entradas = [{"name": "chica", "polygon": CUADRADO}, {"name": "gigante", "polygon": gigante}]
    with pytest.raises(ga.SinUbicacionValida) as error:
        ga.create_margin_population(2, entradas, (0, ANCHO), (0, LARGO), max_individual_attempts=5)
    assert error.value.indices_entrada == [1]
    assert isinstance(error.value, RuntimeError)  # compatible con quien capturaba RuntimeError
