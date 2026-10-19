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
  /** Texto visible de la fila. Qué insumo es lo dice `codigoTipoInsumo`; la descripción solo
   *  identifica a los insumos libres (sin tipo). */
  descripcion: string;
  /** Tela, color y cantidad son null para una marca sin material (ej. Estampado). */
  idTipoTela: number | null;
  /** Código estable del tipo de tela (ej. "JERSEY", "RIBB", "CIERRE"), no el nombre humano. */
  tipoTela: string | null;
  nombreTipoTela: string | null;
  esPorPeso: boolean | null;
  idPaletaColor: number | null;
  nombreColor: string | null;
  hexColor: string | null;
  cantidad: number | null;
  /** Tipo del catálogo (ver TipoInsumoCatalogo); los tres en null para un insumo libre. */
  idTipoInsumo: number | null;
  codigoTipoInsumo: string | null;
  nombreTipoInsumo: string | null;
  /** true si la cantidad fue escrita a mano; false si es la sugerida por el reparto. */
  cantidadEditadaManualmente: boolean;
}

export interface ProductoInsumoSecundarioItemRequest {
  /** Id de la fila ya guardada, o null si es nueva. Las filas que no viajan se borran. */
  id: number | null;
  /** null = insumo libre, identificado por `descripcion`. */
  idTipoInsumo: number | null;
  descripcion: string | null;
  /** Tela, color y cantidad van en null solo para una marca sin material (Estampado). */
  idTipoTela: number | null;
  idPaletaColor: number | null;
  cantidad: number | null;
  cantidadEditadaManualmente: boolean;
}
