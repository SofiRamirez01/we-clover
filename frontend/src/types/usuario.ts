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
}

export interface UsuarioUpdateRequest {
  nombre: string;
  email: string;
  telefono?: string;
  idRol: number;
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
}
