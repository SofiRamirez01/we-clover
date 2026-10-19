import ComboboxColor from '../../components/ComboboxColor';
import type { PaletaColorResponse } from '../../types/paletaColores';
import type { GrupoAsignacionInsumoResponse, TipoInsumoCatalogo } from '../../types/tipoInsumo';
import {
  CODIGO_TIPO_INSUMO_CAPUCHA,
  CODIGO_TIPO_INSUMO_CIERRE,
  CODIGO_TIPO_INSUMO_ESTAMPADO,
  CODIGO_TIPO_INSUMO_PUNOS_CINTURA,
} from '../../types/tipoInsumo';
import type { TipoTelaCatalogo } from '../../types/tipoTela';
import { cantidadInicial, filaNueva, pinesSinAsignar, repartirGramos } from './insumosFicha';
import type { AsignacionesPin, FilaInsumoFicha } from './insumosFicha';

const inputSelectClase =
  'rounded-lg border border-wc-border bg-white px-2 py-1.5 text-xs text-wc-text outline-none disabled:bg-wc-bg disabled:text-wc-text-muted';

/** Tipos que siempre tienen su sección en un Buzo o una Campera, aunque todavía no tengan filas. */
const CODIGOS_CON_SECCION_FIJA = [CODIGO_TIPO_INSUMO_CAPUCHA, CODIGO_TIPO_INSUMO_PUNOS_CINTURA];

interface SeccionInsumosSecundariosProps {
  filas: FilaInsumoFicha[];
  onCambiarFilas: (filas: FilaInsumoFicha[]) => void;
  tiposInsumo: TipoInsumoCatalogo[];
  tiposTela: TipoTelaCatalogo[];
  coloresParaTipoTela: (idTipoTela: number | '') => PaletaColorResponse[];
  /** Buzo o Campera: muestran las secciones de Capucha y Puños y cintura aunque estén vacías. */
  mostrarCapuchaYPunos: boolean;
  /** Pines de la moldería del producto, por tipo de insumo (vacío si no hay moldería o piezas con tela propia). */
  gruposPines: GrupoAsignacionInsumoResponse[];
  asignacionesPin: AsignacionesPin;
  onCambiarAsignacionesPin: (asignaciones: AsignacionesPin) => void;
  llevaEstampado: boolean;
  onCambiarLlevaEstampado: (lleva: boolean) => void;
  /** Mensaje si no se pudo cargar el catálogo de tipos de insumo. */
  errorCatalogo: string | null;
}

/**
 * Insumos secundarios de la Ficha: una sección por tipo de insumo (Capucha, Puños y cintura…)
 * y otra para los insumos libres. Cada fila elige su tipo — o "Otro (texto libre)" —, tela,
 * color y gramos. Cuando un tipo tiene dos o más filas, bajo cada una se marcan los pines de la
 * moldería que cubre. El Cierre no se edita acá (tiene su propio selector en el modal).
 */
export default function SeccionInsumosSecundarios({
  filas,
  onCambiarFilas,
  tiposInsumo,
  tiposTela,
  coloresParaTipoTela,
  mostrarCapuchaYPunos,
  gruposPines,
  asignacionesPin,
  onCambiarAsignacionesPin,
  llevaEstampado,
  onCambiarLlevaEstampado,
  errorCatalogo,
}: SeccionInsumosSecundariosProps) {
  /** Tipos que se pueden elegir en una fila: los que consumen material, salvo el Cierre. */
  const tiposElegibles = tiposInsumo.filter((t) => t.consumeMaterial && t.codigo !== CODIGO_TIPO_INSUMO_CIERRE);
  const tipoEstampado = tiposInsumo.find((t) => t.codigo === CODIGO_TIPO_INSUMO_ESTAMPADO);

  const tiposConSeccion = tiposElegibles
    .filter((tipo) => filas.some((f) => f.idTipoInsumo === tipo.id) || (mostrarCapuchaYPunos && CODIGOS_CON_SECCION_FIJA.includes(tipo.codigo)))
    .sort((a, b) => ordenSeccion(a) - ordenSeccion(b));
  const filasLibres = filas.filter((f) => f.idTipoInsumo == null || !tiposElegibles.some((t) => t.id === f.idTipoInsumo));

  function ordenSeccion(tipo: TipoInsumoCatalogo): number {
    const fijo = CODIGOS_CON_SECCION_FIJA.indexOf(tipo.codigo);
    return fijo >= 0 ? fijo : CODIGOS_CON_SECCION_FIJA.length;
  }

  /** Cualquier cambio de estructura (alta, baja, tipo o tela) vuelve a repartir los gramos sugeridos. */
  function publicar(nuevas: FilaInsumoFicha[]) {
    onCambiarFilas(repartirGramos(nuevas, tiposInsumo, tiposTela));
  }

  function cambiarFila(clave: string, cambio: Partial<FilaInsumoFicha>, repartir = true) {
    const nuevas = filas.map((f) => (f.clave === clave ? { ...f, ...cambio } : f));
    if (repartir) publicar(nuevas);
    else onCambiarFilas(nuevas);
  }

  function quitarAsignacionesDe(clave: string) {
    const restantes = Object.fromEntries(Object.entries(asignacionesPin).filter(([, c]) => c !== clave));
    if (Object.keys(restantes).length !== Object.keys(asignacionesPin).length) onCambiarAsignacionesPin(restantes);
  }

  function agregar(tipo: TipoInsumoCatalogo | null) {
    publicar([...filas, filaNueva(tipo, tiposTela)]);
  }

  function eliminar(clave: string) {
    quitarAsignacionesDe(clave);
    publicar(filas.filter((f) => f.clave !== clave));
  }

  function cambiarTipoInsumo(fila: FilaInsumoFicha, valor: string) {
    const tipo = valor === '' ? null : (tiposInsumo.find((t) => t.id === Number(valor)) ?? null);
    const telaActual = tiposTela.find((t) => t.id === fila.idTipoTela);
    const telaSugerida = tipo?.idTipoTelaSugerido != null ? tiposTela.find((t) => t.id === tipo.idTipoTelaSugerido) : undefined;
    // Se conserva la tela ya elegida salvo que el tipo nuevo no la admita (ej. Capucha en "Cierre").
    const telaInvalida = !!tipo?.soloTelasPorPeso && !!telaActual && !telaActual.esPorPeso;
    const tela = !telaActual || telaInvalida ? telaSugerida : telaActual;
    const cambiaTela = tela?.id !== telaActual?.id;

    quitarAsignacionesDe(fila.clave);
    cambiarFila(fila.clave, {
      idTipoInsumo: tipo?.id ?? null,
      descripcion: tipo ? '' : fila.descripcion,
      idTipoTela: tela?.id ?? '',
      idPaletaColor: cambiaTela ? '' : fila.idPaletaColor,
      cantidad: cambiaTela && tela ? cantidadInicial(tela) : fila.cantidad,
      cantidadEditadaManualmente: cambiaTela ? false : fila.cantidadEditadaManualmente,
    });
  }

  /** Al cambiar la tela se resetea el color (la paleta cambia) y la cantidad vuelve a la sugerida. */
  function cambiarTela(fila: FilaInsumoFicha, valor: string) {
    const tela = valor === '' ? undefined : tiposTela.find((t) => t.id === Number(valor));
    cambiarFila(fila.clave, {
      idTipoTela: tela?.id ?? '',
      idPaletaColor: '',
      cantidad: tela ? cantidadInicial(tela) : '',
      cantidadEditadaManualmente: false,
    });
  }

  function telasPara(fila: FilaInsumoFicha): TipoTelaCatalogo[] {
    const tipo = tiposInsumo.find((t) => t.id === fila.idTipoInsumo);
    return tipo?.soloTelasPorPeso ? tiposTela.filter((t) => t.esPorPeso) : tiposTela;
  }

  function volverASugeridos(idTipoInsumo: number) {
    publicar(filas.map((f) => (f.idTipoInsumo === idTipoInsumo ? { ...f, cantidadEditadaManualmente: false } : f)));
  }

  function alternarPin(idPosicionPieza: number, clave: string) {
    const restantes = { ...asignacionesPin };
    if (restantes[idPosicionPieza] === clave) delete restantes[idPosicionPieza];
    else restantes[idPosicionPieza] = clave;
    onCambiarAsignacionesPin(restantes);
  }

  function renderFila(fila: FilaInsumoFicha, grupoPines: GrupoAsignacionInsumoResponse | undefined, filasDelTipo: number) {
    const esLibre = !tiposElegibles.some((t) => t.id === fila.idTipoInsumo);
    const mostrarPines = !!grupoPines && grupoPines.pines.length > 0 && filasDelTipo >= 2;

    return (
      <div key={fila.clave} className="flex flex-col gap-1">
        <div className="flex flex-wrap items-center gap-1.5">
          <select
            value={esLibre ? '' : (fila.idTipoInsumo ?? '')}
            onChange={(e) => cambiarTipoInsumo(fila, e.target.value)}
            aria-label="Tipo de insumo"
            className={`${inputSelectClase} w-[7.5rem] flex-none`}
          >
            {tiposElegibles.map((tipo) => (
              <option key={tipo.id} value={tipo.id}>
                {tipo.nombre}
              </option>
            ))}
            <option value="">Otro (texto libre)</option>
          </select>
          {esLibre && (
            <input
              value={fila.descripcion}
              onChange={(e) => cambiarFila(fila.clave, { descripcion: e.target.value }, false)}
              placeholder="¿Para qué es? (ej: Cuello)"
              className={`${inputSelectClase} min-w-[9rem] flex-[2]`}
            />
          )}
          <select
            value={fila.idTipoTela}
            onChange={(e) => cambiarTela(fila, e.target.value)}
            aria-label="Tela"
            className={`${inputSelectClase} w-[5.5rem] flex-none`}
          >
            <option value="">Elegir tela…</option>
            {telasPara(fila).map((tela) => (
              <option key={tela.id} value={tela.id}>
                {tela.nombre}
              </option>
            ))}
          </select>
          <ComboboxColor
            colores={coloresParaTipoTela(fila.idTipoTela)}
            value={fila.idPaletaColor}
            onChange={(idPaletaColor) => cambiarFila(fila.clave, { idPaletaColor }, false)}
            placeholder="Color…"
            disabled={fila.idTipoTela === ''}
            compacto
            className="flex-1 min-w-[6rem]"
          />
          <input
            type="number"
            min={0}
            step="0.01"
            value={fila.cantidad}
            onChange={(e) => cambiarFila(fila.clave, { cantidad: e.target.value, cantidadEditadaManualmente: true }, false)}
            disabled={fila.idTipoTela === ''}
            placeholder="Cant."
            aria-label="Cantidad"
            title={fila.cantidadEditadaManualmente ? 'Cantidad escrita a mano' : 'Cantidad sugerida'}
            className={`${inputSelectClase} w-14`}
          />
          <button
            type="button"
            onClick={() => eliminar(fila.clave)}
            aria-label="Eliminar insumo"
            className="h-6 w-6 shrink-0 rounded text-sm font-bold text-wc-text-muted transition hover:bg-wc-bg hover:text-red-600"
          >
            ×
          </button>
        </div>

        {mostrarPines && (
          <div className="flex flex-wrap items-center gap-1 pl-1">
            <span className="text-[11px] text-wc-text-muted">Cubre:</span>
            {grupoPines.pines.map((pin) => {
              const claveAsignada = asignacionesPin[pin.idPosicionPieza];
              const deEstaFila = claveAsignada === fila.clave;
              const deOtraFila = !deEstaFila && filas.some((f) => f.clave === claveAsignada && f.idTipoInsumo === fila.idTipoInsumo);
              return (
                <button
                  key={pin.idPosicionPieza}
                  type="button"
                  onClick={() => alternarPin(pin.idPosicionPieza, fila.clave)}
                  aria-pressed={deEstaFila}
                  title={deOtraFila ? 'Asignado a otra fila: tocá para pasarlo a esta' : undefined}
                  className={`rounded-full border px-2 py-0.5 text-[11px] font-medium transition ${
                    deEstaFila
                      ? 'border-wc-green bg-wc-green text-white'
                      : deOtraFila
                        ? 'border-wc-border bg-wc-bg text-wc-text-muted'
                        : 'border-wc-border bg-white text-wc-text hover:border-wc-green'
                  }`}
                >
                  {pin.etiqueta}
                </button>
              );
            })}
          </div>
        )}
      </div>
    );
  }

  return (
    <>
      <span className="text-xs font-semibold text-wc-text">Insumos secundarios</span>

      {errorCatalogo && <p className="text-xs font-medium text-red-600">{errorCatalogo}</p>}

      {tiposConSeccion.map((tipo) => {
        const filasDelTipo = filas.filter((f) => f.idTipoInsumo === tipo.id);
        const grupoPines = gruposPines.find((g) => g.idTipoInsumo === tipo.id);
        const faltantes = grupoPines ? pinesSinAsignar(grupoPines, filasDelTipo, asignacionesPin) : [];
        const hayEditadas = filasDelTipo.some((f) => f.cantidadEditadaManualmente);

        return (
          <div key={tipo.id} className="flex flex-col gap-1">
            <span className="text-[11px] font-medium text-wc-text-muted">{tipo.nombre}</span>
            {filasDelTipo.map((fila) => renderFila(fila, grupoPines, filasDelTipo.length))}
            {faltantes.length > 0 && (
              <p role="status" className="rounded-md border border-amber-300 bg-amber-50 px-2 py-1 text-[11px] font-medium text-amber-800">
                Falta asignar: {faltantes.map((pin) => pin.etiqueta).join(', ')}. No impide guardar, pero el corte no va a saber
                de qué color es.
              </p>
            )}
            <div className="flex flex-wrap items-center gap-3">
              <button type="button" onClick={() => agregar(tipo)} className="text-[11px] font-semibold text-wc-green underline">
                + Agregar color a {tipo.nombre}
              </button>
              {hayEditadas && (
                <button
                  type="button"
                  onClick={() => volverASugeridos(tipo.id)}
                  title="Hay cantidades escritas a mano: mientras tanto no se reparten solas"
                  className="text-[11px] font-semibold text-wc-text-muted underline"
                >
                  Volver a los gramos sugeridos
                </button>
              )}
            </div>
          </div>
        );
      })}

      {filasLibres.length > 0 && (
        <div className="flex flex-col gap-1">
          {tiposConSeccion.length > 0 && <span className="text-[11px] font-medium text-wc-text-muted">Otros insumos</span>}
          {filasLibres.map((fila) => renderFila(fila, undefined, 0))}
        </div>
      )}

      <button type="button" onClick={() => agregar(null)} className="self-start text-xs font-semibold text-wc-green underline">
        + Agregar insumo
      </button>

      {tipoEstampado && (
        // Un <div> y no un <label>: el estilo global de label lo apila en columna.
        <div className="flex flex-wrap items-center gap-2 text-xs text-wc-text">
          <input
            type="checkbox"
            aria-label="Lleva estampado"
            checked={llevaEstampado}
            onChange={(e) => onCambiarLlevaEstampado(e.target.checked)}
          />
          <span>Lleva estampado</span>
          <span className="text-[11px] text-wc-text-muted">(suma la etapa Estampado en Producción)</span>
        </div>
      )}
    </>
  );
}
