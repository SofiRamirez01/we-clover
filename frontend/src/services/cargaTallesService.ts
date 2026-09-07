import api from './api';
import type {
  AlumnoCreateRequest,
  AlumnoResponse,
  CargaTallesResponse,
  ComboResponse,
  ComboUpsertRequest,
  LinkCargaTallesResponse,
} from '../types/cargaTalles';

// ---------- Internas (desde Ficha Técnica) ----------

/** El link se genera solo al crear el pedido (ver PedidoService.crearPedido) — esta consulta
 *  nunca devuelve 404 por "todavía no generado": si por algún motivo faltara (pedido viejo, de
 *  antes de este cambio), el backend lo crea en el momento. */
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

/** El propio representante cierra la carga cuando ya terminó de cargar todo — el backend
 *  vuelve a validar que esté completo (no solo confía en el botón "Finalizar" deshabilitado
 *  del lado del cliente). El Vendedor/Administrativo sigue pudiendo cerrar/reabrir desde Ficha
 *  Técnica sin cambios. */
export async function finalizarCargaTalles(token: string): Promise<void> {
  await api.post(`/carga-talles/${token}/finalizar`);
}
