import type { TipoTela } from '../../types/paletaColores';
import type { ProductoResponse } from '../../types/pedido';
import type { TipoTelaCatalogo } from '../../types/tipoTela';

export const TIPO_PRENDA_CAMPERA = 'Campera';
export const TIPO_PRENDA_BUZO = 'Buzo';

/** Mismo default que PedidoService.TIPO_TELA_POR_DEFECTO en el backend: se usa acá solo para
 * PRESELECCIONAR visualmente cuando el producto todavía no tiene tipoTela guardado (legacy). */
const TELA_POR_DEFECTO_SEGUN_PRENDA: Partial<Record<string, TipoTela>> = {
  Buzo: 'FRIZA',
  Remera: 'JERSEY',
  Chomba: 'PIQUE',
  Campera: 'FRIZA',
  Bandera: 'SPUM',
};

export function telaEfectiva(producto: ProductoResponse): TipoTela | null {
  if (producto.tipoTela) return producto.tipoTela;
  if (producto.tipoPrenda) return TELA_POR_DEFECTO_SEGUN_PRENDA[producto.tipoPrenda] ?? null;
  return null;
}

/**
 * Nombre humano-legible de una tela (ej. "Frisa"), leído del catálogo real (`tipos_tela` /
 * GET /api/tipos-tela) en vez de un mapa de labels hardcodeado en el front — ese mapa viejo
 * (`TIPO_TELA_LABELS` en types/paletaColores.ts) quedaba desactualizado apenas alguien corregía
 * el `nombre` de una fila en la base (ej. "Friza" → "Frisa") sin tocar código y redeployar.
 * `codigo` sigue siendo la clave estable para comparaciones/filtros — esto es solo para mostrar.
 */
export function nombreTela(codigo: TipoTela, catalogo: TipoTelaCatalogo[]): string {
  return catalogo.find((t) => t.codigo === codigo)?.nombre ?? codigo;
}

export interface EstadoDiseno {
  completo: boolean;
  faltantes: string[];
}

/** Completitud de la ficha técnica de una prenda: imagen cargada, tela definida, todos los
 *  colores por posición asignados (solo si el producto tiene posiciones, ej. ya tiene moldería)
 *  y color de cierre asignado (solo Camperas). Puramente derivado de datos ya existentes en
 *  ProductoResponse — no depende de ningún campo nuevo. */
export function estadoDiseno(producto: ProductoResponse): EstadoDiseno {
  const faltantes: string[] = [];

  if (!producto.imagenDisenoUrl) faltantes.push('Imagen');
  if (!telaEfectiva(producto)) faltantes.push('Tela');

  const posiciones = producto.patronCorteColores ?? [];
  const faltaAlgunColor = posiciones.some(
    (posicion) => !producto.colores.some((c) => c.idPatronCorteColor === posicion.id),
  );
  if (posiciones.length > 0 && faltaAlgunColor) faltantes.push('Colores');

  if (producto.tipoPrenda === TIPO_PRENDA_CAMPERA && !producto.nombreColorCierre) faltantes.push('Cierre');

  return { completo: faltantes.length === 0, faltantes };
}
