import { useEffect, useLayoutEffect, useRef, useState } from 'react';
import './AccionesPedidoMenu.css';
import type { PedidoResponse } from '../types/pedido';

interface AccionesPedidoMenuProps {
  pedido: PedidoResponse;
  onEditar: (pedido: PedidoResponse) => void;
  onVerHistorial: (pedido: PedidoResponse) => void;
}

export default function AccionesPedidoMenu({ pedido, onEditar, onVerHistorial }: AccionesPedidoMenuProps) {
  const [abierto, setAbierto] = useState(false);
  const [triggerRect, setTriggerRect] = useState<DOMRect | null>(null);
  const [estilo, setEstilo] = useState<{ top: number; left: number } | null>(null);

  const contenedorRef = useRef<HTMLDivElement>(null);
  const triggerRef = useRef<HTMLButtonElement>(null);
  const panelRef = useRef<HTMLDivElement>(null);

  useLayoutEffect(() => {
    if (!abierto || !triggerRect || !panelRef.current) return;
    const margen = 8;
    const panel = panelRef.current.getBoundingClientRect();

    let top = triggerRect.bottom + 4;
    if (top + panel.height > window.innerHeight - margen) {
      top = triggerRect.top - panel.height - 4;
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

  function alAbrir() {
    const rect = triggerRef.current?.getBoundingClientRect();
    setTriggerRect(rect ?? null);
    setEstilo(null);
    setAbierto((v) => !v);
  }

  return (
    <div className="acciones-pedido-menu" ref={contenedorRef}>
      <button
        type="button"
        ref={triggerRef}
        className="acciones-pedido-trigger"
        onClick={alAbrir}
        aria-label="Más opciones"
      >
        ⋮
      </button>

      {abierto && (
        <div
          ref={panelRef}
          className="acciones-pedido-panel"
          style={
            estilo
              ? { top: estilo.top, left: estilo.left, visibility: 'visible' }
              : { top: 0, left: 0, visibility: 'hidden' }
          }
        >
          <button
            type="button"
            onClick={() => {
              setAbierto(false);
              onEditar(pedido);
            }}
          >
            Editar
          </button>
          <button
            type="button"
            onClick={() => {
              setAbierto(false);
              onVerHistorial(pedido);
            }}
          >
            Historial de cambios
          </button>
        </div>
      )}
    </div>
  );
}
