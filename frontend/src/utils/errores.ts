export function extraerMensajeError(error: unknown, fallback: string): string {
  if (error && typeof error === 'object' && 'response' in error) {
    // @ts-expect-error -- acceso best-effort al cuerpo de error de axios
    const mensaje: unknown = error.response?.data?.mensaje;
    if (typeof mensaje === 'string') {
      return mensaje;
    }
  }
  return fallback;
}
