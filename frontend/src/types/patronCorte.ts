import type { TipoPrendaOption } from './pedido';

export interface PatronCorteColorResponse {
  id: number;
  orden: number;
  gramos: number;
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
}
