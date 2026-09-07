import { useRef, useState } from 'react';
import { clamparSagita, previsualizarArcoPorSagita, puntoPorSagita, sagitaDePunto } from './geometriaPieza';
import type { Punto } from '../../types/pieza';

interface ArcoSagitaEditorProps {
  p0: Punto;
  p1: Punto;
  sagita: number;
  onChange: (sagita: number) => void;
}

/**
 * Mini editor para definir la curvatura de un ARCO arrastrando un punto en vez de tipear un
 * radio y elegir un lado — pensado para arcos de círculos grandes, donde el radio/centro real
 * caen muy lejos de las medidas de la pieza y son imprácticos de medir a mano. Lo único que se
 * pide es "cuánto se separa el arco de la recta" (la sagita), que sí es medible sobre la tela.
 */
export default function ArcoSagitaEditor({ p0, p1, sagita, onChange }: ArcoSagitaEditorProps) {
  const svgRef = useRef<SVGSVGElement | null>(null);
  const [arrastrando, setArrastrando] = useState(false);

  const puntoHandle = puntoPorSagita(p0, p1, sagita);
  const curva = previsualizarArcoPorSagita(p0, p1, sagita);

  const cuerdaLargo = Math.hypot(p1[0] - p0[0], p1[1] - p0[1]) || 1;
  const puntosViewBox: Punto[] = [p0, p1, puntoHandle, ...(curva ?? [])];
  const xs = puntosViewBox.map(([x]) => x);
  const ys = puntosViewBox.map(([, y]) => -y);
  const minX = Math.min(...xs);
  const maxX = Math.max(...xs);
  const minY = Math.min(...ys);
  const maxY = Math.max(...ys);
  const margen = Math.max(maxX - minX, maxY - minY, cuerdaLargo * 0.3, 1) * 0.25 + 0.5;
  const viewBox = `${minX - margen} ${minY - margen} ${maxX - minX + margen * 2} ${maxY - minY + margen * 2}`;
  const grosorTrazo = Math.max((maxX - minX + margen * 2) / 120, 0.03);

  function puntoDesdeEvento(e: { clientX: number; clientY: number }): Punto | null {
    const svg = svgRef.current;
    if (!svg) return null;
    const screenCTM = svg.getScreenCTM();
    if (!screenCTM) return null;
    const pt = svg.createSVGPoint();
    pt.x = e.clientX;
    pt.y = e.clientY;
    const local = pt.matrixTransform(screenCTM.inverse());
    // El SVG dibuja con Y invertida (ver construcción del viewBox arriba), así que se revierte acá.
    return [local.x, -local.y];
  }

  function manejarMovimiento(e: React.PointerEvent<SVGSVGElement>) {
    if (!arrastrando) return;
    const puntoReal = puntoDesdeEvento(e);
    if (!puntoReal) return;
    // Recortado acá también: al arrastrar, el número mostrado tiene que quedar pegado al
    // límite (como si el handle chocara contra una pared), no seguir creciendo por su cuenta
    // mientras la curva ya dejó de moverse.
    onChange(clamparSagita(p0, p1, sagitaDePunto(p0, p1, puntoReal)));
  }

  function empezarArrastre(e: React.PointerEvent<SVGCircleElement>) {
    e.currentTarget.setPointerCapture(e.pointerId);
    setArrastrando(true);
  }

  function terminarArrastre(e: React.PointerEvent<SVGElement>) {
    if (e.currentTarget.hasPointerCapture?.(e.pointerId)) {
      e.currentTarget.releasePointerCapture(e.pointerId);
    }
    setArrastrando(false);
  }

  return (
    <svg
      ref={svgRef}
      viewBox={viewBox}
      onPointerMove={manejarMovimiento}
      onPointerUp={terminarArrastre}
      className="h-32 w-32 shrink-0 touch-none rounded-lg border border-wc-border bg-wc-bg"
    >
      <line x1={p0[0]} y1={-p0[1]} x2={p1[0]} y2={-p1[1]} stroke="#94a3b8" strokeDasharray={`${grosorTrazo * 3} ${grosorTrazo * 2}`} strokeWidth={grosorTrazo} />
      {curva && curva.length > 0 && (
        <path
          d={`M ${curva.map(([x, y]) => `${x} ${-y}`).join(' L ')}`}
          fill="none"
          stroke="#2f855a"
          strokeWidth={grosorTrazo * 1.5}
        />
      )}
      <circle cx={p0[0]} cy={-p0[1]} r={grosorTrazo * 2} fill="#64748b" />
      <circle cx={p1[0]} cy={-p1[1]} r={grosorTrazo * 2} fill="#64748b" />
      <circle
        cx={puntoHandle[0]}
        cy={-puntoHandle[1]}
        r={grosorTrazo * 3.5}
        fill="#2f855a"
        stroke="white"
        strokeWidth={grosorTrazo}
        className="cursor-grab active:cursor-grabbing"
        onPointerDown={empezarArrastre}
        onPointerUp={terminarArrastre}
      />
    </svg>
  );
}
