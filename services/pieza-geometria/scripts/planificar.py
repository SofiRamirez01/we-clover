"""Corre el planificador (sin algoritmo genético) sobre un JSON de preparacion-corte.

Uso: python scripts/planificar.py preparacion.json [--tope 0] [--modo TELA] [--comparar 0 1 2 4]

1. Plan con el tope y el modo pedidos (por defecto 0 y TELA), con su hoja de cortes.
2. Solo como información, compara topes de excedente: para cada uno, el plan que minimiza
   cortes dentro de ese tope (modo COSTO) frente al plan aplicado. No aplica ninguno. El punto
   de equilibrio son los minutos de corte que ahorra cada metro de tela de más: subir el tope
   conviene si un metro de tela vale menos que esos minutos.
Los largos son estimados por área (eficiencia supuesta en ParametrosCorte).
"""
import argparse
import json
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))

from app.corte.parametros import ParametrosCorte  # noqa: E402
from app.corte.planificador import planificar  # noqa: E402
from app.corte.residuo import optimizar_residuo  # noqa: E402


def _orden_talle(talle):
    return (0, int(talle)) if str(talle).isdigit() else (1, str(talle))


def _modo(ic):
    partes = []
    if ic.ranuras:
        partes.append(f"{ic.ranuras} ranura(s)")
    if ic.enteras:
        partes.append(f"{ic.enteras} entera(s)")
    if ic.mitades:
        partes.append(f"{ic.mitades} mitad(es)")
    return ", ".join(partes)


def hoja_de_cortes(plan, colores):
    print(" Hoja de cortes:")
    for numero, corte in enumerate(plan.cortes, start=1):
        capas = ", ".join(f"{colores[c]}={l}" for c, l in corte.capas.items())
        talles = sorted({ic.item.geo.talle for ic in corte.items}, key=_orden_talle)
        excedente = sum(sum(ic.excedente.values()) for ic in corte.items)
        inevitable = sum(sum(ic.excedente_inevitable.values()) for ic in corte.items)
        print(f"  corte {numero}: {corte.codigo_tela:7} capas[{capas}] talles {'+'.join('T' + t for t in talles)}"
              f" | largo est. {corte.largo_estimado_cm:6.1f} cm | tela est. {corte.tela_estimada_cm:6.1f} cm"
              f" | excedente {excedente} (inevitable {inevitable})")
        for ic in sorted(corte.items, key=lambda ic: (ic.item.geo.nombre, _orden_talle(ic.item.geo.talle))):
            pedidos = sorted({o.get("codigoInternoPedido") for o in ic.item.origen if o.get("codigoInternoPedido")})
            extra = {colores[c]: e for c, e in ic.excedente.items() if e}
            print(f"      {ic.item.geo.nombre[:24]:24} T{ic.item.geo.talle:3} u/capa={ic.unidades_por_capa}"
                  f" ({_modo(ic)}) demanda={sum(ic.demanda.values())} produce={sum(ic.produccion.values())}"
                  + (f" excedente={extra}" if extra else "") + (f" pedidos={pedidos}" if pedidos else ""))


def resumen(plan):
    return (f"{len(plan.cortes)} cortes, {sum(c.capas_totales for c in plan.cortes)} capas en total,"
            f" excedente {plan.excedente_total} piezas, tela est. {plan.tela_estimada_cm:.0f} cm,"
            f" prendas partidas {len(plan.prendas_partidas)}")


def comparar_topes(preparacion, base, p, topes):
    print(f"\n Comparación de topes (solo información; el plan aplicado es el de arriba):")
    print(f"  {'tope':>4} | {'cortes':>6} | {'ahorrados':>9} | {'piezas de más':>13} | {'tela de más':>11} |"
          f" equilibrio")
    for tope in topes:
        alternativa = planificar(preparacion, ParametrosCorte.desde_dict(
            preparacion["parametros"], excedente_maximo_por_item=tope, modo_objetivo="COSTO"))
        ahorrados = len(base.cortes) - len(alternativa.cortes)
        piezas = alternativa.excedente_total - base.excedente_total
        metros = (alternativa.tela_estimada_cm - base.tela_estimada_cm) / 100
        if ahorrados > 0 and metros > 0:
            minutos = ahorrados * p.minutos_por_corte_adicional
            equilibrio = f"{minutos / metros:.0f} min por metro ({minutos:.0f} min / {metros:.1f} m)"
        elif ahorrados > 0:
            equilibrio = "ahorra cortes sin gastar tela de más"
        else:
            equilibrio = "no ahorra cortes"
        print(f"  {tope:>4} | {len(alternativa.cortes):>6} | {ahorrados:>9} | {piezas:>13} | {metros:>9.1f} m |"
              f" {equilibrio}")


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("preparacion")
    parser.add_argument("--tope", type=int, default=0)
    parser.add_argument("--modo", default="TELA", choices=["TELA", "COSTO"])
    parser.add_argument("--comparar", type=int, nargs="*", default=[0, 1, 2, 4])
    args = parser.parse_args()
    preparacion = json.loads(Path(args.preparacion).read_text(encoding="utf-8"))
    p = ParametrosCorte.desde_dict(preparacion["parametros"], excedente_maximo_por_item=args.tope,
                                   modo_objetivo=args.modo)
    plan = optimizar_residuo(planificar(preparacion, p), p)
    colores = {c["idPaletaColor"]: c["nombreColor"]
               for g in preparacion["grupos"] for it in g["items"] for c in it["porColor"]}
    print(f"Tanda {preparacion['nombreTanda']} (id {preparacion['idTanda']}) | modo {p.modo_objetivo},"
          f" excedenteMaximoPorItem {p.excedente_maximo_por_item}, eficiencia estimada {p.eficiencia_estimada:.0%}")
    print(f" Plan: {resumen(plan)}; estrategia por tela {plan.estrategias}")
    hoja_de_cortes(plan, colores)
    for nr in plan.no_resueltas:
        print(f" NO RESUELTA: {nr['motivo']}: {nr['detalle']}")
    for advertencia in plan.advertencias:
        print(f" ADVERTENCIA: {advertencia}")
    if args.comparar:
        comparar_topes(preparacion, plan, p, args.comparar)


if __name__ == "__main__":
    main()
