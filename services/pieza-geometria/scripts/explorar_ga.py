"""Exploración (no es código de producción): corridas del algoritmo genético sobre cortes armados a mano.

Uso: python scripts/explorar_ga.py preparacion.json --grupo E5 [--semillas 1 101 ...] [--csv ...]

Cada corrida es `ubicar_corte` tal cual (reinicios con semilla, semilla + 1..., presupuesto del modo
COMPLETO, corte anticipado y compactación final). Los cortes se arman en este script a partir de la
preparación real (no se toca app/). Una fila por (experimento, variante, semilla) en el CSV, que se
va completando a medida que termina cada corrida (se puede cortar y retomar: saltea lo ya medido).

Grupos:
- E5:  corte 3 actual (Verde = 1) contra el mismo corte forzado a 3 capas (bolsillo mitad / entero), y
       corte 1 con y sin Bolsillo T2.
- E4:  eficiencia según figuras y capas: 6 buzos T4 Verde repartidos en 6, 3, 2 y 1 capas.
- E6:  excedente: cortes que el planificador arma con tope 0, 1, 2 y 3 (ver --cortes-json).
"""
import argparse
import csv
import json
import sys
import time
from pathlib import Path

RAIZ = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(RAIZ))

from app.corte.parametros import ParametrosCorte  # noqa: E402
from app.corte.piezas import preparar_piezas  # noqa: E402
from app.corte.planificador import Corte, ItemDemanda, ItemEnCorte, planificar  # noqa: E402
from app.corte.tizada import armar_entradas, ubicar_corte  # noqa: E402

RESULTADOS = RAIZ / "scripts" / "resultados"
COLUMNAS = ["experimento", "variante", "semilla_base", "capas", "entradas", "area_piezas_cm2", "largo_estimado_cm",
            "largo_sin_compactar_cm", "largo_final_cm", "tela_cm", "eficiencia", "estado", "reinicios",
            "semilla_ganadora", "segundos", "verificacion_ok", "detalle"]
SEMILLAS = [740188520, 1, 101, 201, 301, 401]

BOLSILLO, CAPUCHA, ESPALDA, FRENTE, MANGA = 13, 15, 5, 9, 14
VERDE = 4


def item_en_corte(geo, color, u, capas, modo=None, demanda=None):
    """ItemEnCorte armado a mano: u unidades por capa; modo None = como el planificador
    (ranuras + mitad suelta si es simétrica, enteras si no), "MITAD" o "ENTERA" para forzar."""
    if modo == "MITAD":
        ranuras, enteras, mitades = 0, 0, u
    elif modo == "ENTERA":
        if u % 2:
            raise ValueError("una entera rinde 2 unidades por capa")
        ranuras, enteras, mitades = 0, u // 2, 0
    elif geo.permite_mitad:
        ranuras, enteras, mitades = u // 2, 0, u % 2
    else:
        ranuras, enteras, mitades = 0, u // 2, 0
    produccion = {color: u * capas}
    demanda = {color: demanda if demanda is not None else u * capas}
    excedente = {color: produccion[color] - demanda[color]}
    item = ItemDemanda(geo, dict(demanda), {color: str(color)})
    return ItemEnCorte(item, u, ranuras, enteras, mitades, demanda, produccion, excedente, {color: 0})


def corte_a_mano(items, capas, p):
    area = sum(ic.area_por_capa_cm2 for ic in items)
    return Corte(1, "FRIZA", "Friza", {VERDE: capas}, items, area / (p.ancho_util_cm * p.eficiencia_para("FRIZA")))


def experimentos(grupo, preparacion, p, args):
    geos, _ = preparar_piezas(preparacion["piezas"], p)
    g = lambda pieza, talle: geos[(pieza, talle)]  # noqa: E731
    if grupo == "E5":
        plan = planificar(preparacion, p)
        corte1, corte3 = plan.cortes[0], plan.cortes[2]
        assert corte1.capas == {VERDE: 2} and corte3.capas == {VERDE: 1}, "el plan cambió: revisar los cortes"
        yield "corte3", "actual 1 capa", corte3
        base3 = [item_en_corte(g(ESPALDA, 1), VERDE, 1, 3, "MITAD"), item_en_corte(g(FRENTE, 1), VERDE, 1, 3, "MITAD"),
                 item_en_corte(g(CAPUCHA, 1), VERDE, 2, 3, "ENTERA")]
        yield "corte3", "3 capas, bolsillo mitad", corte_a_mano(
            base3 + [item_en_corte(g(BOLSILLO, 1), VERDE, 1, 3, "MITAD")], 3, p)
        yield "corte3", "3 capas, bolsillo entero", corte_a_mano(
            base3 + [item_en_corte(g(BOLSILLO, 1), VERDE, 2, 3, "ENTERA", demanda=3)], 3, p)
        yield "corte1", "actual (con Bolsillo T2)", corte1
        sin_t2 = [ic for ic in corte1.items if not (ic.item.geo.id_pieza == BOLSILLO and ic.item.geo.talle == "2")]
        assert len(sin_t2) == len(corte1.items) - 1
        yield "corte1", "sin Bolsillo T2", Corte(corte1.id_tipo_tela, corte1.codigo_tela, corte1.nombre_tela,
                                                 dict(corte1.capas), sin_t2, corte1.largo_estimado_cm)
    elif grupo == "E4":
        # 6 buzos T4 Verde (de la tela FRIZA: espalda, frente, bolsillo, 2 capuchas, 2 mangas) en L capas
        # con n = 6 / L buzos por capa: misma demanda, distinta cantidad de figuras (con 10 buzos en
        # una capa el largo estimado pasaba los 800 cm).
        for capas in (6, 3, 2, 1):
            n = 6 // capas
            items = [item_en_corte(g(ESPALDA, 4), VERDE, n, capas), item_en_corte(g(FRENTE, 4), VERDE, n, capas),
                     item_en_corte(g(BOLSILLO, 4), VERDE, n, capas), item_en_corte(g(CAPUCHA, 4), VERDE, 2 * n, capas),
                     item_en_corte(g(MANGA, 4), VERDE, 2 * n, capas)]
            yield "eficiencia_6_buzos_T4", f"{capas} capas x {n} buzo(s)", corte_a_mano(items, capas, p)
    elif grupo == "E6":
        # Planes que habilitaría aceptar piezas de más (el planificador actual, en modo TELA, nunca
        # los elige: cada pieza de más suma tela estimada). Los ítems se arman con las funciones del
        # planificador (unidades_por_capa con el tope dado), así respetan sus reglas.
        from dataclasses import replace

        from app.corte import planificador as pl

        plan = planificar(preparacion, p)
        corte1, corte3, natural, jersey_francia = plan.cortes[0], plan.cortes[2], plan.cortes[3], plan.cortes[5]
        assert natural.capas_totales == 1 and jersey_francia.codigo_tela == "JERSEY", "el plan cambió"

        def recapar(corte, capas, tope):
            pt = replace(p.para_tela(corte.codigo_tela), excedente_maximo_por_item=tope)
            color = next(iter(corte.capas))
            nuevos = []
            for ic in corte.items:
                u = pl.unidades_por_capa(ic.item, {color: capas}, pt)
                assert u is not None, f"{ic.item.geo.nombre} T{ic.item.geo.talle} no entra con tope {tope}"
                nuevos.append(pl._item_en_corte(ic.item, u, {color: capas}, ic.item.demanda, pt))
            return Corte(corte.id_tipo_tela, corte.codigo_tela, corte.nombre_tela, {color: capas}, nuevos,
                         pl._largo_estimado(nuevos, pt))

        # Tope 2: los ítems T1 del corte 3 entran en el corte 1 (2 capas) y el corte 3 desaparece.
        pt2 = replace(p.para_tela("FRIZA"), excedente_maximo_por_item=2)
        absorbidos = [pl._item_en_corte(ic.item, pl.unidades_por_capa(ic.item, dict(corte1.capas), pt2),
                                        dict(corte1.capas), ic.item.demanda, pt2) for ic in corte3.items]
        items = list(corte1.items) + absorbidos
        yield "excedente_corte1", "corte 1 + T1 (tope 2, sin corte 3)", Corte(
            corte1.id_tipo_tela, corte1.codigo_tela, corte1.nombre_tela, dict(corte1.capas), items,
            pl._largo_estimado(items, pt2))
        yield "excedente_natural", "Natural 1 capa (tope 0, actual)", natural
        yield "excedente_natural", "Natural 2 capas (tope 2)", recapar(natural, 2, 2)
        yield "excedente_jersey_francia", "Jersey Francia 1 capa (tope 0, actual)", jersey_francia
        yield "excedente_jersey_francia", "Jersey Francia 2 capas (tope 2)", recapar(jersey_francia, 2, 2)
    else:
        raise SystemExit(f"grupo desconocido: {grupo}")


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("preparacion")
    parser.add_argument("--grupo", required=True)
    parser.add_argument("--semillas", type=int, nargs="*", default=SEMILLAS)
    parser.add_argument("--csv", default=str(RESULTADOS / "explorar_ga.csv"))
    parser.add_argument("--cortes-json", help="definición de cortes para el grupo E6")
    parser.add_argument("--solo", nargs="*", help="variantes a correr (default: todas)")
    args = parser.parse_args()

    preparacion = json.loads(Path(args.preparacion).read_text(encoding="utf-8"))
    p0 = ParametrosCorte.desde_dict(preparacion["parametros"], modo_tiempo="COMPLETO")
    hechas = set()
    nuevo = not Path(args.csv).exists()
    if not nuevo:
        with open(args.csv, encoding="utf-8") as archivo:
            hechas = {(f["experimento"], f["variante"], int(f["semilla_base"])) for f in csv.DictReader(archivo)}
    lista = list(experimentos(args.grupo, preparacion, p0, args))
    print(f"{args.grupo}: {len(lista)} variantes × {len(args.semillas)} semillas, de a una corrida por vez", flush=True)
    with open(args.csv, "a", newline="", encoding="utf-8") as archivo:
        escritor = csv.DictWriter(archivo, fieldnames=COLUMNAS)
        if nuevo:
            escritor.writeheader()
        for semilla in args.semillas:
            for experimento, variante, corte in lista:
                if args.solo and variante not in args.solo:
                    continue
                if (experimento, variante, semilla) in hechas:
                    continue
                p = ParametrosCorte.desde_dict(preparacion["parametros"], modo_tiempo="COMPLETO", semilla=semilla)
                entradas, _ = armar_entradas(corte, p)
                inicio = time.perf_counter()
                tz = ubicar_corte(corte, p)
                area = sum(pieza.poligono.area for pieza in tz.piezas)
                detalle = "; ".join(
                    f"{ic.item.geo.nombre} T{ic.item.geo.talle} u={ic.unidades_por_capa}"
                    f" (r{ic.ranuras} e{ic.enteras} m{ic.mitades}) prod={sum(ic.produccion.values())}"
                    f" dem={sum(ic.demanda.values())}" for ic in corte.items) if len(corte.items) <= 8 else f"{len(corte.items)} ítems"
                fila = {"experimento": experimento, "variante": variante, "semilla_base": semilla,
                        "capas": corte.capas_totales, "entradas": len(entradas), "area_piezas_cm2": round(area, 1),
                        "largo_estimado_cm": round(corte.largo_estimado_cm, 1),
                        "largo_sin_compactar_cm": round(tz.largo_sin_compactar_cm, 2),
                        "largo_final_cm": round(tz.largo_usado_cm, 2),
                        "tela_cm": round(tz.largo_usado_cm * corte.capas_totales, 2), "eficiencia": round(tz.eficiencia, 4),
                        "estado": tz.estado, "reinicios": tz.reinicios, "semilla_ganadora": tz.semilla_ganadora,
                        "segundos": round(time.perf_counter() - inicio, 1), "verificacion_ok": tz.verificacion_ok,
                        "detalle": detalle}
                escritor.writerow(fila)
                archivo.flush()
                print(f"{experimento:22} | {variante:28} | semilla {semilla:>9} | {len(entradas):2} figuras ×"
                      f" {corte.capas_totales:2} capas | largo {tz.largo_usado_cm:7.2f} cm → tela {fila['tela_cm']:8.2f} cm"
                      f" | efic. {tz.eficiencia:5.1%} | {tz.estado:16} {tz.reinicios} reinicio(s) | {fila['segundos']:5.0f} s"
                      f" | verif {'ok' if tz.verificacion_ok else 'FALLÓ'}", flush=True)


if __name__ == "__main__":
    main()
