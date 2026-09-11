import api from './api';
import type { PatronCortePosicionPiezaPayload, PatronCortePosicionPiezaResponse } from '../types/patronCorte';

export async function agregarPosicionPieza(
  idPatronCorte: number,
  idColor: number,
  payload: PatronCortePosicionPiezaPayload,
): Promise<PatronCortePosicionPiezaResponse> {
  const { data } = await api.post<PatronCortePosicionPiezaResponse>(
    `/patrones-corte/${idPatronCorte}/colores/${idColor}/piezas`,
    payload,
  );
  return data;
}

export async function actualizarPosicionPieza(
  idPatronCorte: number,
  idColor: number,
  idPosicion: number,
  payload: PatronCortePosicionPiezaPayload,
): Promise<PatronCortePosicionPiezaResponse> {
  const { data } = await api.put<PatronCortePosicionPiezaResponse>(
    `/patrones-corte/${idPatronCorte}/colores/${idColor}/piezas/${idPosicion}`,
    payload,
  );
  return data;
}

export async function eliminarPosicionPieza(idPatronCorte: number, idColor: number, idPosicion: number): Promise<void> {
  await api.delete(`/patrones-corte/${idPatronCorte}/colores/${idColor}/piezas/${idPosicion}`);
}
