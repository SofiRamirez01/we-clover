import UnidadComboRow from './UnidadComboRow';
import type { BorradorCombo } from './FilaAlumno';
import type { ComboResponse, GrupoTallaResponse } from '../../types/cargaTalles';

interface ComboCeldaProps {
  tabla: GrupoTallaResponse | undefined;
  combos: ComboResponse[];
  borrador: Record<number, BorradorCombo>;
  editando: boolean;
  soloLecturaGlobal: boolean;
  onCambiarAncho: (idCombo: number, valor: string) => void;
  onCambiarLargo: (idCombo: number, valor: string) => void;
  onCambiarObservacion: (idCombo: number, valor: string) => void;
}

/** Celda alumno×producto: la(s) unidad(es) de esa prenda que quedaron fijadas para este alumno
 *  al momento del alta (ej. dos remeras) — cada una con su propia medida. La composición ya no
 *  se ajusta acá: si está mal, se borra el alumno entero (ver el tacho en la fila) y se vuelve a
 *  agregar con la composición correcta. */
export default function ComboCelda({
  tabla,
  combos,
  borrador,
  editando,
  soloLecturaGlobal,
  onCambiarAncho,
  onCambiarLargo,
  onCambiarObservacion,
}: ComboCeldaProps) {
  return (
    <div className="flex min-w-[11rem] flex-col gap-1.5">
      {combos.map((combo) => {
        const d = borrador[combo.id] ?? { ancho: '', largo: '', observacion: '' };
        return (
          <UnidadComboRow
            key={combo.id}
            combo={combo}
            tabla={tabla}
            editando={editando}
            soloLecturaGlobal={soloLecturaGlobal}
            anchoTexto={d.ancho}
            largoTexto={d.largo}
            observacion={d.observacion}
            onCambiarAncho={(valor) => onCambiarAncho(combo.id, valor)}
            onCambiarLargo={(valor) => onCambiarLargo(combo.id, valor)}
            onCambiarObservacion={(valor) => onCambiarObservacion(combo.id, valor)}
          />
        );
      })}
    </div>
  );
}
