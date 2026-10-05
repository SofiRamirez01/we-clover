import type { EstadoProduccion } from './produccion';

/** Filtros globales de la página de Reportes (aplican a Ventas y a Producción). Fechas en
 *  YYYY-MM-DD. Acá se suma `idEmpleado` cuando exista el reporte por empleado. */
export interface ReporteFiltros {
  desde: string;
  hasta: string;
  /** null = todas las prendas. */
  idTipoPrenda: number | null;
}

export interface VentasPorMes {
  /** YYYY-MM */
  mes: string;
  unidades: number;
  monto: number;
}

export interface VentasPorTipoPrenda {
  idTipoPrenda: number;
  tipoPrenda: string;
  unidades: number;
  monto: number;
}

export interface VentasPresupuestados {
  cantidadPedidos: number;
  unidades: number;
  monto: number;
  montoIncompleto: boolean;
}

/**
 * GET /reportes/ventas (ver ReporteVentasResponse en el backend). pedidosSinDesglose = pedidos
 * importados sin precio por prenda: con filtro de tipo de prenda su monto no se puede sumar
 * (montoIncompleto), y sin filtro no se puede repartir en porTipoPrenda.
 */
export interface ReporteVentasResponse {
  desde: string;
  hasta: string;
  idTipoPrenda: number | null;

  unidadesTotales: number;
  montoTotal: number;
  precioPromedioUnidad: number;
  cantidadPedidos: number;

  montoIncompleto: boolean;
  pedidosSinDesglose: number;

  porTipoPrenda: VentasPorTipoPrenda[];
  porMes: VentasPorMes[];

  presupuestados: VentasPresupuestados;
}

export interface ProduccionPorEstado {
  estado: EstadoProduccion;
  unidades: number;
}

export interface ProduccionPorMes {
  /** YYYY-MM */
  mes: string;
  unidades: number;
}

/**
 * GET /reportes/produccion (ver ReporteProduccionResponse en el backend). porEstado es la foto
 * actual (no depende del período, solo del tipo de prenda) y trae siempre los 9 estados en orden
 * de pipeline; unidadesEnPlanta es la suma de todos menos ENTREGADO.
 */
export interface ReporteProduccionResponse {
  desde: string;
  hasta: string;
  idTipoPrenda: number | null;

  porEstado: ProduccionPorEstado[];
  unidadesEnPlanta: number;

  terminadasPorMes: ProduccionPorMes[];
}
