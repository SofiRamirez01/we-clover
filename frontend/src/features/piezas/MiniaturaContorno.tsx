import type { Punto } from '../../types/pieza';

interface MiniaturaContornoProps {
  coordenadas: Punto[];
  /** Lado del cuadrado en px (el SVG es siempre cuadrado, ver viewBox). Default 56 (h-14/w-14). */
  size?: number;
}

/** Miniatura SVG de un contorno ya tesselado (mismo formato que PiezaTalle.coordenadasJson) —
 * reusada por GraduacionTalleView (tabla de talles) y por el picker de Piezas (Parte 4). */
export default function MiniaturaContorno({ coordenadas, size = 56 }: MiniaturaContornoProps) {
  if (coordenadas.length === 0) {
    return (
      <div
        style={{ height: size, width: size }}
        className="flex shrink-0 items-center justify-center text-[10px] text-wc-text-muted"
      >
        —
      </div>
    );
  }
  const xs = coordenadas.map(([x]) => x);
  const ys = coordenadas.map(([, y]) => -y);
  const minX = Math.min(...xs);
  const maxX = Math.max(...xs);
  const minY = Math.min(...ys);
  const maxY = Math.max(...ys);
  const margen = Math.max(maxX - minX, maxY - minY, 1) * 0.12;
  const viewBox = `${minX - margen} ${minY - margen} ${maxX - minX + margen * 2} ${maxY - minY + margen * 2}`;
  const d = `M ${coordenadas.map(([x, y]) => `${x} ${-y}`).join(' L ')} Z`;
  const grosor = Math.max((maxX - minX + maxY - minY) / 120, 0.15);
  return (
    <svg viewBox={viewBox} style={{ height: size, width: size }} className="shrink-0">
      <path d={d} fill="#2f855a22" stroke="#2f855a" strokeWidth={grosor} />
    </svg>
  );
}
