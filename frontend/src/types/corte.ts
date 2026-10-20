/** Módulo 4 — optimización de cortes de una tanda (ver /api/tandas/{id}/cortes/...). */

export type EstadoTrabajoCorte = 'EN_COLA' | 'EJECUTANDO' | 'TERMINADO' | 'ERROR' | 'CANCELADO';
export type ModoTiempoCorte = 'RAPIDO' | 'COMPLETO';

export interface OpcionesOptimizacion {
  modoTiempo?: ModoTiempoCorte;
  presupuestoSegundosPorCorte?: number;
  excedenteMaximoPorItem?: number;
  excedenteMaximoPorTela?: Record<string, number>;
}

export interface TrabajoCorteCreado {
  idTrabajo: string;
  idTanda: number;
  estado: EstadoTrabajoCorte;
  posicionEnCola: number | null;
}

export interface ProgresoCorte {
  corteActual: number | null;
  cortesTotales: number | null;
  corridaActual: number | null;
  semillaActual: number | null;
  generacionActual: number | null;
  generacionesTotales: number | null;
  mejorLargoCm: number | null;
  mejorEficiencia: number | null;
  segundosTranscurridos: number | null;
}

export interface CantidadPorColorCorte {
  idPaletaColor: number;
  nombreColor: string | null;
  cantidad: number;
}

export interface CapasColor {
  idPaletaColor: number;
  nombreColor: string | null;
  capas: number;
}

export type ModoPieza = 'ENTERA' | 'MITAD_AL_DOBLEZ';

export interface PiezaUbicada {
  idPieza: number;
  idTalle: number;
  talle: string;
  nombre: string;
  copia: number;
  modo: ModoPieza;
  rotacion: 0 | 180;
  /** Polígono final en cm: x a lo ancho (0 y anchoCm son los dobleces), y a lo largo. */
  poligonoCm: [number, number][];
}

export interface ItemCorteResultado {
  idPieza: number;
  idTalle: number;
  talle: string;
  nombre: string;
  unidadesPorCapa: number;
  ranuras: number;
  enteras: number;
  mitades: number;
  demanda: CantidadPorColorCorte[];
  produccion: CantidadPorColorCorte[];
  excedente: CantidadPorColorCorte[];
  excedenteInevitable: CantidadPorColorCorte[];
}

export interface CorridaCorte {
  semilla: number;
  largoCm: number;
  generaciones: number;
  motivoFin: string;
  segundos: number;
  estado: string;
  verificacionOk: boolean;
}

export interface CorteResultado {
  numero: number;
  idTipoTela: number;
  codigoTela: string;
  nombreTela: string;
  anchoCm: number;
  largoUsadoCm: number;
  largoSinCompactarCm: number;
  largoEstimadoCm: number;
  eficiencia: number;
  telaCm: number;
  estado: 'COMPLETO' | 'LIMITE_DE_TIEMPO';
  verificacionOk: boolean;
  presupuestoSegundos: number;
  segundos: number;
  semillaGanadora: number;
  reinicios: number;
  corridas: CorridaCorte[];
  capasPorColor: CapasColor[];
  capasTotales: number;
  piezas: PiezaUbicada[];
  items: ItemCorteResultado[];
  sustituciones: unknown[];
  advertencias: string[];
}

export interface NoResuelta {
  idPieza: number;
  idTalle: number;
  talle: string | null;
  idTipoTela: number;
  cantidad: number;
  motivo: string;
  detalle: string;
}

export interface ResultadoOptimizacion {
  cortes: CorteResultado[];
  noResueltas: NoResuelta[];
  advertencias: string[];
  resumen: {
    cortes: number;
    capasTotales: number;
    largoTotalCm: number;
    telaTotalCm: number;
    excedenteTotal: number;
    cortesAlLimiteDeTiempo: number[];
    segundos: number;
  };
}

export interface TrabajoCorte {
  idTrabajo: string;
  idTanda: number;
  estado: EstadoTrabajoCorte;
  posicionEnCola: number | null;
  progreso: ProgresoCorte | null;
  creado: string | null;
  iniciado: string | null;
  terminado: string | null;
  venceEn: string | null;
  error: string | null;
  resultado: ResultadoOptimizacion | null;
}

/** Lo que se muestra de la preparación antes de generar (GET /tandas/{id}/preparacion-corte). */
export interface ResumenPreparacionCorte {
  idTanda: number;
  nombreTanda: string;
  grupos: { idTipoTela: number; codigoTipoTela: string; nombreTipoTela: string; items: unknown[] }[];
  resumen: { unidadesIncluidas: number; unidadesPendientes: number; piezasDistintas: number };
}
