import type { ProductoResponse } from '../types/pedido';
import type { TipoTelaCatalogo } from '../types/tipoTela';

/**
 * Cuánto de un (tipoTela, color) consume un producto — tela de cuerpo (gramos de cada
 * posición ya marcada, según el patrón de corte) más insumos secundarios ya cargados, todo
 * multiplicado por cantidadTotal. Espeja exactamente PlanificacionCompraService.calcularDetalles
 * del backend (mismo criterio de agrupación), para que el contador en vivo de la pantalla de
 * creación coincida con lo que el backend va a persistir al confirmar — es solo una
 * previsualización client-side, el cálculo real y autoritativo sigue siendo el del backend.
 */
export interface ConsumoArticulo {
  codigoTipoTela: string;
  nombreTipoTela: string;
  esPorPeso: boolean;
  idPaletaColor: number;
  nombreColor: string;
  hexColor: string;
  cantidad: number;
}

const GRAMOS_POR_KILOGRAMO = 1000;

/** `patronCorteColores[].gramos` y `insumosSecundarios[].cantidad` son gramos por prenda (la
 *  receta de la moldería) — eso no cambia. Al agregar el consumo de un lote de prendas, el
 *  resultado se expresa en la unidad en la que efectivamente se compra la tela (kilogramos),
 *  no en gramos — espeja PlanificacionCompraService.convertirAUnidadDeCompra del backend. Los
 *  insumos que se compran por unidad (ej. Cierre) no se convierten. */
function convertirAUnidadDeCompra(cantidadEnGramos: number, esPorPeso: boolean): number {
  return esPorPeso ? cantidadEnGramos / GRAMOS_POR_KILOGRAMO : cantidadEnGramos;
}

export function calcularConsumoProducto(producto: ProductoResponse, tiposTela: TipoTelaCatalogo[]): ConsumoArticulo[] {
  const acumulado = new Map<string, ConsumoArticulo>();

  function sumar(
    codigoTipoTela: string,
    nombreTipoTela: string,
    esPorPeso: boolean,
    idPaletaColor: number,
    nombreColor: string,
    hexColor: string,
    cantidad: number,
  ) {
    const clave = `${codigoTipoTela}::${idPaletaColor}`;
    const existente = acumulado.get(clave);
    if (existente) {
      existente.cantidad += cantidad;
    } else {
      acumulado.set(clave, { codigoTipoTela, nombreTipoTela, esPorPeso, idPaletaColor, nombreColor, hexColor, cantidad });
    }
  }

  const tipoCuerpo = producto.tipoTela ? tiposTela.find((t) => t.codigo === producto.tipoTela) : undefined;
  if (tipoCuerpo && producto.patronCorteColores) {
    const gramosPorOrden = new Map(producto.patronCorteColores.map((p) => [p.orden, p.gramos]));
    for (const color of producto.colores) {
      const gramos = gramosPorOrden.get(color.ordenPatronCorteColor) ?? 0;
      const cantidad = convertirAUnidadDeCompra(gramos * producto.cantidadTotal, tipoCuerpo.esPorPeso);
      sumar(tipoCuerpo.codigo, tipoCuerpo.nombre, tipoCuerpo.esPorPeso, color.idPaletaColor, color.nombreColor, color.hexColor, cantidad);
    }
  }

  for (const insumo of producto.insumosSecundarios) {
    const cantidad = convertirAUnidadDeCompra(insumo.cantidad * producto.cantidadTotal, insumo.esPorPeso);
    sumar(insumo.tipoTela, insumo.nombreTipoTela, insumo.esPorPeso, insumo.idPaletaColor, insumo.nombreColor, insumo.hexColor, cantidad);
  }

  return Array.from(acumulado.values());
}

/** Suma el consumo de varios productos (ej. todos los tildados en la pantalla de creación). */
export function calcularConsumoTotal(productos: ProductoResponse[], tiposTela: TipoTelaCatalogo[]): ConsumoArticulo[] {
  const acumulado = new Map<string, ConsumoArticulo>();
  for (const producto of productos) {
    for (const item of calcularConsumoProducto(producto, tiposTela)) {
      const clave = `${item.codigoTipoTela}::${item.idPaletaColor}`;
      const existente = acumulado.get(clave);
      if (existente) {
        existente.cantidad += item.cantidad;
      } else {
        acumulado.set(clave, { ...item });
      }
    }
  }
  return Array.from(acumulado.values()).sort(
    (a, b) => a.nombreTipoTela.localeCompare(b.nombreTipoTela) || a.nombreColor.localeCompare(b.nombreColor),
  );
}
