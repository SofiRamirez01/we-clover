import { useEffect, useState } from 'react';
import { listarNotasPedido } from '../../services/seguimientoService';
import { extraerMensajeError } from '../../utils/errores';
import type { NotaPedido, ProduccionPedidoResponse } from '../../types/produccion';
import { formatearFechaCorta } from './tandaVisual';

interface PanelSeguimientoPedidoProps {
  pedido: ProduccionPedidoResponse;
  /** Solo ROLE_ADMINISTRATIVO edita la ubicación; las notas las escribe cualquiera que vea la
   *  pantalla (ADMINISTRATIVO y PLANTA). */
  puedeEditarUbicacion: boolean;
  onAgregarNota: (texto: string) => Promise<NotaPedido | null>;
  onCambiarUbicacion: (ubicacion: string) => Promise<boolean>;
}

/** Ubicación física y notas del pedido — se muestra al expandir la fila. Las notas se piden
 *  recién acá (la grilla solo trae la última). */
export default function PanelSeguimientoPedido({
  pedido,
  puedeEditarUbicacion,
  onAgregarNota,
  onCambiarUbicacion,
}: PanelSeguimientoPedidoProps) {
  const [notas, setNotas] = useState<NotaPedido[] | null>(null);
  const [errorNotas, setErrorNotas] = useState<string | null>(null);
  const [textoNota, setTextoNota] = useState('');
  const [guardandoNota, setGuardandoNota] = useState(false);

  const [editandoUbicacion, setEditandoUbicacion] = useState(false);
  const [ubicacion, setUbicacion] = useState(pedido.ubicacionActual ?? '');
  const [guardandoUbicacion, setGuardandoUbicacion] = useState(false);

  useEffect(() => {
    let cancelado = false;
    listarNotasPedido(pedido.id)
      .then((data) => {
        if (!cancelado) setNotas(data);
      })
      .catch((err) => {
        if (!cancelado) setErrorNotas(extraerMensajeError(err, 'No se pudieron cargar las notas.'));
      });
    return () => {
      cancelado = true;
    };
  }, [pedido.id]);

  async function guardarNota() {
    const texto = textoNota.trim();
    if (!texto || guardandoNota) return;
    setGuardandoNota(true);
    const nueva = await onAgregarNota(texto);
    setGuardandoNota(false);
    if (nueva) {
      setNotas((prev) => [nueva, ...(prev ?? [])]);
      setTextoNota('');
    }
  }

  async function guardarUbicacion() {
    if (guardandoUbicacion) return;
    setGuardandoUbicacion(true);
    const ok = await onCambiarUbicacion(ubicacion);
    setGuardandoUbicacion(false);
    if (ok) setEditandoUbicacion(false);
  }

  return (
    <div className="grid gap-4 border-t border-wc-border bg-wc-bg/60 px-3 py-3 md:grid-cols-[minmax(0,1fr)_minmax(0,2fr)]">
      <div className="flex flex-col gap-1.5">
        <span className="text-[11px] font-bold uppercase tracking-wide text-wc-text-muted">Ubicación actual</span>
        {editandoUbicacion ? (
          <div className="flex flex-wrap items-center gap-1.5">
            <input
              type="text"
              value={ubicacion}
              maxLength={255}
              autoFocus
              onChange={(e) => setUbicacion(e.target.value)}
              onKeyDown={(e) => {
                if (e.key === 'Enter') guardarUbicacion();
                if (e.key === 'Escape') setEditandoUbicacion(false);
              }}
              placeholder="Ej.: con Adrián, en estampado externo…"
              className="min-w-[10rem] flex-1 rounded-lg border border-wc-border bg-white px-2 py-1 text-sm text-wc-text"
            />
            <button
              type="button"
              onClick={guardarUbicacion}
              disabled={guardandoUbicacion}
              className="rounded-md bg-wc-green px-2.5 py-1 text-xs font-semibold text-white hover:bg-wc-green-dark disabled:opacity-60"
            >
              Guardar
            </button>
            <button
              type="button"
              onClick={() => {
                setUbicacion(pedido.ubicacionActual ?? '');
                setEditandoUbicacion(false);
              }}
              className="text-xs font-semibold text-wc-text-muted hover:text-wc-text"
            >
              Cancelar
            </button>
          </div>
        ) : (
          <div className="flex flex-wrap items-center gap-2">
            <span className={`text-sm ${pedido.ubicacionActual ? 'font-semibold text-wc-text' : 'text-wc-text-muted'}`}>
              {pedido.ubicacionActual ?? 'Sin ubicación cargada'}
            </span>
            {puedeEditarUbicacion && (
              <button
                type="button"
                onClick={() => {
                  setUbicacion(pedido.ubicacionActual ?? '');
                  setEditandoUbicacion(true);
                }}
                className="text-xs font-semibold text-wc-green underline"
              >
                {pedido.ubicacionActual ? 'Cambiar' : 'Cargar'}
              </button>
            )}
          </div>
        )}
      </div>

      <div className="flex flex-col gap-1.5">
        <span className="text-[11px] font-bold uppercase tracking-wide text-wc-text-muted">Notas</span>
        <div className="flex items-start gap-1.5">
          <input
            type="text"
            value={textoNota}
            maxLength={1000}
            onChange={(e) => setTextoNota(e.target.value)}
            onKeyDown={(e) => {
              if (e.key === 'Enter') guardarNota();
            }}
            placeholder="Agregar una nota (ej.: falta bandera)…"
            className="flex-1 rounded-lg border border-wc-border bg-white px-2 py-1 text-sm text-wc-text"
          />
          <button
            type="button"
            onClick={guardarNota}
            disabled={guardandoNota || !textoNota.trim()}
            className="rounded-md bg-wc-green px-2.5 py-1 text-xs font-semibold text-white hover:bg-wc-green-dark disabled:cursor-not-allowed disabled:opacity-60"
          >
            Agregar
          </button>
        </div>
        {errorNotas ? (
          <p className="text-xs font-medium text-red-600">{errorNotas}</p>
        ) : notas === null ? (
          <p className="text-xs text-wc-text-muted">Cargando notas…</p>
        ) : notas.length === 0 ? (
          <p className="text-xs text-wc-text-muted">Este pedido todavía no tiene notas.</p>
        ) : (
          <ul className="m-0 flex max-h-40 list-none flex-col gap-1 overflow-y-auto p-0">
            {notas.map((nota) => (
              <li key={nota.id} className="rounded border border-wc-border bg-white px-2 py-1 text-sm text-wc-text">
                {nota.texto}
                <span className="ml-2 text-[11px] text-wc-text-muted">
                  {nota.nombreAutor} · {formatearFechaCorta(nota.fecha)}
                </span>
              </li>
            ))}
          </ul>
        )}
      </div>
    </div>
  );
}
