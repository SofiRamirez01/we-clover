export type UnidadMedida = 'KG' | 'UNIDAD';

export interface ProveedorResponse {
  id: number;
  cuit: string;
  nombre: string;
  activo: boolean;
}

export interface ProveedorRequest {
  cuit: string;
  nombre: string;
}

/** Artículo (color de la carta) que un proveedor puntual puede proveer, con su precio estimado. */
export interface ArticuloProveedorResponse {
  id: number;
  idProveedor: number;
  nombreProveedor: string;
  idPaletaColor: number;
  nombreColor: string;
  hexColor: string;
  tipoTela: string;
  unidadMedida: UnidadMedida;
  precioEstimado: number | null;
  /** A lo sumo un artículo preferido por color (lo hace cumplir el backend). */
  preferido: boolean;
  activo: boolean;
}

export interface ArticuloProveedorRequest {
  idPaletaColor: number;
  unidadMedida: UnidadMedida;
  precioEstimado?: number;
  preferido?: boolean;
}

export interface ArticuloProveedorUpdateRequest {
  unidadMedida: UnidadMedida;
  precioEstimado?: number;
  preferido: boolean;
  activo: boolean;
}

/** CUIT se guarda como 11 dígitos sin guiones (ver backend); esto es solo para mostrarlo. */
export function formatearCuit(cuit: string): string {
  if (!/^\d{11}$/.test(cuit)) return cuit;
  return `${cuit.slice(0, 2)}-${cuit.slice(2, 10)}-${cuit.slice(10)}`;
}
