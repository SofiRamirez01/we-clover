import { useEffect, useRef, useState } from 'react';

const ChevronDownIcon = () => (
  <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth={2} strokeLinecap="round" strokeLinejoin="round">
    <path d="m6 9 6 6 6-6" />
  </svg>
);

interface Opcion {
  value: string;
  label: string;
}

interface FiltroMultiSelectProps {
  label: string;
  opciones: Opcion[];
  seleccionados: Set<string>;
  onCambiar: (seleccionados: Set<string>) => void;
}

/** Desplegable de selección múltiple genérico — mismo patrón que
 *  planificador-compras/FiltroTipoPrenda.tsx (cerrado por defecto, se cierra solo al clickear
 *  afuera), parametrizado con label/opciones para reusarlo acá con EstadoPedido. */
export default function FiltroMultiSelect({ label, opciones, seleccionados, onCambiar }: FiltroMultiSelectProps) {
  const [abierto, setAbierto] = useState(false);
  const contenedorRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    if (!abierto) return;
    function alClickearFuera(e: MouseEvent) {
      if (contenedorRef.current && !contenedorRef.current.contains(e.target as Node)) {
        setAbierto(false);
      }
    }
    document.addEventListener('mousedown', alClickearFuera);
    return () => document.removeEventListener('mousedown', alClickearFuera);
  }, [abierto]);

  function toggle(valor: string) {
    const copia = new Set(seleccionados);
    if (copia.has(valor)) copia.delete(valor);
    else copia.add(valor);
    onCambiar(copia);
  }

  const etiqueta = seleccionados.size === 0 ? label : `${label} (${seleccionados.size})`;

  return (
    <div className="relative flex flex-col gap-1" ref={contenedorRef}>
      <label className="text-xs font-semibold text-wc-text">{label}</label>
      <button
        type="button"
        onClick={() => setAbierto((v) => !v)}
        className={`flex items-center gap-2 rounded-lg border bg-white px-2.5 py-1.5 text-sm transition ${
          seleccionados.size > 0 ? 'border-wc-green text-wc-green' : 'border-wc-border text-wc-text'
        }`}
      >
        {etiqueta}
        <ChevronDownIcon />
      </button>

      {abierto && (
        <div className="absolute left-0 top-full z-20 mt-1 flex max-h-64 w-max min-w-[12rem] max-w-xs flex-col gap-1 overflow-y-auto rounded-lg border border-wc-border bg-white p-2 shadow-lg">
          {opciones.map((opcion) => (
            <div
              key={opcion.value}
              className="flex flex-row flex-nowrap items-center gap-2 rounded px-1.5 py-1 text-sm text-wc-text hover:bg-wc-bg"
            >
              <input
                id={`filtro-multi-${label}-${opcion.value}`}
                type="checkbox"
                checked={seleccionados.has(opcion.value)}
                onChange={() => toggle(opcion.value)}
                className="h-4 w-4 shrink-0 accent-wc-green"
              />
              <label htmlFor={`filtro-multi-${label}-${opcion.value}`} className="whitespace-nowrap cursor-pointer">
                {opcion.label}
              </label>
            </div>
          ))}
        </div>
      )}
    </div>
  );
}
