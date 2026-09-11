import BarraProgreso from './BarraProgreso';

interface BarraProgresoTallesProps {
  cargados: number;
  total: number;
}

export default function BarraProgresoTalles({ cargados, total }: BarraProgresoTallesProps) {
  if (total <= 0) {
    return <span className="text-xs text-wc-text-muted">—</span>;
  }

  const pct = Math.min(100, Math.round((cargados / total) * 100));
  return <BarraProgreso pct={pct} etiqueta={`${cargados}/${total} (${pct}%)`} />;
}
