import { useEffect, useState } from 'react';
import { listarNotasPedido } from '../../services/seguimientoService';
import { extraerMensajeError } from '../../utils/errores';
import type { NotaPedido, ProduccionPedidoResponse } from '../../types/produccion';
import { formatearFechaCorta } from './tandaVisual';

interface PanelSeguimientoPedidoProps {
  pedido: ProduccionPedidoResponse;
  onAgregarNota: (texto: string) => Promise<NotaPedido | null>;
}

/** Notas del pedido — se muestra al expandir la fila. Las notas se piden recién acá (la grilla
 *  solo trae la última). Las escribe cualquiera que vea la pantalla (ADMINISTRATIVO y PLANTA). */
export default function PanelSeguimientoPedido({ pedido, onAgregarNota }: PanelSeguimientoPedidoProps) {
  const [notas, setNotas] = useState<NotaPedido[] | null>(null);
  const [errorNotas, setErrorNotas] = useState<string | null>(null);
  const [textoNota, setTextoNota] = useState('');
  const [guardandoNota, setGuardandoNota] = useState(false);

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

  return (
    <div className="flex flex-col gap-1.5 border-t border-wc-border bg-wc-bg/60 px-3 py-3">
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
          placeholder="Agregar una nota (ej.: falta bandera, está con Adrián)…"
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
  );
}
