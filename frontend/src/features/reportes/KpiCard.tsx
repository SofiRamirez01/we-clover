import type { ReactNode } from 'react';

interface KpiCardProps {
  label: string;
  valor: string;
  /** Línea chica debajo del valor (aclaración, unidad, período). */
  detalle?: ReactNode;
  /** Aviso corto junto al label (ej. "Monto incompleto"). */
  aviso?: string;
  /** Color de identidad de la tarjeta (ej. el del estado de producción): se muestra como un
   *  punto junto al label — el texto nunca toma el color del dato. */
  color?: string;
  /**
   * "apilada" (por defecto): label arriba y valor abajo — para valores largos (montos).
   * "en-linea": label a la izquierda y valor grande a la derecha, en el mismo renglón; si la
   * tarjeta queda angosta, el valor baja al renglón siguiente. Para conteos cortos (ej. unidades
   * por estado).
   */
  disposicion?: 'apilada' | 'en-linea';
}

function Encabezado({ label, aviso, color, grande }: Pick<KpiCardProps, 'label' | 'aviso' | 'color'> & { grande: boolean }) {
  return (
    <div className={`flex min-w-0 items-center gap-2 ${grande ? '' : 'flex-wrap'}`}>
      {color && <span className="h-2.5 w-2.5 shrink-0 rounded-full" style={{ backgroundColor: color }} aria-hidden="true" />}
      <span className={grande ? 'min-w-0 truncate text-base text-wc-text' : 'text-xs font-semibold text-wc-text-muted'}>{label}</span>
      {aviso && (
        <span className="rounded-full bg-amber-100 px-2 py-0.5 text-[11px] font-semibold text-amber-900">
          ⚠ {aviso}
        </span>
      )}
    </div>
  );
}

/** Tarjeta de un número (KPI). Reutilizable en cualquier sección de Reportes. */
export default function KpiCard({ label, valor, detalle, aviso, color, disposicion = 'apilada' }: KpiCardProps) {
  if (disposicion === 'en-linea') {
    // El corte entre "en el mismo renglón" y "valor abajo" depende del ancho de la TARJETA
    // (container query), no del de la pantalla: así responde igual sin importar en cuántas
    // columnas esté la grilla que la contiene.
    return (
      <div className="@container min-w-0 rounded-xl border border-wc-border bg-white px-4 py-4">
        <div className="flex flex-col gap-1 @[12rem]:flex-row @[12rem]:items-center @[12rem]:justify-between @[12rem]:gap-3">
          <Encabezado label={label} aviso={aviso} color={color} grande />
          <span className="shrink-0 text-3xl font-bold leading-none text-wc-text">{valor}</span>
        </div>
        {detalle && <div className="mt-1.5 text-xs text-wc-text-muted">{detalle}</div>}
      </div>
    );
  }

  return (
    <div className="flex min-w-0 flex-col gap-1 rounded-lg border border-wc-border bg-white p-4">
      <Encabezado label={label} aviso={aviso} color={color} grande={false} />
      <span className="truncate text-2xl font-semibold text-wc-text" title={valor}>
        {valor}
      </span>
      {detalle && <span className="text-xs text-wc-text-muted">{detalle}</span>}
    </div>
  );
}
