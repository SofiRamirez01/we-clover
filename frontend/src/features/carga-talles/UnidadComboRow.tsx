import { calcularTalleCliente } from './calcularTalleCliente';
import type { ComboResponse, GrupoTallaResponse } from '../../types/cargaTalles';

interface UnidadComboRowProps {
  combo: ComboResponse;
  tabla: GrupoTallaResponse | undefined;
  /** false = fila del alumno bloqueada (ya guardada) — solo lectura con el talle fijo. */
  editando: boolean;
  /** true = toda la pantalla es de solo lectura (carga CERRADA) — nunca editable, con o sin
   *  `editando`. */
  soloLecturaGlobal: boolean;
  anchoTexto: string;
  largoTexto: string;
  observacion: string;
  onCambiarAncho: (valor: string) => void;
  onCambiarLargo: (valor: string) => void;
  onCambiarObservacion: (valor: string) => void;
}

/**
 * Una unidad puntual (una prenda) dentro de la celda alumno×producto. Totalmente controlada
 * desde `FilaAlumno` — no guarda nada por su cuenta (el guardado es por fila de alumno
 * completa, ver FilaAlumno.guardarFila) ni tiene estado propio de ancho/largo/observación.
 * El talle es una vista previa calculada en el cliente (ver calcularTalleCliente) mientras se
 * edita; el que persiste es el que confirma el backend al guardar.
 */
export default function UnidadComboRow({
  combo,
  tabla,
  editando,
  soloLecturaGlobal,
  anchoTexto,
  largoTexto,
  observacion,
  onCambiarAncho,
  onCambiarLargo,
  onCambiarObservacion,
}: UnidadComboRowProps) {
  const anchoNum = anchoTexto.trim() ? Number(anchoTexto) : null;
  const largoNum = largoTexto.trim() ? Number(largoTexto) : null;
  const medidaCompleta = anchoNum != null && !Number.isNaN(anchoNum) && largoNum != null && !Number.isNaN(largoNum);
  const preview = medidaCompleta ? calcularTalleCliente(tabla, anchoNum!, largoNum!) : null;

  const bloqueada = !editando || soloLecturaGlobal;

  return (
    <div className="flex flex-col gap-1.5 rounded-lg bg-wc-bg p-2">
      <div className="flex flex-wrap items-center gap-1.5">
        {bloqueada ? (
          <span className="text-xs text-wc-text">
            {combo.anchoCm != null && combo.largoCm != null ? `${combo.anchoCm} × ${combo.largoCm} cm` : 'Sin medir'}
          </span>
        ) : (
          <>
            <input
              type="number"
              min={1}
              value={anchoTexto}
              onChange={(e) => onCambiarAncho(e.target.value)}
              placeholder="Ancho cm"
              className="w-20 rounded-md border border-wc-border bg-white px-1.5 py-1 text-xs text-wc-text"
            />
            <input
              type="number"
              min={1}
              value={largoTexto}
              onChange={(e) => onCambiarLargo(e.target.value)}
              placeholder="Largo cm"
              className="w-20 rounded-md border border-wc-border bg-white px-1.5 py-1 text-xs text-wc-text"
            />
          </>
        )}

        {/* Talle: en la misma fila que los inputs, no debajo. */}
        {bloqueada && combo.anchoCm != null && !combo.personalizado && (
          <span className="rounded-lg bg-wc-green/15 px-2 py-0.5 text-xs font-semibold text-wc-green">
            Talle {combo.talle}
          </span>
        )}
        {bloqueada && combo.personalizado && (
          <span className="rounded-lg bg-amber-100 px-2 py-0.5 text-xs font-semibold text-amber-700">Personalizado</span>
        )}
        {!bloqueada && preview && !preview.personalizado && (
          <span className="rounded-lg bg-wc-green/15 px-2 py-0.5 text-xs font-semibold text-wc-green">
            Talle {preview.talle}
          </span>
        )}
        {!bloqueada && preview?.personalizado && (
          <span className="rounded-lg bg-amber-100 px-2 py-0.5 text-xs font-semibold text-amber-700">Personalizado</span>
        )}
      </div>

      {/* Observación de personalizado: visible cuando ya quedó personalizado (fijo) o cuando la
          vista previa en curso da personalizado. */}
      {((bloqueada && combo.personalizado) || (!bloqueada && preview?.personalizado)) && (
        bloqueada ? (
          combo.observacionPersonalizado && <p className="text-xs text-wc-text-muted">{combo.observacionPersonalizado}</p>
        ) : (
          <textarea
            value={observacion}
            onChange={(e) => onCambiarObservacion(e.target.value)}
            placeholder="Observación (ej: tiene los brazos muy largos)"
            rows={2}
            className="w-full rounded-md border border-wc-border bg-white px-1.5 py-1 text-xs text-wc-text"
          />
        )
      )}
    </div>
  );
}

