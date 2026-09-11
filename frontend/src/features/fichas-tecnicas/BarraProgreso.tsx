interface BarraProgresoProps {
  /** Ya clampeado 0-100. */
  pct: number;
  etiqueta: string;
}

/**
 * Rojo <50%, naranja 50-99%, verde 100% — umbral acordado con el negocio, reutilizado por
 * cualquier barra de avance de Ficha Técnica (talles, pago, etc.). `track` tiñe el fondo
 * completo de la barra (no solo el relleno): a 0% el relleno mide 0px y no se vería nada, así
 * que sin el tinte de fondo un avance "recién empezado" se confundiría visualmente con "—".
 */
function colorPorProgreso(pct: number): { barra: string; texto: string; track: string } {
  if (pct >= 100) return { barra: 'bg-wc-green', texto: 'text-wc-green-dark', track: 'bg-wc-green/15' };
  if (pct >= 50) return { barra: 'bg-amber-500', texto: 'text-amber-700', track: 'bg-amber-100' };
  return { barra: 'bg-red-500', texto: 'text-red-600', track: 'bg-red-100' };
}

export default function BarraProgreso({ pct, etiqueta }: BarraProgresoProps) {
  const { barra, texto, track } = colorPorProgreso(pct);

  return (
    <div className="flex w-full max-w-[120px] flex-col gap-1">
      <div className={`h-1.5 w-full overflow-hidden rounded-full ${track}`}>
        <div className={`h-full rounded-full transition-all ${barra}`} style={{ width: `${pct}%` }} />
      </div>
      <span className={`text-[10px] font-semibold ${texto}`}>{etiqueta}</span>
    </div>
  );
}
