/**
 * `codigo` estable del catálogo tipos_tela (backend) — para el nombre humano-legible real
 * (que puede cambiar en la base, ej. corregir una falta de ortografía) usar
 * `nombreTela(codigo, tiposTela)` de features/fichas-tecnicas/telaUtils.ts contra el catálogo
 * de GET /api/tipos-tela, no un mapa fijo acá.
 */
export type TipoTela = 'FRIZA' | 'JERSEY' | 'PIQUE' | 'SPUM' | 'CIERRE';

/** Telas reales de una prenda (excluye CIERRE, que solo categoriza colores de cierre). */
export const TIPOS_TELA_PRENDA: TipoTela[] = ['FRIZA', 'JERSEY', 'PIQUE', 'SPUM'];

export interface PaletaColorResponse {
  id: number;
  nombre: string;
  hex: string;
  tipoTela: TipoTela;
  activo: boolean;
}

export interface PaletaColorCreateRequest {
  nombre: string;
  hex: string;
  tipoTela: TipoTela;
}

export interface ProductoColorItemRequest {
  idPatronCorteColor: number;
  idPaletaColor: number;
  coordenadaX?: number;
  coordenadaY?: number;
  rgbDetectado?: string;
}

/** Insumo secundario de un producto puntual: cierre, capucha (Jersey), puños/cintura (Ribb), o
 *  cualquier otro agregado a mano — ver ProductoInsumoSecundario en el backend. */
export interface ProductoInsumoSecundarioResponse {
  id: number;
  /** Identifica de qué insumo se trata (ej. "Capucha", "Puños y cintura", "Cierre", o texto
   *  libre) — es la clave real de la fila, no idTipoTela: dos insumos distintos de un mismo
   *  producto pueden compartir la misma tela. */
  descripcion: string;
  idTipoTela: number;
  /** Código estable del tipo de tela (ej. "JERSEY", "RIBB", "CIERRE"), no el nombre humano. */
  tipoTela: string;
  nombreTipoTela: string;
  esPorPeso: boolean;
  idPaletaColor: number;
  nombreColor: string;
  hexColor: string;
  cantidad: number;
}

export interface ProductoInsumoSecundarioItemRequest {
  descripcion: string;
  idTipoTela: number;
  idPaletaColor: number;
  cantidad: number;
}
