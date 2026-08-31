import { useEffect } from 'react';

export interface AccionModalConfirmacion {
  label: string;
  onClick: () => void;
  /** primaria = verde (acción recomendada), secundaria = neutra (seguir/cancelar), peligro = roja (destructiva). */
  variante?: 'primaria' | 'secundaria' | 'peligro';
}

interface ModalConfirmacionProps {
  titulo: string;
  mensaje: string;
  /** En el orden en que se muestran, de arriba hacia abajo. */
  acciones: AccionModalConfirmacion[];
  /** Clickear afuera, Escape, o cualquier acción que decida cerrar sin más trámite. */
  onCerrar: () => void;
}

const CLASES_VARIANTE: Record<NonNullable<AccionModalConfirmacion['variante']>, string> = {
  primaria: 'bg-wc-green text-white hover:bg-wc-green-dark',
  secundaria: 'border border-wc-border bg-white text-wc-text-muted hover:bg-wc-bg',
  peligro: 'bg-red-600 text-white hover:bg-red-700',
};

/**
 * Popup de confirmación genérico, con la misma estética que el resto de los modales del
 * sistema (overlay oscuro + tarjeta blanca redondeada) — pensado para reusarse en cualquier
 * pantalla que necesite plantear una decisión de 2 o más opciones antes de una acción
 * destructiva o irreversible, en vez de `window.confirm` (que no se puede estilar y desentona
 * con el resto de la UI).
 */
export default function ModalConfirmacion({ titulo, mensaje, acciones, onCerrar }: ModalConfirmacionProps) {
  useEffect(() => {
    function alPresionarTecla(e: KeyboardEvent) {
      if (e.key === 'Escape') onCerrar();
    }
    document.addEventListener('keydown', alPresionarTecla);
    return () => document.removeEventListener('keydown', alPresionarTecla);
  }, [onCerrar]);

  return (
    <div
      className="tw-scope fixed inset-0 z-50 flex items-center justify-center bg-black/70 p-6"
      onClick={onCerrar}
      role="dialog"
      aria-modal="true"
    >
      <div
        className="flex w-full max-w-sm flex-col gap-4 rounded-xl bg-white p-6 shadow-2xl"
        onClick={(e) => e.stopPropagation()}
      >
        <div>
          <h2 className="text-base font-bold text-wc-text">{titulo}</h2>
          <p className="mt-1 text-sm text-wc-text-muted">{mensaje}</p>
        </div>

        <div className="flex flex-col gap-2">
          {acciones.map((accion) => (
            <button
              key={accion.label}
              type="button"
              onClick={accion.onClick}
              className={`rounded-md px-4 py-2 text-sm font-semibold transition ${CLASES_VARIANTE[accion.variante ?? 'secundaria']}`}
            >
              {accion.label}
            </button>
          ))}
        </div>
      </div>
    </div>
  );
}
