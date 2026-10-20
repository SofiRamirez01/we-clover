import api from './api';
import type {
  OpcionesOptimizacion,
  ResumenPreparacionCorte,
  TrabajoCorte,
  TrabajoCorteCreado,
} from '../types/corte';

/** Demanda de la tanda lista para cortar (lo que entra y lo que queda pendiente). */
export async function obtenerPreparacionCorte(idTanda: number): Promise<ResumenPreparacionCorte> {
  const { data } = await api.get<ResumenPreparacionCorte>(`/tandas/${idTanda}/preparacion-corte`);
  return data;
}

/** Lanza la optimización (solo ROLE_ADMINISTRATIVO). Corre en segundo plano: devuelve el id. */
export async function optimizarCortes(idTanda: number, opciones: OpcionesOptimizacion): Promise<TrabajoCorteCreado> {
  const { data } = await api.post<TrabajoCorteCreado>(`/tandas/${idTanda}/cortes/optimizar`, opciones);
  return data;
}

export async function consultarTrabajoCorte(idTanda: number, idTrabajo: string): Promise<TrabajoCorte> {
  const { data } = await api.get<TrabajoCorte>(`/tandas/${idTanda}/cortes/trabajos/${idTrabajo}`);
  return data;
}

/** Solo ROLE_ADMINISTRATIVO; da 409 si el trabajo ya terminó. */
export async function cancelarTrabajoCorte(idTanda: number, idTrabajo: string): Promise<TrabajoCorte> {
  const { data } = await api.delete<TrabajoCorte>(`/tandas/${idTanda}/cortes/trabajos/${idTrabajo}`);
  return data;
}
