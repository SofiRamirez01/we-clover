/** Catálogo de tipos de insumo secundario (backend: entidad TipoInsumo, GET /api/tipos-insumo). */
export interface TipoInsumoCatalogo {
  id: number;
  /** Clave estable (ej. "CAPUCHA", "PUNOS_CINTURA", "CIERRE", "ESTAMPADO"); `nombre` es el texto visible. */
  codigo: string;
  nombre: string;
  /** false para las marcas sin material (Estampado): sus filas no llevan tela, color ni cantidad. */
  consumeMaterial: boolean;
  idTipoTelaSugerido: number | null;
  codigoTipoTelaSugerido: string | null;
  /** true si las filas de este tipo solo pueden usar telas que se compran por peso. */
  soloTelasPorPeso: boolean;
}

export const CODIGO_TIPO_INSUMO_CAPUCHA = 'CAPUCHA';
export const CODIGO_TIPO_INSUMO_PUNOS_CINTURA = 'PUNOS_CINTURA';
export const CODIGO_TIPO_INSUMO_CIERRE = 'CIERRE';
export const CODIGO_TIPO_INSUMO_ESTAMPADO = 'ESTAMPADO';

/** Un pin de la moldería cuya pieza se corta en un insumo secundario (ver AsignacionInsumoPieza en el backend). */
export interface PinInsumoResponse {
  idPosicionPieza: number;
  /** Texto a mostrar: la etiqueta del pin o el nombre de la pieza, numerado si se repite. */
  etiqueta: string;
  idPieza: number;
  nombrePieza: string;
  /** Fila de insumo asignada explícitamente, o null. */
  idProductoInsumo: number | null;
}

export interface GrupoAsignacionInsumoResponse {
  idTipoInsumo: number;
  codigoTipoInsumo: string;
  nombreTipoInsumo: string;
  requiereAsignacion: boolean;
  idsProductoInsumo: number[];
  pines: PinInsumoResponse[];
  pinesSinAsignar: number;
}

export interface AsignacionesInsumoResponse {
  idProducto: number;
  grupos: GrupoAsignacionInsumoResponse[];
  pinesSinAsignar: number;
}

export interface AsignacionInsumoPiezaItemRequest {
  idPosicionPieza: number;
  idProductoInsumo: number;
}
