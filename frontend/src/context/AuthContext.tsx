import { createContext, useCallback, useContext, useMemo, useState } from 'react';
import type { ReactNode } from 'react';
import { login as loginRequest } from '../services/authService';
import { extraerMensajeError } from '../utils/errores';
import type { LoginResponse } from '../types/auth';

const STORAGE_KEY = 'weclover.usuario';

interface AuthContextValue {
  usuario: LoginResponse | null;
  cargando: boolean;
  error: string | null;
  login: (email: string, password: string) => Promise<void>;
  logout: () => void;
}

const AuthContext = createContext<AuthContextValue | null>(null);

function leerUsuarioGuardado(): LoginResponse | null {
  const crudo = localStorage.getItem(STORAGE_KEY);
  if (!crudo) return null;
  try {
    return JSON.parse(crudo) as LoginResponse;
  } catch {
    return null;
  }
}

export function AuthProvider({ children }: { children: ReactNode }) {
  const [usuario, setUsuario] = useState<LoginResponse | null>(leerUsuarioGuardado);
  const [cargando, setCargando] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const login = useCallback(async (email: string, password: string) => {
    setCargando(true);
    setError(null);
    try {
      const usuarioAutenticado = await loginRequest({ email, password });
      localStorage.setItem(STORAGE_KEY, JSON.stringify(usuarioAutenticado));
      setUsuario(usuarioAutenticado);
    } catch (err) {
      setError(extraerMensajeError(err, 'No se pudo iniciar sesión. Intentá nuevamente.'));
      throw err;
    } finally {
      setCargando(false);
    }
  }, []);

  const logout = useCallback(() => {
    localStorage.removeItem(STORAGE_KEY);
    setUsuario(null);
  }, []);

  const value = useMemo(
    () => ({ usuario, cargando, error, login, logout }),
    [usuario, cargando, error, login, logout],
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth(): AuthContextValue {
  const ctx = useContext(AuthContext);
  if (!ctx) {
    throw new Error('useAuth debe usarse dentro de un AuthProvider');
  }
  return ctx;
}
