import json
import matplotlib.pyplot as plt
import shapely.geometry as geom
import shapely.plotting
from shapely.geometry import Polygon


def load_polygons_from_file(filename):
    with open(filename, "r", encoding="utf-8") as file:
        polygon_data = json.load(file)

    polygons = []
    for index, item in enumerate(polygon_data, start=1):
        if isinstance(item, dict):
            name = item.get("name", f"polygon_{index}")
            coords = item.get("coordinates") or item.get("coords")
            if coords is None:
                raise ValueError(f"Missing coordinates for polygon {name}")
            sim_flag = bool(item.get("simetrico", False))
            polygons.append({
                "name": name,
                "polygon": Polygon(coords),
                "simetrico": sim_flag,
            })
        elif isinstance(item, list):
            polygons.append({
                "name": f"polygon_{index}",
                "polygon": Polygon(item),
                "simetrico": False,
            })
        else:
            raise ValueError("Each polygon entry must be a dict or list in polygons.json.")

    return polygons


def prompt_polygon_selection(polygons):
    print("Polígonos disponibles en polygons.json:")
    for index, entry in enumerate(polygons, start=1):
        center = entry["polygon"].centroid
        print(f"{index}: {entry['name']} (centro={center.x:.2f},{center.y:.2f})")

    while True:
        selection = input("Ingresar los números de polígonos a agregar (separados por coma), o 'todo': ").strip().lower()
        if selection == "todo":
            return list(range(len(polygons)))

        try:
            indices = [int(token.strip()) - 1 for token in selection.split(",") if token.strip()]
            if not indices or any(i < 0 or i >= len(polygons) for i in indices):
                raise ValueError
            return sorted(set(indices))
        except ValueError:
            print("Selección inválida. Usar números como 1,2 o 'todo'.")


def prompt_copy_counts(selected_entries):
    copy_counts = []
    for entry in selected_entries:
        while True:
            count_str = input(f"¿Cuántas copias de '{entry['name']}'? (simétrico={entry.get('simetrico', False)}) ").strip()
            try:
                count = int(count_str)
                if count < 1:
                    raise ValueError
                copy_counts.append(count)
                break
            except ValueError:
                print("Ingresar un número entero positivo.")
    return copy_counts


def prompt_simetrico_flags(selected_entries):
    """Allow the user to confirm/change the simetrico flag per selected polygon."""
    sim_flags = []
    for entry in selected_entries:
        default = entry.get("simetrico", False)
        resp = input(f"¿Configurar '{entry['name']}' como simétrico? [s/N] (actual={default}): ").strip().lower()
        if resp in ("s", "si"):
            sim_flags.append(True)
        elif resp in ("n", "no"):
            sim_flags.append(False)
        else:
            sim_flags.append(default)
    return sim_flags


def create_base_polygons(selected_entries, copy_counts, sim_flags):
    base_entries = []
    for entry, copies, sim_flag in zip(selected_entries, copy_counts, sim_flags):
        for copy_index in range(1, copies + 1):
            name = entry["name"] if copies == 1 else f"{entry['name']}_copy{copy_index}"
            polygon = entry["polygon"]
            base_entries.append({
                "name": name,
                "polygon": polygon,
                "center": polygon.centroid,
                "simetrico": sim_flag,
            })
    return base_entries


def create_margin_polygons(base_entries, offset=5):
    margin_entries = []
    for entry in base_entries:
        margin_polygon = entry["polygon"].buffer(offset, join_style=2)
        if isinstance(margin_polygon, geom.MultiPolygon):
            margin_polygon = max(margin_polygon, key=lambda p: p.area)

        margin_entries.append({
            "name": f"margen_{entry['name']}",
            "polygon": margin_polygon,
            "center": margin_polygon.centroid,
            "simetrico": entry.get("simetrico", False),
        })
    return margin_entries


def build_patrones(base_entries, margin_entries):
    patrones = []
    for base, margin in zip(base_entries, margin_entries):
        patrones.append({
            "base": {
                "name": base["name"],
                "coordinates": [[float(x), float(y)] for x, y in base["polygon"].exterior.coords],
                "center": [float(base["center"].x), float(base["center"].y)],
                "simetrico": bool(base.get("simetrico", False)),
            },
            "margen": {
                "name": margin["name"],
                "coordinates": [[float(x), float(y)] for x, y in margin["polygon"].exterior.coords],
                "center": [float(margin["center"].x), float(margin["center"].y)],
                "simetrico": bool(margin.get("simetrico", False)),
            },
        })
    return patrones


def save_patrones(patrones, filename):
    with open(filename, "w", encoding="utf-8") as f:
        json.dump(patrones, f, indent=2)


def load_patrones_from_file(filename):
    with open(filename, "r", encoding="utf-8") as f:
        return json.load(f)


def load_active_polygon_sets(polygons_file="polygons.json", offset=5):
    polygons = load_polygons_from_file(polygons_file)
    if not polygons:
        return [], []

    copy_counts = [1] * len(polygons)
    sim_flags = [entry.get("simetrico", False) for entry in polygons]
    base_entries = create_base_polygons(polygons, copy_counts, sim_flags)
    margin_entries = create_margin_polygons(base_entries, offset=offset)
    return base_entries, margin_entries


def plot_polygons(base_entries, margin_entries):
    fig, ax = plt.subplots()

    for entry in margin_entries:
        sim_flag = entry.get("simetrico", False)
        label = f"{entry['name']}{' (S)' if sim_flag else ''}"
        shapely.plotting.plot_polygon(entry["polygon"], ax=ax, color="red", alpha=0.35)
        center = entry["center"]
        ax.text(center.x, center.y, label, fontsize=8, color="red", ha="center", va="center")

    for entry in base_entries:
        sim_flag = entry.get("simetrico", False)
        label = f"{entry['name']}{' (S)' if sim_flag else ''}"
        shapely.plotting.plot_polygon(entry["polygon"], ax=ax, color="blue", alpha=0.6)
        center = entry["center"]
        ax.text(center.x, center.y, label, fontsize=8, color="blue", ha="center", va="center")

    ax.set_aspect("equal", adjustable="box")
    plt.show()


if __name__ == "__main__":
    polygons_file = "polygons.json"
    polygons = load_polygons_from_file(polygons_file)

    if not polygons:
        raise SystemExit("No polygons found in polygons.json.")

    selected_indices = prompt_polygon_selection(polygons)
    selected_polygons = [polygons[i] for i in selected_indices]
    copy_counts = prompt_copy_counts(selected_polygons)
    sim_flags = prompt_simetrico_flags(selected_polygons)

    base_polygons = create_base_polygons(selected_polygons, copy_counts, sim_flags)
    margin_polygons = create_margin_polygons(base_polygons, offset=5)

    print("Selected base polygons:")
    for entry in base_polygons:
        print(f"- {entry['name']} center=({entry['center'].x:.2f},{entry['center'].y:.2f})")

    print("Generated margin polygons:")
    for entry in margin_polygons:
        print(f"- {entry['name']} center=({entry['center'].x:.2f},{entry['center'].y:.2f})")
    # Build patrones list pairing each base polygon with its margen polygon
    patrones = build_patrones(base_polygons, margin_polygons)

    patrones_file = "patrones.json"
    save_patrones(patrones, patrones_file)
    print(f"Saved {len(patrones)} patrones to {patrones_file}")

    plot_polygons(base_polygons, margin_polygons)
