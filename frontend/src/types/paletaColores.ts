export type TipoTela = 'FRIZA' | 'JERSEY' | 'PIQUE' | 'SPUM' | 'CIERRE';

export const TIPO_TELA_LABELS: Record<TipoTela, string> = {
  FRIZA: 'Friza',
  JERSEY: 'Jersey',
  PIQUE: 'Piqué',
  SPUM: 'Spum',
  CIERRE: 'Cierre',
};

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
