import { useEffect, useState } from 'react';
import { listarPedidosPaginado } from '../../services/pedidoService';
import type { ContadorEstadoPedido, EstadoPedido, FiltrosListadoPedidos, PedidoResponse } from '../../types/pedido';

export const TAMANIO_PAGINA = 50;

export type EstadoCarga = 'cargando' | 'listo' | 'error';

/** Lo cargado para una consulta (filtros + número de recarga): si `solicitud` no es la
 *  consulta actual, esos datos son de filtros anteriores y el listado está cargando. */
interface Datos {
  solicitud: string;
  pedidos: PedidoResponse[];
  contadores: ContadorEstadoPedido[];
  total: number;
  pagina: number;
  hayMas: boolean;
}

/** Pasa un pedido de un estado a otro en los contadores, sin volver a pedirlos al backend.
 *  Sirve porque todo pedido del listado entra en los contadores (que usan menos filtros). */
function moverEnContadores(
  contadores: ContadorEstadoPedido[],
  pedido: PedidoResponse,
  desde: EstadoPedido,
  hacia: EstadoPedido,
): ContadorEstadoPedido[] {
  if (desde === hacia) return contadores;
  const unidades: Record<string, number> = {};
  for (const producto of pedido.productos) {
    if (producto.tipoPrenda) {
      unidades[producto.tipoPrenda] = (unidades[producto.tipoPrenda] ?? 0) + producto.cantidadTotal;
    }
  }
  return contadores.map((contador) => {
    const signo = contador.estado === desde ? -1 : contador.estado === hacia ? 1 : 0;
    if (signo === 0) return contador;
    const unidadesPorTipoPrenda = { ...contador.unidadesPorTipoPrenda };
    for (const [tipo, cantidad] of Object.entries(unidades)) {
      unidadesPorTipoPrenda[tipo] = Math.max(0, (unidadesPorTipoPrenda[tipo] ?? 0) + signo * cantidad);
    }
    return { ...contador, pedidos: Math.max(0, contador.pedidos + signo), unidadesPorTipoPrenda };
  });
}

/**
 * Listado de Base Ventas paginado en el backend (GET /pedidos/listado). Vuelve a la página 0
 * cada vez que cambian los filtros; las respuestas de filtros anteriores que lleguen tarde se
 * descartan (la carga anterior se aborta y cada resultado sabe de qué consulta es).
 */
export function useListadoPedidos(filtros: FiltrosListadoPedidos) {
  const clave = JSON.stringify(filtros);
  const [recargas, setRecargas] = useState(0);
  const solicitud = `${recargas}|${clave}`;
  const [datos, setDatos] = useState<Datos | null>(null);
  const [solicitudConError, setSolicitudConError] = useState<string | null>(null);
  const [cargandoMas, setCargandoMas] = useState(false);
  const [solicitudConErrorMas, setSolicitudConErrorMas] = useState<string | null>(null);

  useEffect(() => {
    const controlador = new AbortController();
    const filtrosActuales = JSON.parse(clave) as FiltrosListadoPedidos;
    const actual = `${recargas}|${clave}`;
    listarPedidosPaginado({ ...filtrosActuales, pagina: 0, tamanio: TAMANIO_PAGINA }, controlador.signal)
      .then((respuesta) => {
        if (controlador.signal.aborted) return;
        setDatos({
          solicitud: actual,
          pedidos: respuesta.pedidos,
          contadores: respuesta.contadores,
          total: respuesta.total,
          pagina: respuesta.pagina,
          hayMas: respuesta.hayMas,
        });
      })
      .catch(() => {
        if (!controlador.signal.aborted) setSolicitudConError(actual);
      });
    return () => controlador.abort();
  }, [clave, recargas]);

  const vigentes = datos?.solicitud === solicitud ? datos : null;
  const estadoCarga: EstadoCarga = vigentes ? 'listo' : solicitudConError === solicitud ? 'error' : 'cargando';

  function cargarMas() {
    if (!vigentes?.hayMas || cargandoMas) return;
    const actual = solicitud;
    const filtrosActuales = JSON.parse(clave) as FiltrosListadoPedidos;
    setCargandoMas(true);
    setSolicitudConErrorMas(null);
    listarPedidosPaginado({ ...filtrosActuales, pagina: vigentes.pagina + 1, tamanio: TAMANIO_PAGINA })
      .then((respuesta) => {
        setDatos((previos) => {
          if (!previos || previos.solicitud !== actual) return previos;
          return {
            ...previos,
            // Si algún pedido cambió de lugar entre páginas (ej. lo editaron recién), no se repite.
            pedidos: [
              ...previos.pedidos,
              ...respuesta.pedidos.filter((nuevo) => !previos.pedidos.some((previo) => previo.id === nuevo.id)),
            ],
            contadores: respuesta.contadores,
            total: respuesta.total,
            pagina: respuesta.pagina,
            hayMas: respuesta.hayMas,
          };
        });
      })
      .catch(() => setSolicitudConErrorMas(actual))
      .finally(() => setCargandoMas(false));
  }

  /** Reemplaza la fila de un pedido (ej. después de cambiarle el estado) y ajusta los contadores. */
  function reemplazarPedido(actualizado: PedidoResponse) {
    setDatos((previos) => {
      if (!previos) return previos;
      const anterior = previos.pedidos.find((pedido) => pedido.id === actualizado.id);
      if (!anterior) return previos;
      return {
        ...previos,
        pedidos: previos.pedidos.map((pedido) => (pedido.id === actualizado.id ? actualizado : pedido)),
        contadores: moverEnContadores(previos.contadores, anterior, anterior.estadoActual, actualizado.estadoActual),
      };
    });
  }

  function recargar() {
    setRecargas((valor) => valor + 1);
  }

  return {
    pedidos: vigentes?.pedidos ?? [],
    // Mientras cargan filtros nuevos se siguen mostrando los contadores anteriores (no saltan a 0).
    contadores: datos?.contadores ?? [],
    total: vigentes?.total ?? 0,
    hayMas: vigentes?.hayMas ?? false,
    estadoCarga,
    cargandoMas,
    errorCargarMas: solicitudConErrorMas === solicitud,
    cargarMas,
    reemplazarPedido,
    recargar,
  };
}

/** Devuelve el valor recién cuando dejó de cambiar durante `demoraMs` (para la búsqueda). */
export function useValorDiferido<T>(valor: T, demoraMs = 300): T {
  const [diferido, setDiferido] = useState(valor);
  useEffect(() => {
    const temporizador = setTimeout(() => setDiferido(valor), demoraMs);
    return () => clearTimeout(temporizador);
  }, [valor, demoraMs]);
  return diferido;
}
