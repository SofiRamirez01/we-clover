import type { LoginResponse } from '../types/auth';

export const USUARIO_STORAGE_KEY = 'weclover.usuario';

export function leerUsuarioGuardado(): LoginResponse | null {
  const crudo = localStorage.getItem(USUARIO_STORAGE_KEY);
  if (!crudo) return null;
  try {
    return JSON.parse(crudo) as LoginResponse;
  } catch {
    return null;
  }
}
