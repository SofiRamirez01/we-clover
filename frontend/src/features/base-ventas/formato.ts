import { BUCKET_POR_ESTADO } from '../../types/pedido';
import type {
  BucketEstadoPedido,
  CampoOrdenPedidos,
  ContadorEstadoPedido,
  DireccionOrden,
  PedidoResponse,
} from '../../types/pedido';

export const TIPOS_BUZO_CAMPERA = ['Buzo', 'Campera'];

/** Columna y sentido elegidos con las flechitas del encabezado (ver EncabezadoOrdenable). */
export interface OrdenListado {
  campo: CampoOrdenPedidos;
  direccion: DireccionOrden;
}

/** Primer clic en una columna: de menor a mayor (A→Z, más viejo primero). Otro clic en la
 *  misma columna: al revés. */
export function siguienteOrden(actual: OrdenListado | null, campo: CampoOrdenPedidos): OrdenListado {
  if (actual?.campo === campo) {
    return { campo, direccion: actual.direccion === 'ASC' ? 'DESC' : 'ASC' };
  }
  return { campo, direccion: 'ASC' };
}

export function sumarUnidadesPorTipos(pedido: PedidoResponse, tipos: string[]): number {
  return pedido.productos
    .filter((p) => p.tipoPrenda && tipos.includes(p.tipoPrenda))
    .reduce((acc, p) => acc + p.cantidadTotal, 0);
}

export interface Contadores {
  total: number;
  pendiente: number;
  en_produccion: number;
  entregado: number;
  cancelado: number;
}

/** Agrupa los contadores por estado del backend en las categorías del tablero. "total" no suma
 *  cancelado a propósito (un pedido cancelado no cuenta como venta), ver BUCKET_POR_ESTADO. */
export function contarPorBucket(
  contadores: ContadorEstadoPedido[],
  valor: (contador: ContadorEstadoPedido) => number,
): Contadores {
  const acc: Record<BucketEstadoPedido, number> = { pendiente: 0, en_produccion: 0, entregado: 0, cancelado: 0 };
  for (const contador of contadores) {
    acc[BUCKET_POR_ESTADO[contador.estado]] += valor(contador);
  }
  return { ...acc, total: acc.pendiente + acc.en_produccion + acc.entregado };
}

export function unidadesDeTipos(contador: ContadorEstadoPedido, tipos: string[]): number {
  return tipos.reduce((acc, tipo) => acc + (contador.unidadesPorTipoPrenda[tipo] ?? 0), 0);
}

export function formatearMoneda(valor: number): string {
  return valor.toLocaleString('es-AR', { minimumFractionDigits: 0, maximumFractionDigits: 0 });
}

export function formatearFecha(iso: string): string {
  const [anio, mes, dia] = iso.split('-');
  return `${dia}/${mes}/${anio}`;
}

/** Las promociones que existen, más la del año actual aunque todavía no tenga pedidos (es la
 *  que el Histórico muestra por defecto), de la más nueva a la más vieja. */
export function promocionesDisponibles(promociones: number[], anioActual: number): number[] {
  return Array.from(new Set([...promociones, anioActual])).sort((a, b) => b - a);
}
