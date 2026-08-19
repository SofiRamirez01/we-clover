export interface ProductoCreateRequest {
  tipoPrenda: string;
  cantidadTotal: number;
  costo: number;
  observaciones?: string;
  imagenDisenoUrl?: string;
}

export interface ProductoResponse {
  id: number;
  tipoPrenda: string;
  cantidadTotal: number;
  costo: number;
  subtotal: number;
  observaciones: string | null;
  imagenDisenoUrl: string | null;
}

export type EstadoPedido =
  | 'PRESUPUESTADO'
  | 'SENADO'
  | 'LISTO_PARA_PRODUCCION'
  | 'CORTADO'
  | 'BORDADO'
  | 'CONFECCIONADO'
  | 'EN_CONTROL'
  | 'TERMINADO'
  | 'ENTREGADO';

export interface PedidoCreateRequest {
  colegioNombre: string;
  colegioLocalidad?: string;
  colegioProvincia?: string;
  representanteNombre: string;
  representanteTelefono?: string;
  representanteEmail: string;
  idVendedor: number;
  codigoInterno: string;
  curso: string;
  cantAlumnos: number;
  observaciones?: string;
  estado: EstadoPedido;
  productos: ProductoCreateRequest[];
  pagoInicial: number;
}

export interface PedidoResponse {
  id: number;
  idColegio: number;
  nombreColegio: string;
  estadoActual: string;
  idRepresentanteCurso: number;
  nombreRepresentanteCurso: string;
  codigoInterno: string;
  curso: string;
  cantAlumnos: number;
  observaciones: string | null;
  fechaCreacion: string;
  fechaActualizacion: string;
  idVendedor: number;
  nombreVendedor: string;
  productos: ProductoResponse[];
  precioTotal: number;
  pagoInicial: number;
  saldo: number;
  porcentajePagado: number;
}
