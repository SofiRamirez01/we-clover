export interface LoginRequest {
  email: string;
  password: string;
}

export interface LoginResponse {
  id: number;
  nombre: string;
  email: string;
  rol: string;
}
