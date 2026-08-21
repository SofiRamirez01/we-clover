import { useEffect, useLayoutEffect, useRef, useState } from 'react';
import type { FormEvent } from 'react';
import './CambiarEstadoPopover.css';
import { cambiarEstadoPedido } from '../services/pedidoService';
import { extraerMensajeError } from '../utils/errores';
import { BUCKET_POR_ESTADO, ESTADOS_PEDIDO, ESTADO_PEDIDO_LABELS } from '../types/pedido';
import type { EstadoPedido, PedidoResponse } from '../types/pedido';

function pad(n: number): string {
  return String(n).padStart(2, '0');
}

function fechaHoraActualLocal(): string {
  const ahora = new Date();
  return `${ahora.getFullYear()}-${pad(ahora.getMonth() + 1)}-${pad(ahora.getDate())}T${pad(ahora.getHours())}:${pad(ahora.getMinutes())}`;
}

interface CambiarEstadoPopoverProps {
  pedido: PedidoResponse;
  onCambiado: (pedidoActualizado: PedidoResponse) => void;
}

export default function CambiarEstadoPopover({ pedido, onCambiado }: CambiarEstadoPopoverProps) {
  const [abierto, setAbierto] = useState(false);
  const [estado, setEstado] = useState<EstadoPedido>(pedido.estadoActual);
  const [fechaCambio, setFechaCambio] = useState(fechaHoraActualLocal());
  const [observaciones, setObservaciones] = useState('');
  const [enviando, setEnviando] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [triggerRect, setTriggerRect] = useState<DOMRect | null>(null);
  const [estilo, setEstilo] = useState<{ top: number; left: number } | null>(null);

  const contenedorRef = useRef<HTMLDivElement>(null);
  const triggerRef = useRef<HTMLButtonElement>(null);
  const panelRef = useRef<HTMLDivElement>(null);

  useLayoutEffect(() => {
    if (!abierto || !triggerRect || !panelRef.current) return;
    const margen = 8;
    const panel = panelRef.current.getBoundingClientRect();

    let top = triggerRect.bottom + 6;
    if (top + panel.height > window.innerHeight - margen) {
      top = triggerRect.top - panel.height - 6;
    }
    top = Math.max(margen, Math.min(top, window.innerHeight - panel.height - margen));

    let left = triggerRect.right - panel.width;
    left = Math.max(margen, Math.min(left, window.innerWidth - panel.width - margen));

    setEstilo({ top, left });
  }, [abierto, triggerRect]);

  useEffect(() => {
    if (!abierto) return;
    function alClickearFuera(e: MouseEvent) {
      if (contenedorRef.current && !contenedorRef.current.contains(e.target as Node)) {
        setAbierto(false);
      }
    }
    function alScrollear() {
      setAbierto(false);
    }
    document.addEventListener('mousedown', alClickearFuera);
    window.addEventListener('scroll', alScrollear, true);
    window.addEventListener('resize', alScrollear);
    return () => {
      document.removeEventListener('mousedown', alClickearFuera);
      window.removeEventListener('scroll', alScrollear, true);
      window.removeEventListener('resize', alScrollear);
    };
  }, [abierto]);

  function abrir() {
    const rect = triggerRef.current?.getBoundingClientRect();
    setTriggerRect(rect ?? null);
    setEstilo(null);
    setEstado(pedido.estadoActual);
    setFechaCambio(fechaHoraActualLocal());
    setObservaciones('');
    setError(null);
    setAbierto(true);
  }

  async function handleSubmit(e: FormEvent) {
    e.preventDefault();
    setEnviando(true);
    setError(null);
    try {
      const actualizado = await cambiarEstadoPedido(pedido.id, {
        estado,
        fechaCambio,
        observaciones: observaciones.trim() || undefined,
      });
      onCambiado(actualizado);
      setAbierto(false);
    } catch (err) {
      setError(extraerMensajeError(err, 'No se pudo cambiar el estado. Intentá nuevamente.'));
    } finally {
      setEnviando(false);
    }
  }

  return (
    <div className="cambiar-estado-popover" ref={contenedorRef}>
      <button
        type="button"
        ref={triggerRef}
        className={`estado-badge estado-badge--${BUCKET_POR_ESTADO[pedido.estadoActual]} estado-badge--clickeable`}
        onClick={abrir}
      >
        {ESTADO_PEDIDO_LABELS[pedido.estadoActual]}
      </button>

      {abierto && (
        <div
          ref={panelRef}
          className="cambiar-estado-popover-panel"
          style={
            estilo
              ? { top: estilo.top, left: estilo.left, visibility: 'visible' }
              : { top: 0, left: 0, visibility: 'hidden' }
          }
        >
          <form onSubmit={handleSubmit}>
            <h3>Cambiar estado del pedido</h3>

            <label>
              Nuevo estado
              <select value={estado} onChange={(e) => setEstado(e.target.value as EstadoPedido)}>
                {ESTADOS_PEDIDO.map((valor) => (
                  <option key={valor} value={valor}>
                    {ESTADO_PEDIDO_LABELS[valor]}
                  </option>
                ))}
              </select>
            </label>

            <label>
              Fecha y hora
              <input
                type="datetime-local"
                value={fechaCambio}
                onChange={(e) => setFechaCambio(e.target.value)}
                required
              />
            </label>

            <label>
              Observaciones
              <textarea
                value={observaciones}
                onChange={(e) => setObservaciones(e.target.value)}
                placeholder="Notas sobre el cambio de estado (opcional)"
                rows={3}
              />
            </label>

            {error && (
              <div className="alerta alerta--error cambiar-estado-popover-error" role="status">
                {error}
              </div>
            )}

            <div className="cambiar-estado-popover-acciones">
              <button type="button" onClick={() => setAbierto(false)} disabled={enviando}>
                Cancelar
              </button>
              <button type="submit" className="btn-guardar" disabled={enviando}>
                {enviando ? 'Guardando…' : 'Guardar'}
              </button>
            </div>
          </form>
        </div>
      )}
    </div>
  );
}
