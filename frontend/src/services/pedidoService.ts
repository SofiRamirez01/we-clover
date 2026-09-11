import api from './api';
import type {
  CambioEstadoRequest,
  HistorialEstadoPedidoResponse,
  ImportacionPedidosExcelResponse,
  PedidoCreateRequest,
  PedidoResponse,
  PedidoUpdateRequest,
  TipoPrendaOption,
} from '../types/pedido';

export async function crearPedido(payload: PedidoCreateRequest): Promise<PedidoResponse> {
  const { data } = await api.post<PedidoResponse>('/pedidos', payload);
  return data;
}

export async function actualizarPedido(id: number, payload: PedidoUpdateRequest): Promise<PedidoResponse> {
  const { data } = await api.put<PedidoResponse>(`/pedidos/${id}`, payload);
  return data;
}

export async function obtenerPedido(id: number): Promise<PedidoResponse> {
  const { data } = await api.get<PedidoResponse>(`/pedidos/${id}`);
  return data;
}

export async function cambiarEstadoPedido(
  id: number,
  payload: CambioEstadoRequest,
): Promise<PedidoResponse> {
  const { data } = await api.patch<PedidoResponse>(`/pedidos/${id}/estado`, payload);
  return data;
}

export async function obtenerHistorialPedido(id: number): Promise<HistorialEstadoPedidoResponse[]> {
  const { data } = await api.get<HistorialEstadoPedidoResponse[]>(`/pedidos/${id}/historial`);
  return data;
}

export async function listarPedidos(): Promise<PedidoResponse[]> {
  const { data } = await api.get<PedidoResponse[]>('/pedidos');
  return data;
}

export async function listarTiposPrenda(): Promise<TipoPrendaOption[]> {
  const { data } = await api.get<TipoPrendaOption[]>('/tipos-prenda');
  return data;
}

export async function importarPedidosExcel(archivo: File): Promise<ImportacionPedidosExcelResponse> {
  const formData = new FormData();
  formData.append('archivo', archivo);
  const { data } = await api.post<ImportacionPedidosExcelResponse>('/pedidos/importar-excel', formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
  });
  return data;
}
