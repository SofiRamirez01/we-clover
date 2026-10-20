import { useState } from 'react';
import type { CorteResultado, PiezaUbicada } from '../../types/corte';
import { colorDePieza } from './formato';

/**
 * Dibujo de una tizada como se ve sobre la mesa: el largo de la tela va de izquierda a derecha y
 * el ancho de arriba a abajo, con los dobleces del tubo arriba y abajo. Se rota 90° (no se
 * espeja) a partir de las coordenadas del resultado, donde x es el ancho e y el largo.
 */
type Escala = 'ajustar' | 2 | 4;
const ESCALAS: { valor: Escala; etiqueta: string }[] = [
  { valor: 'ajustar', etiqueta: 'Ajustar' },
  { valor: 2, etiqueta: '2×' },
  { valor: 4, etiqueta: '4×' },
];
const MARGEN = 6; // cm alrededor de la tela, para la regla
const TAMANO_TEXTO = 2.6; // cm

function puntos(pieza: PiezaUbicada, ancho: number): string {
  return pieza.poligonoCm.map(([x, y]) => `${y + MARGEN},${ancho - x + MARGEN}`).join(' ');
}

function caja(pieza: PiezaUbicada) {
  const xs = pieza.poligonoCm.map(([x]) => x);
  const ys = pieza.poligonoCm.map(([, y]) => y);
  return { minX: Math.min(...xs), maxX: Math.max(...xs), minY: Math.min(...ys), maxY: Math.max(...ys) };
}

function etiqueta(pieza: PiezaUbicada): string {
  const nombre = pieza.nombre.replace(/\s+Buzo\s+T\d+$/i, '').replace(/\s+T\d+-\d+$/i, '');
  return `${nombre} T${pieza.talle}`;
}

export default function DibujoTizada({ corte }: { corte: CorteResultado }) {
  const [escala, setEscala] = useState<Escala>('ajustar');
  const [resaltada, setResaltada] = useState<string | null>(null);
  const ancho = corte.anchoCm;
  const largo = Math.max(corte.largoUsadoCm, 10);
  const vistaAncho = largo + 2 * MARGEN;
  const vistaAlto = ancho + 2 * MARGEN;
  const marcas: number[] = [];
  for (let marca = 0; marca <= largo; marca += 50) marcas.push(marca);

  return (
    <div className="flex flex-col gap-2">
      <div className="flex items-center justify-between gap-3 text-xs text-wc-text-muted">
        <span>
          Largo {corte.largoUsadoCm.toLocaleString('es-AR', { maximumFractionDigits: 1 })} cm × ancho {ancho} cm (tubo:
          doblez arriba y abajo). Las piezas rayadas son mitades al doblez.
        </span>
        <div className="flex shrink-0 overflow-hidden rounded-md border border-wc-border" role="group" aria-label="Zoom">
          {ESCALAS.map((opcion) => (
            <button
              key={String(opcion.valor)}
              type="button"
              onClick={() => setEscala(opcion.valor)}
              className={`px-2.5 py-1 text-xs font-semibold ${
                escala === opcion.valor ? 'bg-wc-green text-white' : 'bg-white text-wc-text-muted hover:bg-wc-bg'
              }`}
            >
              {opcion.etiqueta}
            </button>
          ))}
        </div>
      </div>
      <div className="overflow-x-auto rounded-md border border-wc-border bg-wc-bg">
        <svg
          viewBox={`0 0 ${vistaAncho} ${vistaAlto}`}
          width={escala === 'ajustar' ? '100%' : vistaAncho * escala}
          height={escala === 'ajustar' ? undefined : vistaAlto * escala}
          role="img"
          aria-label={`Tizada del corte ${corte.numero}: ${corte.piezas.length} piezas en ${Math.round(corte.largoUsadoCm)} cm`}
          className="block"
        >
          <defs>
            <pattern id={`rayado-${corte.numero}`} width="2" height="2" patternUnits="userSpaceOnUse" patternTransform="rotate(45)">
              <line x1="0" y1="0" x2="0" y2="2" stroke="#1f2421" strokeOpacity="0.18" strokeWidth="0.6" />
            </pattern>
          </defs>
          <rect x={MARGEN} y={MARGEN} width={largo} height={ancho} fill="#ffffff" stroke="#9aa39b" strokeWidth={0.3} />
          <line x1={MARGEN} y1={MARGEN} x2={MARGEN + largo} y2={MARGEN} stroke="#025939" strokeWidth={0.5} strokeDasharray="2 1.2" />
          <line x1={MARGEN} y1={MARGEN + ancho} x2={MARGEN + largo} y2={MARGEN + ancho} stroke="#025939" strokeWidth={0.5} strokeDasharray="2 1.2" />
          {marcas.map((marca) => (
            <g key={marca}>
              <line x1={MARGEN + marca} y1={MARGEN + ancho} x2={MARGEN + marca} y2={MARGEN + ancho + 1.5} stroke="#9aa39b" strokeWidth={0.3} />
              <text x={MARGEN + marca} y={MARGEN + ancho + 4.5} fontSize={2.6} textAnchor="middle" fill="#6b7268">
                {marca}
              </text>
            </g>
          ))}
          {corte.piezas.map((pieza) => {
            const clave = `${pieza.idPieza}-${pieza.idTalle}`;
            const atenuada = resaltada !== null && resaltada !== clave;
            const c = caja(pieza);
            const anchoEnDibujo = c.maxY - c.minY;
            const altoEnDibujo = c.maxX - c.minX;
            const texto = etiqueta(pieza);
            const entraTexto = anchoEnDibujo > texto.length * TAMANO_TEXTO * 0.55 && altoEnDibujo > TAMANO_TEXTO * 1.6;
            const titulo = `${pieza.nombre} · T${pieza.talle} · copia ${pieza.copia} · ${
              pieza.modo === 'ENTERA' ? 'entera' : 'mitad al doblez'
            } · ${pieza.rotacion}°`;
            return (
              <g
                key={`${clave}-${pieza.copia}`}
                opacity={atenuada ? 0.25 : 1}
                onMouseEnter={() => setResaltada(clave)}
                onMouseLeave={() => setResaltada(null)}
              >
                <title>{titulo}</title>
                <polygon points={puntos(pieza, ancho)} fill={colorDePieza(pieza.idPieza)} stroke="#1f2421" strokeWidth={0.25} />
                {pieza.modo === 'MITAD_AL_DOBLEZ' && (
                  <polygon points={puntos(pieza, ancho)} fill={`url(#rayado-${corte.numero})`} stroke="none" />
                )}
                {entraTexto && (
                  <text
                    x={(c.minY + c.maxY) / 2 + MARGEN}
                    y={ancho - (c.minX + c.maxX) / 2 + MARGEN + TAMANO_TEXTO / 3}
                    fontSize={TAMANO_TEXTO}
                    textAnchor="middle"
                    fill="#1f2421"
                    pointerEvents="none"
                  >
                    {texto}
                  </text>
                )}
              </g>
            );
          })}
        </svg>
      </div>
    </div>
  );
}
