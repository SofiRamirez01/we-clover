// El backend sirve los archivos subidos (moldería, ficha técnica, etc.) como recurso
// estático fuera de /api (ver WebConfig.java), así que hay que armar la URL absoluta a mano.
const ORIGEN_API = (import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8080/api').replace(/\/api\/?$/, '');

export function urlArchivoSubido(rutaRelativa: string): string {
  return `${ORIGEN_API}${rutaRelativa}`;
}
