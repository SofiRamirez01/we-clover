export interface RolOption {
  id: number;
  nombre: string;
  descripcion: string | null;
}

export interface UsuarioCreateRequest {
  nombre: string;
  email: string;
  telefono?: string;
  idRol: number;
  /** enum_id de la opción "Vendedor" de Kommo; null = sin asignar. */
  kommoVendedorEnumId: number | null;
}

export interface UsuarioUpdateRequest {
  nombre: string;
  email: string;
  telefono?: string;
  idRol: number;
  kommoVendedorEnumId: number | null;
}

/** Opción del campo "Vendedor" de los leads de Kommo (GET /api/kommo/vendedores). */
export interface VendedorKommo {
  enumId: number;
  nombre: string;
}

export interface UsuarioResponse {
  id: number;
  nombre: string;
  email: string;
  telefono: string | null;
  idRol: number;
  rol: string;
  rolDescripcion: string | null;
  habilitado: boolean;
  kommoVendedorEnumId: number | null;
}
