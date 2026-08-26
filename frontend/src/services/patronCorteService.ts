import api from './api';
import { urlArchivoSubido } from '../utils/urlArchivos';
import type { PatronCorteResponse } from '../types/patronCorte';

export const urlImagenPatronCorte = urlArchivoSubido;

export async function crearPatronCorte(
  numeroInterno: number,
  nombre: string,
  idTipoPrenda: number,
  imagen: File,
  gramosPorColor: number[],
): Promise<PatronCorteResponse> {
  const formData = new FormData();
  formData.append('numeroInterno', String(numeroInterno));
  formData.append('nombre', nombre);
  formData.append('idTipoPrenda', String(idTipoPrenda));
  formData.append('imagen', imagen);
  gramosPorColor.forEach((gramos) => formData.append('gramosPorColor', String(gramos)));

  const { data } = await api.postForm<PatronCorteResponse>('/patrones-corte', formData);
  return data;
}

export async function listarPatronesCorte(): Promise<PatronCorteResponse[]> {
  const { data } = await api.get<PatronCorteResponse[]>('/patrones-corte');
  return data;
}
