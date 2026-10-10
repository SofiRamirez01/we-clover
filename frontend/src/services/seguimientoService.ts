import api from './api';
import type { NotaPedido } from '../types/produccion';

/** Notas internas del pedido, la más nueva primero. */
export async function listarNotasPedido(idPedido: number): Promise<NotaPedido[]> {
  const { data } = await api.get<NotaPedido[]>(`/pedidos/${idPedido}/notas`);
  return data;
}

export async function agregarNotaPedido(idPedido: number, texto: string): Promise<NotaPedido> {
  const { data } = await api.post<NotaPedido>(`/pedidos/${idPedido}/notas`, { texto });
  return data;
}

/** Texto vacío = el pedido queda sin ubicación. Devuelve la ubicación guardada. */
export async function cambiarUbicacionPedido(idPedido: number, ubicacion: string): Promise<string | null> {
  const { data } = await api.patch<{ idPedido: number; ubicacionActual: string | null }>(
    `/pedidos/${idPedido}/ubicacion`,
    { ubicacion: ubicacion.trim() || null },
  );
  return data.ubicacionActual;
}
