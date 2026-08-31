import api from './api';
import type {
  ArticuloResumenResponse,
  PlanificacionCompraBorradorRequest,
  PlanificacionCompraBorradorResponse,
  PlanificacionCompraDetalleResponse,
  PlanificacionCompraResponse,
  ProductoElegibleResponse,
} from '../types/planificacionCompra';

/** Filtra por Pedido.fechaEstimadaEntrega (no fecha de venta) — ver backend, CAMBIO 4. */
export async function listarProductosElegibles(fechaDesde: string, fechaHasta: string): Promise<ProductoElegibleResponse[]> {
  const { data } = await api.get<ProductoElegibleResponse[]>('/productos/elegibles-planificacion', {
    params: { fechaDesde, fechaHasta },
  });
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
