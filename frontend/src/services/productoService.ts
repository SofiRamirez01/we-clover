import api from './api';
import type { EstadoPedido, ProductoResponse } from '../types/pedido';
import type { ProductoColorItemRequest, ProductoInsumoSecundarioItemRequest, TipoTela } from '../types/paletaColores';

export async function subirImagenDisenoProducto(idProducto: number, imagen: File): Promise<ProductoResponse> {
  const formData = new FormData();
  formData.append('imagen', imagen);

  const { data } = await api.postForm<ProductoResponse>(`/productos/${idProducto}/imagen`, formData);
  return data;
}

export async function eliminarImagenDisenoProducto(idProducto: number): Promise<ProductoResponse> {
  const { data } = await api.delete<ProductoResponse>(`/productos/${idProducto}/imagen`);
  return data;
}

export async function cambiarEstadoProducto(idProducto: number, estado: EstadoPedido): Promise<ProductoResponse> {
  const { data } = await api.patch<ProductoResponse>(`/productos/${idProducto}/estado`, { estado });
  return data;
}

export async function asignarColoresProducto(
  idProducto: number,
  colores: ProductoColorItemRequest[],
): Promise<ProductoResponse> {
  const { data } = await api.post<ProductoResponse>(`/productos/${idProducto}/colores`, { colores });
  return data;
}

/** idPatronCorte puede ser null para desasignar la moldería del producto. */
export async function actualizarPatronCorteProducto(idProducto: number, idPatronCorte: number | null): Promise<ProductoResponse> {
  const { data } = await api.patch<ProductoResponse>(`/productos/${idProducto}/patron-corte`, { idPatronCorte });
  return data;
}

export async function actualizarTipoTelaProducto(idProducto: number, tipoTela: TipoTela): Promise<ProductoResponse> {
  const { data } = await api.patch<ProductoResponse>(`/productos/${idProducto}/tipo-tela`, { tipoTela });
  return data;
}

export async function actualizarColorCierreProducto(idProducto: number, idPaletaColor: number): Promise<ProductoResponse> {
  const { data } = await api.patch<ProductoResponse>(`/productos/${idProducto}/color-cierre`, { idPaletaColor });
  return data;
}

/** Reemplaza todo el set de insumos secundarios del producto (capucha, puños, cierre y libres) de una vez. */
export async function actualizarInsumosSecundariosProducto(
  idProducto: number,
  insumos: ProductoInsumoSecundarioItemRequest[],
): Promise<ProductoResponse> {
  const { data } = await api.put<ProductoResponse>(`/productos/${idProducto}/insumos-secundarios`, { insumos });
  return data;
}
