export type EstadoCargaTalles = 'ABIERTO' | 'CERRADO';

export interface LinkCargaTallesResponse {
  idPedido: number;
  token: string;
  estado: EstadoCargaTalles;
}

/** Una unidad (un "combo") de un Producto asignada a un alumno, con su medida si ya se cargó. */
export interface ComboResponse {
  id: number;
  idProducto: number;
  nombreTipoPrenda: string;
  anchoCm: number | null;
  largoCm: number | null;
  /** Etiqueta del talle ya calculado (ej. "12"), null si todavía no se cargó medida o si dio
   *  personalizado. */
  talle: string | null;
  personalizado: boolean;
  observacionPersonalizado: string | null;
}

export interface AlumnoResponse {
  id: number;
  nombreAlumno: string;
  orden: number;
  combos: ComboResponse[];
}

/** `tieneTalle = false` (ej. Bandera) no se ofrece como combo por alumno — es solo informativo
 *  en el resumen del pedido. `cantidadCargada` cuenta unidades cargadas por los alumnos (no
 *  alumnos distintos: uno puede aportar más de una del mismo Producto). */
export interface ProductoPedidoResumenResponse {
  idProducto: number;
  nombreTipoPrenda: string;
  cantidadTotal: number;
  tieneTalle: boolean;
  cantidadCargada: number;
  /** Cuál de `CargaTallesResponse.tablasTalle` usar para la vista previa de talle del lado del
   *  cliente — null si `tieneTalle` es false. */
  nombreGrupoTalle: string | null;
}

export interface FilaTablaTalleResponse {
  talle: string;
  anchoCm: number;
  largoCm: number;
}

/** Tabla de talles de referencia de un grupo (ej. "Chomba/Remera"), ordenada de menor a mayor. */
export interface GrupoTallaResponse {
  nombreGrupo: string;
  filas: FilaTablaTalleResponse[];
}

export interface CargaTallesResponse {
  idPedido: number;
  nombreColegio: string;
  curso: string;
  cantAlumnosPedido: number;
  estado: EstadoCargaTalles;
  token: string;
  tablasTalle: GrupoTallaResponse[];
  productos: ProductoPedidoResumenResponse[];
  alumnos: AlumnoResponse[];
}

export interface AlumnoCreateRequest {
  nombreAlumno: string;
}

export interface ComboUpsertRequest {
  anchoCm: number | null;
  largoCm: number | null;
  observacionPersonalizado: string | null;
}
