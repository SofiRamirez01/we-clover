import api from './api';
import type {
  ArticuloResumenResponse,
  PlanificacionCompraBorradorRequest,
  PlanificacionCompraBorradorResponse,
  PlanificacionCompraDetalleResponse,
  PlanificacionCompraResponse,
  ProductoElegibleResponse,
} from '../types/planificacionCompra';

/** Trae todos los productos candidatos; la pantalla filtra por tanda, pagos, tipo de prenda y
 *  fecha de entrega del lado del cliente. */
export async function listarProductosElegibles(): Promise<ProductoElegibleResponse[]> {
  const { data } = await api.get<ProductoElegibleResponse[]>('/productos/elegibles-planificacion');
  return data;
}

export async function crearBorradorPlanificacion(payload: PlanificacionCompraBorradorRequest): Promise<PlanificacionCompraResponse> {
  const { data } = await api.post<PlanificacionCompraResponse>('/planificaciones-compra/borradores', payload);
  return data;
}

export async function actualizarBorradorPlanificacion(
  id: number,
  payload: PlanificacionCompraBorradorRequest,
): Promise<PlanificacionCompraResponse> {
  const { data } = await api.put<PlanificacionCompraResponse>(`/planificaciones-compra/borradores/${id}`, payload);
  return data;
}

export async function obtenerBorradorPlanificacion(id: number): Promise<PlanificacionCompraBorradorResponse> {
  const { data } = await api.get<PlanificacionCompraBorradorResponse>(`/planificaciones-compra/${id}/borrador`);
  return data;
}

export async function confirmarPlanificacion(id: number): Promise<PlanificacionCompraResponse> {
  const { data } = await api.post<PlanificacionCompraResponse>(`/planificaciones-compra/${id}/confirmar`);
  return data;
}

/** Editar una planificación confirmada: vuelve a borrador con sus productos tildados (el
 *  consumo se recalcula al confirmar de nuevo). */
export async function reabrirPlanificacion(id: number): Promise<PlanificacionCompraResponse> {
  const { data } = await api.post<PlanificacionCompraResponse>(`/planificaciones-compra/${id}/reabrir`);
  return data;
}

/** Sirve para cualquier estado: borrador o confirmada. */
export async function eliminarPlanificacion(id: number): Promise<void> {
  await api.delete(`/planificaciones-compra/${id}`);
}

export async function listarPlanificacionesCompra(): Promise<PlanificacionCompraResponse[]> {
  const { data } = await api.get<PlanificacionCompraResponse[]>('/planificaciones-compra');
  return data;
}

export async function obtenerPlanificacionCompra(id: number): Promise<PlanificacionCompraResponse> {
  const { data } = await api.get<PlanificacionCompraResponse>(`/planificaciones-compra/${id}`);
  return data;
}

export async function obtenerResumenPlanificacion(id: number): Promise<ArticuloResumenResponse[]> {
  const { data } = await api.get<ArticuloResumenResponse[]>(`/planificaciones-compra/${id}/resumen`);
  return data;
}

export async function obtenerDetallePlanificacion(id: number): Promise<PlanificacionCompraDetalleResponse[]> {
  const { data } = await api.get<PlanificacionCompraDetalleResponse[]>(`/planificaciones-compra/${id}/detalle`);
  return data;
}
