import type { CantidadPorColorCorte } from '../../types/corte';

/** Un color por pieza (por id, nunca por nombre), suave para que se lean los bordes y el texto. */
const PALETA_PIEZAS = [
  '#a7d3c0', '#f6c89f', '#b8c7f0', '#f3b6c2', '#d8c2ef', '#f1e2a0',
  '#a9dce3', '#c9dca2', '#f2bfa5', '#c3c7cc', '#e6c8a8', '#b5e0b5',
];

export function colorDePieza(idPieza: number): string {
  return PALETA_PIEZAS[idPieza % PALETA_PIEZAS.length];
}

export function cm(valor: number | null | undefined, decimales = 0): string {
  if (valor === null || valor === undefined) return '—';
  return `${valor.toLocaleString('es-AR', { maximumFractionDigits: decimales })} cm`;
}

/** Largos de tela: en metros a partir de 1 m, que es como se habla en planta. */
export function largoTela(valorCm: number): string {
  if (valorCm < 100) return cm(valorCm);
  return `${(valorCm / 100).toLocaleString('es-AR', { minimumFractionDigits: 2, maximumFractionDigits: 2 })} m`;
}

export function porcentaje(valor: number | null | undefined): string {
  if (valor === null || valor === undefined) return '—';
  return `${(valor * 100).toLocaleString('es-AR', { maximumFractionDigits: 1 })} %`;
}

export function duracion(segundos: number | null | undefined): string {
  if (segundos === null || segundos === undefined) return '—';
  const total = Math.round(segundos);
  const minutos = Math.floor(total / 60);
  const resto = total % 60;
  return minutos > 0 ? `${minutos} min ${resto.toString().padStart(2, '0')} s` : `${resto} s`;
}

export function total(cantidades: CantidadPorColorCorte[]): number {
  return cantidades.reduce((suma, c) => suma + c.cantidad, 0);
}

/** "Verde 4 · Natural 2", o solo el número si hay un color. */
export function porColor(cantidades: CantidadPorColorCorte[]): string {
  const conValor = cantidades.filter((c) => c.cantidad > 0);
  if (conValor.length <= 1) return String(total(cantidades));
  return conValor.map((c) => `${c.nombreColor ?? `#${c.idPaletaColor}`} ${c.cantidad}`).join(' · ');
}
