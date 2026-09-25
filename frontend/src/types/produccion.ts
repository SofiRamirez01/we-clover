import type { EstadoBandera, EstadoPedido } from './pedido';

export type EtapaProduccion = 'CORTE' | 'ESTAMPADO' | 'BORDADO' | 'CONFECCION' | 'APODO' | 'OJAL' | 'CONTROL';

/** Mismo orden de pipeline que el backend (EtapaProduccion.java) — se usa tal cual para las
 *  columnas fijas de la grilla y del modal de carga masiva. */
export const ETAPAS_PRODUCCION: EtapaProduccion[] = [
  'CORTE',
  'ESTAMPADO',
  'BORDADO',
  'CONFECCION',
  'APODO',
  'OJAL',
  'CONTROL',
];

export const ETAPA_PRODUCCION_LABELS: Record<EtapaProduccion, string> = {
  CORTE: 'Corte',
  ESTAMPADO: 'Estampado',
  BORDADO: 'Bordado',
  CONFECCION: 'Confección',
  APODO: 'Apodo',
  OJAL: 'Ojal',
  CONTROL: 'Control',
};

export const ESTADOS_BANDERA: EstadoBandera[] = ['PENDIENTE', 'PEDIDO', 'RECIBIDO'];

export const ESTADO_BANDERA_LABELS: Record<EstadoBandera, string> = {
  PENDIENTE: 'Pendiente',
  PEDIDO: 'Pedido al proveedor',
  RECIBIDO: 'Recibido',
};

/** Siempre trae las 7 etapas — aplica=false para las que no correspondan a ese producto (ver
 *  ProduccionEtapaResponse en el backend). */
export interface ProduccionEtapaResponse {
  etapa: EtapaProduccion;
  aplica: boolean;
  completado: boolean;
  fechaCompletado: string | null;
  empleadoId: number | null;
  empleadoNombre: string | null;
}

/** Si esBandera es true, estadoVisual es null y etapas viene vacío — usar estadoBandera/
 *  fechaPedidoProveedor/fechaRecibido en su lugar. Si es false, es al revés. */
export interface ProduccionProductoResponse {
  id: number;
  tipoPrenda: string | null;
  cantidadTotal: number;
  esBandera: boolean;
  estadoVisual: string | null;
  etapas: ProduccionEtapaResponse[];
  estadoBandera: EstadoBandera | null;
  fechaPedidoProveedor: string | null;
  fechaRecibido: string | null;
}

export interface ProduccionPedidoResponse {
  id: number;
  codigoInterno: string;
  colegio: string;
  curso: string;
  estadoActual: EstadoPedido;
  fechaVenta: string;
  fechaEstimadaEntrega: string;
  porcentajePagado: number;
  /** Null si el pedido no está activo (ENTREGADO/CANCELADO). */
  prioridadAutomatica: number | null;
  prioridadManual: number | null;
  productos: ProduccionProductoResponse[];
}

export interface ProduccionFiltros {
  estado?: EstadoPedido[];
  etapaPendiente?: EtapaProduccion;
  pagoMin?: number;
  pagoMax?: number;
}

/** Para poblar el selector de empleado (GET /api/usuarios?rol=ROLE_PLANTA) — ver
 *  UsuarioResumenResponse en el backend. */
export interface UsuarioResumen {
  id: number;
  nombre: string;
}
