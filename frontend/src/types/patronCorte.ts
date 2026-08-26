export interface PatronCorteColorResponse {
  id: number;
  orden: number;
  gramos: number;
}

export interface PatronCorteResponse {
  id: number;
  numeroInterno: number;
  nombre: string;
  idTipoPrenda: number;
  tipoPrenda: string;
  imagenUrl: string;
  cantidadColores: number;
  activo: boolean;
  colores: PatronCorteColorResponse[];
}
