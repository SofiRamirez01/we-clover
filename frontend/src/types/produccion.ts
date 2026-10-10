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

/** Estado de producción persistido de una prenda (EstadoProduccion.java), en orden de pipeline.
 *  No hay CONTROL: completar CONTROL (con el resto) es lo que la lleva a TERMINADO. */
export type EstadoProduccion =
  | 'PENDIENTE'
  | 'CORTADO'
  | 'ESTAMPADO'
  | 'BORDADO'
  | 'CONFECCION'
  | 'APODO'
  | 'OJAL'
  | 'TERMINADO'
  | 'ENTREGADO';

export const ESTADOS_PRODUCCION: EstadoProduccion[] = [
  'PENDIENTE',
  'CORTADO',
  'ESTAMPADO',
  'BORDADO',
  'CONFECCION',
  'APODO',
  'OJAL',
  'TERMINADO',
  'ENTREGADO',
];

export const ESTADO_PRODUCCION_LABELS: Record<EstadoProduccion, string> = {
  PENDIENTE: 'Pendiente',
  CORTADO: 'Cortado',
  ESTAMPADO: 'Estampado',
  BORDADO: 'Bordado',
  CONFECCION: 'Confección',
  APODO: 'Apodo',
  OJAL: 'Ojal',
  TERMINADO: 'Terminado',
  ENTREGADO: 'Entregado',
};

export const ESTADOS_BANDERA: EstadoBandera[] =['PENDIENTE', 'PEDIDO', 'RECIBIDO'];

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

/** Derivado de los pedidos de la tanda (ver TandaEstadoCalculador en el backend); nunca se
 *  setea a mano. */
export type EstadoTanda = 'PLANIFICADA' | 'EN_PRODUCCION' | 'CERRADA';

export const ESTADO_TANDA_LABELS: Record<EstadoTanda, string> = {
  PLANIFICADA: 'Planificada',
  EN_PRODUCCION: 'En producción',
  CERRADA: 'Cerrada',
};

/** Tanda de un pedido en la grilla. `id` es la única clave: `nombre` es una etiqueta editable y
 *  `posicion` cambia sola al cerrarse una tanda anterior (null si está CERRADA). */
export interface ProduccionTanda {
  id: number;
  nombre: string;
  posicion: number | null;
  estado: EstadoTanda;
}

export interface UnidadesPorTipoPrenda {
  tipoPrenda: string;
  unidades: number;
}

/** GET /api/tandas — totales reales de la tanda, sin importar los filtros de la grilla. */
export interface TandaResponse extends ProduccionTanda {
  cantidadPedidos: number;
  unidadesPorTipoPrenda: UnidadesPorTipoPrenda[];
  fechaCreacion: string;
  nombreCreador: string | null;
}

/** Nota interna de un pedido: solo se agregan, no se editan. */
export interface NotaPedido {
  id: number;
  texto: string;
  nombreAutor: string;
  fecha: string;
}

/** Aviso calculado para quien prioriza (GET /api/tandas/alertas). */
export interface AlertaPriorizacion {
  tipo: 'LISTO_SIN_TANDA';
  idPedido: number;
  codigoInterno: string;
  colegio: string;
  curso: string;
}

/** Referencia a una tanda dentro de una sesión de priorización: `id` si ya existe, `idTemporal`
 *  si se crea en la misma sesión. Nunca por nombre. */
export type TandaRef = { id: number; idTemporal?: undefined } | { id?: undefined; idTemporal: string };

export interface MovimientoTanda {
  idPedido: number;
  /** Tanda en la que estaba el pedido al abrir el popup (null = sin tanda): si ya no coincide,
   *  el backend rechaza toda la sesión. */
  idTandaOrigenEsperada: number | null;
  /** null = queda sin tanda. */
  destino: TandaRef | null;
  motivo: string | null;
}

/** POST /api/tandas/priorizacion — se aplica todo o nada. */
export interface PriorizacionRequest {
  nota: string | null;
  tandasNuevas: { idTemporal: string; nombre: string }[];
  ordenTandas: TandaRef[];
  movimientos: MovimientoTanda[];
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
  /** Puntaje sugerido (rank por % de pago): solo ordena los pedidos sin tanda. Null si el
   *  pedido no está activo (ENTREGADO/CANCELADO). */
  prioridadAutomatica: number | null;
  /** null = sin tanda. */
  tanda: ProduccionTanda | null;
  ubicacionActual: string | null;
  ultimaNota: NotaPedido | null;
  cantidadNotas: number;
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
