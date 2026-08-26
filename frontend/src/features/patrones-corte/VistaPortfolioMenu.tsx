import { useEffect, useRef, useState } from 'react';

export type TamanoVista = 'muy-grande' | 'grande' | 'mediano' | 'pequeno' | 'lista';

const MonitorIcon = ({ size }: { size: number }) => (
  <svg width={size} height={size} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth={1.8} strokeLinecap="round" strokeLinejoin="round">
    <rect x="3" y="4" width="18" height="13" rx="1.5" />
    <path d="M9 20h6M12 17v3" />
  </svg>
);

const GridIcon = () => (
  <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth={1.8} strokeLinecap="round" strokeLinejoin="round">
    <rect x="3.5" y="3.5" width="7.5" height="7.5" rx="1.2" />
    <rect x="13" y="3.5" width="7.5" height="7.5" rx="1.2" />
    <rect x="3.5" y="13" width="7.5" height="7.5" rx="1.2" />
    <rect x="13" y="13" width="7.5" height="7.5" rx="1.2" />
  </svg>
);

const ListIcon = () => (
  <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth={1.8} strokeLinecap="round" strokeLinejoin="round">
    <path d="M8 6h13M8 12h13M8 18h13" />
    <path d="M3 6h.01M3 12h.01M3 18h.01" />
  </svg>
);

const ViewButtonIcon = () => (
  <svg width="17" height="17" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth={1.8} strokeLinecap="round" strokeLinejoin="round">
    <rect x="3.5" y="4.5" width="17" height="11" rx="1.5" />
    <path d="M3.5 19h17" />
  </svg>
);

export const OPCIONES_VISTA: { value: TamanoVista; label: string; icon: (props: { seleccionado: boolean }) => import('react').ReactElement }[] = [
  { value: 'muy-grande', label: 'Iconos muy grandes', icon: () => <MonitorIcon size={22} /> },
  { value: 'grande', label: 'Iconos grandes', icon: () => <MonitorIcon size={18} /> },
  { value: 'mediano', label: 'Iconos medianos', icon: () => <MonitorIcon size={15} /> },
  { value: 'pequeno', label: 'Iconos pequeños', icon: () => <GridIcon /> },
  { value: 'lista', label: 'Lista', icon: () => <ListIcon /> },
];

interface VistaPortfolioMenuProps {
  valor: TamanoVista;
  onCambiar: (valor: TamanoVista) => void;
}

export default function VistaPortfolioMenu({ valor, onCambiar }: VistaPortfolioMenuProps) {
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

  return (
    <div className="relative" ref={contenedorRef}>
      <button
        type="button"
        onClick={() => setAbierto((v) => !v)}
        aria-label="Cambiar tamaño de vista"
        aria-expanded={abierto}
        className="flex items-center gap-2 rounded-lg border border-wc-border bg-white px-3 py-2 text-sm text-wc-text outline-none transition focus:border-wc-green focus:ring-2 focus:ring-wc-green/20"
      >
        <ViewButtonIcon />
      </button>

      {abierto && (
        <div className="absolute right-0 top-full z-10 mt-1 w-56 overflow-hidden rounded-lg border border-wc-border bg-white py-1.5 shadow-lg">
          {OPCIONES_VISTA.map(({ value, label, icon: Icon }) => {
            const seleccionado = value === valor;
            return (
              <button
                key={value}
                type="button"
                onClick={() => {
                  onCambiar(value);
                  setAbierto(false);
                }}
                className={`flex w-full items-center gap-3 border-0 bg-transparent px-3 py-2 text-left text-sm outline-none transition hover:bg-wc-bg ${
                  seleccionado ? 'font-semibold text-wc-text' : 'text-wc-text'
                }`}
              >
                <span className="w-2.5 text-wc-green">{seleccionado && '•'}</span>
                <span className="flex w-6 items-center justify-center text-wc-text-muted">
                  <Icon seleccionado={seleccionado} />
                </span>
                {label}
              </button>
            );
          })}
        </div>
      )}
    </div>
  );
}
