import copy
import matplotlib.pyplot as plt
import shapely.plotting
import numpy as np
import random
from shapely.affinity import translate
from shapely.geometry import Polygon
import polyorder


def create_margin_rectangle(polygons_file="polygons.json", width=900, offset=5):
    """Create and return a rectangle polygon with width 900 and length equal to the sum of margin polygon lengths."""
    _, margin_polygons = polyorder.load_active_polygon_sets(polygons_file, offset=offset)
    total_length = sum(entry["polygon"].length for entry in margin_polygons)
    return Polygon([(0, 0), (width, 0), (width, total_length), (0, total_length)])


def make_margin_parameters(margin_polygons):
    """Return a list of parameter dicts for each margin polygon.

    Each dict has keys: 'name', 'polygon', 'x', 'y' where x and y are set to 0.
    """
    params = []
    for entry in margin_polygons:
        params.append({
            "name": entry.get("name"),
            "polygon": entry.get("polygon"),
            "x": 0,
            "y": 0,
        })
    return params


def place_margin_polygons(margin_params, rectangle, spacing=0):
    """Translate each margin polygon into the given rectangle.

    Polygons are stacked vertically from the rectangle bottom and assigned x/y offsets.
    Returns the same list with updated 'polygon', 'x', and 'y' values.
    """
    minx, miny, maxx, maxy = rectangle.bounds
    current_y = miny

    for entry in margin_params:
        poly = entry["polygon"]
        poly_minx, poly_miny, poly_maxx, poly_maxy = poly.bounds
        width = poly_maxx - poly_minx
        height = poly_maxy - poly_miny

        target_x = minx - poly_minx
        target_y = current_y - poly_miny

        moved = translate(poly, xoff=target_x, yoff=target_y)
        entry["polygon"] = moved
        entry["x"] = target_x
        entry["y"] = target_y

        current_y += height + spacing

    return margin_params


def plot_rectangle(rectangle, color="blue", alpha=0.5):
    """Plot the given rectangle polygon."""
    fig, ax = plt.subplots()
    shapely.plotting.plot_polygon(rectangle, ax=ax, color=color, alpha=alpha)
    ax.set_aspect("equal", adjustable="box")
    plt.show()


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
    min_x = min(b[0] for b in all_bounds)
    min_y = min(b[1] for b in all_bounds)
    max_x = max(b[2] for b in all_bounds)
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
    plt.title(title or "Margin Polygon Placement")
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
    poly_minx, poly_miny, poly_maxx, poly_maxy = polygon.bounds
    center = polygon.centroid
    min_x = x_bounds[0] - (center.x - poly_minx)
    max_x = x_bounds[1] - (center.x - poly_maxx)
    min_y = y_bounds[0] - (center.y - poly_miny)
    max_y = y_bounds[1] - (center.y - poly_maxy)
    return min_x, max_x, min_y, max_y


def create_margin_population(size, margin_polygons, x_bounds, y_bounds, max_individual_attempts=500, max_placement_attempts=200):
    """Create a population of valid individuals with non-overlapping in-bounds margin polygons."""
    population = []
    width = x_bounds[1]
    for _ in range(size):
        for attempt in range(max_individual_attempts):
            individual = []
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
                    candidate = {"name": name, "polygon": polygon, "x": x, "y": y}
                    if score_margin_positions(individual + [candidate], x_bounds, y_bounds) > -30000000000:
                        individual.append(candidate)
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

        polygon = gene1.get("polygon") or gene2.get("polygon")
        child1.append({"name": gene1.get("name"), "polygon": polygon, "x": x1, "y": y1})
        child2.append({"name": gene1.get("name"), "polygon": polygon, "x": x2, "y": y2})

    return child1, child2


def mutate_margin_individual(individual, mutation_rate_x, mutation_rate_y,
                             x_bounds=(0, 900), y_bounds=(0, 900),
                             mutation_scale_x=50, mutation_scale_y_pos=50, mutation_scale_y_neg=50):
    """Randomly perturb x and y independently per margin polygon.

    Each coordinate has its own mutation probability and scale.
    The y-direction supports separate positive and negative scales,
    and the negative scale is amplified for higher current y values.
    """
    mutated = []
    y_max = max(y_bounds[1], 1.0)
    for gene in individual:
        new_x = gene["x"]
        new_y = gene["y"]
        if random.random() < mutation_rate_x:
            new_x += random.uniform(-mutation_scale_x, mutation_scale_x)
            new_x = max(min(new_x, x_bounds[1]), x_bounds[0])
        if random.random() < mutation_rate_y:
            neg_scale = mutation_scale_y_neg * (gene["y"] / (y_max))
            new_y += random.uniform(-neg_scale, mutation_scale_y_pos)
            new_y = max(min(new_y, y_bounds[1]), y_bounds[0])
        mutated.append({
            "name": gene.get("name"),
            "polygon": gene.get("polygon"),
            "x": new_x,
            "y": new_y,
        })
    return mutated


def compute_average_centroid_distance(polygons):
    """Compute the average pairwise distance between polygon centroids."""
    centers = [poly.centroid for poly in polygons]
    n = len(centers)
    if n <= 1:
        return 0.0

    total = 0.0
    count = 0
    for i in range(n):
        for j in range(i + 1, n):
            dx = centers[i].x - centers[j].x
            dy = centers[i].y - centers[j].y
            total += (dx * dx + dy * dy) ** 0.5
            count += 1
    return total / max(count, 1)


def score_margin_positions(margin_params, x_bounds=(0, 900), y_bounds=(0, 900)):
    """Translate copies of margin polygons to x/y centers and score placement.

    Scoring:
    - Penalizes intersections and out-of-bounds with -30000000000
    - Rewards valid placements closer to y=0, lower height, and more compact clusters
    """
    moved_polygons = []
    for entry in margin_params:
        poly = entry["polygon"]
        center = poly.centroid
        x_offset = entry["x"] - center.x
        y_offset = entry["y"] - center.y
        moved = translate(poly, xoff=x_offset, yoff=y_offset)
        moved_polygons.append(moved)

    # Check for illegal overlaps but allow boundary touches
    for i, poly_a in enumerate(moved_polygons):
        for poly_b in moved_polygons[i + 1 :]:
            if poly_a.intersects(poly_b) and poly_a.intersection(poly_b).area > 1e-8:
                return -30000000000

    # Check bounds and calculate metrics
    min_y = float("inf")
    max_y = float("-inf")
    touch_y0_count = 0
    touch_pair_count = 0
    min_x_allowed, min_y_allowed = x_bounds[0], y_bounds[0]
    max_x_allowed, max_y_allowed = x_bounds[1], y_bounds[1]
    individual_y0_distances = []
    for poly in moved_polygons:
        minx, miny, maxx, maxy = poly.bounds
        if minx < min_x_allowed or miny < min_y_allowed or maxx > max_x_allowed or maxy > max_y_allowed:
            return -30000000000
        if abs(miny - min_y_allowed) < 1e-8:
            touch_y0_count += 1
        min_y = min(min_y, miny)
        max_y = max(max_y, maxy)
        individual_y0_distances.append(miny - min_y_allowed)

    for i, poly_a in enumerate(moved_polygons):
        for poly_b in moved_polygons[i + 1 :]:
            if poly_a.touches(poly_b):
                touch_pair_count += 1

    avg_dist = compute_average_centroid_distance(moved_polygons)
    compactness_weight = 0.2
    individual_y0_weight = 50.0
    max_height_weight = 120.0
    y0_touch_bonus = 100.0
    pair_touch_bonus = 1.0

    # Valid placement: reward each polygon for being close to y=0, compact clustering, and low overall height
    score = -sum(individual_y0_distances) * individual_y0_weight - max_y * max_height_weight - compactness_weight * avg_dist
    score += y0_touch_bonus * touch_y0_count
    score += pair_touch_bonus * touch_pair_count
    return score


def selection(population, fitnesses, tournament_size):
    """Select parents via tournament selection and return deep copies of winners.

    This works for both simple tuples and nested individuals such as lists of
    margin parameter dictionaries.
    """
    selected = []
    for _ in range(len(population)):
        tournament = random.sample(list(zip(population, fitnesses)), tournament_size)
        winner = max(tournament, key=lambda x: x[1])[0]
        selected.append(copy.deepcopy(winner))
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


def genetic_algorithm_margin_placement(margin_polygons, rectangle, 
                                       x_bounds, y_bounds,
                                       population_size, generations, 
                                       mutation_rate_x, mutation_rate_y,
                                       mutation_scale_x, mutation_scale_y_pos, mutation_scale_y_neg,
                                       tournament_size, crossover_prob, width):
    """Run a genetic algorithm to optimize margin polygon placement.
    
    All parameters must be provided by the caller.
    Returns a tuple of (best_individual, best_fitness, snapshots).
    """
    population = create_margin_population(population_size, margin_polygons, x_bounds, y_bounds)
    
    best_individual = None
    best_fitness = float('-inf')
    
    snapshots = []
    for generation in range(generations):
        fitnesses = [score_margin_positions(ind, x_bounds, y_bounds) for ind in population]

        # Diversity metrics
        metrics = compute_diversity_metrics(population, fitnesses)
        gen_best_idx = max(range(len(fitnesses)), key=lambda i: fitnesses[i])
        if fitnesses[gen_best_idx] > best_fitness:
            best_fitness = fitnesses[gen_best_idx]
            best_individual = copy.deepcopy(population[gen_best_idx])
        
        print(f"Generation {generation}: best_fitness = {best_fitness} | "
              f"uniq_fit={metrics['unique_fitness_count']} "
              f"uniq_inds={metrics['unique_individuals_count']} "
              f"avg_dist={metrics['avg_pairwise_distance']:.2f}")

        if best_individual is not None and (
            generation == 0
            or (generation & (generation - 1)) == 0
            or generation == generations - 1
        ):
            snapshots.append((generation, copy.deepcopy(best_individual)))
        
        parents = selection(population, fitnesses, tournament_size)
        # Shuffle parents to avoid deterministic pairing of identical parents
        random.shuffle(parents)

        offspring = []
        for i in range(0, len(parents), 2):
            if random.random() < crossover_prob and i + 1 < len(parents):
                child1, child2 = crossover_margin_individuals(parents[i], parents[i+1])
                offspring.extend([child1, child2])
            else:
                offspring.append(copy.deepcopy(parents[i]))
                if i + 1 < len(parents):
                    offspring.append(copy.deepcopy(parents[i+1]))
        
        # Elitism: keep the top 3 individuals unmutated (or fewer if population is small)
        elite_count = min(3, population_size)
        # find top elite_count indices by fitness
        sorted_idx = sorted(range(len(fitnesses)), key=lambda i: fitnesses[i], reverse=True)
        elite_idx = sorted_idx[:elite_count]
        elites = [copy.deepcopy(population[i]) for i in elite_idx]

        # Mutate the rest of offspring to fill the population
        num_to_mutate = population_size - len(elites)
        mutated_offspring = [mutate_margin_individual(ind, mutation_rate_x, mutation_rate_y,
                     x_bounds, y_bounds,
                     mutation_scale_x, mutation_scale_y_pos, mutation_scale_y_neg)
                 for ind in offspring[:num_to_mutate]]

        population = elites + mutated_offspring
    
    return best_individual, best_fitness, snapshots



if __name__ == "__main__":
    # Let user enter and see polygons
    _, margin_polygons = polyorder.load_active_polygon_sets("polygons.json", offset=5)
    
    # Create rectangle
    rectangle = create_margin_rectangle("polygons.json", width=900, offset=5)
    
    # Extract bounds
    minx, miny, maxx, maxy = rectangle.bounds
    width = 900
    y_bounds = (0, maxy)
    x_bounds = (0, width)
    
    # GA parameters
    population_size = 100
    generations = 600
    # Independent mutation rates and scales for x and y
    mutation_rate_x = 0.5
    mutation_rate_y = 0.99
    mutation_scale_x = 200
    mutation_scale_y_pos = 1
    mutation_scale_y_neg = 500
    tournament_size = 20
    crossover_prob = 0.9
    
    # Run GA
    best_individual, best_fitness, snapshots = genetic_algorithm_margin_placement(
        margin_polygons, 
        rectangle,
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
        width=width
    )
    
    print(f"\nBest fitness: {best_fitness}")
    print(f"Best individual: {best_individual[:3]}")  # Show first 3 margin positions
    
    # Plot the best individual and saved snapshots
    plot_margin_individual(best_individual, width=width, title="Final best individual")
    plot_margin_snapshots(snapshots, width=width)
    