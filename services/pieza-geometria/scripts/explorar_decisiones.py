"""Exploración (no es código de producción): cómo decide hoy el planificador los cortes de una tela.

Uso: python scripts/explorar_decisiones.py preparacion.json [--tela FRIZA] [--tope 0]

1. Todas las combinaciones que prueba `planificar` para la tela (tope de búsqueda × orden de
   selección × descomposición) con su evaluación (tela estimada, cortes, prendas partidas, capas) y
   cuál gana.
2. La cobertura voraz paso a paso para cada grupo de la combinación ganadora: en cada paso los
   planes de capas candidatos con su clave de selección y el elegido.
3. Contrafácticos: la misma tela con planes de capas alternativos para los ítems de un corte, con la
   evaluación del planificador (para ver qué desempate decide).
Usa las funciones de app/corte/planificador.py sin modificarlas.
"""
import argparse
import json
import sys
from dataclasses import replace
from pathlib import Path

RAIZ = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(RAIZ))

from app.corte import planificador as pl  # noqa: E402
from app.corte.parametros import ParametrosCorte  # noqa: E402
from app.corte.piezas import preparar_piezas  # noqa: E402


def _items(preparacion, grupo_tela, p):
    geos, _ = preparar_piezas(preparacion["piezas"], p)
    return [pl.ItemDemanda(geos[(it["idPieza"], it["idTalle"])],
                           {c["idPaletaColor"]: c["cantidad"] for c in it["porColor"] if c["cantidad"] > 0},
                           {c["idPaletaColor"]: c["nombreColor"] for c in it["porColor"]}, it.get("origen", []))
            for it in grupo_tela["items"]]


def _nombre(item):
    return f"{item.geo.nombre.split()[0]} T{item.geo.talle}"


def _describir(cortes, nombres_color):
    partes = []
    for capas, items in cortes:
        partes.append(f"[{','.join(f'{nombres_color[c]}={l}' for c, l in capas.items())}] "
                      + " ".join(f"{_nombre(ic.item)}×{ic.unidades_por_capa}" for ic in items))
    return partes


def trazar_voraz(items, p, orden, nombres_color, mostrar=6):
    """La misma cobertura que pl.resolver_grupo, imprimiendo cada paso."""
    colores = sorted(items[0].colores)
    candidatos = pl._candidatos(items, colores, p)
    restantes = list(items)
    paso = 0
    while restantes:
        paso += 1
        evaluados = []
        for capas in candidatos:
            factibles = [(item, u) for item in restantes if (u := pl.unidades_por_capa(item, capas, p)) is not None]
            if not factibles:
                continue
            cajas = pl.empacar(factibles, capas, p)
            por_corte = -len(factibles) / len(cajas)
            excedente = pl._area_excedente(cajas) / len(factibles)
            clave = ((por_corte, excedente) if (orden or p.modo_objetivo) == pl.COSTO else (excedente, por_corte)) \
                + (sum(capas.values()),)
            evaluados.append((clave, capas, cajas, factibles))
        if not evaluados:
            print(f"    paso {paso}: ningún plan de capas cubre a {[_nombre(i) for i in restantes]}")
            break
        evaluados.sort(key=lambda e: e[0])
        print(f"    paso {paso}: quedan {len(restantes)} ítems; {len(evaluados)} planes de capas factibles. Mejores:")
        for clave, capas, cajas, factibles in evaluados[:mostrar]:
            print(f"      {'→' if clave == evaluados[0][0] else ' '} capas {','.join(f'{nombres_color[c]}={l}' for c, l in capas.items()):10}"
                  f" cubre {len(factibles):2} ítems en {len(cajas)} corte(s) | clave (excedente {clave[0] if orden != pl.COSTO else clave[1]:.0f} cm²/ítem,"
                  f" ítems/corte {-(clave[1] if orden != pl.COSTO else clave[0]):.1f}, capas {clave[2]})")
        _, capas, cajas, factibles = evaluados[0]
        cubiertos = {id(i) for i, _ in factibles}
        print(f"      elegido: {' | '.join(_describir([(capas, c) for c in cajas], nombres_color))}")
        restantes = [i for i in restantes if id(i) not in cubiertos]


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("preparacion")
    parser.add_argument("--tela", default="FRIZA")
    parser.add_argument("--tope", type=int, default=0)
    args = parser.parse_args()

    preparacion = json.loads(Path(args.preparacion).read_text(encoding="utf-8"))
    general = ParametrosCorte.desde_dict(preparacion["parametros"], excedente_maximo_por_item=args.tope)
    grupo_tela = next(g for g in preparacion["grupos"] if g["codigoTipoTela"] == args.tela)
    p = general.para_tela(args.tela)
    items = _items(preparacion, grupo_tela, p)
    nombres_color = {c["idPaletaColor"]: c["nombreColor"] for it in grupo_tela["items"] for c in it["porColor"]}

    print(f"## {args.tela}: {len(items)} ítems, eficiencia supuesta {p.eficiencia_estimada:.0%}, tope {args.tope}")
    print("\n1) Combinaciones evaluadas (clave TELA = tela est., cortes, prendas partidas, capas):")
    mejor = None
    for tope in pl._topes_de_busqueda(p.excedente_maximo_por_item):
        p_tope = replace(p, excedente_maximo_por_item=tope)
        for orden in (pl.TELA, pl.COSTO):
            for estrategia in (pl.POR_CONJUNTO, pl.POR_COLOR, pl.MEZCLA):
                resultado = pl._resolver_tela(items, estrategia, p_tope, orden)
                cumple = pl._cumple_tope(resultado, p.excedente_maximo_por_item)
                clave = pl._evaluar(resultado, p)
                if cumple and (mejor is None or clave < mejor[0]):
                    mejor = (clave, estrategia, orden, tope, resultado)
                print(f"   tope búsqueda {tope} | orden {orden:5} | {estrategia:23} | tela {clave[0]:7.1f} cm,"
                      f" {clave[1]} cortes, {clave[2]} partidas, {clave[3]} capas | {'cumple' if cumple else 'NO cumple el tope'}")
    clave, estrategia, orden, tope, resultado = mejor
    print(f"   GANA: tope búsqueda {tope}, orden {orden}, {estrategia} → {clave}")
    for linea in _describir(resultado, nombres_color):
        print(f"     {linea}")

    print("\n2) Cobertura voraz de la combinación ganadora, grupo por grupo:")
    p_tope = replace(p, excedente_maximo_por_item=tope)
    for grupo in pl._descomponer(items, estrategia):
        print(f"  grupo colores {[nombres_color[c] for c in sorted(grupo[0].colores)]}: {len(grupo)} ítems")
        trazar_voraz(grupo, p_tope, orden, nombres_color)

    print("\n3) Contrafácticos (evaluación del planificador, largo estimado por área):")
    verde = [i for i in items if set(i.demanda) == {min(i.demanda)} and nombres_color[min(i.demanda)] == "Verde"]
    t1 = [i for i in verde if i.geo.talle == "1" and i.geo.nombre.split()[0] != "Manga"]
    if t1:
        color = next(iter(t1[0].demanda))
        for l in (1, 3):
            capas = {color: l}
            ics = [pl._item_en_corte(i, pl.unidades_por_capa(i, capas, p), capas, i.demanda, p) for i in t1]
            largo = pl._largo_estimado(ics, p)
            print(f"   ítems T1 sin manga con Verde={l}: largo est. {largo:6.1f} cm × {l} = tela {largo * l:6.1f} cm"
                  f" | excedente {sum(sum(ic.excedente.values()) for ic in ics)}")
        print("   → con la estimación por área, la tela no depende de las capas: decide el desempate 'menos capas'.")


if __name__ == "__main__":
    main()
