import { useEffect, useRef, useState } from 'react';
import type { ProduccionEtapaResponse } from '../../types/produccion';
import type { UsuarioResumen } from '../../types/produccion';

const ComentarioIcon = () => (
  <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth={2} strokeLinecap="round" strokeLinejoin="round">
    <path d="M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z" />
  </svg>
);

const PersonaIcon = () => (
  <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth={2} strokeLinecap="round" strokeLinejoin="round">
    <circle cx="12" cy="8" r="4" />
    <path d="M4 21a8 8 0 0 1 16 0" />
  </svg>
);

interface EtapaCeldaProps {
  etapa: ProduccionEtapaResponse;
  empleados: UsuarioResumen[];
  guardando: boolean;
  onCambiar: (completado: boolean, idEmpleado: number | null, comentario?: string) => void;
}

type Panel = 'comentario' | 'empleado' | null;

/**
 * Una celda de la grilla de etapas: "—" si no aplica a este producto; si aplica, un checkbox
 * (guarda al instante) y dos íconos al lado:
 * - comentario: abre un campo para tildar o destildar dejando una aclaración (ej. "se repite
 *   bordado por falla"), que queda en el historial;
 * - persona (solo con la etapa completada): despliega los empleados de planta para asignar
 *   quién la hizo. Queda en verde cuando hay alguien asignado (el nombre se ve al pasar el
 *   mouse y dentro de la lista).
 * Los dos paneles se abren dentro de la propia celda, no flotando: la tabla scrollea en
 * horizontal y un desplegable flotante quedaría recortado.
 */
export default function EtapaCelda({ etapa, empleados, guardando, onCambiar }: EtapaCeldaProps) {
  const [panel, setPanel] = useState<Panel>(null);
  const [comentario, setComentario] = useState('');
  const contenedorRef = useRef<HTMLDivElement>(null);

  // La lista de empleados se cierra sola al clickear afuera (el campo de comentario no: se
  // perdería lo escrito).
  useEffect(() => {
    if (panel !== 'empleado') return;
    function alClickearFuera(e: MouseEvent) {
      if (contenedorRef.current && !contenedorRef.current.contains(e.target as Node)) setPanel(null);
    }
    document.addEventListener('mousedown', alClickearFuera);
    return () => document.removeEventListener('mousedown', alClickearFuera);
  }, [panel]);

  if (!etapa.aplica) {
    return <span className="text-wc-text-muted">—</span>;
  }

  const accion = etapa.completado ? 'Desmarcar' : 'Marcar';
  const tituloEmpleado = etapa.empleadoNombre ? `Asignado a ${etapa.empleadoNombre}` : 'Asignar empleado';
  // Si se destilda la etapa con la lista abierta, la lista deja de tener sentido.
  const panelVisible: Panel = panel === 'empleado' && !etapa.completado ? null : panel;

  function alternar(cual: Exclude<Panel, null>) {
    setPanel((actual) => (actual === cual ? null : cual));
  }

  function confirmarConComentario() {
    onCambiar(!etapa.completado, etapa.empleadoId, comentario);
    setPanel(null);
    setComentario('');
  }

  function asignar(idEmpleado: number | null) {
    setPanel(null);
    if (idEmpleado !== etapa.empleadoId) onCambiar(true, idEmpleado);
  }

  const clasesOpcion = (seleccionada: boolean) =>
    `w-full truncate rounded px-1.5 py-1 text-left text-[11px] transition ${
      seleccionada ? 'bg-wc-green/10 font-semibold text-wc-green' : 'text-wc-text hover:bg-wc-bg'
    }`;

  return (
    <div ref={contenedorRef} className="flex flex-col items-center gap-1">
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
          onClick={() => alternar('comentario')}
          title={`${accion} con comentario`}
          aria-label={`${accion} con comentario`}
          className={`rounded p-0.5 transition disabled:opacity-50 ${
            panelVisible === 'comentario' ? 'text-wc-green' : 'text-wc-text-muted hover:text-wc-text'
          }`}
        >
          <ComentarioIcon />
        </button>
        {etapa.completado && (
          <button
            type="button"
            disabled={guardando}
            onClick={() => alternar('empleado')}
            title={tituloEmpleado}
            aria-label={tituloEmpleado}
            aria-expanded={panelVisible === 'empleado'}
            className={`rounded p-0.5 transition disabled:opacity-50 ${
              etapa.empleadoId != null
                ? 'bg-wc-green/10 text-wc-green'
                : panelVisible === 'empleado'
                  ? 'text-wc-green'
                  : 'text-wc-text-muted hover:text-wc-text'
            }`}
          >
            <PersonaIcon />
          </button>
        )}
      </div>

      {panelVisible === 'comentario' && (
        <div className="flex w-36 flex-col gap-1">
          <input
            type="text"
            value={comentario}
            maxLength={255}
            autoFocus
            onChange={(e) => setComentario(e.target.value)}
            onKeyDown={(e) => {
              if (e.key === 'Enter') confirmarConComentario();
              if (e.key === 'Escape') setPanel(null);
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

      {panelVisible === 'empleado' && (
        <div
          role="listbox"
          aria-label="Empleado asignado"
          className="flex max-h-40 w-36 flex-col gap-0.5 overflow-y-auto rounded-lg border border-wc-border bg-white p-1 shadow-sm"
        >
          <button
            type="button"
            role="option"
            aria-selected={etapa.empleadoId == null}
            onClick={() => asignar(null)}
            className={clasesOpcion(etapa.empleadoId == null)}
          >
            Sin asignar
          </button>
          {empleados.map((empleado) => (
            <button
              key={empleado.id}
              type="button"
              role="option"
              aria-selected={etapa.empleadoId === empleado.id}
              onClick={() => asignar(empleado.id)}
              className={clasesOpcion(etapa.empleadoId === empleado.id)}
            >
              {empleado.nombre}
            </button>
          ))}
        </div>
      )}
    </div>
  );
}
