import json
import random
import matplotlib.pyplot as plt
import numpy as np
import shapely.geometry as geom
import shapely.plotting
from shapely.geometry import Polygon
from prettytable import PrettyTable


def save_polygons_to_file(polygons, filename):
    """Save a list of named polygons to a JSON file."""
    polygon_data = []
    for index, entry in enumerate(polygons, start=1):
        if isinstance(entry, Polygon):
            polygon = entry
            name = f"polygon_{index}"
            sim_flag = False
        elif isinstance(entry, dict):
            polygon = entry.get("polygon")
            name = entry.get("name", f"polygon_{index}")
            sim_flag = bool(entry.get("simetrico", False))
        else:
            raise ValueError("Polygons must be Shapely Polygon objects or dict entries with name and polygon.")

        if not isinstance(polygon, Polygon):
            raise ValueError("Only Shapely Polygon objects can be saved.")

        polygon_data.append({
            "name": name,
            "coordinates": list(polygon.exterior.coords),
            "simetrico": sim_flag,
        })

    with open(filename, "w", encoding="utf-8") as file:
        json.dump(polygon_data, file, indent=2)


def load_polygons_from_file(filename):
    """Load named polygons from a JSON file and return a list of dict entries."""
    with open(filename, "r", encoding="utf-8") as file:
        polygon_data = json.load(file)

    polygons = []
    for index, item in enumerate(polygon_data, start=1):
        if isinstance(item, list):
            polygons.append({
                "name": f"polygon_{index}",
                "polygon": Polygon(item),
                "simetrico": False,
            })
        elif isinstance(item, dict):
            name = item.get("name", f"polygon_{index}")
            coords = item.get("coordinates") or item.get("coords")
            sim_flag = bool(item.get("simetrico", False))
            polygons.append({
                "name": name,
                "polygon": Polygon(coords),
                "simetrico": sim_flag,
            })
        else:
            raise ValueError("Unexpected polygon format in JSON file.")

    return polygons


def prompt_polygon_from_user(default_name=None):
    """Prompt the user for a polygon name and vertices."""
    name = input(f"Nombre del polígono [{default_name or 'polygon'}]: ").strip()
    if not name:
        name = default_name or "polygon"

    print("Ingresar los vértices del polígono como x,y. Presionar Enter sin escribir nada para terminar.")
    vertices = []

    while True:
        line = input("Vértice (x,y): ").strip()
        if not line:
            break
        try:
            x_str, y_str = line.split(",")
            x = float(x_str.strip())
            y = float(y_str.strip())
            vertices.append((x, y))
        except ValueError:
            print("Formato inválido. Usar x,y (por ejemplo: 1,2).")

    if len(vertices) < 3:
        print("Un polígono necesita al menos 3 vértices. Intentar de nuevo.")
        return None

    sim_input = input("¿Simétrico? [s/N]: ").strip().lower()
    sim_flag = sim_input in ("s", "si")

    return {"name": name, "polygon": Polygon(vertices), "simetrico": sim_flag}


def prompt_polygons():
    """Prompt the user to enter one or more named polygons."""
    polygons = []
    while True:
        default_name = f"polygon_{len(polygons)+1}"
        polygon_entry = prompt_polygon_from_user(default_name=default_name)
        if polygon_entry is not None:
            polygons.append(polygon_entry)

        if not polygons:
            continue

        add_more = input("¿Agregar otro polígono? [s/N]: ").strip().lower()
        if add_more not in ("s", "si"):
            break

    return polygons


def plot_polygons(polygons):
    fig, ax = plt.subplots()
    for entry in polygons:
        polygon = entry["polygon"] if isinstance(entry, dict) else entry
        name = entry["name"] if isinstance(entry, dict) else None
        sim_flag = entry.get("simetrico") if isinstance(entry, dict) else False
        label = f"{name}{' (S)' if sim_flag else ''}" if name else None
        shapely.plotting.plot_polygon(polygon, ax=ax, color="blue", alpha=0.5)
        if label:
            centroid = polygon.centroid
            ax.text(centroid.x, centroid.y, label, fontsize=8, ha="center", va="center")
    ax.set_aspect("equal", adjustable="box")
    plt.show()


if __name__ == "__main__":
    polygons_file = "polygons.json"
    default_polygon = {"name": "default", "polygon": Polygon([
        (0, 5),
        (1, 1),
        (3, 0),
        (4, 1),
        (5, 5),
    ]), "simetrico": False}

    use_manual = input("¿Ingresar los polígonos manualmente? [s/N]: ").strip().lower() in ("s", "si")
    if use_manual:
        polygons = prompt_polygons()
        if not polygons:
            polygons = [default_polygon]
            print("No se ingresó ningún polígono válido; usando el polígono predeterminado.")
    else:
        polygons = [default_polygon]

    save_polygons_to_file(polygons, polygons_file)
    print(f"Saved {len(polygons)} polygon(s) to {polygons_file}")
    plot_polygons(polygons)
