import type { EstadoProduccion } from '../../types/produccion';

/**
 * Colores de los gráficos de Reportes, en un solo lugar para que tarjetas y gráficos usen
 * siempre los mismos. Recharts pinta en SVG y necesita el valor hex, no una clase de Tailwind.
 */

/** Serie única (barras de ventas, terminadas por mes): el verde de marca (--color-wc-green). */
export const COLOR_SERIE = '#025939';

/** Grilla y ejes: un paso por encima de la superficie, que no compita con el dato. */
export const COLOR_GRILLA = '#e4e4e0';

/** Texto de ejes y etiquetas: tinta atenuada (--color-wc-text-muted), nunca el color del dato. */
export const COLOR_TEXTO_EJE = '#6b7268';

/** Fondo del cursor al pasar sobre una barra. */
export const COLOR_CURSOR = '#f5f6f3';

/**
 * Paleta fija por estado de producción: un mismo estado tiene siempre el mismo color en las
 * tarjetas y en la torta, y el color no cambia aunque un filtro oculte otros estados.
 *
 * El reparto de matices no es estético. En la torta, un estado sin unidades no tiene porción, así
 * que cualquier par de estados puede quedar pegado, y con 8 matices no existe un reparto donde
 * todos los pares se distingan bien (también con daltonismo). Se eligió, midiendo la diferencia
 * de color de cada par, el que mejor separa a los estados cercanos en el circuito, y se dejaron
 * los dos matices más conflictivos (naranja y rosa) para ESTAMPADO y OJAL, los estados que no
 * aplican a todas las prendas y casi siempre están en 0. Por eso la torta lleva siempre al lado
 * la lista con nombre y cantidad: la identidad no depende solo del color. Si se agrega o
 * reordena un estado, volver a medirlo.
 *
 * ENTREGADO va en gris neutro a propósito: ya salió de planta y no aparece en la torta.
 */
export const COLORES_ESTADO_PRODUCCION: Record<EstadoProduccion, string> = {
  PENDIENTE: '#2a78d6',
  CORTADO: '#1baf7a',
  ESTAMPADO: '#eb6834',
  BORDADO: '#eda100',
  CONFECCION: '#4a3aa7',
  APODO: '#e34948',
  OJAL: '#e87ba4',
  TERMINADO: '#008300',
  ENTREGADO: '#8a8f88',
};
