import api from './api';
import type { ProductoResponse } from '../types/pedido';
import type { EstadoBandera } from '../types/pedido';
import type { ProductoColorItemRequest, ProductoInsumoSecundarioItemRequest, TipoTela } from '../types/paletaColores';
import type { EtapaProduccion } from '../types/produccion';

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

/** Marca/revierte una etapa de producción (ver Pantalla de Producción). No se tipa ni se usa
 *  el body de la respuesta: la pantalla que llama a esto ya actualiza optimistamente su propio
 *  estado local y, si el pedido cambió de estado (ej. se disparó EN_PRODUCCION), vuelve a pedir
 *  la grilla completa en vez de parsear esta respuesta puntual. */
export async function marcarEtapaProducto(
  idProducto: number,
  etapa: EtapaProduccion,
  completado: boolean,
  idEmpleado?: number | null,
): Promise<void> {
  await api.put(`/productos/${idProducto}/etapas/${etapa}`, { completado, idEmpleado: idEmpleado ?? null });
}

export interface EtapaBulkItem {
  idProducto: number;
  etapa: EtapaProduccion;
  completado: boolean;
  idEmpleado?: number | null;
}

/** Carga masiva (ver modal de la Pantalla de Producción) — un solo request para todo el lote. */
export async function marcarEtapasBulk(etapas: EtapaBulkItem[]): Promise<void> {
  await api.put('/productos/etapas/bulk', { etapas });
}

/** Cambia el estado del flujo de Bandera (PENDIENTE/PEDIDO/RECIBIDO) — las fechas
 *  correspondientes se completan solas del lado del backend. */
export async function marcarEstadoBanderaProducto(idProducto: number, estadoBandera: EstadoBandera): Promise<void> {
  await api.put(`/productos/${idProducto}/bandera/estado`, { estadoBandera });
}
