import api from './api';
import type { CambioEstadoRequest, PedidoCreateRequest, PedidoResponse, TipoPrendaOption } from '../types/pedido';

export async function crearPedido(payload: PedidoCreateRequest): Promise<PedidoResponse> {
  const { data } = await api.post<PedidoResponse>('/pedidos', payload);
  return data;
}

export async function cambiarEstadoPedido(
  id: number,
  payload: CambioEstadoRequest,
): Promise<PedidoResponse> {
  const { data } = await api.patch<PedidoResponse>(`/pedidos/${id}/estado`, payload);
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
