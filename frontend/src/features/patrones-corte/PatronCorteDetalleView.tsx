import { useEffect, useRef, useState } from 'react';
import type { MouseEvent } from 'react';
import { obtenerPatronCorte, urlImagenPatronCorte } from '../../services/patronCorteService';
import { agregarPosicionPieza, actualizarPosicionPieza, eliminarPosicionPieza } from '../../services/patronCortePosicionPiezaService';
import PiezaPickerPanel from '../piezas/PiezaPickerPanel';
import MiniaturaContorno from '../piezas/MiniaturaContorno';
import { extraerMensajeError } from '../../utils/errores';
import type { PatronCorteColorResponse, PatronCortePosicionPiezaResponse, PatronCorteResponse } from '../../types/patronCorte';
import type { PiezaResumenResponse } from '../../types/pieza';

interface PatronCorteDetalleViewProps {
  patronCorteId: number;
}

/**
 * - 'agregar': se eligió "+ Agregar pieza" en la tarjeta de un color. Mientras piezaElegida es
 *   null se muestra el buscador de piezas en esa misma tarjeta; una vez elegida, se espera el
 *   click sobre la imagen para ubicar el pin (recién ahí se dispara el POST).
 * - 'cambiar': se eligió "Cambiar" sobre un pin ya existente — mismo buscador, pero al elegir
 *   una pieza se dispara un PUT de una, sin tocar la posición del pin.
 */
type AccionPanel =
  | { tipo: 'agregar'; colorId: number; piezaElegida: PiezaResumenResponse | null }
  | { tipo: 'cambiar'; color: PatronCorteColorResponse; posicion: PatronCortePosicionPiezaResponse };

/**
 * Detalle de una moldería con sus pines de Piezas sobre la imagen (Requisito 4.1, Parte 4).
 * Todo el flujo de elegir color + elegir pieza vive en el panel de la derecha, junto al listado
 * de piezas ya asignadas a cada color — la imagen (más chica, a la izquierda) solo se usa para
 * el click puntual que ubica un pin nuevo.
 */
export default function PatronCorteDetalleView({ patronCorteId }: PatronCorteDetalleViewProps) {
  const [patron, setPatron] = useState<PatronCorteResponse | null>(null);
  const [cargando, setCargando] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const [accion, setAccion] = useState<AccionPanel | null>(null);
  const [ocupado, setOcupado] = useState(false);
  const [errorPin, setErrorPin] = useState<string | null>(null);

  const contenedorImagenRef = useRef<HTMLDivElement | null>(null);

  useEffect(() => {
    let cancelado = false;
    setCargando(true);
    setError(null);
    obtenerPatronCorte(patronCorteId)
      .then((data) => {
        if (cancelado) return;
        setPatron(data);
        setCargando(false);
      })
      .catch((err) => {
        if (cancelado) return;
        setError(extraerMensajeError(err, 'No se pudo cargar la moldería.'));
        setCargando(false);
      });
    return () => {
      cancelado = true;
    };
  }, [patronCorteId]);

  async function recargar() {
    const data = await obtenerPatronCorte(patronCorteId);
    setPatron(data);
  }

  function iniciarAgregar(colorId: number) {
    setAccion({ tipo: 'agregar', colorId, piezaElegida: null });
    setErrorPin(null);
  }

  function iniciarCambiar(color: PatronCorteColorResponse, posicion: PatronCortePosicionPiezaResponse) {
    setAccion({ tipo: 'cambiar', color, posicion });
    setErrorPin(null);
  }

  function cancelarAccion() {
    setAccion(null);
    setErrorPin(null);
  }

  /** Solo hace algo si ya se eligió qué pieza agregar y se está esperando el click de ubicación. */
  async function handleClickImagen(e: MouseEvent<HTMLDivElement>) {
    if (!accion || accion.tipo !== 'agregar' || !accion.piezaElegida) return;
    const contenedor = contenedorImagenRef.current;
    if (!contenedor) return;
    const rect = contenedor.getBoundingClientRect();
    const x = (e.clientX - rect.left) / rect.width;
    const y = (e.clientY - rect.top) / rect.height;

    setOcupado(true);
    setErrorPin(null);
    try {
      await agregarPosicionPieza(patronCorteId, accion.colorId, {
        piezaId: accion.piezaElegida.id,
        coordenadaXPin: x,
        coordenadaYPin: y,
      });
      await recargar();
      setAccion(null);
    } catch (err) {
      setErrorPin(extraerMensajeError(err, 'No se pudo asignar la pieza.'));
    } finally {
      setOcupado(false);
    }
  }

  async function handleSeleccionarEnPanel(pieza: PiezaResumenResponse) {
    if (!accion) return;
    if (accion.tipo === 'agregar') {
      // Todavía falta el click sobre la imagen: solo guardamos qué pieza se eligió.
      setAccion({ ...accion, piezaElegida: pieza });
      return;
    }
    setOcupado(true);
    setErrorPin(null);
    try {
      await actualizarPosicionPieza(patronCorteId, accion.color.id, accion.posicion.id, {
        piezaId: pieza.id,
        coordenadaXPin: accion.posicion.coordenadaXPin,
        coordenadaYPin: accion.posicion.coordenadaYPin,
        etiqueta: accion.posicion.etiqueta,
      });
      await recargar();
      setAccion(null);
    } catch (err) {
      setErrorPin(extraerMensajeError(err, 'No se pudo cambiar la pieza.'));
    } finally {
      setOcupado(false);
    }
  }

  async function eliminarPosicion(color: PatronCorteColorResponse, posicion: PatronCortePosicionPiezaResponse) {
    setOcupado(true);
    setErrorPin(null);
    try {
      await eliminarPosicionPieza(patronCorteId, color.id, posicion.id);
      await recargar();
      if (accion?.tipo === 'cambiar' && accion.posicion.id === posicion.id) setAccion(null);
    } catch (err) {
      setErrorPin(extraerMensajeError(err, 'No se pudo eliminar la asignación.'));
    } finally {
      setOcupado(false);
    }
  }

  if (cargando) return <p className="text-sm text-wc-text-muted">Cargando moldería…</p>;
  if (error && !patron) return <p className="text-sm text-red-600">{error}</p>;
  if (!patron) return null;

  const coloresOrdenados = [...patron.colores].sort((a, b) => a.orden - b.orden);
  const todosLosPines = coloresOrdenados.flatMap((color) => color.piezas.map((posicion) => ({ color, posicion })));
  const esperandoClickImagen = accion?.tipo === 'agregar' && accion.piezaElegida != null;

  return (
    <div className="flex flex-col gap-4">
      <div>
        <h2 className="text-lg font-bold text-wc-text">
          #{patron.numeroInterno} {patron.nombre}
        </h2>
        <p className="mt-1 text-sm text-wc-text-muted">
          {patron.tiposPrenda.map((t) => t.nombre).join(' / ')} · {patron.cantidadColores} color
          {patron.cantidadColores > 1 ? 'es' : ''}
          {patron.nombreGrupoTalle ? ` · Grupo de talle: ${patron.nombreGrupoTalle}` : ''}
        </p>
        {patron.idGrupoTalle == null && (
          <p className="mt-1 text-xs text-wc-text-muted">
            Esta moldería no tiene un único grupo de talle determinable en sus tipos de prenda: no se le pueden asignar piezas.
          </p>
        )}
      </div>

      {errorPin && (
        <div className="rounded-lg border border-red-300 bg-red-50 px-4 py-3 text-sm font-medium text-red-700">{errorPin}</div>
      )}

      <div className="flex flex-col gap-6 lg:flex-row lg:items-start">
        {/* Imagen: mitad de pantalla, a la izquierda */}
        <div className="w-full lg:w-1/2">
          {esperandoClickImagen && (
            <p className="mb-2 text-xs font-semibold text-wc-green">Hacé click en la imagen para ubicar la pieza.</p>
          )}
          <div
            ref={contenedorImagenRef}
            onClick={handleClickImagen}
            className={`relative w-full overflow-hidden rounded-xl border border-wc-border bg-wc-bg ${
              esperandoClickImagen ? 'cursor-crosshair' : ''
            }`}
          >
            <img src={urlImagenPatronCorte(patron.imagenUrl)} alt={patron.nombre} className="block w-full select-none" draggable={false} />

            {todosLosPines.map(({ color, posicion }) => (
              <span
                key={posicion.id}
                style={{ left: `${posicion.coordenadaXPin * 100}%`, top: `${posicion.coordenadaYPin * 100}%` }}
                className="absolute flex -translate-x-1/2 -translate-y-1/2 flex-col items-center gap-0.5"
                title={posicion.pieza.nombre}
              >
                <span className="rounded-full border-2 border-white bg-wc-green px-1.5 py-0.5 text-[10px] font-bold leading-none text-white shadow">
                  {color.orden}
                </span>
                <span className="rounded border border-wc-border bg-white p-0.5 shadow">
                  <MiniaturaContorno coordenadas={posicion.pieza.coordenadas} size={22} />
                </span>
              </span>
            ))}
          </div>
        </div>

        {/* Panel derecho: piezas asignadas por color, con alta/cambio de pieza en el mismo lugar */}
        <div className="flex min-w-0 w-full flex-col gap-3 lg:w-1/2">
          {coloresOrdenados.map((color) => {
            const agregandoEsteColor = accion?.tipo === 'agregar' && accion.colorId === color.id;
            const cambiandoEnEsteColor = accion?.tipo === 'cambiar' && accion.color.id === color.id;

            return (
              <div key={color.id} className="rounded-xl border border-wc-border bg-white p-4 shadow-sm">
                <div className="flex items-center justify-between gap-2">
                  <h3 className="text-sm font-bold text-wc-text">
                    Color {color.orden} <span className="font-normal text-wc-text-muted">({color.gramos} g)</span>
                  </h3>
                  {patron.idGrupoTalle != null && !agregandoEsteColor && (
                    <button
                      type="button"
                      onClick={() => iniciarAgregar(color.id)}
                      disabled={ocupado}
                      className="shrink-0 rounded-lg bg-wc-green/10 px-3 py-1.5 text-xs font-semibold text-wc-green transition hover:bg-wc-green/20 disabled:cursor-not-allowed disabled:opacity-60"
                    >
                      + Agregar pieza
                    </button>
                  )}
                </div>

                {color.piezas.length === 0 && !agregandoEsteColor && (
                  <p className="mt-2 text-xs text-wc-text-muted">Todavía no hay piezas asignadas a este color.</p>
                )}

                {color.piezas.length > 0 && (
                  <ul className="mt-2 flex flex-col gap-1.5">
                    {color.piezas.map((posicion) => (
                      <li
                        key={posicion.id}
                        className="flex items-center gap-2 rounded-lg border border-wc-border px-2 py-1.5 text-xs text-wc-text"
                      >
                        <MiniaturaContorno coordenadas={posicion.pieza.coordenadas} size={32} />
                        <span className="flex-1 truncate font-semibold">{posicion.pieza.nombre}</span>
                        <button
                          type="button"
                          onClick={() => iniciarCambiar(color, posicion)}
                          disabled={ocupado}
                          className="shrink-0 font-semibold text-wc-green underline disabled:cursor-not-allowed disabled:opacity-60"
                        >
                          Cambiar
                        </button>
                        <button
                          type="button"
                          onClick={() => eliminarPosicion(color, posicion)}
                          disabled={ocupado}
                          className="shrink-0 font-semibold text-red-600 underline disabled:cursor-not-allowed disabled:opacity-60"
                        >
                          Eliminar
                        </button>
                      </li>
                    ))}
                  </ul>
                )}

                {agregandoEsteColor && !accion.piezaElegida && (
                  <PiezaPickerPanel idGrupoTalle={patron.idGrupoTalle!} onSeleccionar={handleSeleccionarEnPanel} onCancelar={cancelarAccion} />
                )}
                {agregandoEsteColor && accion.piezaElegida && (
                  <div className="mt-3 flex items-center gap-2 rounded-lg border border-wc-green/40 bg-wc-green/5 p-2 text-xs">
                    <MiniaturaContorno coordenadas={accion.piezaElegida.coordenadas} size={32} />
                    <span className="flex-1 font-semibold text-wc-text">
                      Hacé click en la imagen para ubicar "{accion.piezaElegida.nombre}"
                    </span>
                    <button type="button" onClick={cancelarAccion} className="shrink-0 font-semibold text-wc-text-muted underline">
                      Cancelar
                    </button>
                  </div>
                )}
                {cambiandoEnEsteColor && (
                  <PiezaPickerPanel idGrupoTalle={patron.idGrupoTalle!} onSeleccionar={handleSeleccionarEnPanel} onCancelar={cancelarAccion} />
                )}
              </div>
            );
          })}
        </div>
      </div>
    </div>
  );
}
