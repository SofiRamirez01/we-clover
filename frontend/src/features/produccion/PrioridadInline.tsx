import { useState } from 'react';
import type { KeyboardEvent } from 'react';

const PencilIcon = () => (
  <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth={2} strokeLinecap="round" strokeLinejoin="round">
    <path d="M11 4H4a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2v-7" />
    <path d="M18.5 2.5a2.121 2.121 0 0 1 3 3L12 15l-4 1 1-4Z" />
  </svg>
);

const XIcon = () => (
  <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth={2} strokeLinecap="round" strokeLinejoin="round">
    <path d="M18 6 6 18M6 6l12 12" />
  </svg>
);

interface PrioridadInlineProps {
  prioridadAutomatica: number | null;
  prioridadManual: number | null;
  onCambiar: (prioridad: number) => void;
  onQuitar: () => void;
}

/**
 * prioridadAutomatica y prioridadManual se muestran lado a lado; si difieren se nota solo por
 * contraste visual (automática tachada/atenuada cuando hay override manual) — no hace falta
 * lógica de alerta aparte, como pidió la consigna. Click en el lápiz abre un input numérico
 * chico; confirma con blur o Enter (mismo patrón que ComboUpsertRequest en Carga de Talles).
 */
export default function PrioridadInline({ prioridadAutomatica, prioridadManual, onCambiar, onQuitar }: PrioridadInlineProps) {
  const [editando, setEditando] = useState(false);
  const [valor, setValor] = useState('');

  function abrirEdicion() {
    setValor(String(prioridadManual ?? prioridadAutomatica ?? ''));
    setEditando(true);
  }

  function confirmar() {
    const numero = Number(valor);
    if (valor.trim() !== '' && Number.isFinite(numero) && numero > 0) {
      onCambiar(Math.round(numero));
    }
    setEditando(false);
  }

  function handleKeyDown(e: KeyboardEvent<HTMLInputElement>) {
    if (e.key === 'Enter') confirmar();
    if (e.key === 'Escape') setEditando(false);
  }

  if (editando) {
    return (
      <input
        type="number"
        min={1}
        autoFocus
        value={valor}
        onChange={(e) => setValor(e.target.value)}
        onBlur={confirmar}
        onKeyDown={handleKeyDown}
        className="w-14 rounded border border-wc-green bg-white px-1.5 py-0.5 text-sm text-wc-text outline-none"
      />
    );
  }

  return (
    <div className="flex items-center gap-1.5">
      <span
        className={
          prioridadManual != null
            ? 'text-xs text-wc-text-muted line-through'
            : 'text-sm font-semibold text-wc-text'
        }
        title="Prioridad automática (rank por % de pago)"
      >
        {prioridadAutomatica ?? '—'}
      </span>
      {prioridadManual != null && (
        <span className="text-sm font-bold text-wc-green" title="Prioridad manual (override)">
          {prioridadManual}
        </span>
      )}
      <button
        type="button"
        onClick={abrirEdicion}
        title="Fijar prioridad manual"
        className="text-wc-text-muted hover:text-wc-text"
      >
        <PencilIcon />
      </button>
      {prioridadManual != null && (
        <button type="button" onClick={onQuitar} title="Volver a prioridad automática" className="text-wc-text-muted hover:text-red-600">
          <XIcon />
        </button>
      )}
    </div>
  );
}
