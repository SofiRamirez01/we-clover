import { useState } from 'react';
import SegmentoEditor from './SegmentoEditor';
import { calcularEstadisticasPoligono, evaluarContorno } from './geometriaPieza';
import { extraerMensajeError } from '../../utils/errores';
import type { Punto, Segmento } from '../../types/pieza';

export interface ResultadoEdicionTalle {
  coordenadas: Punto[];
  areaCm2: number;
  anchoCm: number;
  largoCm: number;
  perimetroCm: number;
}

interface EditorTalleModalProps {
  talle: string;
  segmentosIniciales: Segmento[];
  onGuardar: (resultado: ResultadoEdicionTalle) => Promise<void>;
  onCerrar: () => void;
}

/**
 * Editor de contorno para UN talle puntual de la graduación (no el talle base de la Pieza, que
 * se edita desde su propio formulario). A diferencia de PiezaForm, acá "Guardar" no llama al
 * servicio de geometría (Python/Shapely): las estadísticas se recalculan localmente con el
 * mismo shoelace/perímetro que usa escalarPieza, sobre la tesselación ya hecha en el navegador.
 */
export default function EditorTalleModal({ talle, segmentosIniciales, onGuardar, onCerrar }: EditorTalleModalProps) {
  const [segmentos, setSegmentos] = useState<Segmento[]>(segmentosIniciales);
  const [guardando, setGuardando] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function handleGuardar() {
    const evaluacion = evaluarContorno(segmentos);
    if (!evaluacion.cerradoCompleto) {
      setError('El contorno debe cerrarse: revisá que todos los tramos se conecten en un único lazo.');
      return;
    }
    setGuardando(true);
    setError(null);
    try {
      const coordenadas = evaluacion.cadenas[0].puntos;
      const stats = calcularEstadisticasPoligono(coordenadas);
      await onGuardar({ coordenadas, ...stats });
    } catch (err) {
      setError(extraerMensajeError(err, 'No se pudo guardar el talle.'));
    } finally {
      setGuardando(false);
    }
  }

  return (
    <div className="tw-scope fixed inset-0 z-50 flex items-center justify-center bg-black/70 p-6" role="dialog" aria-modal="true">
      <div className="flex max-h-[90vh] w-full max-w-3xl flex-col gap-4 overflow-y-auto rounded-xl bg-white p-6 shadow-2xl">
        <h2 className="text-lg font-bold text-wc-text">Editar talle {talle}</h2>
        <SegmentoEditor segmentos={segmentos} onChange={setSegmentos} />
        {error && <p className="text-xs font-medium text-red-600">{error}</p>}
        <div className="flex justify-end gap-3 border-t border-wc-border pt-4">
          <button
            type="button"
            onClick={onCerrar}
            disabled={guardando}
            className="rounded-md border border-wc-border bg-white px-4 py-2 text-sm font-semibold text-wc-text-muted transition hover:bg-wc-bg disabled:cursor-not-allowed disabled:opacity-60"
          >
            Cancelar
          </button>
          <button
            type="button"
            onClick={handleGuardar}
            disabled={guardando}
            className="rounded-lg bg-wc-green px-5 py-2.5 text-sm font-semibold text-white transition hover:bg-wc-green-dark disabled:cursor-not-allowed disabled:opacity-60"
          >
            {guardando ? 'Guardando…' : 'Guardar'}
          </button>
        </div>
      </div>
    </div>
  );
}
