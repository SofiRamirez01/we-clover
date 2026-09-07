from math import hypot
from functools import lru_cache
import matplotlib.pyplot as plt
import shapely.plotting
import numpy as np
import random
from shapely.affinity import translate, scale
from shapely.geometry import Polygon
import polyorder


@lru_cache(maxsize=4096)
def _polygon_properties(polygon):
    """Cache immutable geometry properties reused throughout the search."""
    minx, miny, maxx, maxy = polygon.bounds
    center = polygon.centroid
    return minx, miny, maxx, maxy, center.x, center.y


def load_margin_polygons(patterns_file="patrones.json"):
    """Load margin geometries saved in the patterns file."""
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
    # Translate polygons to their target positions
    moved_polygons = []
    for entry in individual:
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

    snapshot_polygons = []
    all_bounds = []
    for generation, individual in snapshots:
        moved_polygons = []
        for entry in individual:
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
    return translate(poly, xoff=x_offset, yoff=y_offset), bounds


def _placement_is_valid(candidate, placed, x_bounds, y_bounds):
    """Check a candidate without recalculating the complete individual score."""
    candidate_poly, candidate_bounds = _move_margin_entry(candidate)
    placed_data = [_move_margin_entry(entry) for entry in placed]
    return _placement_is_valid_moved(
        candidate_poly, candidate_bounds, placed_data, x_bounds, y_bounds
    )


def _placement_is_valid_moved(candidate_poly, candidate_bounds, placed_data,
                              x_bounds, y_bounds, excluded_index=None):
    """Check a moved candidate against already translated polygons."""
    min_x, min_y, max_x, max_y = candidate_bounds
    if min_x < x_bounds[0] or min_y < y_bounds[0] or max_x > x_bounds[1] or max_y > y_bounds[1]:
        return False

    for index, (placed_poly, placed_bounds) in enumerate(placed_data):
        if index == excluded_index:
            continue
        if (
            candidate_bounds[2] < placed_bounds[0]
            or placed_bounds[2] < candidate_bounds[0]
            or candidate_bounds[3] < placed_bounds[1]
            or placed_bounds[3] < candidate_bounds[1]
        ):
            continue
        if candidate_poly.intersection(placed_poly).area > 1e-8:
            return False
    return True


def _individual_max_y(individual):
    """Return the highest translated boundary of an individual."""
    max_y = float("-inf")
    for entry in individual:
        properties = _polygon_properties(entry["polygon"])
        max_y = max(max_y, properties[3] + entry["y"] - properties[5])
    return max_y


def create_margin_population(size, margin_polygons, x_bounds, y_bounds, max_individual_attempts=500, max_placement_attempts=200):
    """Create a population of valid individuals with non-overlapping in-bounds margin polygons."""
    population = []
    for _ in range(size):
        for _ in range(max_individual_attempts):
            individual = []
            placed_data = []
            valid = True
            for entry in margin_polygons:
                polygon = entry.get("polygon")
                name = entry.get("name")
                min_x, max_x, min_y, max_y = get_margin_position_bounds(polygon, x_bounds, y_bounds)
                if min_x > max_x or min_y > max_y:
                    valid = False
                    break

                placement_found = False
                for _ in range(max_placement_attempts):
                    x = random.uniform(min_x, max_x)
                    t = random.random()
                    y = min_y + (t * t) * (max_y - min_y)
                    candidate = {
                        "name": name,
                        "polygon": polygon,
                        "x": x,
                        "y": y,
                        "flipped": False,
                    }
                    candidate_poly, candidate_bounds = _move_margin_entry(candidate)
                    if _placement_is_valid_moved(
                        candidate_poly, candidate_bounds, placed_data, x_bounds, y_bounds
                    ):
                        individual.append(candidate)
                        placed_data.append((candidate_poly, candidate_bounds))
                        placement_found = True
                        break

                if not placement_found:
                    valid = False
                    break

            if valid and score_margin_positions(individual, x_bounds, y_bounds) > -30000000000:
                population.append(individual)
                break
        else:
            raise RuntimeError(
                f"Unable to generate a valid starting individual after {max_individual_attempts} attempts"
            )
    return population


def create_random_immigrants(count, margin_polygons, x_bounds, y_bounds):
    """Create replacement individuals to restore diversity during late search."""
    return create_margin_population(
        count,
        margin_polygons,
        x_bounds,
        y_bounds,
        max_individual_attempts=100,
        max_placement_attempts=100,
    )


def crossover_margin_individuals(parent1, parent2, alpha=None):
    """Blend the x/y of two margin individuals to create two children."""
    if alpha is None:
        alpha = random.random()

    child1 = []
    child2 = []
    for gene1, gene2 in zip(parent1, parent2):
        x1 = alpha * gene1["x"] + (1 - alpha) * gene2["x"]
        y1 = alpha * gene1["y"] + (1 - alpha) * gene2["y"]
        x2 = alpha * gene2["x"] + (1 - alpha) * gene1["x"]
        y2 = alpha * gene2["y"] + (1 - alpha) * gene1["y"]

        polygon1 = gene1.get("polygon") or gene2.get("polygon")
        polygon2 = gene2.get("polygon") or gene1.get("polygon")
        child1.append({
            "name": gene1.get("name"),
            "polygon": polygon1,
            "x": x1,
            "y": y1,
            "flipped": gene1.get("flipped", False),
        })
        child2.append({
            "name": gene2.get("name"),
            "polygon": polygon2,
            "x": x2,
            "y": y2,
            "flipped": gene2.get("flipped", False),
        })

    return child1, child2


def mutate_margin_individual(individual, mutation_rate_x, mutation_rate_y,
                             x_bounds=(0, 900), y_bounds=(0, 900),
                             mutation_scale_x=50, mutation_scale_y_pos=50, mutation_scale_y_neg=50,
                             mutation_rate_flip=0.1):
    """Randomly perturb x and y and occasionally mirror a polygon.

    Each coordinate has its own mutation probability and scale.
    The y-direction supports separate positive and negative scales,
    and the negative scale is amplified for higher current y values.
    A small chance also flips the polygon around its centroid to explore
    mirrored orientations without changing the placement center.
    """
    mutated = []
    y_max = max(y_bounds[1], 1.0)
    moved_individual = [_move_margin_entry(gene) for gene in individual]
    for index, gene in enumerate(individual):
        new_x = gene["x"]
        new_y = gene["y"]
        polygon = gene.get("polygon")
        flipped = gene.get("flipped", False)
        min_x, max_x, min_y, _ = get_margin_position_bounds(
            polygon, x_bounds, y_bounds
        )

        if random.random() < mutation_rate_x:
            proposed_x = new_x + random.uniform(-mutation_scale_x, mutation_scale_x)
            proposed_gene = {
                "polygon": polygon,
                "x": proposed_x,
                "y": new_y,
            }
            proposed_poly, proposed_bounds = _move_margin_entry(proposed_gene)
            if _placement_is_valid_moved(
                proposed_poly, proposed_bounds, moved_individual, x_bounds, y_bounds,
                excluded_index=index,
            ):
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
                proposed_poly, proposed_bounds = _move_margin_entry(proposed_gene)
                if _placement_is_valid_moved(
                    proposed_poly, proposed_bounds, moved_individual, x_bounds, y_bounds,
                    excluded_index=index,
                ):
                    new_y = proposed_y
            else:
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
                    proposed_poly, proposed_bounds = _move_margin_entry(proposed_gene)
                    if _placement_is_valid_moved(
                        proposed_poly, proposed_bounds, moved_individual, x_bounds, y_bounds,
                        excluded_index=index,
                    ):
                        new_x = proposed_x
        if polygon is not None and random.random() < mutation_rate_flip:
            cx, cy = polygon.centroid.x, polygon.centroid.y
            axis = random.choice(["vertical", "horizontal"])
            if axis == "vertical":
                polygon = scale(polygon, xfact=-1, yfact=1, origin=(cx, cy))
            else:
                polygon = scale(polygon, xfact=1, yfact=-1, origin=(cx, cy))
            flipped = not flipped

        mutated.append({
            "name": gene.get("name"),
            "polygon": polygon,
            "x": new_x,
            "y": new_y,
            "flipped": flipped,
        })
    return mutated


def score_margin_positions(margin_params, x_bounds, y_bounds):
    """Translate copies of margin polygons to x/y centers and score placement.

    Scoring:
    - Penalizes intersections and out-of-bounds with -30000000000
    - Rewards valid placements closer to y=0, lower height, and more compact clusters
    """
    moved_polygons = []
    centers = []
    moved_bounds = []
    for entry in margin_params:
        moved, bounds = _move_margin_entry(entry)
        moved_polygons.append(moved)
        moved_bounds.append(bounds)
        centers.append((entry["x"], entry["y"]))

    for minx, miny, maxx, maxy in moved_bounds:
        if minx < x_bounds[0] or miny < y_bounds[0] or maxx > x_bounds[1] or maxy > y_bounds[1]:
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
            if not boxes_are_separate:
                intersection = poly_a.intersection(poly_b)
                if intersection.area > 1e-8:
                    return -30000000000
                if not intersection.is_empty:
                    touch_pair_count += 1

            bbox_dx = max(bounds_b[0] - bounds_a[2], bounds_a[0] - bounds_b[2], 0.0)
            bbox_dy = max(bounds_b[1] - bounds_a[3], bounds_a[1] - bounds_b[3], 0.0)
            bbox_gap = hypot(bbox_dx, bbox_dy)
            if bbox_gap < nearest_gaps[i] or bbox_gap < nearest_gaps[j]:
                gap = poly_a.distance(poly_b)
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
    min_x_allowed, min_y_allowed = x_bounds[0], y_bounds[0]
    max_x_allowed, max_y_allowed = x_bounds[1], y_bounds[1]
    individual_y0_distances = []
    for entry, (minx, miny, maxx, maxy) in zip(margin_params, moved_bounds):
        if abs(miny - min_y_allowed) < 1e-8:
            touch_y0_count += 1
        if entry.get("simetrico", False) and (
            abs(minx - min_x_allowed) < 1e-8 or abs(maxx - max_x_allowed) < 1e-8
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
                                       restart_rate=0.3):
    """Run a genetic algorithm to optimize margin polygon placement.
    
    All parameters must be provided by the caller.
    Returns a tuple of (best_individual, best_fitness, snapshots).
    """
    population = create_margin_population(population_size, margin_polygons, x_bounds, y_bounds)
    
    best_individual = None
    best_fitness = float('-inf')
    stagnation_generations = 0
    
    snapshots = []
    for generation in range(generations):
        fitnesses = [score_margin_positions(ind, x_bounds, y_bounds) for ind in population]

        unique_fitness_count = len(set(fitnesses))
        gen_best_idx = max(range(len(fitnesses)), key=lambda i: fitnesses[i])
        if fitnesses[gen_best_idx] > best_fitness:
            best_fitness = fitnesses[gen_best_idx]
            best_individual = _copy_individual(population[gen_best_idx])
            stagnation_generations = 0
        else:
            stagnation_generations += 1
        
        print(f"Generation {generation}: best_fitness = {best_fitness} | "
              f"uniq_fit={unique_fitness_count} "
              f"maxy={_individual_max_y(best_individual):.2f} "
              f"avg_dist=skipped")

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
                child1, child2 = crossover_margin_individuals(parents[i], parents[i+1])
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
            "flipped": entry.get("flipped", False),
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
    