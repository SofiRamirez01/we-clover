import type { ProductoInsumoSecundarioResponse, TipoTela } from './paletaColores';

export interface TipoPrendaOption {
  id: number;
  nombre: string;
}

export interface ProductoCreateRequest {
  /** Solo se usa al editar un pedido existente: identifica el Producto a actualizar in-place
   *  en vez de recrearlo, para no perder moldería/tela/imagen/colores. Ausente para una prenda
   *  nueva o en el alta de un pedido. */
  id?: number;
  idTipoPrenda: number;
  /** Ya no se elige en el alta del pedido — se completa después desde Ficha Técnica ("Moldería"). */
  idPatronCorte?: number;
  cantidadTotal: number;
  costo: number;
  observaciones?: string;
  imagenDisenoUrl?: string;
}

export interface PatronCorteColorResponse {
  id: number;
  orden: number;
  gramos: number;
}

export type MetodoDeteccionColor = 'MANUAL' | 'AUTOMATICO';

export interface ProductoColorResponse {
  id: number;
  idPatronCorteColor: number;
  ordenPatronCorteColor: number;
  idPaletaColor: number;
  nombreColor: string;
  hexColor: string;
  metodoDeteccion: MetodoDeteccionColor;
  coordenadaX: number | null;
  coordenadaY: number | null;
  rgbDetectado: string | null;
}

export interface ProductoResponse {
  id: number;
  idTipoPrenda: number | null;
  tipoPrenda: string | null;
  idPatronCorte: number | null;
  patronCorteColores: PatronCorteColorResponse[] | null;
  tipoTela: TipoTela | null;
  idColorCierre: number | null;
  nombreColorCierre: string | null;
  hexColorCierre: string | null;
  cantidadTotal: number;
  costo: number;
  subtotal: number;
  observaciones: string | null;
  imagenDisenoUrl: string | null;
  estadoActual: EstadoPedido;
  colores: ProductoColorResponse[];
  insumosSecundarios: ProductoInsumoSecundarioResponse[];
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

export interface PedidoUpdateRequest {
  colegioNombre: string;
  colegioLocalidad?: string;
  colegioProvincia?: string;
  representanteNombre: string;
  representanteTelefono?: string;
  representanteEmail: string;
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

export interface HistorialEstadoPedidoResponse {
  id: number;
  estado: EstadoPedido;
  fechaCambio: string;
  observaciones: string | null;
  nombreUsuario: string;
  emailUsuario: string;
}

export interface PedidoResponse {
  id: number;
  idColegio: number;
  nombreColegio: string;
  localidadColegio: string | null;
  provinciaColegio: string | null;
  estadoActual: EstadoPedido;
  idRepresentanteCurso: number;
  nombreRepresentanteCurso: string;
  telefonoRepresentanteCurso: string | null;
  emailRepresentanteCurso: string;
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
  emailVendedor: string;
  productos: ProductoResponse[];
  precioTotal: number;
  pagoInicial: number;
  saldo: number;
  porcentajePagado: number;
}
