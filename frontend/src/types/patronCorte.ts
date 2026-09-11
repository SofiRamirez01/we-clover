import type { TipoPrendaOption } from './pedido';
import type { PiezaResumenResponse } from './pieza';

/** Un pin: una Pieza física puesta sobre la imagen del patrón para un color puntual. */
export interface PatronCortePosicionPiezaResponse {
  id: number;
  coordenadaXPin: number;
  coordenadaYPin: number;
  etiqueta: string | null;
  pieza: PiezaResumenResponse;
}

export interface PatronCortePosicionPiezaPayload {
  piezaId: number;
  coordenadaXPin: number;
  coordenadaYPin: number;
  etiqueta?: string | null;
}

export interface PatronCorteColorResponse {
  id: number;
  orden: number;
  gramos: number;
  piezas: PatronCortePosicionPiezaResponse[];
}

export interface PatronCorteResponse {
  id: number;
  numeroInterno: number;
  nombre: string;
  tiposPrenda: TipoPrendaOption[];
  imagenUrl: string;
  cantidadColores: number;
  activo: boolean;
  colores: PatronCorteColorResponse[];
  /** Derivado de tiposPrenda; null si no hay un único grupo de talle determinable (en ese caso
   *  no se le pueden asignar Piezas a los pines). */
  idGrupoTalle: number | null;
  nombreGrupoTalle: string | null;
}
