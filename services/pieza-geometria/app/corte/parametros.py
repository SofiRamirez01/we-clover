from dataclasses import dataclass, field, fields


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
    # TELA: minimizar Σ(largo × capas). COSTO: minimizar minutos (ver planificador.py), apagado.
    modo_objetivo: str = "TELA"
    # Minutos que cuesta una prenda partida entre cortes (solo en modo COSTO).
    costo_prenda_partida: float = 0.0
    # Desvío máximo (área de la diferencia simétrica / área) para ofrecer la mitad al doblez.
    tolerancia_simetria: float = 0.01
    # Aprovechamiento supuesto para estimar el largo antes de correr el algoritmo; se calibra
    # con la medición (Parte 3).
    eficiencia_estimada: float = 0.70
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
    # Reintentos de un corte si la verificación final con polígonos exactos falla.
    reintentos_verificacion: int = 1
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
