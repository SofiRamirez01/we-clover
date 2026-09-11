import api from './api';
import type { PiezaTalleBatchItemPayload, PiezaTalleResponse, PiezaTalleUpsertPayload } from '../types/pieza';

export async function listarTallesPieza(idPieza: number): Promise<PiezaTalleResponse[]> {
  const { data } = await api.get<PiezaTalleResponse[]>(`/piezas/${idPieza}/talles`);
  return data;
}

export async function guardarTallePieza(
  idPieza: number,
  idTalle: number,
  payload: PiezaTalleUpsertPayload,
): Promise<PiezaTalleResponse> {
  const { data } = await api.put<PiezaTalleResponse>(`/piezas/${idPieza}/talles/${idTalle}`, payload);
  return data;
}

export async function guardarTallesPiezaEnLote(
  idPieza: number,
  talles: PiezaTalleBatchItemPayload[],
): Promise<PiezaTalleResponse[]> {
  const { data } = await api.post<PiezaTalleResponse[]>(`/piezas/${idPieza}/talles/batch`, { talles });
  return data;
}

export async function revertirTallePieza(idPieza: number, idTalle: number): Promise<PiezaTalleResponse> {
  const { data } = await api.post<PiezaTalleResponse>(`/piezas/${idPieza}/talles/${idTalle}/revertir`);
  return data;
}
