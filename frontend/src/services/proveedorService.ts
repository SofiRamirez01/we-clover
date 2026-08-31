import api from './api';
import type {
  ArticuloProveedorRequest,
  ArticuloProveedorResponse,
  ArticuloProveedorUpdateRequest,
  ProveedorRequest,
  ProveedorResponse,
} from '../types/proveedor';

export async function listarProveedores(soloActivos = true): Promise<ProveedorResponse[]> {
  const { data } = await api.get<ProveedorResponse[]>('/proveedores', {
    params: soloActivos ? { activo: true } : undefined,
  });
  return data;
}

export async function crearProveedor(payload: ProveedorRequest): Promise<ProveedorResponse> {
  const { data } = await api.post<ProveedorResponse>('/proveedores', payload);
  return data;
}

export async function agregarArticuloProveedor(
  idProveedor: number,
  payload: ArticuloProveedorRequest,
): Promise<ArticuloProveedorResponse> {
  const { data } = await api.post<ArticuloProveedorResponse>(`/proveedores/${idProveedor}/articulos`, payload);
  return data;
}

export async function listarArticulosPorColor(idPaletaColor: number): Promise<ArticuloProveedorResponse[]> {
  const { data } = await api.get<ArticuloProveedorResponse[]>('/articulos-proveedor', {
    params: { idPaletaColor },
  });
  return data;
}

export async function actualizarArticuloProveedor(
  id: number,
  payload: ArticuloProveedorUpdateRequest,
): Promise<ArticuloProveedorResponse> {
  const { data } = await api.put<ArticuloProveedorResponse>(`/articulos-proveedor/${id}`, payload);
  return data;
}
