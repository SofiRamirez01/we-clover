import type { ProductoInsumoSecundarioResponse, TipoTela } from './paletaColores';
import type { EtapaProduccion } from './produccion';

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
  /** Solo se usan si tipoPrenda es Bandera (ver EstadoBandera en el backend) — null para el
   *  resto. El seguimiento por etapa del resto de las prendas (ProductoEtapaProduccion,
   *  reemplaza al viejo estadoActual) todavía no tiene pantalla propia — Entrega 2. */
  estadoBandera: EstadoBandera | null;
  fechaPedidoProveedor: string | null;
  fechaRecibido: string | null;
  colores: ProductoColorResponse[];
  insumosSecundarios: ProductoInsumoSecundarioResponse[];
}

export type EstadoBandera = 'PENDIENTE' | 'PEDIDO' | 'RECIBIDO';

export type EstadoPedido =
  | 'PRESUPUESTADO'
  | 'SENADO'
  | 'LISTO_PARA_PRODUCCION'
  | 'EN_PRODUCCION'
  | 'TERMINADO'
  | 'ENTREGADO'
  | 'CANCELADO';

export const ESTADO_PEDIDO_LABELS: Record<EstadoPedido, string> = {
  PRESUPUESTADO: 'Presupuestado',
  SENADO: 'Señado',
  LISTO_PARA_PRODUCCION: 'Listo para Producción',
  EN_PRODUCCION: 'En Producción',
  TERMINADO: 'Terminado',
  ENTREGADO: 'Entregado',
  CANCELADO: 'Cancelado',
};

/**
 * Agrupación de estados para los contadores del listado de pedidos (ver PedidosListView):
 * "pendiente" = todavía no entró a producción, "en_produccion" = etapas de fabricación,
 * "entregado" = ya se le dio al cliente, "cancelado" = pedido dado de baja. Es una
 * interpretación propia del negocio, no algo que exista en el backend — si no coincide con
 * cómo lo piensa el equipo, ajustar acá.
 */
export type BucketEstadoPedido = 'pendiente' | 'en_produccion' | 'entregado' | 'cancelado';

export const BUCKET_POR_ESTADO: Record<EstadoPedido, BucketEstadoPedido> = {
  PRESUPUESTADO: 'pendiente',
  SENADO: 'pendiente',
  LISTO_PARA_PRODUCCION: 'pendiente',
  EN_PRODUCCION: 'en_produccion',
  TERMINADO: 'en_produccion',
  ENTREGADO: 'entregado',
  CANCELADO: 'cancelado',
};

export type ResponsableCurso = 'ALUMNO' | 'ADULTO';

export const RESPONSABLE_CURSO_LABELS: Record<ResponsableCurso, string> = {
  ALUMNO: 'Alumno',
  ADULTO: 'Adulto',
};

export interface PedidoCreateRequest {
  colegioNombre: string;
  colegioLocalidad?: string;
  colegioProvincia?: string;
  colegioNivel?: string;
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
  responsableCurso?: ResponsableCurso;
  contratoFirmado?: boolean;
  cantidadCuotas?: number;
}

export const ESTADOS_PEDIDO: EstadoPedido[] = [
  'PRESUPUESTADO',
  'SENADO',
  'LISTO_PARA_PRODUCCION',
  'EN_PRODUCCION',
  'TERMINADO',
  'ENTREGADO',
  'CANCELADO',
];

/** Estados que una persona puede setear a mano (ver PedidoService.validarEstadoManual en el
 *  backend) — LISTO_PARA_PRODUCCION/EN_PRODUCCION/TERMINADO son 100% automáticos y el backend
 *  rechaza con 409 cualquier intento de setearlos acá. */
export const ESTADOS_PEDIDO_MANUALES: EstadoPedido[] = ['PRESUPUESTADO', 'SENADO', 'ENTREGADO', 'CANCELADO'];

export interface CambioEstadoRequest {
  estado: EstadoPedido;
  fechaCambio: string;
  observaciones?: string;
}

export interface PedidoUpdateRequest {
  colegioNombre: string;
  colegioLocalidad?: string;
  colegioProvincia?: string;
  colegioNivel?: string;
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
  responsableCurso?: ResponsableCurso;
  contratoFirmado?: boolean;
  cantidadCuotas?: number;
}

export type TipoEventoHistorial = 'ESTADO_PEDIDO' | 'ETAPA_PRODUCCION';

/**
 * Fila del historial unificado del pedido (GET /pedidos/{id}/historial) — combina cambios de
 * EstadoPedido con cambios de etapa de producción de cualquier prenda del pedido, en una sola
 * línea de tiempo. Los campos de cada rama van `null` cuando `tipoEvento` es el otro tipo (ver
 * HistorialCambioResponse en el backend).
 */
export interface HistorialCambioResponse {
  id: number;
  fechaCambio: string;
  tipoEvento: TipoEventoHistorial;

  estadoPedido: EstadoPedido | null;
  observaciones: string | null;

  tipoPrenda: string | null;
  etapa: EtapaProduccion | null;
  etapaCompletado: boolean | null;
  nombreEmpleadoAsignado: string | null;

  /** null solo es posible para ESTADO_PEDIDO (transición automática) — un cambio de etapa
   *  siempre tiene un actor humano. */
  nombreUsuario: string | null;
  emailUsuario: string | null;
}

export interface PedidoResponse {
  id: number;
  idColegio: number;
  nombreColegio: string;
  localidadColegio: string | null;
  provinciaColegio: string | null;
  nivelColegio: string | null;
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
  /** Precio del "combo": suma de costo de cada tipo de prenda del pedido (no ponderado por
   *  cantidad) — no necesariamente coincide con precioTotal / cantAlumnos, y está bien que no
   *  coincida. */
  precioUnitario: number;
  pagoInicial: number;
  saldo: number;
  porcentajePagado: number;
  responsableCurso: ResponsableCurso | null;
  contratoFirmado: boolean;
  cantidadCuotas: number | null;
  /** Rank (1 = más prioritario) entre los pedidos activos por % pagado, calculado al vuelo.
   *  Null si el pedido no está activo (ENTREGADO/CANCELADO). Sin pantalla propia todavía —
   *  Entrega 2. */
  prioridadAutomatica: number | null;
  prioridadManual: number | null;
}

export interface PedidoImportadoResumen {
  filaExcel: number;
  idPedido: number;
  codigoInterno: string;
  colegio: string;
}

export interface FilaImportacionSaltada {
  filaExcel: number;
  colegio: string | null;
  motivo: string;
}

export interface ImportacionPedidosExcelResponse {
  totalFilas: number;
  importados: number;
  pedidosImportados: PedidoImportadoResumen[];
  filasSalteadas: FilaImportacionSaltada[];
}
