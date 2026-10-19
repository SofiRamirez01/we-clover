import type { ProductoInsumoSecundarioItemRequest, ProductoInsumoSecundarioResponse } from '../../types/paletaColores';
import type { GrupoAsignacionInsumoResponse, TipoInsumoCatalogo } from '../../types/tipoInsumo';
import { CODIGO_TIPO_INSUMO_CIERRE, CODIGO_TIPO_INSUMO_ESTAMPADO } from '../../types/tipoInsumo';
import type { TipoTelaCatalogo } from '../../types/tipoTela';

/**
 * Una fila de insumo secundario mientras se edita en la Ficha (borrador hasta el Guardar
 * general). No incluye el Cierre (tiene su propio selector) ni el Estampado (es un tilde).
 */
export interface FilaInsumoFicha {
  /** Identidad local de la fila en pantalla; las filas nuevas todavía no tienen id del backend. */
  clave: string;
  /** Id de la fila ya guardada, o null si se agregó en esta sesión. */
  idInsumo: number | null;
  /** Tipo del catálogo, o null = "Otro (texto libre)". */
  idTipoInsumo: number | null;
  /** Solo para "Otro": qué es el insumo. */
  descripcion: string;
  idTipoTela: number | '';
  idPaletaColor: number | '';
  /** String crudo del input numérico; se valida y convierte recién al guardar. */
  cantidad: string;
  /** true si el usuario escribió la cantidad: congela el reparto automático de su tipo. */
  cantidadEditadaManualmente: boolean;
}

/** Pin de la moldería → clave de la fila que lo cubre. */
export type AsignacionesPin = Record<number, string>;

function esCodigo(insumo: ProductoInsumoSecundarioResponse, codigo: string): boolean {
  return insumo.codigoTipoInsumo === codigo;
}

export function insumoCierre(insumos: ProductoInsumoSecundarioResponse[]): ProductoInsumoSecundarioResponse | undefined {
  return insumos.find((i) => esCodigo(i, CODIGO_TIPO_INSUMO_CIERRE));
}

export function insumoEstampado(insumos: ProductoInsumoSecundarioResponse[]): ProductoInsumoSecundarioResponse | undefined {
  return insumos.find((i) => esCodigo(i, CODIGO_TIPO_INSUMO_ESTAMPADO));
}

/** Filas editables a partir de lo que ya guardó el backend (todo menos Cierre y Estampado). */
export function filasDesdeInsumos(insumos: ProductoInsumoSecundarioResponse[]): FilaInsumoFicha[] {
  return insumos
    .filter((i) => !esCodigo(i, CODIGO_TIPO_INSUMO_CIERRE) && !esCodigo(i, CODIGO_TIPO_INSUMO_ESTAMPADO))
    .map((i) => ({
      clave: crypto.randomUUID(),
      idInsumo: i.id,
      idTipoInsumo: i.idTipoInsumo ?? null,
      descripcion: i.idTipoInsumo != null ? '' : i.descripcion,
      idTipoTela: i.idTipoTela ?? '',
      idPaletaColor: i.idPaletaColor ?? '',
      cantidad: i.cantidad != null ? String(i.cantidad) : '',
      cantidadEditadaManualmente: i.cantidadEditadaManualmente ?? false,
    }));
}

/** Fila nueva de ese tipo (o libre si es null), con la tela sugerida del tipo ya elegida. */
export function filaNueva(tipo: TipoInsumoCatalogo | null, tiposTela: TipoTelaCatalogo[]): FilaInsumoFicha {
  const tela = tipo?.idTipoTelaSugerido != null ? tiposTela.find((t) => t.id === tipo.idTipoTelaSugerido) : undefined;
  return {
    clave: crypto.randomUUID(),
    idInsumo: null,
    idTipoInsumo: tipo?.id ?? null,
    descripcion: '',
    idTipoTela: tela?.id ?? '',
    idPaletaColor: '',
    cantidad: tela ? cantidadInicial(tela) : '',
    cantidadEditadaManualmente: false,
  };
}

/** Cantidad con la que arranca una fila al elegir una tela: los gramos sugeridos si se compra
 *  por peso, 1 si es por unidad. Para las filas con tipo, el reparto la ajusta después. */
export function cantidadInicial(tela: TipoTelaCatalogo): string {
  if (!tela.esPorPeso) return '1';
  return tela.gramosSugerido != null ? String(tela.gramosSugerido) : '';
}

/** Reparte `total` en `filas` enteros que suman exactamente `total`; el resto va adelante. */
export function repartir(total: number, filas: number): number[] {
  const base = Math.floor(total / filas);
  const resto = total % filas;
  return Array.from({ length: filas }, (_, i) => base + (i < resto ? 1 : 0));
}

/**
 * Mismo reparto que hace el backend al guardar (ver RepartoGramosInsumo): las filas de un tipo
 * que comparten tela se reparten los gramos sugeridos de esa tela; una fila de otra tela recibe
 * el sugerido completo de la suya; y si alguna fila del tipo fue editada a mano, no se toca
 * ninguna de ese tipo. Acá se aplica en vivo para que se vea antes de guardar.
 */
export function repartirGramos(
  filas: FilaInsumoFicha[],
  tiposInsumo: TipoInsumoCatalogo[],
  tiposTela: TipoTelaCatalogo[],
): FilaInsumoFicha[] {
  const congelados = new Set(filas.filter((f) => f.cantidadEditadaManualmente && f.idTipoInsumo != null).map((f) => f.idTipoInsumo));
  const grupos = new Map<string, FilaInsumoFicha[]>();
  for (const fila of filas) {
    const tipo = tiposInsumo.find((t) => t.id === fila.idTipoInsumo);
    const tela = tiposTela.find((t) => t.id === fila.idTipoTela);
    if (!tipo || !tipo.consumeMaterial || congelados.has(tipo.id)) continue;
    if (!tela || !tela.esPorPeso || tela.gramosSugerido == null) continue;
    const clave = `${tipo.id}|${tela.id}`;
    grupos.set(clave, [...(grupos.get(clave) ?? []), fila]);
  }

  const cantidadPorClave = new Map<string, string>();
  for (const grupo of grupos.values()) {
    const tela = tiposTela.find((t) => t.id === grupo[0].idTipoTela)!;
    const partes = repartir(tela.gramosSugerido!, grupo.length);
    grupo.forEach((fila, i) => cantidadPorClave.set(fila.clave, String(partes[i])));
  }
  return filas.map((fila) => (cantidadPorClave.has(fila.clave) ? { ...fila, cantidad: cantidadPorClave.get(fila.clave)! } : fila));
}

/** Pines de un grupo que todavía no tienen una fila (existente) asignada. */
export function pinesSinAsignar(
  grupo: GrupoAsignacionInsumoResponse,
  filasDelTipo: FilaInsumoFicha[],
  asignaciones: AsignacionesPin,
): GrupoAsignacionInsumoResponse['pines'] {
  if (filasDelTipo.length < 2) return [];
  const claves = new Set(filasDelTipo.map((f) => f.clave));
  return grupo.pines.filter((pin) => !claves.has(asignaciones[pin.idPosicionPieza]));
}

function filaVacia(fila: FilaInsumoFicha): boolean {
  return fila.idTipoTela === '' && fila.idPaletaColor === '' && !fila.cantidad && !fila.descripcion.trim();
}

/** Un item del PUT junto con la clave local de la fila de la que salió (null para Cierre y Estampado). */
export interface ItemConOrigen {
  clave: string | null;
  item: ProductoInsumoSecundarioItemRequest;
}

/**
 * Valida las filas y arma los items del PUT. Una fila totalmente vacía no se manda (el usuario
 * la agregó y se arrepintió); una fila con algo cargado tiene que estar completa.
 */
export function construirItemsDeFilas(
  filas: FilaInsumoFicha[],
  tiposInsumo: TipoInsumoCatalogo[],
): { items: ItemConOrigen[] } | { error: string } {
  const items: ItemConOrigen[] = [];
  for (const fila of filas) {
    const tipo = tiposInsumo.find((t) => t.id === fila.idTipoInsumo);
    if (filaVacia(fila)) continue;

    const nombre = tipo ? tipo.nombre : fila.descripcion.trim();
    if (!nombre) {
      return { error: 'Escribí para qué es cada insumo agregado a mano.' };
    }
    if (fila.idTipoTela === '' || fila.idPaletaColor === '') {
      return { error: `Elegí tipo de tela y color para "${nombre}".` };
    }
    const cantidad = Number(fila.cantidad);
    if (!fila.cantidad || !Number.isFinite(cantidad) || cantidad <= 0) {
      return { error: `Ingresá una cantidad válida para "${nombre}".` };
    }
    items.push({
      clave: fila.clave,
      item: {
        id: fila.idInsumo,
        idTipoInsumo: tipo ? tipo.id : null,
        descripcion: tipo ? null : nombre,
        idTipoTela: fila.idTipoTela,
        idPaletaColor: fila.idPaletaColor,
        cantidad,
        cantidadEditadaManualmente: fila.cantidadEditadaManualmente,
      },
    });
  }
  return { items };
}

/**
 * Después de guardar: a qué id del backend quedó asociada cada fila local. Las que ya tenían
 * id lo conservan; las nuevas se emparejan, en el mismo orden en que viajaron, con los ids que
 * aparecen en la respuesta y no se habían enviado.
 */
export function idsPorClave(enviados: ItemConOrigen[], guardados: ProductoInsumoSecundarioResponse[]): Map<string, number> {
  const idsEnviados = new Set(enviados.map((e) => e.item.id).filter((id): id is number => id != null));
  const idsNuevos = guardados.map((i) => i.id).filter((id) => !idsEnviados.has(id));
  const resultado = new Map<string, number>();
  let siguiente = 0;
  for (const { clave, item } of enviados) {
    const id = item.id ?? idsNuevos[siguiente++];
    if (clave != null && id != null) resultado.set(clave, id);
  }
  return resultado;
}
