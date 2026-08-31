import api from './api';
import type { PaletaColorResponse } from '../types/paletaColores';

/**
 * Variante de POST /paleta-colores con `tipoTela` como string libre (no el union `TipoTela`
 * acotado de types/paletaColores.ts) — la pantalla Carta de colores arma sus pestañas
 * dinámicamente desde el catálogo TipoTela, así que puede necesitar crear un color para un
 * tipo que ese union todavía no conoce (ej. uno agregado hoy mismo al catálogo).
 */
export async function crearColorCarta(payload: { nombre: string; hex: string; tipoTela: string }): Promise<PaletaColorResponse> {
  const { data } = await api.post<PaletaColorResponse>('/paleta-colores', payload);
  return data;
}
