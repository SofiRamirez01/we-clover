"""Optimizador de residuo: interfaz preparada, todavía NO implementado.

En una entrega posterior va a buscar con geometría sustituciones sobre el residuo del
planificador (excedentes sobre el tope, cortes de una capa, cortes extra por una demanda chica):
una pieza necesaria B que entre completa en otra pieza A de la misma tela, probando alineaciones,
giros de 0° y 180° y sin espejo salvo que B sea simétrica. Dos casos:
- SOBRE_TALLE: una pieza de un talle cortada con el molde de un talle mayor.
- PIEZA_EQUIVALENTE: por ejemplo una espalda cortada también como frente, recortando después la
  curva del cuello.

Regla de decisión (para cuando se implemente): se acepta si elimina un corte o un excedente, con
aprovechamiento >= `aprovechamiento_minimo`, y si el desperdicio en cm² es menor que el de producir
piezas de más. Eliminar un corte ahorra `minutos_por_corte_adicional` y cada recorte cuesta
`minutos_por_recorte` (por operación sobre un grupo de capas, sin importar cuántas). Los pares de
`pares_prohibidos` nunca se sustituyen.
"""
from dataclasses import asdict, dataclass

from app.corte.parametros import ParametrosCorte
from app.corte.planificador import Plan

SOBRE_TALLE = "SOBRE_TALLE"
PIEZA_EQUIVALENTE = "PIEZA_EQUIVALENTE"


@dataclass(frozen=True)
class Sustitucion:
    pieza_origen: int
    talle_origen: int
    pieza_usada: int
    talle_usado: int
    tipo: str  # SOBRE_TALLE o PIEZA_EQUIVALENTE
    largo_recorte_cm: float
    desperdicio_cm2: float

    def a_dict(self) -> dict:
        datos = asdict(self)
        return {"piezaOrigen": datos["pieza_origen"], "talleOrigen": datos["talle_origen"],
                "piezaUsada": datos["pieza_usada"], "talleUsado": datos["talle_usado"],
                "tipo": datos["tipo"], "largoRecorteCm": datos["largo_recorte_cm"],
                "desperdicioCm2": datos["desperdicio_cm2"]}


def _orden_talle(talle):
    return (0, int(talle)) if str(talle).isdigit() else (1, str(talle))


def _encaja(b, a):
    """Mejor ubicación de B adentro de A probando alineaciones de bounding box y giros 0°/180°
    (sin espejo: si B es simétrica su espejo es ella misma). Devuelve B ubicada o None."""
    from shapely.affinity import rotate, translate

    ax0, ay0, ax1, ay1 = a.bounds
    contenedor = a.buffer(1e-6)
    for giro in (0, 180):
        candidata = rotate(b, giro, origin="centroid") if giro else b
        bx0, by0, bx1, by1 = candidata.bounds
        for dx in (ax0 - bx0, (ax0 + ax1 - bx0 - bx1) / 2, ax1 - bx1):
            for dy in (ay0 - by0, (ay0 + ay1 - by0 - by1) / 2, ay1 - by1):
                ubicada = translate(candidata, dx, dy)
                if contenedor.contains(ubicada):
                    return ubicada
    return None


def buscar_candidatas(plan: Plan, parametros: ParametrosCorte) -> list[dict]:
    """Solo para informar (no aplica nada): sustituciones geométricamente posibles sobre el residuo.

    Residuo = ítems de cortes de una sola capa o con excedente por encima del tope. Para cada ítem B
    del residuo busca otra pieza A de la misma tela, en otro corte con capas de los colores de B,
    que lo contenga completo, con
    aprovechamiento área(B) / área(A) >= `aprovechamiento_minimo`. Se queda con la A de menor
    desperdicio para cada B.
    """
    prohibidos = {tuple(par) for par in parametros.pares_prohibidos}
    candidatas = []
    for indice_b, corte_b in enumerate(plan.cortes):
        tope = parametros.para_tela(corte_b.codigo_tela).excedente_maximo_por_item
        residuo = [ic for ic in corte_b.items
                   if corte_b.capas_totales == 1
                   or any(e > max(tope, ic.excedente_inevitable[c]) for c, e in ic.excedente.items())]
        for ic_b in residuo:
            b = ic_b.item.geo
            mejor = None
            for indice_a, corte_a in enumerate(plan.cortes):
                # El corte que presta el molde tiene que tener capas de los colores de B.
                if (indice_a == indice_b or corte_a.id_tipo_tela != corte_b.id_tipo_tela
                        or not ic_b.item.colores <= set(corte_a.capas)):
                    continue
                for ic_a in corte_a.items:
                    a = ic_a.item.geo
                    if (a.id_pieza, a.id_talle) == (b.id_pieza, b.id_talle) or a.poligono.area < b.poligono.area:
                        continue
                    if ((b.id_pieza, a.id_pieza) in prohibidos
                            or b.poligono.area / a.poligono.area < parametros.aprovechamiento_minimo):
                        continue
                    ubicada = _encaja(b.poligono, a.poligono)
                    if ubicada is None:
                        continue
                    desperdicio = a.poligono.area - b.poligono.area
                    if mejor is None or desperdicio < mejor["desperdicioCm2"]:
                        recorte = ubicada.exterior.difference(a.poligono.exterior.buffer(0.05)).length
                        sobre_talle = a.id_pieza == b.id_pieza and _orden_talle(a.talle) > _orden_talle(b.talle)
                        mejor = {**Sustitucion(b.id_pieza, b.id_talle, a.id_pieza, a.id_talle,
                                               SOBRE_TALLE if sobre_talle else PIEZA_EQUIVALENTE,
                                               round(recorte, 1), round(desperdicio, 1)).a_dict(),
                                 "nombreOrigen": f"{b.nombre} T{b.talle}", "nombreUsada": f"{a.nombre} T{a.talle}",
                                 "aprovechamiento": round(b.poligono.area / a.poligono.area, 3),
                                 "corteOrigen": indice_b + 1, "corteUsado": indice_a + 1}
            if mejor is not None:
                candidatas.append(mejor)
    return candidatas


def optimizar_residuo(plan: Plan, parametros: ParametrosCorte) -> Plan:
    """Hoy no hace nada: devuelve el plan tal cual, con `sustituciones` vacío en cada corte.

    Si `sustituciones_habilitadas` está activo se avisa que todavía no está implementado.
    """
    if parametros.sustituciones_habilitadas:
        aviso = "Las sustituciones están habilitadas pero el optimizador de residuo todavía no está implementado"
        if aviso not in plan.advertencias:
            plan.advertencias.append(aviso)
    return plan
