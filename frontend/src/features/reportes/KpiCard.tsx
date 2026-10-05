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
}

/** Tarjeta de un número (KPI). Reutilizable en cualquier sección de Reportes. */
export default function KpiCard({ label, valor, detalle, aviso, color }: KpiCardProps) {
  return (
    <div className="flex min-w-0 flex-col gap-1 rounded-lg border border-wc-border bg-white p-4">
      <div className="flex flex-wrap items-center gap-2">
        {color && <span className="h-2.5 w-2.5 shrink-0 rounded-full" style={{ backgroundColor: color }} aria-hidden="true" />}
        <span className="text-xs font-semibold text-wc-text-muted">{label}</span>
        {aviso && (
          <span className="rounded-full bg-amber-100 px-2 py-0.5 text-[11px] font-semibold text-amber-900">
            ⚠ {aviso}
          </span>
        )}
      </div>
      <span className="truncate text-2xl font-semibold text-wc-text" title={valor}>
        {valor}
      </span>
      {detalle && <span className="text-xs text-wc-text-muted">{detalle}</span>}
    </div>
  );
}
