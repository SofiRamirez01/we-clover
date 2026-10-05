interface TooltipGraficoProps {
  titulo: string;
  /** Color del dato: se muestra como una marca junto al valor, el texto queda en tinta. */
  color: string;
  nombreValor: string;
  valor: string;
  /** Línea secundaria (ej. el monto), sin marca de color. */
  detalle?: string;
}

/** Contenido común de los tooltips de los gráficos de Reportes. */
export default function TooltipGrafico({ titulo, color, nombreValor, valor, detalle }: TooltipGraficoProps) {
  return (
    <div className="rounded-lg border border-wc-border bg-white px-3 py-2 text-xs shadow-md">
      <div className="mb-1 font-semibold text-wc-text">{titulo}</div>
      <div className="flex items-center gap-2 text-wc-text">
        <span className="h-2.5 w-2.5 shrink-0 rounded-sm" style={{ backgroundColor: color }} aria-hidden="true" />
        <span className="text-wc-text-muted">{nombreValor}</span>
        <span className="ml-auto pl-3 font-semibold tabular-nums">{valor}</span>
      </div>
      {detalle && <div className="mt-1 text-wc-text-muted">{detalle}</div>}
    </div>
  );
}
