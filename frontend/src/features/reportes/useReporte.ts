import { useCallback, useEffect, useState } from 'react';
import { extraerMensajeError } from '../../utils/errores';
import type { ReporteFiltros } from '../../types/reportes';

interface Resultado<T> {
  /** A qué filtros (e intento) corresponde este resultado. */
  clave: string;
  datos: T | null;
  error: string | null;
}

export interface EstadoReporte<T> {
  /** Último resultado bueno; mientras se recarga con otros filtros sigue siendo el anterior. */
  datos: T | null;
  cargando: boolean;
  error: string | null;
  reintentar: () => void;
}

/**
 * Carga de un reporte según los filtros globales, con sus estados de carga y error. Cada sección
 * de Reportes usa el suyo, así una falla en una no afecta a las demás. `filtros` null (rango a
 * medida incompleto) no dispara ninguna consulta. `cargar` debe ser una función estable (las de
 * reportesService).
 */
export function useReporte<T>(
  cargar: (filtros: ReporteFiltros, signal: AbortSignal) => Promise<T>,
  filtros: ReporteFiltros | null,
  mensajeError: string,
): EstadoReporte<T> {
  const [resultado, setResultado] = useState<Resultado<T> | null>(null);
  const [intento, setIntento] = useState(0);

  const desde = filtros?.desde;
  const hasta = filtros?.hasta;
  const idTipoPrenda = filtros?.idTipoPrenda ?? null;
  const clave = desde && hasta ? `${desde}|${hasta}|${idTipoPrenda ?? ''}|${intento}` : null;

  useEffect(() => {
    if (!clave || !desde || !hasta) return;
    const controlador = new AbortController();
    cargar({ desde, hasta, idTipoPrenda }, controlador.signal)
      .then((datos) => setResultado({ clave, datos, error: null }))
      .catch((err) => {
        if (controlador.signal.aborted) return;
        setResultado({ clave, datos: null, error: extraerMensajeError(err, mensajeError) });
      });
    return () => controlador.abort();
  }, [cargar, clave, desde, hasta, idTipoPrenda, mensajeError]);

  const reintentar = useCallback(() => setIntento((n) => n + 1), []);

  // "Cargando" se deriva: hay filtros y el resultado guardado todavía es de otros filtros.
  const cargando = clave !== null && resultado?.clave !== clave;

  return {
    datos: resultado?.datos ?? null,
    cargando,
    error: cargando ? null : (resultado?.error ?? null),
    reintentar,
  };
}
