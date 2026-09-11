import { useEffect, useState } from 'react';
import { listarPiezasResumen } from '../../services/piezaService';
import { extraerMensajeError } from '../../utils/errores';
import MiniaturaContorno from './MiniaturaContorno';
import PiezaForm from './PiezaForm';
import type { PiezaResumenResponse } from '../../types/pieza';

interface PiezaPickerPanelProps {
  /** A qué grupo de talle tienen que pertenecer las Piezas ofrecidas. */
  idGrupoTalle: number;
  onSeleccionar: (pieza: PiezaResumenResponse) => void;
  onCancelar: () => void;
}

/**
 * Buscador + miniaturas + alta rápida de Piezas (Requisito 4.1 Parte 4), embebido en línea
 * dentro de la tarjeta de un color (no como modal): todo el flujo de agregar/cambiar una pieza
 * queda en el mismo lugar de la pantalla, junto al listado de piezas de ese color.
 *
 * El alta rápida reusa PiezaForm tal cual (con el grupo de talle bloqueado en idGrupoTalle) en
 * vez de duplicar un mini-formulario: al guardar, vuelve al buscador para que el usuario elija
 * la pieza recién creada de la lista (más simple que intentar auto-seleccionarla).
 */
export default function PiezaPickerPanel({ idGrupoTalle, onSeleccionar, onCancelar }: PiezaPickerPanelProps) {
  const [modo, setModo] = useState<'buscar' | 'crear'>('buscar');
  const [q, setQ] = useState('');
  const [piezas, setPiezas] = useState<PiezaResumenResponse[]>([]);
  const [cargando, setCargando] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (modo !== 'buscar') return;
    let cancelado = false;
    setCargando(true);
    setError(null);
    const idTimeout = setTimeout(() => {
      listarPiezasResumen(idGrupoTalle, q)
        .then((data) => {
          if (cancelado) return;
          setPiezas(data);
          setCargando(false);
        })
        .catch((err) => {
          if (cancelado) return;
          setError(extraerMensajeError(err, 'No se pudieron cargar las piezas.'));
          setCargando(false);
        });
    }, 250);
    return () => {
      cancelado = true;
      clearTimeout(idTimeout);
    };
  }, [idGrupoTalle, q, modo]);

  return (
    <div className="mt-3 flex flex-col gap-3 rounded-lg border border-wc-border bg-wc-bg p-3">
      {modo === 'buscar' ? (
        <>
          <div className="flex items-center gap-2">
            <input
              autoFocus
              value={q}
              onChange={(e) => setQ(e.target.value)}
              placeholder="Buscar pieza por nombre…"
              className="flex-1 rounded-lg border border-wc-border bg-white px-3 py-1.5 text-sm text-wc-text outline-none focus:border-wc-green focus:ring-2 focus:ring-wc-green/20"
            />
            <button type="button" onClick={() => setModo('crear')} className="shrink-0 text-xs font-semibold text-wc-green underline">
              + Nueva
            </button>
          </div>

          {cargando && <p className="text-xs text-wc-text-muted">Buscando…</p>}
          {error && <p className="text-xs font-medium text-red-600">{error}</p>}
          {!cargando && !error && piezas.length === 0 && (
            <p className="text-xs text-wc-text-muted">No hay piezas que coincidan en este grupo de talle.</p>
          )}
          {!cargando && !error && piezas.length > 0 && (
            <div className="grid max-h-64 grid-cols-3 gap-2 overflow-y-auto sm:grid-cols-4">
              {piezas.map((pieza) => (
                <button
                  key={pieza.id}
                  type="button"
                  onClick={() => onSeleccionar(pieza)}
                  className="flex flex-col items-center gap-1 rounded-lg border border-wc-border bg-white p-2 text-center transition hover:border-wc-green hover:bg-wc-green/5"
                >
                  <MiniaturaContorno coordenadas={pieza.coordenadas} size={44} />
                  <span className="line-clamp-2 text-[11px] font-semibold text-wc-text">{pieza.nombre}</span>
                  {!pieza.graduacionCompleta && (
                    <span className="rounded-full bg-amber-50 px-1.5 py-0.5 text-[9px] font-semibold text-amber-700">Incompleta</span>
                  )}
                </button>
              ))}
            </div>
          )}

          <div className="flex justify-end border-t border-wc-border pt-2">
            <button type="button" onClick={onCancelar} className="text-xs font-semibold text-wc-text-muted underline">
              Cancelar
            </button>
          </div>
        </>
      ) : (
        <div className="flex flex-col gap-2">
          <div className="flex items-center justify-between">
            <span className="text-xs font-bold text-wc-text">Nueva pieza</span>
            <button type="button" onClick={() => setModo('buscar')} className="text-xs font-semibold text-wc-text-muted underline">
              ← Volver a buscar
            </button>
          </div>
          <PiezaForm modo="crear" idGrupoTalleFijo={idGrupoTalle} onGuardada={() => setModo('buscar')} />
        </div>
      )}
    </div>
  );
}
