interface BarraProgresoTallesProps {
  cargados: number;
  total: number;
}

/**
 * Rojo <50%, naranja 50-99%, verde 100% — umbral acordado con el negocio. `track` tiñe el fondo
 * completo de la barra (no solo el relleno): a 0% el relleno mide 0px y no se vería nada, así
 * que sin el tinte de fondo un pedido "recién empezado" se confundiría visualmente con la
 * columna "—" de una prenda sin talles.
 */
function colorPorProgreso(pct: number): { barra: string; texto: string; track: string } {
  if (pct >= 100) return { barra: 'bg-wc-green', texto: 'text-wc-green-dark', track: 'bg-wc-green/15' };
  if (pct >= 50) return { barra: 'bg-amber-500', texto: 'text-amber-700', track: 'bg-amber-100' };
  return { barra: 'bg-red-500', texto: 'text-red-600', track: 'bg-red-100' };
}

export default function BarraProgresoTalles({ cargados, total }: BarraProgresoTallesProps) {
  if (total <= 0) {
    return <span className="text-xs text-wc-text-muted">—</span>;
  }

  const pct = Math.min(100, Math.round((cargados / total) * 100));
  const { barra, texto, track } = colorPorProgreso(pct);

  return (
    <div className="flex w-full max-w-[120px] flex-col gap-1">
      <div className={`h-1.5 w-full overflow-hidden rounded-full ${track}`}>
        <div className={`h-full rounded-full transition-all ${barra}`} style={{ width: `${pct}%` }} />
      </div>
      <span className={`text-[10px] font-semibold ${texto}`}>
        {cargados}/{total} ({pct}%)
      </span>
    </div>
  );
}
