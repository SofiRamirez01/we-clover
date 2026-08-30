import type { TipoTela } from '../../types/paletaColores';
import type { ProductoResponse } from '../../types/pedido';

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
