"""Resultado de una optimización en el formato JSON de la API (camelCase, en cm)."""
from app.corte.parametros import ParametrosCorte
from app.corte.tizada import ResultadoOptimizacion, Tizada


def _coordenadas(poligono):
    return [[round(x, 3), round(y, 3)] for x, y in poligono.exterior.coords]


def _por_color(valores: dict, nombres: dict):
    return [{"idPaletaColor": color, "nombreColor": nombres.get(color), "cantidad": cantidad}
            for color, cantidad in valores.items()]


def tizada_a_dict(numero: int, tizada: Tizada, p: ParametrosCorte) -> dict:
    corte = tizada.corte
    nombres = {color: nombre for ic in corte.items for color, nombre in ic.item.nombres_color.items()}
    return {
        "numero": numero,
        "idTipoTela": corte.id_tipo_tela,
        "codigoTela": corte.codigo_tela,
        "nombreTela": corte.nombre_tela,
        "anchoCm": p.ancho_util_cm,
        "largoUsadoCm": round(tizada.largo_usado_cm, 2),
        "largoSinCompactarCm": round(tizada.largo_sin_compactar_cm, 2),
        "largoEstimadoCm": round(corte.largo_estimado_cm, 2),
        "eficiencia": round(tizada.eficiencia, 4),
        "telaCm": round(tizada.largo_usado_cm * corte.capas_totales, 2),
        "estado": tizada.estado,
        "verificacionOk": tizada.verificacion_ok,
        "presupuestoSegundos": tizada.presupuesto_segundos,
        "segundos": round(tizada.segundos, 1),
        "semillaGanadora": tizada.semilla_ganadora,
        "reinicios": tizada.reinicios,
        "corridas": [{"semilla": c.semilla, "largoCm": round(c.largo_cm, 2), "generaciones": c.generaciones,
                      "motivoFin": c.motivo_fin, "segundos": round(c.segundos, 1), "estado": c.estado,
                      "verificacionOk": c.verificacion_ok} for c in tizada.corridas],
        "capasPorColor": [{"idPaletaColor": color, "nombreColor": nombres.get(color), "capas": capas}
                          for color, capas in corte.capas.items()],
        "capasTotales": corte.capas_totales,
        "piezas": [{"idPieza": pz.id_pieza, "idTalle": pz.id_talle, "talle": pz.talle, "nombre": pz.nombre,
                    "copia": pz.copia, "modo": pz.modo, "rotacion": pz.rotacion,
                    "poligonoCm": _coordenadas(pz.poligono)} for pz in tizada.piezas],
        "items": [{"idPieza": ic.item.geo.id_pieza, "idTalle": ic.item.geo.id_talle, "talle": ic.item.geo.talle,
                   "nombre": ic.item.geo.nombre, "unidadesPorCapa": ic.unidades_por_capa,
                   "ranuras": ic.ranuras, "enteras": ic.enteras, "mitades": ic.mitades,
                   "demanda": _por_color(ic.demanda, nombres), "produccion": _por_color(ic.produccion, nombres),
                   "excedente": _por_color(ic.excedente, nombres),
                   "excedenteInevitable": _por_color(ic.excedente_inevitable, nombres)} for ic in corte.items],
        "sustituciones": [s.a_dict() if hasattr(s, "a_dict") else s for s in corte.sustituciones],
        "advertencias": list(tizada.advertencias),
    }


def resultado_a_dict(resultado: ResultadoOptimizacion, p: ParametrosCorte, segundos: float) -> dict:
    cortes = [tizada_a_dict(numero, tz, p) for numero, tz in enumerate(resultado.tizadas, start=1)]
    return {
        "cortes": cortes,
        "noResueltas": resultado.no_resueltas,
        "advertencias": resultado.advertencias,
        "prendasPartidas": [{"idTipoTela": tela, "idProducto": producto, "idTalle": talle}
                            for tela, producto, talle in resultado.plan.prendas_partidas],
        "estrategiaPorTela": {str(tela): estrategia for tela, estrategia in resultado.plan.estrategias.items()},
        "resumen": {
            "cortes": len(cortes),
            "capasTotales": sum(c["capasTotales"] for c in cortes),
            "largoTotalCm": round(sum(c["largoUsadoCm"] for c in cortes), 2),
            "telaTotalCm": round(sum(c["telaCm"] for c in cortes), 2),
            "excedenteTotal": sum(e["cantidad"] for c in cortes for it in c["items"] for e in it["excedente"]),
            "cortesAlLimiteDeTiempo": [c["numero"] for c in cortes if c["estado"] == "LIMITE_DE_TIEMPO"],
            "segundos": round(segundos, 1),
        },
        "parametros": {"modoObjetivo": p.modo_objetivo, "modoTiempo": p.modo_tiempo,
                       "presupuestoSegundosPorCorte": p.presupuesto_segundos,
                       "excedenteMaximoPorItem": p.excedente_maximo_por_item,
                       "excedenteMaximoPorTela": p.excedente_maximo_por_tela,
                       "eficienciaEstimadaPorTela": p.eficiencia_estimada_por_tela,
                       "generacionesEstancamiento": p.estancamiento_generaciones,
                       "umbralEstancamientoCm": p.estancamiento_umbral_cm,
                       "semilla": p.semilla},
    }
