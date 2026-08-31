import { useEffect, useLayoutEffect, useMemo, useRef, useState } from 'react';
import { createPortal } from 'react-dom';
import type { KeyboardEvent } from 'react';
import type { PaletaColorResponse } from '../types/paletaColores';

interface ComboboxColorProps {
  colores: PaletaColorResponse[];
  value: number | '';
  onChange: (idPaletaColor: number | '') => void;
  placeholder?: string;
  disabled?: boolean;
  /** Estilo compacto (texto/padding chicos) para usarlo dentro de filas apretadas, ej. las
   *  planillas de insumos secundarios o de stock. */
  compacto?: boolean;
  className?: string;
}

interface Posicion {
  top: number;
  left: number;
  width: number;
}

/**
 * Reemplazo de un `<select>` nativo para elegir un color de la paleta cuando la lista es larga
 * (la carta de colores tiene decenas de filas por tela) — con buscador por nombre, para no
 * tener que scrollear un combo nativo. Sin dependencias externas: la app no traía ninguna
 * librería de combobox, así que es una implementación liviana propia (input + lista filtrada +
 * navegación por teclado), pensada para reusarse en cualquier lugar que hoy tenga un `<select>`
 * de `PaletaColores` (Ficha Técnica: gotero, color de cierre, insumos secundarios; Stock).
 *
 * El panel desplegable se renderiza con un portal a `document.body` (no como hijo directo del
 * botón) y se posiciona con `position: fixed` a partir de `getBoundingClientRect()` — si no,
 * cualquier ancestro con scroll (la tabla de Stock, los paneles internos de
 * `ModalColoresGotero`) lo recorta o le agrega scrollbars propias en vez de superponerse,
 * porque un contenedor con `overflow-x: auto` sin `overflow-y` explícito termina recortando el
 * eje Y también (regla de CSSOM: si un eje no es `visible` el otro dejar de serlo).
 */
export default function ComboboxColor({ colores, value, onChange, placeholder = 'Elegí un color…', disabled, compacto, className }: ComboboxColorProps) {
  const [abierto, setAbierto] = useState(false);
  const [busqueda, setBusqueda] = useState('');
  const [indiceActivo, setIndiceActivo] = useState(0);
  const [posicion, setPosicion] = useState<Posicion | null>(null);
  const botonRef = useRef<HTMLButtonElement | null>(null);
  const panelRef = useRef<HTMLDivElement | null>(null);
  const inputRef = useRef<HTMLInputElement | null>(null);

  const colorSeleccionado = useMemo(() => colores.find((c) => c.id === value) ?? null, [colores, value]);

  const filtrados = useMemo(() => {
    const termino = busqueda.trim().toLowerCase();
    if (!termino) return colores;
    return colores.filter((c) => c.nombre.toLowerCase().includes(termino));
  }, [colores, busqueda]);

  function recalcularPosicion() {
    const rect = botonRef.current?.getBoundingClientRect();
    if (!rect) return;
    setPosicion({ top: rect.bottom + 4, left: rect.left, width: rect.width });
  }

  useLayoutEffect(() => {
    if (!abierto) return;
    recalcularPosicion();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [abierto]);

  useEffect(() => {
    if (!abierto) return;
    function alCerrarPorFuera(e: MouseEvent) {
      const target = e.target as Node;
      if (botonRef.current?.contains(target) || panelRef.current?.contains(target)) return;
      setAbierto(false);
      setBusqueda('');
    }
    // capture=true + scroll de cualquier contenedor (no solo window) para no quedar desanclado
    // del botón cuando se scrollea la tabla o el panel interno de un modal.
    document.addEventListener('mousedown', alCerrarPorFuera);
    window.addEventListener('scroll', recalcularPosicion, true);
    window.addEventListener('resize', recalcularPosicion);
    return () => {
      document.removeEventListener('mousedown', alCerrarPorFuera);
      window.removeEventListener('scroll', recalcularPosicion, true);
      window.removeEventListener('resize', recalcularPosicion);
    };
  }, [abierto]);

  useEffect(() => {
    setIndiceActivo(0);
  }, [busqueda, abierto]);

  function abrir() {
    if (disabled) return;
    setAbierto(true);
    setBusqueda('');
    requestAnimationFrame(() => inputRef.current?.focus());
  }

  function elegir(color: PaletaColorResponse) {
    onChange(color.id);
    setAbierto(false);
    setBusqueda('');
  }

  function handleKeyDown(e: KeyboardEvent<HTMLInputElement>) {
    if (e.key === 'ArrowDown') {
      e.preventDefault();
      setIndiceActivo((i) => Math.min(i + 1, filtrados.length - 1));
    } else if (e.key === 'ArrowUp') {
      e.preventDefault();
      setIndiceActivo((i) => Math.max(i - 1, 0));
    } else if (e.key === 'Enter') {
      e.preventDefault();
      if (filtrados[indiceActivo]) elegir(filtrados[indiceActivo]);
    } else if (e.key === 'Escape') {
      e.preventDefault();
      setAbierto(false);
      setBusqueda('');
    }
  }

  const paddingBoton = compacto ? 'px-2 py-1' : 'px-2 py-1.5';
  const textoBoton = compacto ? 'text-xs' : 'text-sm';

  return (
    <div className={`relative ${className ?? ''}`}>
      <button
        ref={botonRef}
        type="button"
        onClick={abrir}
        disabled={disabled}
        className={`flex w-full items-center gap-1.5 rounded-lg border border-wc-border bg-white ${paddingBoton} text-left ${textoBoton} text-wc-text outline-none disabled:cursor-not-allowed disabled:bg-wc-bg disabled:text-wc-text-muted`}
      >
        {colorSeleccionado ? (
          <>
            <span className="h-3.5 w-3.5 shrink-0 rounded-full border border-wc-border" style={{ backgroundColor: colorSeleccionado.hex }} />
            <span className="flex-1 truncate">{colorSeleccionado.nombre}</span>
          </>
        ) : (
          <span className="flex-1 truncate text-wc-text-muted">{placeholder}</span>
        )}
      </button>

      {abierto && posicion &&
        createPortal(
          <div
            ref={panelRef}
            style={{ position: 'fixed', top: posicion.top, left: posicion.left, width: Math.max(posicion.width, 176) }}
            // tw-scope: portalado a document.body, fuera de cualquier .tw-scope ancestro — sin
            // esta clase, el reset sin-layer de shared.css (input/select/textarea) le vuelve a
            // ganar a las utilidades de Tailwind del buscador (ver styles/tailwind.css).
            className="tw-scope z-50 overflow-hidden rounded-lg border border-wc-border bg-white shadow-lg"
          >
            <input
              ref={inputRef}
              value={busqueda}
              onChange={(e) => setBusqueda(e.target.value)}
              onKeyDown={handleKeyDown}
              placeholder="Buscar color…"
              className="w-full border-b border-wc-border px-2 py-1.5 text-sm text-wc-text outline-none"
            />
            <div className="max-h-52 overflow-y-auto py-1">
              {filtrados.length === 0 && <p className="px-3 py-2 text-xs text-wc-text-muted">Sin resultados.</p>}
              {filtrados.map((color, index) => (
                <button
                  key={color.id}
                  type="button"
                  onMouseDown={(e) => e.preventDefault()}
                  onClick={() => elegir(color)}
                  onMouseEnter={() => setIndiceActivo(index)}
                  className={`flex w-full items-center gap-2 px-3 py-1.5 text-left text-sm ${
                    index === indiceActivo ? 'bg-wc-green/10' : ''
                  } ${color.id === value ? 'font-semibold text-wc-green' : 'text-wc-text'}`}
                >
                  <span className="h-3.5 w-3.5 shrink-0 rounded-full border border-wc-border" style={{ backgroundColor: color.hex }} />
                  <span className="flex-1 truncate">{color.nombre}</span>
                </button>
              ))}
            </div>
          </div>,
          document.body,
        )}
    </div>
  );
}
