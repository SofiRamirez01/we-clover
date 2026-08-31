import type { UnidadMedida } from './proveedor';

/** Fila de stock tal como la devuelve el backend (GET /api/stock). */
export interface StockResponse {
  id: number;
  idPaletaColor: number;
  nombreColor: string;
  hexColor: string;
  idTipoTela: number;
  codigoTipoTela: string;
  nombreTipoTela: string;
  /** Null = fila "sin proveedor" (proveedor desconocido). */
  idProveedor: number | null;
  nombreProveedor: string | null;
  cantidad: number;
  unidadMedida: UnidadMedida;
  fechaUltimaActualizacion: string;
  idActualizadoPor: number;
  nombreActualizadoPor: string;
}

export interface StockUpsertItem {
  idPaletaColor: number;
  idProveedor: number | null;
  cantidad: number;
}

export interface StockGuardarCambiosRequest {
  items: StockUpsertItem[];
}
