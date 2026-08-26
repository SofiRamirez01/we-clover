import api from './api';
import type { EstadoPedido, ProductoResponse } from '../types/pedido';

export async function subirImagenDisenoProducto(idProducto: number, imagen: File): Promise<ProductoResponse> {
  const formData = new FormData();
  formData.append('imagen', imagen);

  const { data } = await api.postForm<ProductoResponse>(`/productos/${idProducto}/imagen`, formData);
  return data;
}

export async function cambiarEstadoProducto(idProducto: number, estado: EstadoPedido): Promise<ProductoResponse> {
  const { data } = await api.patch<ProductoResponse>(`/productos/${idProducto}/estado`, { estado });
  return data;
}
