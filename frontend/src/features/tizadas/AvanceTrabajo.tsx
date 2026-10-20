import type { TrabajoCorte } from '../../types/corte';
import { cm, duracion, porcentaje } from './formato';

interface AvanceTrabajoProps {
  trabajo: TrabajoCorte;
  puedeCancelar: boolean;
  cancelando: boolean;
  onCancelar: () => void;
}

/** Avance en vivo de un trabajo en cola o en curso, con la opción de cancelarlo. */
export default function AvanceTrabajo({ trabajo, puedeCancelar, cancelando, onCancelar }: AvanceTrabajoProps) {
  const p = trabajo.progreso;
  const enCurso = trabajo.estado === 'EJECUTANDO';
  const cortes = p?.cortesTotales ?? 0;
  const actual = p?.corteActual ?? 0;
  const fraccionCorte = p?.generacionesTotales ? Math.min(1, (p.generacionActual ?? 0) / p.generacionesTotales) : 0;
  const avance = cortes > 0 ? Math.min(1, (Math.max(actual - 1, 0) + fraccionCorte) / cortes) : 0;

  let titulo: string;
  if (trabajo.estado === 'EN_COLA') {
    titulo = `En cola${trabajo.posicionEnCola ? ` (posición ${trabajo.posicionEnCola})` : ''}: arranca cuando termine el trabajo anterior`;
  } else if (cortes === 0) {
    titulo = 'Planificando los cortes…';
  } else {
    titulo = `Corte ${actual} de ${cortes}`;
  }

  return (
    <section className="flex flex-col gap-3 rounded-lg border border-wc-border bg-white p-4" aria-live="polite">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div className="flex items-center gap-3">
          <span className="relative flex h-3 w-3" aria-hidden="true">
            <span className="absolute inline-flex h-full w-full animate-ping rounded-full bg-wc-green opacity-60" />
            <span className="relative inline-flex h-3 w-3 rounded-full bg-wc-green" />
          </span>
          <h3 className="m-0 text-base font-semibold text-wc-text">{titulo}</h3>
        </div>
        {puedeCancelar && (
          <button
            type="button"
            onClick={onCancelar}
            disabled={cancelando}
            className="rounded-md border border-red-300 bg-white px-3 py-1.5 text-sm font-semibold text-red-700 hover:bg-red-50 disabled:opacity-50"
          >
            {cancelando ? 'Cancelando…' : 'Cancelar'}
          </button>
        )}
      </div>

      <div className="h-2 w-full overflow-hidden rounded-full bg-wc-bg">
        <div className="h-full rounded-full bg-wc-green transition-all duration-700" style={{ width: `${Math.round(avance * 100)}%` }} />
      </div>

      {enCurso && cortes > 0 && (
        <dl className="m-0 grid grid-cols-2 gap-x-6 gap-y-1 text-sm sm:grid-cols-5">
          <div>
            <dt className="text-xs text-wc-text-muted">Intento</dt>
            <dd className="m-0 font-semibold text-wc-text">{p?.corridaActual || '—'}</dd>
          </div>
          <div>
            <dt className="text-xs text-wc-text-muted">Generación</dt>
            <dd className="m-0 font-semibold text-wc-text">
              {p?.generacionActual
                ? `${p.generacionActual} de ${p.generacionesTotales ?? '—'}`
                : 'Armando las primeras ubicaciones…'}
            </dd>
          </div>
          <div>
            <dt className="text-xs text-wc-text-muted">Mejor largo hasta ahora</dt>
            <dd className="m-0 font-semibold text-wc-text">{cm(p?.mejorLargoCm)}</dd>
          </div>
          <div>
            <dt className="text-xs text-wc-text-muted">Aprovechamiento</dt>
            <dd className="m-0 font-semibold text-wc-text">{porcentaje(p?.mejorEficiencia)}</dd>
          </div>
          <div>
            <dt className="text-xs text-wc-text-muted">Tiempo</dt>
            <dd className="m-0 font-semibold text-wc-text">{duracion(p?.segundosTranscurridos)}</dd>
          </div>
        </dl>
      )}
    </section>
  );
}
