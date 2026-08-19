import api from './api';
import type { RolOption, UsuarioCreateRequest, UsuarioResponse, UsuarioUpdateRequest } from '../types/usuario';

export async function crearUsuario(payload: UsuarioCreateRequest): Promise<UsuarioResponse> {
  const { data } = await api.post<UsuarioResponse>('/usuarios', payload);
  return data;
}

export async function listarRolesCorporativos(): Promise<RolOption[]> {
  const { data } = await api.get<RolOption[]>('/roles/corporativos');
  return data;
}

export async function listarUsuariosCorporativos(): Promise<UsuarioResponse[]> {
  const { data } = await api.get<UsuarioResponse[]>('/usuarios/corporativos');
  return data;
}

export async function actualizarUsuario(id: number, payload: UsuarioUpdateRequest): Promise<UsuarioResponse> {
  const { data } = await api.put<UsuarioResponse>(`/usuarios/${id}`, payload);
  return data;
}

export async function eliminarUsuario(id: number): Promise<void> {
  await api.delete(`/usuarios/${id}`);
}
