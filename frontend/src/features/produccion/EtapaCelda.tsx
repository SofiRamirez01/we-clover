import { useState } from 'react';
import type { ProduccionEtapaResponse } from '../../types/produccion';
import type { UsuarioResumen } from '../../types/produccion';

const ComentarioIcon = () => (
  <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth={2} strokeLinecap="round" strokeLinejoin="round">
    <path d="M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z" />
  </svg>
);

interface EtapaCeldaProps {
  etapa: ProduccionEtapaResponse;
  empleados: UsuarioResumen[];
  guardando: boolean;
  onCambiar: (completado: boolean, idEmpleado: number | null, comentario?: string) => void;
}

/** Una celda de la grilla de etapas: "—" si no aplica a este producto, checkbox si aplica. Al
 *  tildarlo (o si ya estaba tildado) aparece debajo un selector chico de empleado, opcional.
 *  El checkbox guarda al instante; el ícono de comentario abre un campo para tildar o destildar
 *  dejando una aclaración (ej. "se repite bordado por falla"), que queda en el historial. */
export default function EtapaCelda({ etapa, empleados, guardando, onCambiar }: EtapaCeldaProps) {
  const [comentando, setComentando] = useState(false);
  const [comentario, setComentario] = useState('');

  if (!etapa.aplica) {
    return <span className="text-wc-text-muted">—</span>;
  }

  const accion = etapa.completado ? 'Desmarcar' : 'Marcar';

  function confirmarConComentario() {
    onCambiar(!etapa.completado, etapa.empleadoId, comentario);
    setComentando(false);
    setComentario('');
  }

  return (
    <div className="flex flex-col items-center gap-1">
      <div className="flex items-center gap-1">
        <input
          type="checkbox"
          checked={etapa.completado}
          disabled={guardando}
          onChange={(e) => onCambiar(e.target.checked, etapa.empleadoId)}
          className="h-4 w-4 accent-wc-green disabled:opacity-50"
        />
        <button
          type="button"
          disabled={guardando}
          onClick={() => setComentando((v) => !v)}
          title={`${accion} con comentario`}
          aria-label={`${accion} con comentario`}
          className={`rounded p-0.5 transition disabled:opacity-50 ${
            comentando ? 'text-wc-green' : 'text-wc-text-muted hover:text-wc-text'
          }`}
        >
          <ComentarioIcon />
        </button>
      </div>
      {comentando && (
        <div className="flex w-36 flex-col gap-1">
          <input
            type="text"
            value={comentario}
            maxLength={255}
            autoFocus
            onChange={(e) => setComentario(e.target.value)}
            onKeyDown={(e) => {
              if (e.key === 'Enter') confirmarConComentario();
              if (e.key === 'Escape') setComentando(false);
            }}
            placeholder="Comentario…"
            className="rounded border border-wc-border bg-white px-1 py-0.5 text-[11px] text-wc-text"
          />
          <button
            type="button"
            onClick={confirmarConComentario}
            className="rounded bg-wc-green px-1 py-0.5 text-[11px] font-semibold text-white hover:bg-wc-green-dark"
          >
            {accion}
          </button>
        </div>
      )}
      {etapa.completado && (
        <select
          value={etapa.empleadoId ?? ''}
          disabled={guardando}
          onChange={(e) => onCambiar(true, e.target.value ? Number(e.target.value) : null)}
          className="w-24 rounded border border-wc-border bg-white px-1 py-0.5 text-[11px] text-wc-text disabled:opacity-50"
        >
          <option value="">Sin asignar</option>
          {empleados.map((empleado) => (
            <option key={empleado.id} value={empleado.id}>
              {empleado.nombre}
            </option>
          ))}
        </select>
      )}
    </div>
  );
}
