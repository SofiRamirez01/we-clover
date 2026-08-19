import axios from 'axios';
import { leerUsuarioGuardado } from '../utils/authStorage';

const api = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8080/api',
  headers: {
    'Content-Type': 'application/json',
  },
});

// No hay JWT todavía (ver doc/pantallas-pendientes.md): mientras tanto, identificamos
// al usuario logueado ante el backend con este header para que pueda validar permisos
// (ej. quién puede editar/eliminar usuarios). Reemplazar por un Authorization: Bearer
// real en cuanto exista sesión de verdad.
api.interceptors.request.use((config) => {
  const usuario = leerUsuarioGuardado();
  if (usuario) {
    config.headers['X-Usuario-Id'] = String(usuario.id);
  }
  return config;
});

export default api;
