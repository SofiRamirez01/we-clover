import { useEffect, useState } from 'react';
import { listarPlanificacionesCompra } from '../../services/planificacionCompraService';
import { extraerMensajeError } from '../../utils/errores';
import type { PlanificacionCompraResponse } from '../../types/planificacionCompra';

interface PlanificacionesListViewProps {
  onNueva: () => void;
  onVerDetalle: (planificacion: PlanificacionCompraResponse) => void;
  onContinuarBorrador: (planificacion: PlanificacionCompraResponse) => void;
  mensajeExito?: string | null;
}

// Mismo ícono/estilo de botón que "Editar ficha de colores" en FichasTecnicasView.tsx.
const PencilIcon = () => (
  <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth={2} strokeLinecap="round" strokeLinejoin="round">
    <path d="M11 4H4a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2v-7" />
    <path d="M18.5 2.5a2.121 2.121 0 0 1 3 3L12 15l-4 1 1-4Z" />
  </svg>
);

function formatearFechaHora(iso: string): string {
  const fecha = new Date(iso);
  return fecha.toLocaleString('es-AR', { day: '2-digit', month: '2-digit', year: 'numeric', hour: '2-digit', minute: '2-digit' });
}

function formatearFecha(iso: string | null): string {
  if (!iso) return 'sin definir';
  const [anio, mes, dia] = iso.split('-');
  return `${dia}/${mes}/${anio}`;
}

export default function PlanificacionesListView({ onNueva, onVerDetalle, onContinuarBorrador, mensajeExito }: PlanificacionesListViewProps) {
  const [planificaciones, setPlanificaciones] = useState<PlanificacionCompraResponse[]>([]);
  const [estadoCarga, setEstadoCarga] = useState<'cargando' | 'listo' | 'error'>('cargando');
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let cancelado = false;
    listarPlanificacionesCompra()
      .then((data) => {
        if (!cancelado) {
          setPlanificaciones(data);
          setEstadoCarga('listo');
        }
      })
      .catch((err) => {
        if (!cancelado) {
          setError(extraerMensajeError(err, 'No se pudo cargar el listado de planificaciones.'));
          setEstadoCarga('error');
        }
      });
    return () => {
      cancelado = true;
    };
  }, []);

  return (
    <div className="flex flex-col gap-5">
      {mensajeExito && (
        <div className="rounded-lg border border-wc-green/30 bg-wc-green/10 px-4 py-3 text-sm font-medium text-wc-green" role="status">
          {mensajeExito}
        </div>
      )}

      <div className="flex items-center justify-between">
        <p className="text-xs text-wc-text-muted">
          {estadoCarga === 'listo' ? `${planificaciones.length} planificación(es)` : ' '}
        </p>
        <button
          type="button"
          onClick={onNueva}
          className="rounded-lg bg-wc-green/10 px-4 py-2 text-sm font-semibold text-wc-green transition hover:bg-wc-green/20"
        >
          + Nueva planificación
        </button>
      </div>

      {estadoCarga === 'cargando' && <p className="text-sm text-wc-text-muted">Cargando…</p>}
      {estadoCarga === 'error' && <p className="text-sm text-wc-text-muted">{error}</p>}
      {estadoCarga === 'listo' && planificaciones.length === 0 && (
        <p className="text-sm text-wc-text-muted">Todavía no se creó ninguna planificación de compra.</p>
      )}

      {estadoCarga === 'listo' && planificaciones.length > 0 && (
        <div className="flex flex-col gap-2">
          {planificaciones.map((p) => {
            const esBorrador = p.estado === 'BORRADOR';
            const clasesFila = `flex items-center justify-between gap-3 rounded-lg border bg-white p-3 text-left shadow-sm transition hover:border-wc-green ${
              esBorrador ? 'border-wc-green' : 'border-wc-border'
            }`;

            const contenido = (
              <>
                <div className="min-w-0 flex-1">
                  <div className="flex items-center gap-2">
                    <p className="truncate text-sm font-bold text-wc-text">{p.nombre || 'Sin nombre todavía'}</p>
                    {esBorrador && (
                      <span className="shrink-0 rounded-full bg-wc-green/10 px-2 py-0.5 text-[10px] font-bold text-wc-green">
                        Borrador
                      </span>
                    )}
                  </div>
                  <p className="text-xs text-wc-text-muted">
                    Entregas {formatearFecha(p.fechaDesde)} al {formatearFecha(p.fechaHasta)} · {p.cantidadProductos} producto
                    {p.cantidadProductos === 1 ? '' : 's'}
                  </p>
                </div>
                <div className="flex shrink-0 items-center gap-3">
                  <div className="text-right text-xs text-wc-text-muted">
                    <p>{p.nombreCreadoPor}</p>
                    <p>{formatearFechaHora(p.fechaCreacion)}</p>
                  </div>
                  {esBorrador && (
                    <button
                      type="button"
                      onClick={() => onContinuarBorrador(p)}
                      aria-label="Continuar editando borrador"
                      title="Continuar editando borrador"
                      className="flex h-7 w-7 shrink-0 items-center justify-center rounded-full border border-wc-border bg-white text-wc-text-muted shadow-sm transition hover:bg-wc-bg hover:text-wc-text"
                    >
                      <PencilIcon />
                    </button>
                  )}
                </div>
              </>
            );

            // Un borrador no es un <button> entero (el ícono de lápiz ya es el botón de acción,
            // y anidar un <button> dentro de otro <button> no es HTML válido) — una confirmada
            // sigue siendo la fila completa clickeable, como antes.
            return esBorrador ? (
              <div key={p.id} className={clasesFila}>
                {contenido}
              </div>
            ) : (
              <button key={p.id} type="button" onClick={() => onVerDetalle(p)} className={clasesFila}>
                {contenido}
              </button>
            );
          })}
        </div>
      )}
    </div>
  );
}
