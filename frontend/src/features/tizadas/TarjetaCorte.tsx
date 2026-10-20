import { useState } from 'react';
import type { CorteResultado } from '../../types/corte';
import DibujoTizada from './DibujoTizada';
import { cm, colorDePieza, duracion, largoTela, porColor, porcentaje, total } from './formato';

/** Lo que se dibujó de esa pieza y talle en la tizada (el algoritmo decide, en las piezas
 *  simétricas, si van enteras o como mitades al doblez). */
function modoPorCapa(corte: CorteResultado, item: CorteResultado['items'][number]): string {
  const piezas = corte.piezas.filter((p) => p.idPieza === item.idPieza && p.idTalle === item.idTalle);
  const enteras = piezas.filter((p) => p.modo === 'ENTERA').length;
  const mitades = piezas.length - enteras;
  const partes: string[] = [];
  if (enteras) partes.push(`${enteras} entera${enteras > 1 ? 's' : ''}`);
  if (mitades) partes.push(`${mitades} mitad${mitades > 1 ? 'es' : ''} al doblez`);
  return partes.join(' + ') || '—';
}

/** Un corte: datos, capas por color, el dibujo de la tizada y qué piezas salen de él. */
export default function TarjetaCorte({ corte }: { corte: CorteResultado }) {
  const [abierta, setAbierta] = useState(true);
  const excedente = corte.items.reduce((suma, item) => suma + total(item.excedente), 0);

  return (
    <section className="flex flex-col gap-4 rounded-lg border border-wc-border bg-white p-4">
      <header className="flex flex-wrap items-start justify-between gap-3">
        <div className="flex flex-col gap-1">
          <h3 className="m-0 text-base font-semibold text-wc-text">
            Corte {corte.numero} · {corte.nombreTela}
          </h3>
          <div className="flex flex-wrap items-center gap-1.5">
            {corte.capasPorColor.map((capa) => (
              <span key={capa.idPaletaColor} className="rounded-full bg-wc-bg px-2.5 py-0.5 text-xs font-semibold text-wc-text">
                {capa.nombreColor ?? `Color ${capa.idPaletaColor}`}: {capa.capas} capa{capa.capas > 1 ? 's' : ''}
              </span>
            ))}
            {corte.estado === 'LIMITE_DE_TIEMPO' && (
              <span
                className="rounded-full bg-amber-100 px-2.5 py-0.5 text-xs font-semibold text-amber-900"
                title="El algoritmo usó todo el tiempo disponible: con más tiempo podría salir más corta"
              >
                Terminó por tiempo
              </span>
            )}
            {!corte.verificacionOk && (
              <span className="rounded-full bg-red-100 px-2.5 py-0.5 text-xs font-semibold text-red-800">
                ⚠ No pasó la verificación
              </span>
            )}
          </div>
        </div>
        <dl className="m-0 grid grid-cols-2 gap-x-6 gap-y-1 text-sm sm:grid-cols-4">
          <div>
            <dt className="text-xs text-wc-text-muted">Largo de la tizada</dt>
            <dd className="m-0 font-semibold text-wc-text">{largoTela(corte.largoUsadoCm)}</dd>
          </div>
          <div>
            <dt className="text-xs text-wc-text-muted">Tela (largo × capas)</dt>
            <dd className="m-0 font-semibold text-wc-text">{largoTela(corte.telaCm)}</dd>
          </div>
          <div>
            <dt className="text-xs text-wc-text-muted">Aprovechamiento</dt>
            <dd className="m-0 font-semibold text-wc-text">{porcentaje(corte.eficiencia)}</dd>
          </div>
          <div>
            <dt className="text-xs text-wc-text-muted">Piezas de más</dt>
            <dd className="m-0 font-semibold text-wc-text">{excedente}</dd>
          </div>
        </dl>
      </header>

      <DibujoTizada corte={corte} />

      <div className="flex items-center justify-between gap-3">
        <button
          type="button"
          onClick={() => setAbierta((valor) => !valor)}
          className="text-sm font-semibold text-wc-green hover:underline"
          aria-expanded={abierta}
        >
          {abierta ? 'Ocultar' : 'Ver'} piezas de este corte ({corte.items.length})
        </button>
        <span className="text-xs text-wc-text-muted">
          {corte.reinicios} corrida{corte.reinicios > 1 ? 's' : ''} en {duracion(corte.segundos)} · semilla ganadora{' '}
          {corte.semillaGanadora}
        </span>
      </div>

      {abierta && (
        <div className="overflow-x-auto">
          <table className="w-full border-collapse text-sm">
            <thead>
              <tr className="border-b border-wc-border text-left text-xs text-wc-text-muted">
                <th className="py-1.5 pr-3 font-semibold">Pieza</th>
                <th className="py-1.5 pr-3 font-semibold">Talle</th>
                <th className="py-1.5 pr-3 font-semibold" title="Piezas dibujadas en la tizada: cada entera sale 2 veces por capa (el tubo son 2 telas) y cada mitad al doblez, 1 vez">
                  En la tizada
                </th>
                <th className="py-1.5 pr-3 text-right font-semibold">Pedidas</th>
                <th className="py-1.5 pr-3 text-right font-semibold">Salen</th>
                <th className="py-1.5 text-right font-semibold">De más</th>
              </tr>
            </thead>
            <tbody>
              {corte.items.map((item) => (
                <tr key={`${item.idPieza}-${item.idTalle}`} className="border-b border-wc-border last:border-0">
                  <td className="py-1.5 pr-3">
                    <span className="inline-flex items-center gap-2">
                      <span className="h-3 w-3 shrink-0 rounded-sm border border-black/20" style={{ backgroundColor: colorDePieza(item.idPieza) }} aria-hidden="true" />
                      {item.nombre}
                    </span>
                  </td>
                  <td className="py-1.5 pr-3">T{item.talle}</td>
                  <td className="py-1.5 pr-3 text-wc-text-muted">{modoPorCapa(corte, item)}</td>
                  <td className="py-1.5 pr-3 text-right">{porColor(item.demanda)}</td>
                  <td className="py-1.5 pr-3 text-right font-semibold">{porColor(item.produccion)}</td>
                  <td className="py-1.5 text-right">{total(item.excedente) || '—'}</td>
                </tr>
              ))}
            </tbody>
          </table>
          {corte.advertencias.length > 0 && (
            <ul className="mt-2 list-disc pl-5 text-xs text-amber-900">
              {corte.advertencias.map((advertencia) => (
                <li key={advertencia}>{advertencia}</li>
              ))}
            </ul>
          )}
          <p className="mt-2 text-xs text-wc-text-muted">
            Estimado del planificador: {cm(corte.largoEstimadoCm)} · compactación final: {cm(corte.largoSinCompactarCm, 1)} →{' '}
            {cm(corte.largoUsadoCm, 1)}
          </p>
        </div>
      )}
    </section>
  );
}
