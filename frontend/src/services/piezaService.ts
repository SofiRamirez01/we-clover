import api from './api';
import type { CalculoBaseResponse, PiezaDetalleResponse, PiezaResponse, Segmento } from '../types/pieza';

export async function calcularBasePieza(segmentos: Segmento[]): Promise<CalculoBaseResponse> {
  // simetrica no afecta este cálculo (área/ancho/largo/perímetro se computan sobre el
  // polígono tal cual) — se manda fija en false solo porque el body de Java la requiere.
  const { data } = await api.post<CalculoBaseResponse>('/piezas/calcular-base', { segmentos, simetrica: false });
  return data;
}

export async function crearPieza(
  nombre: string,
  idGrupoTalle: number,
  idTalleBase: number,
  segmentos: Segmento[],
  simetrica: boolean,
): Promise<PiezaResponse> {
  const { data } = await api.post<PiezaResponse>('/piezas', {
    nombre,
    idGrupoTalle,
    idTalleBase,
    segmentos,
    simetrica,
  });
  return data;
}

export async function listarPiezas(idGrupoTalle?: number): Promise<PiezaResponse[]> {
  const { data } = await api.get<PiezaResponse[]>('/piezas', {
    params: idGrupoTalle ? { idGrupoTalle } : {},
  });
  return data;
}

export async function obtenerPieza(id: number): Promise<PiezaDetalleResponse> {
  const { data } = await api.get<PiezaDetalleResponse>(`/piezas/${id}`);
  return data;
}

export async function actualizarPieza(
  id: number,
  nombre: string,
  idGrupoTalle: number,
  idTalleBase: number,
  segmentos: Segmento[],
  simetrica: boolean,
): Promise<PiezaResponse> {
  const { data } = await api.put<PiezaResponse>(`/piezas/${id}`, {
    nombre,
    idGrupoTalle,
    idTalleBase,
    segmentos,
    simetrica,
  });
  return data;
}
