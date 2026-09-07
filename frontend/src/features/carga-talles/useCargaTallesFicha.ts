import { useEffect, useState } from 'react';
import { cerrarCargaTalles, obtenerCargaTallesInterno, reabrirCargaTalles } from '../../services/cargaTallesService';
import { extraerMensajeError } from '../../utils/errores';
import type { CargaTallesResponse } from '../../types/cargaTalles';

/** Trae y administra la carga de talles de un Pedido puntual para mostrarla en su tarjeta de
 *  Ficha Técnica: el link (para copiar), el estado (para cerrar/reabrir) y el resumen por
 *  producto (para el conteo de talles cargados). El link ya existe siempre (se genera solo al
 *  crear el pedido), así que acá no hay paso de "generar". */
export function useCargaTallesFicha(idPedido: number) {
  const [carga, setCarga] = useState<CargaTallesResponse | null>(null);
  const [cargando, setCargando] = useState(true);
  const [accionando, setAccionando] = useState(false);
  const [error, setError] = useState<string | null>(null);

  function recargar() {
    obtenerCargaTallesInterno(idPedido)
      .then(setCarga)
      .catch(() => setCarga(null))
      .finally(() => setCargando(false));
  }

  useEffect(() => {
    recargar();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [idPedido]);

  async function cerrarOReabrir() {
    if (!carga) return;
    setAccionando(true);
    setError(null);
    try {
      if (carga.estado === 'ABIERTO') {
        await cerrarCargaTalles(idPedido);
      } else {
        await reabrirCargaTalles(idPedido);
      }
      recargar();
    } catch (err) {
      setError(extraerMensajeError(err, 'No se pudo actualizar el estado de la carga.'));
    } finally {
      setAccionando(false);
    }
  }

  return { carga, cargando, accionando, error, cerrarOReabrir };
}
