import type { ProductoResponse } from './pedido';
import type { UnidadMedida } from './proveedor';

export interface PlanificacionResumenResponse {
  id: number;
  nombre: string;
}

/** Producto candidato a una planificación, con el contexto de su pedido de origen y su
 *  elegibilidad ya resuelta por el backend (ver ProductoService.motivoDisenoIncompleto). */
export interface ProductoElegibleResponse {
  producto: ProductoResponse;
  idPedido: number;
  codigoInternoPedido: string;
  nombreColegio: string;
  fechaVentaPedido: string;
  fechaEstimadaEntregaPedido: string;
  porcentajePagadoPedido: number;
  disenoCompleto: boolean;
  motivoIncompleto: string | null;
  /** Null si el producto no tiene moldería asignada (ahí disenoCompleto ya es false). */
  nombreMolderia: string | null;
  numeroInternoMolderia: number | null;
  planificacionesQueLoIncluyen: PlanificacionResumenResponse[];
}

export type EstadoPlanificacionCompra = 'BORRADOR' | 'CONFIRMADA';

/** Nada es obligatorio: un borrador se puede guardar a mitad de completar (ver backend,
 *  PlanificacionCompraBorradorRequest — las validaciones de "obligatorio" son recién al
 *  confirmar). */
export interface PlanificacionCompraBorradorRequest {
  nombre?: string;
  fechaDesde?: string;
  fechaHasta?: string;
  idsProductos: number[];
}

/** Para "continuar editando" un borrador desde el listado. */
export interface PlanificacionCompraBorradorResponse {
  id: number;
  nombre: string;
  fechaDesde: string | null;
  fechaHasta: string | null;
  idsProductos: number[];
}

export interface PlanificacionCompraResponse {
  id: number;
  nombre: string;
  fechaCreacion: string;
  fechaDesde: string | null;
  fechaHasta: string | null;
  idCreadoPor: number;
  nombreCreadoPor: string;
  cantidadProductos: number;
  estado: EstadoPlanificacionCompra;
}

/** Resumen unificado (CAMBIO 5): cantidad total por (tipoTela, color) a lo largo de toda la
 *  planificación, con el estimado en pesos según el proveedor preferido de ese color, si hay
 *  uno cargado (ver ArticuloProveedor.preferido). */
export interface ArticuloResumenResponse {
  idTipoTela: number;
  tipoTela: string;
  nombreTipoTela: string;
  idPaletaColor: number;
  nombreColor: string;
  hexColor: string;
  cantidad: number;
  unidadMedida: UnidadMedida;
  nombreProveedorPreferido: string | null;
  precioUnitarioEstimado: number | null;
  estimadoTotal: number | null;
}

/** Fila sin agrupar, para la vista de detalle por producto/pedido. */
export interface PlanificacionCompraDetalleResponse {
  id: number;
  idProducto: number;
  tipoPrenda: string | null;
  idPedido: number;
  codigoInternoPedido: string;
  nombreColegio: string;
  idTipoTela: number;
  tipoTela: string;
  nombreTipoTela: string;
  idPaletaColor: number;
  nombreColor: string;
  hexColor: string;
  cantidad: number;
  unidadMedida: UnidadMedida;
}
