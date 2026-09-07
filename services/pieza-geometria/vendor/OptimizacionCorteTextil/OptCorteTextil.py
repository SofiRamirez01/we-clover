"""Main workflow for creating, ordering, and optimizing textile patterns."""

import json
from pathlib import Path

from shapely.geometry import Polygon

import algorpatronsnap
import polymaker
import polyorder


POLYGONS_FILE = "polygons.json"
PATTERNS_FILE = "patrones.json"
CANVAS_WIDTH = 800
CANVAS_OFFSET = 5


def create_polygons(polygons_file=POLYGONS_FILE):
	"""Create polygons interactively and save them for the next workflow step."""
	polygons = polymaker.prompt_polygons()
	if not polygons:
		print("No se crearon poligonos.")
		return False

	polymaker.save_polygons_to_file(polygons, polygons_file)
	print(f"Guardados {len(polygons)} poligonos en {polygons_file}.")
	polymaker.plot_polygons(polygons)
	return True


def transform_polygons(polygons_file=POLYGONS_FILE, patterns_file=PATTERNS_FILE):
	"""Select polygons, create copies and margins, then save the patterns."""
	polygons = polyorder.load_polygons_from_file(polygons_file)
	if not polygons:
		print("No hay poligonos para transformar.")
		return False

	selected_indices = polyorder.prompt_polygon_selection(polygons)
	selected_polygons = [polygons[index] for index in selected_indices]
	copy_counts = polyorder.prompt_copy_counts(selected_polygons)
	sim_flags = polyorder.prompt_simetrico_flags(selected_polygons)

	base_polygons = polyorder.create_base_polygons(
		selected_polygons, copy_counts, sim_flags
	)
	margin_polygons = polyorder.create_margin_polygons(
		base_polygons, offset=CANVAS_OFFSET
	)
	patterns = polyorder.build_patrones(base_polygons, margin_polygons)
	polyorder.save_patrones(patterns, patterns_file)

	print(f"Guardados {len(patterns)} patrones en {patterns_file}.")
	polyorder.plot_polygons(base_polygons, margin_polygons)
	return True


def load_margin_polygons(patterns_file=PATTERNS_FILE):
	"""Load margin geometries saved by the ordering step."""
	patterns = polyorder.load_patrones_from_file(patterns_file)
	margin_polygons = []
	for pattern in patterns:
		margin = pattern["margen"]
		margin_polygons.append({
			"name": margin["name"],
			"polygon": Polygon(margin["coordinates"]),
			"simetrico": margin.get("simetrico", False),
		})
	return margin_polygons


def send_to_canvas(patterns_file=PATTERNS_FILE):
	"""Optimize saved margins and display the result on the canvas."""
	margin_polygons = load_margin_polygons(patterns_file)
	if not margin_polygons:
		print("No hay patrones para pasar al lienzo.")
		return False

	canvas_height = sum(entry["polygon"].length for entry in margin_polygons)
	y_bounds = (0, canvas_height)
	x_bounds = (0, CANVAS_WIDTH)

	best_individual, best_fitness, snapshots = (
		algorpatronsnap.genetic_algorithm_margin_placement(
			margin_polygons,
			x_bounds=x_bounds,
			y_bounds=y_bounds,
			population_size=100,
			generations=200,
			mutation_rate_x=0.3,
			mutation_rate_y=0.99,
			mutation_scale_x=200,
			mutation_scale_y_pos=1,
			mutation_scale_y_neg=500,
			tournament_size=10,
			crossover_prob=0.9,
			mutation_rate_flip=0.15,
		)
	)

	print(f"Mejor fitness: {best_fitness}")
	
	# Save best individual to JSON
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
			"max_y": float(algorpatronsnap._individual_max_y(best_individual)),
			"placements": best_individual_data,
		}, f, indent=2, ensure_ascii=False)
	
	print("Mejor individual guardado en best_individual.json")
	
	algorpatronsnap.plot_margin_individual(
		best_individual,
		width=CANVAS_WIDTH,
		title="Mejor distribucion en el lienzo",
	)
	algorpatronsnap.plot_margin_snapshots(snapshots, width=CANVAS_WIDTH)
	return True


def run_full_workflow():
	"""Run all three stages in sequence."""
	if create_polygons() and transform_polygons():
		send_to_canvas()


def main():
	"""Let the user choose which workflow stages to execute."""
	actions = {
		"1": ("Crear poligonos", create_polygons),
		"2": ("Transformar a patrones", transform_polygons),
		"3": ("Pasar al lienzo", send_to_canvas),
		"4": ("Ejecutar flujo completo", run_full_workflow),
	}

	while True:
		print("\nOptCorteTextil")
		for key, (label, _) in actions.items():
			print(f"{key}. {label}")
		print("0. Salir")
		option = input("Selecciona una opcion: ").strip()

		if option == "0":
			return
		action = actions.get(option)
		if action is None:
			print("Opcion no valida.")
			continue

		if option == "2" and not Path(POLYGONS_FILE).exists():
			print(f"No existe {POLYGONS_FILE}. Ejecuta primero 'Crear poligonos'.")
			continue
		if option == "3" and not Path(PATTERNS_FILE).exists():
			print(f"No existe {PATTERNS_FILE}. Ejecuta primero 'Transformar a patrones'.")
			continue
		action[1]()


if __name__ == "__main__":
	main()
