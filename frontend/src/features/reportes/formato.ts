const FORMATO_ARS = new Intl.NumberFormat('es-AR', {
  style: 'currency',
  currency: 'ARS',
  maximumFractionDigits: 0,
});

const FORMATO_ENTERO = new Intl.NumberFormat('es-AR', { maximumFractionDigits: 0 });

const MESES_CORTOS = ['ene', 'feb', 'mar', 'abr', 'may', 'jun', 'jul', 'ago', 'sep', 'oct', 'nov', 'dic'];
const MESES_LARGOS = [
  'Enero', 'Febrero', 'Marzo', 'Abril', 'Mayo', 'Junio',
  'Julio', 'Agosto', 'Septiembre', 'Octubre', 'Noviembre', 'Diciembre',
];

/** $ 6.645.000 — sin centavos: los montos de pedidos son siempre enteros grandes. */
export function formatoARS(monto: number): string {
  return FORMATO_ARS.format(monto);
}

export function formatoEntero(numero: number): string {
  return FORMATO_ENTERO.format(numero);
}

/** "2026-08" → "ago 26" (para ticks de eje, donde no entra el nombre completo). */
export function mesCorto(mes: string): string {
  const [anio, numero] = mes.split('-').map(Number);
  return `${MESES_CORTOS[numero - 1]} ${String(anio).slice(2)}`;
}

/** "2026-08" → "Agosto 2026" (para tooltips y tablas). */
export function mesLargo(mes: string): string {
  const [anio, numero] = mes.split('-').map(Number);
  return `${MESES_LARGOS[numero - 1]} ${anio}`;
}

function pad(n: number): string {
  return String(n).padStart(2, '0');
}

export function aISO(fecha: Date): string {
  return `${fecha.getFullYear()}-${pad(fecha.getMonth() + 1)}-${pad(fecha.getDate())}`;
}

/** Últimos N meses calendario terminando hoy: desde el día 1 de hace N-1 meses (mismo criterio
 *  que el default del backend para 12). */
export function rangoUltimosMeses(cantidadMeses: number, hoy: Date = new Date()): { desde: string; hasta: string } {
  const desde = new Date(hoy.getFullYear(), hoy.getMonth() - (cantidadMeses - 1), 1);
  return { desde: aISO(desde), hasta: aISO(hoy) };
}
