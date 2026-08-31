import { useEffect, useRef, useState } from 'react';

const ChevronDownIcon = () => (
  <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth={2} strokeLinecap="round" strokeLinejoin="round">
    <path d="m6 9 6 6 6-6" />
  </svg>
);

interface FiltroTipoPrendaProps {
  opciones: string[];
  seleccionados: Set<string>;
  onCambiar: (seleccionados: Set<string>) => void;
}

/**
 * Desplegable de selección múltiple (checkboxes) para tipo de prenda — reemplaza a los chips
 * siempre visibles porque con muchos tipos ocupaban demasiado espacio horizontal en la barra
 * de filtros. Cerrado por defecto; se cierra solo al clickear afuera (mismo criterio que
 * CambiarEstadoPopover, versión simplificada porque acá no hace falta reposicionarse contra
 * los bordes de la ventana: siempre cuelga debajo de su propio botón).
 */
export default function FiltroTipoPrenda({ opciones, seleccionados, onCambiar }: FiltroTipoPrendaProps) {
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

  function toggle(opcion: string) {
    const copia = new Set(seleccionados);
    if (copia.has(opcion)) copia.delete(opcion);
    else copia.add(opcion);
    onCambiar(copia);
  }

  const etiqueta = seleccionados.size === 0 ? 'Tipo de prenda' : `Tipo de prenda (${seleccionados.size})`;

  return (
    <div className="relative flex flex-col gap-1" ref={contenedorRef}>
      <label className="text-xs font-semibold text-wc-text">Tipo de prenda</label>
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
          {opciones.length === 0 ? (
            <p className="px-1.5 py-1 text-xs text-wc-text-muted">No hay tipos de prenda para elegir.</p>
          ) : (
            opciones.map((opcion) => (
              <div
                key={opcion}
                className="flex flex-row flex-nowrap items-center gap-2 rounded px-1.5 py-1 text-sm text-wc-text hover:bg-wc-bg"
              >
                <input
                  id={`filtro-tipo-prenda-${opcion}`}
                  type="checkbox"
                  checked={seleccionados.has(opcion)}
                  onChange={() => toggle(opcion)}
                  className="h-4 w-4 shrink-0 accent-wc-green"
                />
                <label
                  htmlFor={`filtro-tipo-prenda-${opcion}`}
                  className="whitespace-nowrap cursor-pointer"
                >
                  {opcion}
                </label>
              </div>
            ))
          )}
        </div>
      )}
    </div>
  );
}
