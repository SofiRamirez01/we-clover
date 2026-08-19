import api from './api';
import type { PedidoCreateRequest, PedidoResponse } from '../types/pedido';

export async function crearPedido(payload: PedidoCreateRequest): Promise<PedidoResponse> {
  const { data } = await api.post<PedidoResponse>('/pedidos', payload);
  return data;
}
