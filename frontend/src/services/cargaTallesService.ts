import api from './api';
import type {
  AlumnoCreateRequest,
  AlumnoResponse,
  CargaTallesResponse,
  ComboResponse,
  ComboUpsertRequest,
  LinkCargaTallesResponse,
} from '../types/cargaTalles';

// ---------- Internas (autenticadas, desde el detalle del Pedido) ----------

export async function generarOObtenerLinkCargaTalles(idPedido: number): Promise<LinkCargaTallesResponse> {
  const { data } = await api.post<LinkCargaTallesResponse>(`/pedidos/${idPedido}/carga-talles`);
  return data;
}

export async function obtenerCargaTallesInterno(idPedido: number): Promise<CargaTallesResponse> {
  const { data } = await api.get<CargaTallesResponse>(`/pedidos/${idPedido}/carga-talles`);
  return data;
}

export async function cerrarCargaTalles(idPedido: number): Promise<LinkCargaTallesResponse> {
  const { data } = await api.post<LinkCargaTallesResponse>(`/pedidos/${idPedido}/carga-talles/cerrar`);
  return data;
}

export async function reabrirCargaTalles(idPedido: number): Promise<LinkCargaTallesResponse> {
  const { data } = await api.post<LinkCargaTallesResponse>(`/pedidos/${idPedido}/carga-talles/reabrir`);
  return data;
}

// ---------- Públicas (sin login, por token) ----------

export async function obtenerCargaTallesPorToken(token: string): Promise<CargaTallesResponse> {
  const { data } = await api.get<CargaTallesResponse>(`/carga-talles/${token}`);
  return data;
}

export async function agregarAlumno(token: string, request: AlumnoCreateRequest): Promise<AlumnoResponse> {
  const { data } = await api.post<AlumnoResponse>(`/carga-talles/${token}/alumnos`, request);
  return data;
}

export async function agregarUnidadCombo(token: string, idAlumno: number, idProducto: number): Promise<ComboResponse> {
  const { data } = await api.post<ComboResponse>(`/carga-talles/${token}/alumnos/${idAlumno}/productos/${idProducto}`);
  return data;
}

export async function actualizarCombo(token: string, idCombo: number, request: ComboUpsertRequest): Promise<ComboResponse> {
  const { data } = await api.put<ComboResponse>(`/carga-talles/${token}/combos/${idCombo}`, request);
  return data;
}

export async function eliminarAlumno(token: string, idAlumno: number): Promise<void> {
  await api.delete(`/carga-talles/${token}/alumnos/${idAlumno}`);
}
