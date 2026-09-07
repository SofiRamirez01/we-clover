import { useEffect, useState } from 'react';
import { listarPiezas } from '../../services/piezaService';
import type { PiezaResponse } from '../../types/pieza';

const VerIcon = () => (
  <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round">
    <circle cx="11" cy="11" r="7" />
    <path d="m21 21-4.3-4.3" />
  </svg>
);

const EditarIcon = () => (
  <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round">
    <path d="M12 20h9" />
    <path d="M16.5 3.5a2.1 2.1 0 0 1 3 3L7 19l-4 1 1-4Z" />
  </svg>
);

const DuplicarIcon = () => (
  <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round">
    <rect x="9" y="9" width="12" height="12" rx="2" />
    <path d="M5 15V5a2 2 0 0 1 2-2h10" />
  </svg>
);

interface PiezasListViewProps {
  onNueva: () => void;
  onVer: (id: number) => void;
  onEditar: (id: number) => void;
  onDuplicar: (id: number) => void;
  mensajeExito?: string | null;
}

export default function PiezasListView({ onNueva, onVer, onEditar, onDuplicar, mensajeExito }: PiezasListViewProps) {
  const [piezas, setPiezas] = useState<PiezaResponse[]>([]);
  const [estadoCarga, setEstadoCarga] = useState<'cargando' | 'listo' | 'error'>('cargando');

  useEffect(() => {
    let cancelado = false;
    listarPiezas()
      .then((data) => {
        if (cancelado) return;
        setPiezas(data);
        setEstadoCarga('listo');
      })
      .catch(() => {
        if (!cancelado) setEstadoCarga('error');
      });
    return () => {
      cancelado = true;
    };
  }, []);

  return (
    <div className="flex flex-col gap-6">
      {mensajeExito && (
        <div className="rounded-lg border border-wc-green/30 bg-wc-green/10 px-4 py-3 text-sm font-medium text-wc-green" role="status">
          {mensajeExito}
        </div>
      )}

      <div className="flex justify-end">
        <button
          type="button"
          onClick={onNueva}
          className="rounded-lg bg-wc-green/10 px-4 py-2 text-sm font-semibold text-wc-green transition hover:bg-wc-green/20"
        >
          + Nueva Pieza
        </button>
      </div>

      {estadoCarga === 'cargando' && <p className="text-sm text-wc-text-muted">Cargando piezas…</p>}
      {estadoCarga === 'error' && <p className="text-sm text-wc-text-muted">No se pudo cargar el listado de piezas.</p>}
      {estadoCarga === 'listo' && piezas.length === 0 && (
        <p className="text-sm text-wc-text-muted">Todavía no hay piezas cargadas.</p>
      )}

      {estadoCarga === 'listo' && piezas.length > 0 && (
        <div className="overflow-hidden rounded-xl border border-wc-border bg-white shadow-sm">
          <table className="w-full text-left text-sm">
            <thead className="bg-wc-bg text-xs font-semibold uppercase text-wc-text-muted">
              <tr>
                <th className="px-4 py-3">Nombre</th>
                <th className="px-4 py-3">Grupo de talle</th>
                <th className="px-4 py-3">Talle base</th>
                <th className="px-4 py-3 text-right">Ancho base</th>
                <th className="px-4 py-3 text-right">Largo base</th>
                <th className="px-4 py-3 text-right">Acciones</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-wc-border">
              {piezas.map((pieza) => (
                <tr key={pieza.id}>
                  <td className="px-4 py-3 font-semibold text-wc-text">{pieza.nombre}</td>
                  <td className="px-4 py-3 text-wc-text-muted">{pieza.nombreGrupoTalle}</td>
                  <td className="px-4 py-3 text-wc-text-muted">{pieza.talleBase}</td>
                  <td className="px-4 py-3 text-right text-wc-text-muted">{pieza.anchoBaseCm.toFixed(1)}cm</td>
                  <td className="px-4 py-3 text-right text-wc-text-muted">{pieza.largoBaseCm.toFixed(1)}cm</td>
                  <td className="px-4 py-3">
                    <div className="flex justify-end gap-2">
                      <button
                        type="button"
                        title="Ver"
                        aria-label="Ver"
                        onClick={() => onVer(pieza.id)}
                        className="inline-flex h-[30px] w-[30px] items-center justify-center rounded-lg border border-wc-border bg-white text-wc-text-muted transition hover:border-wc-green hover:bg-wc-green/10 hover:text-wc-green-dark"
                      >
                        <VerIcon />
                      </button>
                      <button
                        type="button"
                        title="Editar"
                        aria-label="Editar"
                        onClick={() => onEditar(pieza.id)}
                        className="inline-flex h-[30px] w-[30px] items-center justify-center rounded-lg border border-wc-border bg-white text-wc-text-muted transition hover:border-wc-green hover:bg-wc-green/10 hover:text-wc-green-dark"
                      >
                        <EditarIcon />
                      </button>
                      <button
                        type="button"
                        title="Duplicar"
                        aria-label="Duplicar"
                        onClick={() => onDuplicar(pieza.id)}
                        className="inline-flex h-[30px] w-[30px] items-center justify-center rounded-lg border border-wc-border bg-white text-wc-text-muted transition hover:border-wc-green hover:bg-wc-green/10 hover:text-wc-green-dark"
                      >
                        <DuplicarIcon />
                      </button>
                    </div>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </div>
  );
}
