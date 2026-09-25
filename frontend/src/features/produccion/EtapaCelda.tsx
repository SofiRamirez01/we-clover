import type { ProduccionEtapaResponse } from '../../types/produccion';
import type { UsuarioResumen } from '../../types/produccion';

interface EtapaCeldaProps {
  etapa: ProduccionEtapaResponse;
  empleados: UsuarioResumen[];
  guardando: boolean;
  onCambiar: (completado: boolean, idEmpleado: number | null) => void;
}

/** Una celda de la grilla de etapas: "—" si no aplica a este producto, checkbox si aplica. Al
 *  tildarlo (o si ya estaba tildado) aparece debajo un selector chico de empleado, opcional. */
export default function EtapaCelda({ etapa, empleados, guardando, onCambiar }: EtapaCeldaProps) {
  if (!etapa.aplica) {
    return <span className="text-wc-text-muted">—</span>;
  }

  return (
    <div className="flex flex-col items-center gap-1">
      <input
        type="checkbox"
        checked={etapa.completado}
        disabled={guardando}
        onChange={(e) => onCambiar(e.target.checked, etapa.empleadoId)}
        className="h-4 w-4 accent-wc-green disabled:opacity-50"
      />
      {etapa.completado && (
        <select
          value={etapa.empleadoId ?? ''}
          disabled={guardando}
          onChange={(e) => onCambiar(true, e.target.value ? Number(e.target.value) : null)}
          className="w-24 rounded border border-wc-border bg-white px-1 py-0.5 text-[11px] text-wc-text disabled:opacity-50"
        >
          <option value="">Sin asignar</option>
          {empleados.map((empleado) => (
            <option key={empleado.id} value={empleado.id}>
              {empleado.nombre}
            </option>
          ))}
        </select>
      )}
    </div>
  );
}
