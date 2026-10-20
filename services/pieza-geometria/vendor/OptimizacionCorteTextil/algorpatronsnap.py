from math import hypot
from collections import OrderedDict
import random
import shapely
from shapely.affinity import translate, rotate
from shapely.geometry import Polygon, box
from shapely.geometry.polygon import orient

# Modificado respecto de v1.0.0 (S.G.O.T.): matplotlib, shapely.plotting, numpy y polyorder
# se importan adentro de las funciones que los usan, para que el servidor no los cargue.
# El espejo (flipped) se reemplazó por una rotación de 180°, y las piezas simétricas pueden
# ir ENTERA o como MITAD al doblez (ver _preparar_entradas).

ENTERA = "ENTERA"
MITAD = "MITAD"
RANURA = "RANURA"
ROTACIONES = (0, 180)
_EPS = 1e-9


class CorteCancelado(Exception):
    """El indicador de cancelación se activó durante la búsqueda."""


class SinUbicacionValida(RuntimeError):
    """No se pudo armar un individuo válido para la población inicial.

    `indices_entrada` son las entradas que no encontraron lugar, de la que más falló a la
    que menos (vacío si todas entraron pero el individuo resultó inválido igual).
    """

    def __init__(self, mensaje, indices_entrada=()):
        super().__init__(mensaje)
        self.indices_entrada = list(indices_entrada)


_PROPIEDADES = OrderedDict()
_PROPIEDADES_MAX = 4096


def _polygon_properties(polygon):
    """Cache immutable geometry properties reused throughout the search.

    La caché es por identidad del objeto (v1.0.0 usaba lru_cache, que para buscar compara y
    hashea las coordenadas del polígono y costaba más de lo que ahorraba). Guarda una referencia
    al polígono, así su id no se reutiliza mientras esté en la caché.
    """
    clave = id(polygon)
    guardado = _PROPIEDADES.get(clave)
    if guardado is not None and guardado[0] is polygon:
        _PROPIEDADES.move_to_end(clave)
        return guardado[1]
    minx, miny, maxx, maxy = polygon.bounds
    center = polygon.centroid
    propiedades = (minx, miny, maxx, maxy, center.x, center.y)
    _PROPIEDADES[clave] = (polygon, propiedades)
    if len(_PROPIEDADES) > _PROPIEDADES_MAX:
        _PROPIEDADES.popitem(last=False)
    return propiedades


def create_half_polygons(base_polygon, offset):
    """Corta una pieza simétrica por su eje vertical (centro del bounding box).

    Devuelve (mitad_base, mitad_margen), las dos con el eje como borde izquierdo (minx):
    la mitad derecha de la pieza. El margen es un buffer en inglete recortado por el
    semiplano del eje, para que no quede margen sobre el doblez.
    """
    minx, miny, maxx, maxy = base_polygon.bounds
    eje = (minx + maxx) / 2
    alto = maxy - miny
    lado_derecho = box(eje, miny - offset - alto, maxx + offset + alto, maxy + offset + alto)
    mitad_base = _poligono_mayor(base_polygon.intersection(lado_derecho))
    mitad_margen = _poligono_mayor(
        mitad_base.buffer(offset, join_style=2).intersection(lado_derecho)
    )
    return orient(mitad_base), orient(mitad_margen)


def _poligono_mayor(geometria):
    if geometria.geom_type == "Polygon":
        return geometria
    poligonos = [g for g in getattr(geometria, "geoms", []) if g.geom_type == "Polygon"]
    if not poligonos:
        raise ValueError("La geometría no tiene ningún polígono")
    return max(poligonos, key=lambda p: p.area)


def place_polygon(polygon, reference_polygon, rotacion, x, y):
    """Aplica a `polygon` la misma transformación que el algoritmo aplicó a `reference_polygon`.

    `reference_polygon` es el polígono con margen en orientación 0 que recibió el algoritmo;
    `polygon` es la pieza sin margen en ese mismo sistema de coordenadas. El algoritmo rota
    sobre el centroide del polígono con margen y lo lleva a (x, y).
    """
    centro = reference_polygon.centroid
    rotado = rotate(polygon, rotacion, origin=centro) if rotacion else polygon
    return translate(rotado, xoff=x - centro.x, yoff=y - centro.y)


def _preparar_entradas(margin_polygons):
    """Normaliza las entradas en plantillas de gen, una por ubicación posible.

    Cada entrada es {name, polygon, simetrico, modo?, mitad?}:
    - modo ENTERA (default): la pieza entera, ubicada libremente.
    - modo MITAD: `mitad` (ver create_half_polygons) con el eje pegado a un doblez.
    - modo RANURA: rinde lo mismo que 1 entera o que 2 mitades, y el algoritmo elige cuál;
      se expande en dos plantillas (rol 0 y rol 1); en ENTERA la de rol 1 queda inactiva.
    """
    if margin_polygons and "formas" in margin_polygons[0]:
        return margin_polygons
    plantillas = []
    for indice, entry in enumerate(margin_polygons):
        modo = entry.get("modo", ENTERA)
        formas = {}
        if modo in (ENTERA, RANURA):
            formas[(ENTERA, 0)] = entry["polygon"]
            formas[(ENTERA, 180)] = rotate(entry["polygon"], 180, origin="centroid")
        if modo in (MITAD, RANURA):
            mitad = entry.get("mitad")
            if mitad is None:
                raise ValueError(f"La entrada {entry.get('name')} en modo {modo} no tiene 'mitad'")
            formas[(MITAD, 0)] = mitad
            formas[(MITAD, 180)] = rotate(mitad, 180, origin="centroid")
        base = {
            "name": entry.get("name"),
            "simetrico": bool(entry.get("simetrico", False)),
            "indice_entrada": indice,
            "formas": formas,
        }
        if modo == RANURA:
            for rol in (0, 1):
                plantillas.append({**base, "modo_entrada": RANURA, "ranura": indice, "rol": rol})
        else:
            plantillas.append({**base, "modo_entrada": modo, "ranura": None, "rol": 0})
    return plantillas


def _nuevo_gen(plantilla, modo, rotacion, x=0.0, y=0.0, activo=True):
    gen = {key: value for key, value in plantilla.items()}
    gen.update({
        "modo": modo,
        "rotacion": rotacion,
        "activo": activo,
        "polygon": plantilla["formas"][(modo, rotacion)] if activo else None,
        "x": x,
        "y": y,
    })
    return gen


def _x_mitad(polygon, rotacion, doblez):
    """x del centroide que deja el eje de la mitad sobre el doblez (izquierdo si 0°, derecho si 180°)."""
    minx, _, maxx, _, center_x, _ = _polygon_properties(polygon)
    if rotacion == 0:
        return doblez[0] + (center_x - minx)
    return doblez[1] - (maxx - center_x)


def _limites_x(gene, x_bounds, doblez):
    return doblez if gene.get("modo") == MITAD else x_bounds


def _activos(individual):
    return [gene for gene in individual if gene.get("activo", True)]


def _revisar_cancelacion(cancelado):
    if cancelado is not None and cancelado.is_set():
        raise CorteCancelado()


def load_margin_polygons(patterns_file="patrones.json"):
    """Load margin geometries saved in the patterns file."""
    import polyorder

    patterns = polyorder.load_patrones_from_file(patterns_file)
    return [
        {
            "name": pattern["margen"]["name"],
            "polygon": Polygon(pattern["margen"]["coordinates"]),
            "simetrico": pattern["margen"].get("simetrico", False),
        }
        for pattern in patterns
    ]


def create_margin_rectangle(patterns_file="patrones.json", width=900):
    """Create a rectangle whose height is based on saved margin geometries."""
    margin_polygons = load_margin_polygons(patterns_file)
    total_length = sum(entry["polygon"].length for entry in margin_polygons)
    return Polygon([(0, 0), (width, 0), (width, total_length), (0, total_length)])


def plot_margin_individual(individual, width=900, title=None):
    """Plot an individual's margin polygon placements over a bounding box.
    
    Blue box: width 900, height determined by the max y of all moved polygons.
    Red polygons: margin polygons positioned according to individual's x/y.
    """
    import matplotlib.pyplot as plt
    import shapely.plotting

    # Translate polygons to their target positions
    moved_polygons = []
    for entry in _activos(individual):
        poly = entry["polygon"]
        center = poly.centroid
        x_offset = entry["x"] - center.x
        y_offset = entry["y"] - center.y
        moved = translate(poly, xoff=x_offset, yoff=y_offset)
        moved_polygons.append(moved)
    
    # Calculate bounding box for all moved polygons
    all_bounds = [poly.bounds for poly in moved_polygons]
    max_y = max(b[3] for b in all_bounds)
    
    # Create the blue bounding box (width 900, height from moved polygons)
    box = Polygon([(0, 0), (width, 0), (width, max_y), (0, max_y)])
    
    # Plot
    fig, ax = plt.subplots(figsize=(10, 8))
    
    # Plot blue box
    shapely.plotting.plot_polygon(box, ax=ax, color="blue", alpha=0.3, edgecolor="blue", linewidth=2)
    
    # Plot red margin polygons
    for poly in moved_polygons:
        shapely.plotting.plot_polygon(poly, ax=ax, color="red", alpha=0.6, edgecolor="darkred", linewidth=1)
    
    ax.set_aspect("equal", adjustable="box")
    ax.set_xlim(-50, width + 50)
    ax.set_ylim(-50, max_y + 50)
    plt.title(title or "Posicionamiento de Polígonos de Margen")
    plt.xlabel("X")
    plt.ylabel("Y")
    plt.show()


def plot_margin_snapshots(snapshots, width=900):
    """Plot saved best individuals for stored generation snapshots side-by-side.

    Each saved generation is drawn in its own subplot with a shared axis range
    so placements can be compared visually.
    """
    if not snapshots:
        return

    import matplotlib.pyplot as plt
    import numpy as np
    import shapely.plotting

    snapshot_polygons = []
    all_bounds = []
    for generation, individual in snapshots:
        moved_polygons = []
        for entry in _activos(individual):
            poly = entry["polygon"]
            center = poly.centroid
            x_offset = entry["x"] - center.x
            y_offset = entry["y"] - center.y
            moved = translate(poly, xoff=x_offset, yoff=y_offset)
            moved_polygons.append(moved)
            all_bounds.append(moved.bounds)
        snapshot_polygons.append((generation, moved_polygons))

    # determine common bounds
    max_y = max(b[3] for b in all_bounds)

    n = len(snapshot_polygons)
    fig_w = max(4 * n, 8)
    fig, axes = plt.subplots(1, n, figsize=(fig_w, 8), squeeze=False)
    axes = axes[0]

    box = Polygon([(0, 0), (width, 0), (width, max_y), (0, max_y)])

    colors = [plt.cm.viridis(v) for v in np.linspace(0, 1, n)]
    for ax, (generation, moved_polygons), color in zip(axes, snapshot_polygons, colors):
        shapely.plotting.plot_polygon(box, ax=ax, color="blue", alpha=0.2, edgecolor="blue", linewidth=1)
        for poly in moved_polygons:
            shapely.plotting.plot_polygon(poly, ax=ax, color=color, alpha=0.6, edgecolor=color, linewidth=0.5)
        ax.set_title(f"Gen {generation}")
        ax.set_aspect("equal", adjustable="box")
        ax.set_xlim(-50, width + 50)
        ax.set_ylim(-50, max_y + 50)
        ax.set_xlabel("X")
        ax.set_ylabel("Y")

    plt.tight_layout()
    plt.show()


def get_margin_position_bounds(polygon, x_bounds, y_bounds):
    """Return valid x/y centroid ranges for a polygon inside the bounding area."""
    poly_minx, poly_miny, poly_maxx, poly_maxy, center_x, center_y = _polygon_properties(polygon)
    min_x = x_bounds[0] + (center_x - poly_minx)
    max_x = x_bounds[1] - (poly_maxx - center_x)
    min_y = y_bounds[0] + (center_y - poly_miny)
    max_y = y_bounds[1] - (poly_maxy - center_y)
    return min_x, max_x, min_y, max_y


def _move_margin_entry(entry):
    """Return a translated polygon and its bounds for a placement."""
    poly = entry["polygon"]
    poly_minx, poly_miny, poly_maxx, poly_maxy, center_x, center_y = _polygon_properties(poly)
    x_offset = entry["x"] - center_x
    y_offset = entry["y"] - center_y
    bounds = (
        poly_minx + x_offset,
        poly_miny + y_offset,
        poly_maxx + x_offset,
        poly_maxy + y_offset,
    )
    return _trasladar(poly, x_offset, y_offset), bounds


_ANILLOS = OrderedDict()


def _trasladar(polygon, dx, dy):
    """Igual que shapely.affinity.translate para un polígono, sin armar la matriz afín en cada
    llamada: suma el desplazamiento a las coordenadas (guardadas por identidad) y construye el
    polígono de una vez."""
    clave = id(polygon)
    guardado = _ANILLOS.get(clave)
    if guardado is None or guardado[0] is not polygon:
        coordenadas = None if len(polygon.interiors) else shapely.get_coordinates(polygon.exterior)
        guardado = (polygon, coordenadas)
        _ANILLOS[clave] = guardado
        if len(_ANILLOS) > _PROPIEDADES_MAX:
            _ANILLOS.popitem(last=False)
    if guardado[1] is None:  # con agujeros: el camino de v1.0.0
        return translate(polygon, xoff=dx, yoff=dy)
    return shapely.polygons(guardado[1] + (dx, dy))


def _placement_is_valid(candidate, placed, x_bounds, y_bounds):
    """Check a candidate without recalculating the complete individual score."""
    candidate_poly, candidate_bounds = _move_margin_entry(candidate)
    placed_data = [_move_margin_entry(entry) for entry in placed]
    return _placement_is_valid_moved(
        candidate_poly, candidate_bounds, placed_data, x_bounds, y_bounds
    )


def _valido_perezoso(entry, placed_data, x_bounds, y_bounds, excluded_index=None):
    """Como _placement_is_valid_moved, pero sin trasladar el polígono si ninguna caja vecina se
    superpone (el caso más común). Devuelve None si no es válido, o (polígono movido o None,
    bounds)."""
    poly = entry["polygon"]
    pminx, pminy, pmaxx, pmaxy, center_x, center_y = _polygon_properties(poly)
    dx, dy = entry["x"] - center_x, entry["y"] - center_y
    bounds = (pminx + dx, pminy + dy, pmaxx + dx, pmaxy + dy)
    if (bounds[0] < x_bounds[0] - _EPS or bounds[1] < y_bounds[0] - _EPS
            or bounds[2] > x_bounds[1] + _EPS or bounds[3] > y_bounds[1] + _EPS):
        return None
    excluded = excluded_index if isinstance(excluded_index, (set, frozenset)) else {excluded_index}
    movido = None
    for index, placed in enumerate(placed_data):
        if placed is None or index in excluded:
            continue
        placed_poly, placed_bounds = placed
        if (bounds[2] < placed_bounds[0] or placed_bounds[2] < bounds[0]
                or bounds[3] < placed_bounds[1] or placed_bounds[3] < bounds[1]):
            continue
        if movido is None:
            movido = _trasladar(poly, dx, dy)
            shapely.prepare(movido)
        if movido.intersects(placed_poly) and movido.intersection(placed_poly).area > 1e-8:
            return None
    return movido, bounds


def _placement_is_valid_moved(candidate_poly, candidate_bounds, placed_data,
                              x_bounds, y_bounds, excluded_index=None):
    """Check a moved candidate against already translated polygons.

    `placed_data` puede tener None (genes inactivos); `excluded_index` puede ser un índice o
    un conjunto de índices.
    """
    min_x, min_y, max_x, max_y = candidate_bounds
    if (min_x < x_bounds[0] - _EPS or min_y < y_bounds[0] - _EPS
            or max_x > x_bounds[1] + _EPS or max_y > y_bounds[1] + _EPS):
        return False

    excluded = excluded_index if isinstance(excluded_index, (set, frozenset)) else {excluded_index}
    preparado = False
    for index, placed in enumerate(placed_data):
        if placed is None or index in excluded:
            continue
        placed_poly, placed_bounds = placed
        if (
            candidate_bounds[2] < placed_bounds[0]
            or placed_bounds[2] < candidate_bounds[0]
            or candidate_bounds[3] < placed_bounds[1]
            or placed_bounds[3] < candidate_bounds[1]
        ):
            continue
        # Mismo criterio que v1.0.0 (área de intersección > 1e-8), pero primero `intersects`
        # sobre el candidato preparado: casi siempre no se tocan y no hace falta el área.
        if not preparado:
            shapely.prepare(candidate_poly)
            preparado = True
        if candidate_poly.intersects(placed_poly) and candidate_poly.intersection(placed_poly).area > 1e-8:
            return False
    return True


def _individual_max_y(individual):
    """Return the highest translated boundary of an individual."""
    max_y = float("-inf")
    for entry in _activos(individual):
        properties = _polygon_properties(entry["polygon"])
        max_y = max(max_y, properties[3] + entry["y"] - properties[5])
    return max_y


def _buscar_ubicacion(gene, placed_data, x_bounds, y_bounds, doblez, attempts, excluded_index=None):
    """Prueba posiciones al azar (y sesgada hacia abajo) para un gen activo.

    Devuelve (gen ubicado, (polígono movido, bounds)) o None. Las mitades tienen x fija.
    """
    polygon = gene["polygon"]
    limites_x = _limites_x(gene, x_bounds, doblez)
    min_x, max_x, min_y, max_y = get_margin_position_bounds(polygon, limites_x, y_bounds)
    if min_x > max_x + _EPS or min_y > max_y + _EPS:
        return None
    for _ in range(attempts):
        if gene["modo"] == MITAD:
            x = _x_mitad(polygon, gene["rotacion"], doblez)
        else:
            x = random.uniform(min_x, max_x)
        t = random.random()
        y = min_y + (t * t) * (max_y - min_y)
        candidate = {**gene, "x": x, "y": y}
        valido = _valido_perezoso(candidate, placed_data, limites_x, y_bounds, excluded_index)
        if valido is not None:
            return candidate, (valido[0] or _move_margin_entry(candidate)[0], valido[1])
    return None


def create_margin_population(size, margin_polygons, x_bounds, y_bounds, max_individual_attempts=500,
                             max_placement_attempts=200, doblez=None, cancelado=None):
    """Create a population of valid individuals with non-overlapping in-bounds margin polygons."""
    doblez = doblez or x_bounds
    plantillas = _preparar_entradas(margin_polygons)
    population = []
    fallas = {}
    for _ in range(size):
        _revisar_cancelacion(cancelado)
        for _ in range(max_individual_attempts):
            individual = []
            placed_data = []
            valid = True
            modo_ranura = {}
            for plantilla in plantillas:
                ranura = plantilla["ranura"]
                if ranura is not None and plantilla["rol"] == 1 and modo_ranura[ranura] == ENTERA:
                    individual.append(_nuevo_gen(plantilla, ENTERA, 0, activo=False))
                    placed_data.append(None)
                    continue

                if ranura is None:
                    modos = [plantilla["modo_entrada"]]
                elif plantilla["rol"] == 0:
                    modos = random.sample([ENTERA, MITAD], 2)
                else:
                    modos = [MITAD]

                ubicado = None
                for modo in modos:
                    gene = _nuevo_gen(plantilla, modo, random.choice(ROTACIONES))
                    ubicado = _buscar_ubicacion(gene, placed_data, x_bounds, y_bounds, doblez,
                                                max_placement_attempts)
                    if ubicado is not None:
                        break
                if ubicado is None:
                    indice = plantilla["indice_entrada"]
                    fallas[indice] = fallas.get(indice, 0) + 1
                    valid = False
                    break
                gene, moved = ubicado
                if ranura is not None and plantilla["rol"] == 0:
                    modo_ranura[ranura] = gene["modo"]
                individual.append(gene)
                placed_data.append(moved)

            if valid and score_margin_positions(individual, x_bounds, y_bounds, doblez) > -30000000000:
                population.append(individual)
                break
        else:
            raise SinUbicacionValida(
                f"Unable to generate a valid starting individual after {max_individual_attempts} attempts",
                sorted(fallas, key=lambda indice: -fallas[indice]),
            )
    return population


def create_random_immigrants(count, margin_polygons, x_bounds, y_bounds, doblez=None):
    """Create replacement individuals to restore diversity during late search."""
    return create_margin_population(
        count,
        margin_polygons,
        x_bounds,
        y_bounds,
        max_individual_attempts=100,
        max_placement_attempts=100,
        doblez=doblez,
    )


def crossover_margin_individuals(parent1, parent2, alpha=None, doblez=None):
    """Blend the x/y of two margin individuals to create two children.

    Cada hijo conserva modo, rotación y genes activos de su padre principal; en las mitades
    la x se recalcula para que el eje siga sobre el doblez.
    """
    if alpha is None:
        alpha = random.random()

    child1 = []
    child2 = []
    for gene1, gene2 in zip(parent1, parent2):
        x1 = alpha * gene1["x"] + (1 - alpha) * gene2["x"]
        y1 = alpha * gene1["y"] + (1 - alpha) * gene2["y"]
        x2 = alpha * gene2["x"] + (1 - alpha) * gene1["x"]
        y2 = alpha * gene2["y"] + (1 - alpha) * gene1["y"]
        child1.append(_hijo(gene1, x1, y1, doblez))
        child2.append(_hijo(gene2, x2, y2, doblez))

    return child1, child2


def _hijo(gene, x, y, doblez):
    child = gene.copy()
    if not gene.get("activo", True):
        return child
    if gene.get("modo") == MITAD and doblez is not None:
        x = _x_mitad(gene["polygon"], gene["rotacion"], doblez)
    child["x"] = x
    child["y"] = y
    return child


def _alternar_modo_ranura(individual, moved_individual, i0, i1, x_bounds, y_bounds, doblez, attempts=20):
    """Pasa una ranura de 1 entera a 2 mitades o al revés, si encuentra lugar válido."""
    gene0, gene1 = individual[i0], individual[i1]
    excluidos = {i0, i1}
    placed = [None if i in excluidos else moved for i, moved in enumerate(moved_individual)]
    if gene0["modo"] == ENTERA:
        nuevos = []
        for gene in (gene0, gene1):
            ubicado = _buscar_ubicacion(_nuevo_gen(gene, MITAD, random.choice(ROTACIONES)),
                                        placed, x_bounds, y_bounds, doblez, attempts)
            if ubicado is None:
                return
            nuevos.append(ubicado)
            placed = placed + [ubicado[1]]
        (nuevo0, moved0), (nuevo1, moved1) = nuevos
    else:
        ubicado = _buscar_ubicacion(_nuevo_gen(gene0, ENTERA, random.choice(ROTACIONES)),
                                    placed, x_bounds, y_bounds, doblez, attempts)
        if ubicado is None:
            return
        nuevo0, moved0 = ubicado
        nuevo1, moved1 = _nuevo_gen(gene1, ENTERA, 0, activo=False), None
    individual[i0], individual[i1] = nuevo0, nuevo1
    moved_individual[i0], moved_individual[i1] = moved0, moved1


def mutate_margin_individual(individual, mutation_rate_x, mutation_rate_y,
                             x_bounds=(0, 900), y_bounds=(0, 900),
                             mutation_scale_x=50, mutation_scale_y_pos=50, mutation_scale_y_neg=50,
                             mutation_rate_flip=0.1, doblez=None, mutation_rate_modo=0.0):
    """Randomly perturb x and y and occasionally rotate a polygon 180°.

    Each coordinate has its own mutation probability and scale.
    The y-direction supports separate positive and negative scales,
    and the negative scale is amplified for higher current y values.
    `mutation_rate_flip` (nombre de v1.0.0) es la probabilidad de rotar la pieza 180° sobre
    su centroide; el hilo sigue en el eje vertical. Ninguna pieza se espeja. Las mitades solo
    mutan en y, y al rotar pasan al otro doblez. `mutation_rate_modo` es la probabilidad de
    alternar una ranura entre 1 entera y 2 mitades. Todo cambio se valida contra el resto.
    """
    doblez = doblez or x_bounds
    mutated = []
    y_max = max(y_bounds[1], 1.0)
    moved_individual = [
        _move_margin_entry(gene) if gene.get("activo", True) else None for gene in individual
    ]
    for index, gene in enumerate(individual):
        if not gene.get("activo", True):
            mutated.append(gene.copy())
            continue
        new_x = gene["x"]
        new_y = gene["y"]
        polygon = gene.get("polygon")
        rotacion = gene.get("rotacion", 0)
        es_mitad = gene.get("modo") == MITAD
        limites_x = _limites_x(gene, x_bounds, doblez)
        min_x, max_x, min_y, _ = get_margin_position_bounds(
            polygon, limites_x, y_bounds
        )

        if not es_mitad and random.random() < mutation_rate_x:
            proposed_x = new_x + random.uniform(-mutation_scale_x, mutation_scale_x)
            proposed_gene = {
                "polygon": polygon,
                "x": proposed_x,
                "y": new_y,
            }
            if _valido_perezoso(proposed_gene, moved_individual, limites_x, y_bounds,
                                excluded_index=index) is not None:
                new_x = proposed_x
        if random.random() < mutation_rate_y:
            downward_space = max(new_y - min_y, 0.0)
            if downward_space > 0:
                movement = random.uniform(
                    0.0,
                    min(mutation_scale_y_neg * (gene["y"] / y_max), downward_space),
                )
                proposed_y = new_y - movement
                proposed_gene = {
                    "polygon": polygon,
                    "x": new_x,
                    "y": proposed_y,
                }
                if _valido_perezoso(proposed_gene, moved_individual, limites_x, y_bounds,
                                    excluded_index=index) is not None:
                    new_y = proposed_y
            elif not es_mitad:
                left_space = max(new_x - min_x, 0.0)
                right_space = max(max_x - new_x, 0.0)
                available_directions = [
                    direction
                    for direction, space in ((-1, left_space), (1, right_space))
                    if space > 0
                ]
                if available_directions:
                    direction = random.choice(available_directions)
                    space = left_space if direction < 0 else right_space
                    proposed_x = new_x + direction * random.uniform(
                        0.0, min(mutation_scale_x, space)
                    )
                    proposed_gene = {
                        "polygon": polygon,
                        "x": proposed_x,
                        "y": new_y,
                    }
                    if _valido_perezoso(proposed_gene, moved_individual, limites_x, y_bounds,
                                        excluded_index=index) is not None:
                        new_x = proposed_x
        if polygon is not None and random.random() < mutation_rate_flip:
            proposed_rot = 180 - rotacion
            proposed_polygon = gene["formas"][(gene["modo"], proposed_rot)]
            proposed_x = _x_mitad(proposed_polygon, proposed_rot, doblez) if es_mitad else new_x
            if _valido_perezoso({"polygon": proposed_polygon, "x": proposed_x, "y": new_y},
                                moved_individual, limites_x, y_bounds, excluded_index=index) is not None:
                polygon, rotacion, new_x = proposed_polygon, proposed_rot, proposed_x

        nuevo = gene.copy()
        nuevo.update({"polygon": polygon, "x": new_x, "y": new_y, "rotacion": rotacion})
        mutated.append(nuevo)
        # Los genes siguientes se validan contra la posición ya mutada de este.
        moved_individual[index] = _move_margin_entry(nuevo)

    if mutation_rate_modo > 0:
        for i0 in range(len(mutated) - 1):
            gene = mutated[i0]
            if (gene.get("ranura") is not None and gene["rol"] == 0
                    and random.random() < mutation_rate_modo):
                _alternar_modo_ranura(mutated, moved_individual, i0, i0 + 1,
                                      x_bounds, y_bounds, doblez)
    return mutated


def score_margin_positions(margin_params, x_bounds, y_bounds, doblez=None):
    """Translate copies of margin polygons to x/y centers and score placement.

    Scoring:
    - Penalizes intersections and out-of-bounds with -30000000000
    - Rewards valid placements closer to y=0, lower height, and more compact clusters
    Las mitades se validan contra `doblez` en x (su eje va sobre el doblez); los genes
    inactivos de una ranura no cuentan.
    """
    doblez = doblez or x_bounds
    margin_params = _activos(margin_params)
    moved_polygons = []
    centers = []
    moved_bounds = []
    for entry in margin_params:
        moved, bounds = _move_margin_entry(entry)
        moved_polygons.append(moved)
        moved_bounds.append(bounds)
        centers.append((entry["x"], entry["y"]))

    for entry, (minx, miny, maxx, maxy) in zip(margin_params, moved_bounds):
        limites_x = _limites_x(entry, x_bounds, doblez)
        if (minx < limites_x[0] - _EPS or miny < y_bounds[0] - _EPS
                or maxx > limites_x[1] + _EPS or maxy > y_bounds[1] + _EPS):
            return -30000000000

    # Check overlaps, touches, and nearest gaps in one pairwise pass.
    nearest_gaps = [float("inf")] * len(moved_polygons)
    centroid_distance_total = 0.0
    pair_count = 0
    touch_pair_count = 0
    for i, poly_a in enumerate(moved_polygons):
        for j in range(i + 1, len(moved_polygons)):
            poly_b = moved_polygons[j]
            bounds_a = moved_bounds[i]
            bounds_b = moved_bounds[j]
            boxes_are_separate = (
                bounds_a[2] < bounds_b[0]
                or bounds_b[2] < bounds_a[0]
                or bounds_a[3] < bounds_b[1]
                or bounds_b[3] < bounds_a[1]
            )
            se_tocan = False
            if not boxes_are_separate:
                # Igual que v1.0.0, sin calcular la intersección cuando no se tocan.
                se_tocan = poly_a.intersects(poly_b)
                if se_tocan:
                    if poly_a.intersection(poly_b).area > 1e-8:
                        return -30000000000
                    touch_pair_count += 1

            bbox_dx = max(bounds_b[0] - bounds_a[2], bounds_a[0] - bounds_b[2], 0.0)
            bbox_dy = max(bounds_b[1] - bounds_a[3], bounds_a[1] - bounds_b[3], 0.0)
            bbox_gap = hypot(bbox_dx, bbox_dy)
            if bbox_gap < nearest_gaps[i] or bbox_gap < nearest_gaps[j]:
                gap = 0.0 if se_tocan else poly_a.distance(poly_b)
                nearest_gaps[i] = min(nearest_gaps[i], gap)
                nearest_gaps[j] = min(nearest_gaps[j], gap)

            dx = centers[i][0] - centers[j][0]
            dy = centers[i][1] - centers[j][1]
            centroid_distance_total += (dx * dx + dy * dy) ** 0.5
            pair_count += 1

    # Calculate metrics for placements already known to be in bounds.
    max_y = float("-inf")
    touch_y0_count = 0
    x_touch_count = 0
    min_y_allowed = y_bounds[0]
    individual_y0_distances = []
    for entry, (minx, miny, maxx, maxy) in zip(margin_params, moved_bounds):
        min_x_allowed, max_x_allowed = _limites_x(entry, x_bounds, doblez)
        if abs(miny - min_y_allowed) < 1e-8:
            touch_y0_count += 1
        if entry.get("simetrico", False) and (
            abs(minx - min_x_allowed) < 1e-6 or abs(maxx - max_x_allowed) < 1e-6
        ):
            x_touch_count += 1
        max_y = max(max_y, maxy)
        individual_y0_distances.append(miny - min_y_allowed)

    nearest_gap_total = sum(gap for gap in nearest_gaps if gap != float("inf"))
    avg_dist = centroid_distance_total / max(pair_count, 1)
    compactness_weight = 0.2
    nearest_gap_weight = 15.0
    individual_y0_weight = 100.0
    max_height_weight = 300.0
    y0_touch_bonus = 100.0
    pair_touch_bonus = 10.0
    x_touch_bonus = 200.0

    # Valid placement: reward each polygon for being close to y=0, compact clustering, and low overall height
    score = (
        -sum(individual_y0_distances) * individual_y0_weight
        - max_y * max_height_weight
        - compactness_weight * avg_dist
        - nearest_gap_weight * nearest_gap_total
    )
    score += y0_touch_bonus * touch_y0_count
    score += x_touch_bonus * x_touch_count
    score += pair_touch_bonus * touch_pair_count
    return score


def _copy_individual(individual):
    """Copy placement records while reusing immutable polygon geometries."""
    return [gene.copy() for gene in individual]


def selection(population, fitnesses, tournament_size):
    """Select parents via tournament selection and return deep copies of winners.

    This works for both simple tuples and nested individuals such as lists of
    margin parameter dictionaries.
    """
    selected = []
    candidates = list(zip(population, fitnesses))
    for _ in range(len(population)):
        tournament = random.sample(candidates, tournament_size)
        winner = max(tournament, key=lambda x: x[1])[0]
        selected.append(_copy_individual(winner))
    return selected


def compute_diversity_metrics(population, fitnesses, sample_limit=200):
    """Compute simple diversity metrics for the population.

    Returns a dict with:
    - unique_fitness_count
    - unique_individuals_count (by rounded x/y tuples)
    - avg_pairwise_distance (Euclidean on flattened x/y vectors)
    """
    import numpy as np

    metrics = {}
    metrics['unique_fitness_count'] = len(set(fitnesses))

    # Unique individuals by rounded coordinates
    def indy_key(ind):
        return tuple((round(g['x'], 3), round(g['y'], 3)) for g in ind)

    keys = [indy_key(ind) for ind in population]
    metrics['unique_individuals_count'] = len(set(keys))

    # Average pairwise distance (sample if too large)
    try:
        N = len(population)
        if N <= 1:
            metrics['avg_pairwise_distance'] = 0.0
            return metrics

        sample_idx = list(range(N))
        if N > sample_limit:
            sample_idx = random.sample(sample_idx, sample_limit)

        arrs = []
        for i in sample_idx:
            ind = population[i]
            flat = []
            for g in ind:
                flat.append(g['x'])
                flat.append(g['y'])
            arrs.append(flat)
        arr = np.array(arrs, dtype=float)

        if arr.shape[0] <= 1:
            metrics['avg_pairwise_distance'] = 0.0
            return metrics

        # pairwise distances
        diffs = arr[:, None, :] - arr[None, :, :]
        dists = np.sqrt((diffs ** 2).sum(axis=-1))
        # take upper triangle
        iu = np.triu_indices(dists.shape[0], k=1)
        avg = float(dists[iu].mean())
        metrics['avg_pairwise_distance'] = avg
    except Exception:
        metrics['avg_pairwise_distance'] = 0.0

    return metrics


def genetic_algorithm_margin_placement(margin_polygons,
                                       x_bounds, y_bounds,
                                       population_size, generations, 
                                       mutation_rate_x, mutation_rate_y,
                                       mutation_scale_x, mutation_scale_y_pos, mutation_scale_y_neg,
                                       tournament_size, crossover_prob,
                                       mutation_rate_flip=0.1,
                                       immigrant_rate=0.1,
                                       diversity_threshold=0.25,
                                       stagnation_limit=40,
                                       restart_rate=0.3,
                                       doblez=None,
                                       mutation_rate_modo=0.1,
                                       on_generation=None,
                                       cancelado=None,
                                       verbose=True):
    """Run a genetic algorithm to optimize margin polygon placement.

    All parameters must be provided by the caller.
    Returns a tuple of (best_individual, best_fitness, snapshots).

    Agregados respecto de v1.0.0 (todos opcionales):
    - doblez: (x_izquierdo, x_derecho) donde va el eje de las mitades; por defecto x_bounds.
    - mutation_rate_modo: probabilidad de alternar una ranura entre 1 entera y 2 mitades.
    - on_generation(gen, total, mejor_fitness, mejor_largo): se llama en cada generación.
    - cancelado: threading.Event; si está activo al inicio de una generación (o mientras se
      arma la población inicial) se lanza CorteCancelado.
    - verbose: si es False no imprime la línea por generación.
    Los genes de best_individual llevan modo, rotacion, activo e indice_entrada.
    """
    doblez = doblez or x_bounds
    plantillas = _preparar_entradas(margin_polygons)
    population = create_margin_population(population_size, plantillas, x_bounds, y_bounds,
                                          doblez=doblez, cancelado=cancelado)

    best_individual = None
    best_fitness = float('-inf')
    stagnation_generations = 0

    snapshots = []
    for generation in range(generations):
        _revisar_cancelacion(cancelado)
        fitnesses = [score_margin_positions(ind, x_bounds, y_bounds, doblez) for ind in population]

        unique_fitness_count = len(set(fitnesses))
        gen_best_idx = max(range(len(fitnesses)), key=lambda i: fitnesses[i])
        if fitnesses[gen_best_idx] > best_fitness:
            best_fitness = fitnesses[gen_best_idx]
            best_individual = _copy_individual(population[gen_best_idx])
            stagnation_generations = 0
        else:
            stagnation_generations += 1

        if verbose:
            print(f"Generation {generation}: best_fitness = {best_fitness} | "
                  f"uniq_fit={unique_fitness_count} "
                  f"maxy={_individual_max_y(best_individual):.2f} "
                  f"avg_dist=skipped")
        if on_generation is not None:
            on_generation(generation, generations, best_fitness, _individual_max_y(best_individual))

        if best_individual is not None and (
            generation == 0
            or (generation & (generation - 1)) == 0
            or generation == generations - 1
        ):
            snapshots.append((generation, _copy_individual(best_individual)))
        
        parents = selection(population, fitnesses, tournament_size)
        # Shuffle parents to avoid deterministic pairing of identical parents
        random.shuffle(parents)

        offspring = []
        for i in range(0, len(parents), 2):
            if random.random() < crossover_prob and i + 1 < len(parents):
                child1, child2 = crossover_margin_individuals(parents[i], parents[i+1], doblez=doblez)
                offspring.extend([child1, child2])
            else:
                offspring.append(_copy_individual(parents[i]))
                if i + 1 < len(parents):
                    offspring.append(_copy_individual(parents[i+1]))
        
        # Elitism: keep the top 3 individuals unmutated (or fewer if population is small)
        elite_count = min(3, population_size)
        # find top elite_count indices by fitness
        sorted_idx = sorted(range(len(fitnesses)), key=lambda i: fitnesses[i], reverse=True)
        elite_idx = sorted_idx[:elite_count]
        elites = [_copy_individual(population[i]) for i in elite_idx]

        num_to_mutate = population_size - len(elites)
        restart = stagnation_generations >= stagnation_limit
        if restart:
            stagnation_generations = 0
        mutation_scale_multiplier = 2.0 if restart else 1.0
        mutated_offspring = [
            mutate_margin_individual(
                ind, mutation_rate_x, mutation_rate_y,
                x_bounds, y_bounds,
                mutation_scale_x * mutation_scale_multiplier,
                mutation_scale_y_pos * mutation_scale_multiplier,
                mutation_scale_y_neg * mutation_scale_multiplier,
                mutation_rate_flip=mutation_rate_flip,
                doblez=doblez,
                mutation_rate_modo=mutation_rate_modo,
            )
            for ind in offspring[:num_to_mutate]
        ]

        population = elites + mutated_offspring
    
    return best_individual, best_fitness, snapshots



if __name__ == "__main__":
    margin_polygons = load_margin_polygons("patrones.json")
    
    # Create rectangle
    rectangle = create_margin_rectangle("patrones.json", width=900)
    
    # Extract bounds
    maxy = rectangle.bounds[3]
    width = 800
    y_bounds = (0, maxy)
    x_bounds = (0, width)
    
    # GA parameters
    population_size = 100
    generations = 200
    # Independent mutation rates and scales for x and y
    mutation_rate_x = 0.3
    mutation_rate_y = 0.99
    mutation_scale_x = 200
    mutation_scale_y_pos = 1
    mutation_scale_y_neg = 500
    mutation_rate_flip = 0.15
    tournament_size = 10
    crossover_prob = 0.9
    
    # Run GA
    best_individual, best_fitness, snapshots = genetic_algorithm_margin_placement(
        margin_polygons, 
        x_bounds=x_bounds,
        y_bounds=y_bounds,
        population_size=population_size,
        generations=generations,
        mutation_rate_x=mutation_rate_x,
        mutation_rate_y=mutation_rate_y,
        mutation_scale_x=mutation_scale_x,
        mutation_scale_y_pos=mutation_scale_y_pos,
        mutation_scale_y_neg=mutation_scale_y_neg,
        tournament_size=tournament_size,
        crossover_prob=crossover_prob,
        mutation_rate_flip=mutation_rate_flip
    )
    
    print(f"\nBest fitness: {best_fitness}")
    print(f"Best individual: {best_individual[:3]}")  # Show first 3 margin positions
    
    # Save best individual to file
    import json
    best_individual_data = []
    for entry in best_individual:
        best_individual_data.append({
            "name": entry.get("name"),
            "x": float(entry.get("x")),
            "y": float(entry.get("y")),
            "simetrico": entry.get("simetrico", False),
            "rotacion": entry.get("rotacion", 0),
        })
    
    with open("best_individual.json", "w", encoding="utf-8") as f:
        json.dump({
            "fitness": best_fitness,
            "max_y": float(_individual_max_y(best_individual)),
            "placements": best_individual_data,
        }, f, indent=2, ensure_ascii=False)
    
    print(f"Best individual saved to best_individual.json")
    
    # Plot the best individual and saved snapshots
    plot_margin_individual(best_individual, width=width, title="Final best individual")
    plot_margin_snapshots(snapshots, width=width)
    