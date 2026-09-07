import { useState } from 'react';
import type { CargaTallesResponse } from '../../types/cargaTalles';

/** Copiar link: dos hojas superpuestas (la de atrás, apagada, sugiere "copiar") con un
 *  eslabón de cadena en la de adelante (sugiere "link"). Sin texto al lado — solo el ícono. */
const CopiarLinkIcon = () => (
  <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth={1.8} strokeLinecap="round" strokeLinejoin="round">
    <rect x="3" y="3" width="13" height="16" rx="2" opacity="0.45" />
    <rect x="8" y="6" width="13" height="16" rx="2" />
    <path d="M12.4 17.1a2 2 0 0 1 0-2.9l1-1a2 2 0 0 1 2.9 2.9" />
    <path d="M16.6 17.5a2 2 0 0 1 0 2.9l-1 1a2 2 0 0 1-2.9-2.9" />
  </svg>
);

const CheckIcon = () => (
  <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth={2.2} strokeLinecap="round" strokeLinejoin="round">
    <path d="M4 12.5 9.5 18 20 6" />
  </svg>
);

/** Candado abierto o cerrado dentro del mismo pill del estado — para que se note por intuición
 *  que tocarlo cambia el estado (abierto → se puede seguir cargando; cerrado → bloqueado). */
const LockIcon = ({ abierto }: { abierto: boolean }) => (
  <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth={2} strokeLinecap="round" strokeLinejoin="round">
    <rect x="3" y="11" width="18" height="10" rx="2" />
    {abierto ? <path d="M7 11V7a5 5 0 0 1 9.9-1" /> : <path d="M7 11V7a5 5 0 0 1 10 0v4" />}
  </svg>
);

interface CargaTallesFichaHeaderProps {
  idPedido: number;
  carga: CargaTallesResponse | null;
  cargando: boolean;
  accionando: boolean;
  error: string | null;
  onCerrarOReabrir: () => void;
}

/** Barra compacta con el link de carga de talles: el estado (ABIERTO/CERRADO) es un botón — al
 *  tocarlo cierra o reabre la carga, con un candado abierto/cerrado adentro y un tooltip que
 *  explica la acción — y al lado un botón de solo ícono para copiar el link (nunca se muestra la
 *  URL completa). "Ver detalle" abre el detalle alumno por alumno en una pestaña aparte, no en
 *  esta misma tarjeta. */
export default function CargaTallesFichaHeader({
  idPedido,
  carga,
  cargando,
  accionando,
  error,
  onCerrarOReabrir,
}: CargaTallesFichaHeaderProps) {
  const [copiado, setCopiado] = useState(false);

  if (cargando) {
    return <p className="text-[11px] text-wc-text-muted">Cargando talles…</p>;
  }
  if (!carga) {
    return <p className="text-[11px] text-wc-text-muted">No se pudo cargar el link de talles.</p>;
  }

  function copiarLink() {
    if (!carga) return;
    const url = `${window.location.origin}/carga-talles/${carga.token}`;
    navigator.clipboard.writeText(url).then(() => {
      setCopiado(true);
      setTimeout(() => setCopiado(false), 2000);
    });
  }

  function verDetalle() {
    window.open(`/fichas-tecnicas/pedidos/${idPedido}/talles`, '_blank', 'noopener');
  }

  const abierta = carga.estado === 'ABIERTO';

  return (
    <div className="flex flex-col gap-1">
      <div className="flex flex-wrap items-center gap-1.5">
        <span className="text-[11px] font-semibold text-wc-text-muted">Link de talles:</span>

        <button
          type="button"
          onClick={onCerrarOReabrir}
          disabled={accionando}
          title={abierta ? 'Presioná para cerrar' : 'Presioná para abrir'}
          className={`inline-flex items-center gap-1 rounded-full px-2 py-1 text-[11px] font-semibold transition disabled:cursor-wait disabled:opacity-60 ${
            abierta ? 'bg-wc-green/10 text-wc-green hover:bg-wc-green/20' : 'bg-wc-text-muted/10 text-wc-text-muted hover:bg-wc-text-muted/20'
          }`}
        >
          <LockIcon abierto={abierta} />
          {accionando ? '…' : abierta ? 'Abierta' : 'Cerrada'}
        </button>

        <button
          type="button"
          onClick={copiarLink}
          title="Copiar link de carga de talles"
          aria-label="Copiar link de carga de talles"
          className="inline-flex items-center justify-center rounded-md p-1.5 text-wc-text-muted transition hover:bg-wc-bg hover:text-wc-text"
        >
          {copiado ? <CheckIcon /> : <CopiarLinkIcon />}
        </button>

        <button
          type="button"
          onClick={verDetalle}
          className="ml-auto text-[11px] font-semibold text-wc-green underline"
        >
          Ver detalle
        </button>
      </div>
      {error && <p className="text-[11px] font-medium text-red-600">{error}</p>}
    </div>
  );
}
