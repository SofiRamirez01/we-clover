export interface TipoPrendaOption {
  id: number;
  nombre: string;
}

export interface ProductoCreateRequest {
  idTipoPrenda: number;
  cantidadTotal: number;
  costo: number;
  observaciones?: string;
  imagenDisenoUrl?: string;
}

export interface ProductoResponse {
  id: number;
  idTipoPrenda: number | null;
  tipoPrenda: string | null;
  cantidadTotal: number;
  costo: number;
  subtotal: number;
  observaciones: string | null;
  imagenDisenoUrl: string | null;
}

export type EstadoPedido =
  | 'PRESUPUESTADO'
  | 'SENADO'
  | 'LISTO_PARA_PRODUCCION'
  | 'CORTADO'
  | 'BORDADO'
  | 'CONFECCIONADO'
  | 'EN_CONTROL'
  | 'TERMINADO'
  | 'ENTREGADO';

export const ESTADO_PEDIDO_LABELS: Record<EstadoPedido, string> = {
  PRESUPUESTADO: 'Presupuestado',
  SENADO: 'Señado',
  LISTO_PARA_PRODUCCION: 'Listo para Producción',
  CORTADO: 'Cortado',
  BORDADO: 'Bordado',
  CONFECCIONADO: 'Confeccionado',
  EN_CONTROL: 'En Control',
  TERMINADO: 'Terminado',
  ENTREGADO: 'Entregado',
};

/**
 * Agrupación de estados para los contadores del listado de pedidos (ver PedidosListView):
 * "pendiente" = todavía no entró a producción, "en_produccion" = etapas de fabricación,
 * "entregado" = ya se le dio al cliente. Es una interpretación propia del negocio, no algo
 * que exista en el backend — si no coincide con cómo lo piensa el equipo, ajustar acá.
 */
export type BucketEstadoPedido = 'pendiente' | 'en_produccion' | 'entregado';

export const BUCKET_POR_ESTADO: Record<EstadoPedido, BucketEstadoPedido> = {
  PRESUPUESTADO: 'pendiente',
  SENADO: 'pendiente',
  LISTO_PARA_PRODUCCION: 'pendiente',
  CORTADO: 'en_produccion',
  BORDADO: 'en_produccion',
  CONFECCIONADO: 'en_produccion',
  EN_CONTROL: 'en_produccion',
  TERMINADO: 'en_produccion',
  ENTREGADO: 'entregado',
};

export interface PedidoCreateRequest {
  colegioNombre: string;
  colegioLocalidad?: string;
  colegioProvincia?: string;
  representanteNombre: string;
  representanteTelefono?: string;
  representanteEmail: string;
  idVendedor: number;
  codigoInterno: string;
  curso: string;
  cantAlumnos: number;
  observaciones?: string;
  estado: EstadoPedido;
  fechaVenta: string;
  fechaEstimadaEntrega: string;
  productos: ProductoCreateRequest[];
  pagoInicial: number;
}

export const ESTADOS_PEDIDO: EstadoPedido[] = [
  'PRESUPUESTADO',
  'SENADO',
  'LISTO_PARA_PRODUCCION',
  'CORTADO',
  'BORDADO',
  'CONFECCIONADO',
  'EN_CONTROL',
  'TERMINADO',
  'ENTREGADO',
];

export interface CambioEstadoRequest {
  estado: EstadoPedido;
  fechaCambio: string;
  observaciones?: string;
}

export interface PedidoResponse {
  id: number;
  idColegio: number;
  nombreColegio: string;
  localidadColegio: string | null;
  estadoActual: EstadoPedido;
  idRepresentanteCurso: number;
  nombreRepresentanteCurso: string;
  codigoInterno: string;
  curso: string;
  cantAlumnos: number;
  observaciones: string | null;
  fechaVenta: string;
  fechaEstimadaEntrega: string;
  fechaCreacion: string;
  fechaActualizacion: string;
  idVendedor: number;
  nombreVendedor: string;
  productos: ProductoResponse[];
  precioTotal: number;
  pagoInicial: number;
  saldo: number;
  porcentajePagado: number;
}
