from dataclasses import dataclass, field, fields, replace

PRESUPUESTO_POR_MODO = {"RAPIDO": 60.0, "COMPLETO": 200.0}


@dataclass(frozen=True)
class ParametrosCorte:
    """Parámetros del corte. Los cinco primeros vienen de la preparación (CorteProperties en el
    backend); el resto tiene su default acá y se puede pisar en cada trabajo."""

    ancho_util_cm: float = 90.0
    largo_maximo_cm: float = 800.0
    margen_corte_cm: float = 0.5
    capas_maximas: int = 20
    telas_por_capa: int = 2
    # Distancia de las piezas enteras al doblez y al borde inferior.
    margen_borde_cm: float = 0.25
    # Piezas de más aceptadas por pieza, talle y color. Lo manda la solicitud; no se prueban
    # otros topes automáticamente.
    excedente_maximo_por_item: int = 0
    # Tope por código de tela (ej. {"RIBB": 2}); pisa a `excedente_maximo_por_item` en esa tela.
    excedente_maximo_por_tela: dict = field(default_factory=dict)
    # TELA: minimizar Σ(largo × capas). COSTO: minimizar minutos (ver planificador.py), apagado.
    modo_objetivo: str = "TELA"
    # Minutos que cuesta una prenda partida entre cortes (solo en modo COSTO).
    costo_prenda_partida: float = 0.0
    # Desvío máximo (área de la diferencia simétrica / área) para ofrecer la mitad al doblez.
    tolerancia_simetria: float = 0.01
    # Aprovechamiento supuesto para estimar el largo antes de correr el algoritmo: por código de
    # tela, y `eficiencia_estimada` para las que no están. PRELIMINARES: salen de una sola
    # medición (tanda A, 8 cortes de un solo pedido grande); hay que recalibrarlas con más tandas.
    eficiencia_estimada: float = 0.70
    eficiencia_estimada_por_tela: dict = field(
        default_factory=lambda: {"FRIZA": 0.66, "JERSEY": 0.65, "RIBB": 0.80})
    # Como la estimación es aproximada, un corte se intenta entero si su largo estimado no pasa
    # el máximo en más de esta fracción (800 cm -> hasta 880); si el algoritmo da más de 800 o no
    # ubica las piezas, recién ahí se parte. Más allá, el planificador lo parte de entrada.
    tolerancia_estimacion_largo: float = 0.10
    # Optimizador de residuo (todavía no implementado, ver residuo.py).
    sustituciones_habilitadas: bool = False
    minutos_por_corte_adicional: float = 120.0
    minutos_por_recorte: float = 10.0
    aprovechamiento_minimo: float = 0.85
    pares_prohibidos: tuple = field(default_factory=tuple)
    # Algoritmo genético (valores iniciales de v1.0.0, en mm: ver README del vendor).
    poblacion: int = 100
    generaciones: int = 200
    tasa_mutacion_x: float = 0.3
    tasa_mutacion_y: float = 0.99
    escala_mutacion_x_mm: float = 200.0
    escala_mutacion_y_pos_mm: float = 1.0
    escala_mutacion_y_neg_mm: float = 500.0
    tasa_rotacion: float = 0.15
    tasa_cambio_modo: float = 0.1
    tamano_torneo: int = 10
    probabilidad_cruce: float = 0.9
    # Simplificación de los polígonos con margen para el chequeo de choques del algoritmo (la
    # versión simplificada contiene a la original; 0 = sin simplificar). Las piezas del resultado
    # y la verificación final usan siempre los polígonos exactos.
    tolerancia_simplificacion_cm: float = 0.05
    # Tiempo por corte: el algoritmo se reinicia con otra semilla mientras quede presupuesto y
    # se queda la tizada más corta. `presupuesto_segundos_por_corte` (de la solicitud) manda
    # sobre `modo_tiempo`; los dos se recortan a `presupuesto_maximo_segundos`.
    modo_tiempo: str = "COMPLETO"
    presupuesto_segundos_por_corte: float | None = None
    presupuesto_maximo_segundos: float = 200.0
    # Corte anticipado: si los últimos K reinicios no acortan la mejor tizada en más de esta
    # fracción, el corte termina aunque quede presupuesto.
    reinicios_sin_mejora: int = 2
    mejora_minima_reinicio: float = 0.01
    # Compactación final de la mejor tizada (mitades solo en y, enteras en x e y).
    compactar: bool = True
    paso_minimo_compactacion_cm: float = 0.01
    # Semilla del primer reinicio (cada reinicio usa semilla + n). None = al azar.
    semilla: int | None = None

    @classmethod
    def desde_dict(cls, datos: dict | None = None, **extra) -> "ParametrosCorte":
        """Acepta claves en camelCase (como las manda el backend) o en snake_case."""
        datos = {**(datos or {}), **extra}
        por_nombre = {f.name: f for f in fields(cls)}
        por_camel = {_camel(nombre): nombre for nombre in por_nombre}
        valores = {}
        for clave, valor in datos.items():
            nombre = clave if clave in por_nombre else por_camel.get(clave)
            if nombre is None:
                continue
            if nombre == "pares_prohibidos":
                valor = tuple(tuple(par) for par in valor)
            valores[nombre] = valor
        return cls(**valores)

    def __post_init__(self):
        if self.modo_objetivo not in ("TELA", "COSTO"):
            raise ValueError(f"modoObjetivo inválido: {self.modo_objetivo} (TELA o COSTO)")
        if self.modo_tiempo not in PRESUPUESTO_POR_MODO:
            raise ValueError(f"modoTiempo inválido: {self.modo_tiempo} (RAPIDO o COMPLETO)")

    def eficiencia_para(self, codigo_tela: str | None) -> float:
        return self.eficiencia_estimada_por_tela.get(codigo_tela, self.eficiencia_estimada)

    def para_tela(self, codigo_tela: str | None) -> "ParametrosCorte":
        """Los mismos parámetros con los valores propios de esa tela."""
        return replace(self, eficiencia_estimada=self.eficiencia_para(codigo_tela),
                       excedente_maximo_por_item=self.excedente_maximo_por_tela.get(
                           codigo_tela, self.excedente_maximo_por_item))

    @property
    def presupuesto_segundos(self) -> float:
        """Segundos por corte: lo pedido (o el del modo), sin pasar el tope. En RAPIDO el tope es
        60 s; en COMPLETO, `presupuesto_maximo_segundos` (200 por defecto, configurable)."""
        tope = self.presupuesto_maximo_segundos
        if self.modo_tiempo == "RAPIDO":
            tope = min(tope, PRESUPUESTO_POR_MODO["RAPIDO"])
        pedido = self.presupuesto_segundos_por_corte or PRESUPUESTO_POR_MODO[self.modo_tiempo]
        return min(pedido, tope)

    @property
    def unidades_por_entera(self) -> int:
        """Una pieza entera ubicada libremente rinde una copia por tela de la capa."""
        return self.telas_por_capa

    @property
    def mitad_disponible(self) -> bool:
        """La mitad al doblez solo tiene sentido en tela tubular (dos telas por capa)."""
        return self.telas_por_capa == 2


def _camel(nombre: str) -> str:
    primera, *resto = nombre.split("_")
    return primera + "".join(parte.capitalize() for parte in resto)
