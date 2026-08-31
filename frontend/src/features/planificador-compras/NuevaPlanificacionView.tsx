import { useEffect, useMemo, useRef, useState } from 'react';
import ModalConfirmacion from '../../components/ModalConfirmacion';
import FilaProductoElegible from './FilaProductoElegible';
import FiltroTipoPrenda from './FiltroTipoPrenda';
import { listarTiposTela } from '../../services/tipoTelaService';
import {
  actualizarBorradorPlanificacion,
  confirmarPlanificacion,
  crearBorradorPlanificacion,
  eliminarPlanificacion,
  listarProductosElegibles,
  obtenerBorradorPlanificacion,
} from '../../services/planificacionCompraService';
import { calcularConsumoTotal } from '../../utils/consumoProducto';
import { extraerMensajeError } from '../../utils/errores';
import { ESTADOS_PEDIDO, ESTADO_PEDIDO_LABELS } from '../../types/pedido';
import type { EstadoPedido } from '../../types/pedido';
import type { PlanificacionCompraBorradorRequest, PlanificacionCompraResponse } from '../../types/planificacionCompra';
import type { ProductoElegibleResponse } from '../../types/planificacionCompra';
import type { TipoTelaCatalogo } from '../../types/tipoTela';

const DEMORA_AUTOGUARDADO_MS = 700;

interface NuevaPlanificacionViewProps {
  /** Id del borrador que se está editando — null si es una planificación nueva, todavía sin
   *  guardar ni una vez. Controlado por el padre (PlanificadorComprasView), que también lo
   *  necesita para el cartel de confirmación de la flecha "Volver" del AppHeader. */
  idBorrador: number | null;
  /** Se llama la primera vez que este borrador se guarda (autoguardado creó la fila en la
   *  base) — el padre lo necesita para saber que ya hay algo que perder si se sale sin guardar. */
  onIdBorradorCreado: (id: number) => void;
  /** Se llama después de eliminar el borrador desde el cartel de Cancelar (no desde la flecha
   *  "Volver", que maneja su propia eliminación en el padre). */
  onBorradorEliminado: () => void;
  onConfirmada: (planificacion: PlanificacionCompraResponse) => void;
  onCancelar: () => void;
}

type EstadoGuardado = 'inactivo' | 'guardando' | 'guardado' | 'error';

export default function NuevaPlanificacionView({
  idBorrador,
  onIdBorradorCreado,
  onBorradorEliminado,
  onConfirmada,
  onCancelar,
}: NuevaPlanificacionViewProps) {
  const [tiposTela, setTiposTela] = useState<TipoTelaCatalogo[]>([]);

  const [cargandoBorrador, setCargandoBorrador] = useState(idBorrador != null);
  const [idBorradorLocal, setIdBorradorLocal] = useState<number | null>(idBorrador);

  // Vacío por defecto (no un rango precargado): la búsqueda arranca recién cuando el usuario
  // carga ambas fechas (ver el useEffect de abajo, que la dispara solo, sin botón) — o cuando
  // se termina de cargar un borrador existente, que puede traerlas ya puestas.
  const [fechaDesde, setFechaDesde] = useState('');
  const [fechaHasta, setFechaHasta] = useState('');
  const [nombre, setNombre] = useState('');
  const [seleccionados, setSeleccionados] = useState<Set<number>>(new Set());

  // Filtros de la tabla: solo de UI, no se persisten en el borrador (no son parte de la
  // planificación en sí, y reiniciarlos al reabrir un borrador es un comportamiento razonable).
  const [tiposPrendaSeleccionados, setTiposPrendaSeleccionados] = useState<Set<string>>(new Set());
  const [filtroEstado, setFiltroEstado] = useState<EstadoPedido | ''>('');
  // Al revés de como se lee: por defecto NO se muestran los ya planificados (hay que tildar
  // para verlos) — antes era al revés (se mostraban salvo que tildaras "excluir").
  const [mostrarYaPlanificados, setMostrarYaPlanificados] = useState(false);
  const [excluirIncompletos, setExcluirIncompletos] = useState(false);
  const [pagoDesde, setPagoDesde] = useState('');
  const [pagoHasta, setPagoHasta] = useState('');

  const [elegibles, setElegibles] = useState<ProductoElegibleResponse[] | null>(null);
  const [buscando, setBuscando] = useState(false);
  const [errorBusqueda, setErrorBusqueda] = useState<string | null>(null);

  const [estadoGuardado, setEstadoGuardado] = useState<EstadoGuardado>('inactivo');
  const [guardando, setGuardando] = useState(false);
  const [errorGuardar, setErrorGuardar] = useState<string | null>(null);
  const [mostrarConfirmCancelar, setMostrarConfirmCancelar] = useState(false);

  const timeoutAutoguardadoRef = useRef<number | null>(null);
  const idBorradorLocalRef = useRef<number | null>(idBorrador);

  useEffect(() => {
    listarTiposTela()
      .then(setTiposTela)
      .catch(() => {});
  }, []);

  // Si se abrió "continuando" un borrador existente, trae nombre/fechas/selección guardados.
  useEffect(() => {
    if (idBorrador == null) return;
    let cancelado = false;
    obtenerBorradorPlanificacion(idBorrador)
      .then((data) => {
        if (cancelado) return;
        setNombre(data.nombre);
        setFechaDesde(data.fechaDesde ?? '');
        setFechaHasta(data.fechaHasta ?? '');
        setSeleccionados(new Set(data.idsProductos));
      })
      .catch((err) => {
        if (!cancelado) setErrorBusqueda(extraerMensajeError(err, 'No se pudo cargar el borrador.'));
      })
      .finally(() => {
        if (!cancelado) setCargandoBorrador(false);
      });
    return () => {
      cancelado = true;
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [idBorrador]);

  // Dispara la búsqueda sola apenas hay un rango de fechas válido — no hay botón "Buscar":
  // funciona como el resto de los filtros, que reaccionan solos al cambiar. Preserva la
  // selección ya tildada si todavía no hay resultados cargados (primera búsqueda de este
  // montaje, incluida la restauración del borrador); cualquier búsqueda posterior a una que
  // ya trajo resultados la limpia, porque un rango de fechas distinto puede traer productos
  // distintos. Se usa `elegibles === null` (no un ref "consumido" en la primera pasada) a
  // propósito: en desarrollo, StrictMode invoca este efecto dos veces seguidas al montar, y
  // con un ref la segunda invocación ya lo encontraba en falso y borraba la selección recién
  // restaurada — con `elegibles` la condición se recalcula sola y da el mismo resultado
  // (todavía null) en ambas invocaciones, porque la búsqueda async ni siquiera resolvió.
  useEffect(() => {
    if (cargandoBorrador) return;
    if (!fechaDesde || !fechaHasta) {
      setElegibles(null);
      setErrorBusqueda(null);
      return;
    }
    if (fechaHasta < fechaDesde) {
      setElegibles(null);
      setErrorBusqueda('La fecha hasta no puede ser anterior a la fecha desde.');
      return;
    }
    buscar(elegibles === null);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [fechaDesde, fechaHasta, cargandoBorrador]);

  // Autoguardado contra la base, con demora corta para no mandar un request por cada tecla —
  // se salta mientras se está cargando un borrador existente (para no pisarlo con el estado
  // todavía vacío) y si no hay nada cargado (pantalla recién abierta, sin cambios), para no
  // crear una fila vacía en la base apenas se entra a la pantalla.
  useEffect(() => {
    if (cargandoBorrador) return;
    if (!nombre.trim() && !fechaDesde && !fechaHasta && seleccionados.size === 0) return;

    if (timeoutAutoguardadoRef.current) window.clearTimeout(timeoutAutoguardadoRef.current);
    timeoutAutoguardadoRef.current = window.setTimeout(() => {
      guardarBorradorAhora();
    }, DEMORA_AUTOGUARDADO_MS);

    return () => {
      if (timeoutAutoguardadoRef.current) window.clearTimeout(timeoutAutoguardadoRef.current);
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [nombre, fechaDesde, fechaHasta, seleccionados, cargandoBorrador]);

  /** Guarda ya mismo (sin esperar la demora del autoguardado) — usado tanto por el timer como
   *  por "Confirmar planificación", que necesita el borrador al día antes de confirmarlo. */
  async function guardarBorradorAhora(): Promise<number | null> {
    setEstadoGuardado('guardando');
    const payload: PlanificacionCompraBorradorRequest = {
      nombre: nombre.trim() || undefined,
      fechaDesde: fechaDesde || undefined,
      fechaHasta: fechaHasta || undefined,
      idsProductos: Array.from(seleccionados),
    };
    try {
      const guardado = idBorradorLocalRef.current != null
        ? await actualizarBorradorPlanificacion(idBorradorLocalRef.current, payload)
        : await crearBorradorPlanificacion(payload);

      if (idBorradorLocalRef.current == null) {
        idBorradorLocalRef.current = guardado.id;
        setIdBorradorLocal(guardado.id);
        onIdBorradorCreado(guardado.id);
      }
      setEstadoGuardado('guardado');
      return guardado.id;
    } catch {
      setEstadoGuardado('error');
      return idBorradorLocalRef.current;
    }
  }

  async function buscar(preservarSeleccion: boolean) {
    setBuscando(true);
    setErrorBusqueda(null);
    try {
      const data = await listarProductosElegibles(fechaDesde, fechaHasta);
      setElegibles(data);
      if (!preservarSeleccion) setSeleccionados(new Set());
    } catch (err) {
      setErrorBusqueda(extraerMensajeError(err, 'No se pudo buscar los productos elegibles.'));
    } finally {
      setBuscando(false);
    }
  }

  const tiposPrendaDisponibles = useMemo(() => {
    if (!elegibles) return [];
    return Array.from(new Set(elegibles.map((e) => e.producto.tipoPrenda).filter((t): t is string => !!t))).sort((a, b) =>
      a.localeCompare(b),
    );
  }, [elegibles]);

  const filtrados = useMemo(() => {
    if (!elegibles) return [];
    const minimo = pagoDesde.trim() ? Number(pagoDesde) : null;
    const maximo = pagoHasta.trim() ? Number(pagoHasta) : null;
    return elegibles.filter((e) => {
      if (tiposPrendaSeleccionados.size > 0 && (!e.producto.tipoPrenda || !tiposPrendaSeleccionados.has(e.producto.tipoPrenda))) {
        return false;
      }
      if (filtroEstado && e.producto.estadoActual !== filtroEstado) return false;
      if (!mostrarYaPlanificados && e.planificacionesQueLoIncluyen.length > 0) return false;
      if (excluirIncompletos && !e.disenoCompleto) return false;
      if (minimo != null && !Number.isNaN(minimo) && e.porcentajePagadoPedido < minimo) return false;
      if (maximo != null && !Number.isNaN(maximo) && e.porcentajePagadoPedido > maximo) return false;
      return true;
    });
  }, [elegibles, tiposPrendaSeleccionados, filtroEstado, mostrarYaPlanificados, excluirIncompletos, pagoDesde, pagoHasta]);

  function toggleProducto(idProducto: number, disenoCompleto: boolean) {
    if (!disenoCompleto) return;
    setSeleccionados((prev) => {
      const copia = new Set(prev);
      if (copia.has(idProducto)) copia.delete(idProducto);
      else copia.add(idProducto);
      return copia;
    });
  }

  function marcarTodosLosCompletos() {
    setSeleccionados(new Set(filtrados.filter((e) => e.disenoCompleto).map((e) => e.producto.id)));
  }

  const productosSeleccionados = useMemo(
    () => (elegibles ?? []).filter((e) => seleccionados.has(e.producto.id)).map((e) => e.producto),
    [elegibles, seleccionados],
  );
  const consumoTotal = useMemo(() => calcularConsumoTotal(productosSeleccionados, tiposTela), [productosSeleccionados, tiposTela]);

  async function handleConfirmar() {
    if (!nombre.trim()) {
      setErrorGuardar('Ingresá un nombre para la planificación.');
      return;
    }
    if (seleccionados.size === 0) {
      setErrorGuardar('Tildá al menos un producto.');
      return;
    }
    setGuardando(true);
    setErrorGuardar(null);
    try {
      if (timeoutAutoguardadoRef.current) window.clearTimeout(timeoutAutoguardadoRef.current);
      const id = await guardarBorradorAhora();
      if (id == null) {
        throw new Error('No se pudo guardar el borrador antes de confirmar');
      }
      const confirmada = await confirmarPlanificacion(id);
      onConfirmada(confirmada);
    } catch (err) {
      setErrorGuardar(extraerMensajeError(err, 'No se pudo confirmar la planificación.'));
    } finally {
      setGuardando(false);
    }
  }

  function handleCancelar() {
    const hayContenido = nombre.trim() !== '' || seleccionados.size > 0 || idBorradorLocal != null;
    if (hayContenido) {
      setMostrarConfirmCancelar(true);
      return;
    }
    onCancelar();
  }

  async function confirmarCancelar() {
    if (timeoutAutoguardadoRef.current) window.clearTimeout(timeoutAutoguardadoRef.current);
    if (idBorradorLocal != null) {
      try {
        await eliminarPlanificacion(idBorradorLocal);
      } catch {
        // Si falla el borrado igual salimos: el usuario ya decidió cancelar.
      }
      onBorradorEliminado();
    }
    setMostrarConfirmCancelar(false);
    onCancelar();
  }

  return (
    <div className="flex flex-col gap-5">
      {cargandoBorrador ? (
        <p className="text-sm text-wc-text-muted">Cargando borrador…</p>
      ) : (
        <>
          <div className="flex flex-wrap items-end gap-3 rounded-lg border border-wc-border bg-white p-4">
            <div className="flex flex-col gap-1">
              <label className="text-xs font-semibold text-wc-text">Entrega desde</label>
              <input
                type="date"
                value={fechaDesde}
                onChange={(e) => setFechaDesde(e.target.value)}
                className="rounded-lg border border-wc-border bg-white px-2 py-1.5 text-sm text-wc-text"
              />
            </div>
            <div className="flex flex-col gap-1">
              <label className="text-xs font-semibold text-wc-text">Entrega hasta</label>
              <input
                type="date"
                value={fechaHasta}
                onChange={(e) => setFechaHasta(e.target.value)}
                className="rounded-lg border border-wc-border bg-white px-2 py-1.5 text-sm text-wc-text"
              />
            </div>
            {buscando && <span className="pb-1.5 text-xs text-wc-text-muted">Buscando…</span>}

            {elegibles && tiposPrendaDisponibles.length > 0 && (
              <FiltroTipoPrenda
                opciones={tiposPrendaDisponibles}
                seleccionados={tiposPrendaSeleccionados}
                onCambiar={setTiposPrendaSeleccionados}
              />
            )}

            {elegibles && (
              <div className="flex flex-col gap-1">
                <label className="text-xs font-semibold text-wc-text">Estado</label>
                <select
                  value={filtroEstado}
                  onChange={(e) => setFiltroEstado(e.target.value as EstadoPedido | '')}
                  className="rounded-lg border border-wc-border bg-white px-2 py-1.5 text-sm text-wc-text"
                >
                  <option value="">Todos</option>
                  {ESTADOS_PEDIDO.map((estado) => (
                    <option key={estado} value={estado}>
                      {ESTADO_PEDIDO_LABELS[estado]}
                    </option>
                  ))}
                </select>
              </div>
            )}

            {elegibles && (
              <div className="flex flex-col gap-1">
                <label className="text-xs font-semibold text-wc-text">% pagado, desde</label>
                <input
                  type="number"
                  min={0}
                  max={100}
                  value={pagoDesde}
                  onChange={(e) => setPagoDesde(e.target.value)}
                  placeholder="0"
                  className="w-20 rounded-lg border border-wc-border bg-white px-2 py-1.5 text-sm text-wc-text"
                />
              </div>
            )}
            {elegibles && (
              <div className="flex flex-col gap-1">
                <label className="text-xs font-semibold text-wc-text">hasta</label>
                <input
                  type="number"
                  min={0}
                  max={100}
                  value={pagoHasta}
                  onChange={(e) => setPagoHasta(e.target.value)}
                  placeholder="100"
                  className="w-20 rounded-lg border border-wc-border bg-white px-2 py-1.5 text-sm text-wc-text"
                />
              </div>
            )}

            {elegibles && (
              <div className="flex flex-row flex-nowrap items-center gap-1.5 pb-1.5 text-sm text-wc-text">
                <input
                  id="mostrarYaPlanificados"
                  type="checkbox"
                  checked={mostrarYaPlanificados}
                  onChange={(e) => setMostrarYaPlanificados(e.target.checked)}
                  className="h-4 w-4 shrink-0 accent-wc-green"
                />
                <label htmlFor="mostrarYaPlanificados" className="whitespace-nowrap cursor-pointer">
                  Mostrar ya planificados
                </label>
              </div>
            )}

            {elegibles && (
              <div className="flex flex-nowrap items-center gap-1.5 pb-1.5 text-sm text-wc-text">
                <input
                  type="checkbox"
                  checked={excluirIncompletos}
                  onChange={(e) => setExcluirIncompletos(e.target.checked)}
                  className="h-4 w-4 shrink-0 accent-wc-green"
                />
                <label className="whitespace-nowrap">Excluir incompletos</label>
              </div>
            )}
          </div>

          {errorBusqueda && <p className="text-xs font-medium text-red-600">{errorBusqueda}</p>}

          {!elegibles && !errorBusqueda && !buscando && (
            <p className="text-sm text-wc-text-muted">Elegí una fecha desde y una fecha hasta para ver los productos.</p>
          )}

          {elegibles && (
            <div className="grid grid-cols-1 gap-5 lg:grid-cols-[2fr_1fr]">
              <div className="flex flex-col gap-3">
                <div className="flex items-center justify-between">
                  <p className="text-xs text-wc-text-muted">
                    {filtrados.length} producto{filtrados.length === 1 ? '' : 's'} · {seleccionados.size} tildado
                    {seleccionados.size === 1 ? '' : 's'}
                  </p>
                  <button type="button" onClick={marcarTodosLosCompletos} className="text-xs font-semibold text-wc-green underline">
                    Tildar todos los completos
                  </button>
                </div>

                {filtrados.length === 0 ? (
                  <p className="text-sm text-wc-text-muted">No hay productos que coincidan con los filtros.</p>
                ) : (
                  <div className="flex flex-col gap-2 overflow-y-auto" style={{ maxHeight: '65vh' }}>
                    {filtrados.map((elegible) => (
                      <FilaProductoElegible
                        key={elegible.producto.id}
                        elegible={elegible}
                        seleccionado={seleccionados.has(elegible.producto.id)}
                        onToggle={() => toggleProducto(elegible.producto.id, elegible.disenoCompleto)}
                      />
                    ))}
                  </div>
                )}
              </div>

              <div className="flex flex-col gap-3 rounded-lg border border-wc-border bg-white p-4">
                <span className="text-xs font-semibold text-wc-text">Consumo estimado</span>
                {consumoTotal.length === 0 ? (
                  <p className="text-xs text-wc-text-muted">Tildá productos para ver el consumo por artículo.</p>
                ) : (
                  <div className="flex flex-col gap-1.5">
                    {consumoTotal.map((item) => (
                      <div key={`${item.codigoTipoTela}::${item.idPaletaColor}`} className="flex items-center gap-2 text-xs">
                        <span className="h-3.5 w-3.5 shrink-0 rounded-full border border-wc-border" style={{ backgroundColor: item.hexColor }} />
                        <span className="flex-1 truncate text-wc-text">
                          {item.nombreTipoTela} · {item.nombreColor}
                        </span>
                        <span className="font-semibold text-wc-text">
                          {item.cantidad.toLocaleString('es-AR', { maximumFractionDigits: 1 })} {item.esPorPeso ? 'g' : 'u.'}
                        </span>
                      </div>
                    ))}
                  </div>
                )}

                <div className="mt-2 flex flex-col gap-1.5 border-t border-wc-border pt-3">
                  <div className="flex items-center justify-between">
                    <label className="text-xs font-semibold text-wc-text">Nombre de la planificación</label>
                    {estadoGuardado === 'guardando' && <span className="text-[11px] text-wc-text-muted">Guardando…</span>}
                    {estadoGuardado === 'guardado' && <span className="text-[11px] text-wc-text-muted">Borrador guardado</span>}
                    {estadoGuardado === 'error' && <span className="text-[11px] font-medium text-red-600">No se pudo guardar</span>}
                  </div>
                  <input
                    value={nombre}
                    onChange={(e) => setNombre(e.target.value)}
                    placeholder='Ej: "Semana 25/08 al 31/08"'
                    className="rounded-lg border border-wc-border bg-white px-2 py-1.5 text-sm text-wc-text"
                  />
                </div>

                {errorGuardar && <p className="text-xs font-medium text-red-600">{errorGuardar}</p>}

                <div className="flex items-center justify-end gap-2">
                  <button
                    type="button"
                    onClick={handleCancelar}
                    disabled={guardando}
                    className="rounded-md border border-wc-border bg-white px-4 py-2 text-sm font-semibold text-wc-text-muted transition hover:bg-wc-bg disabled:cursor-not-allowed disabled:opacity-60"
                  >
                    Cancelar
                  </button>
                  <button
                    type="button"
                    onClick={handleConfirmar}
                    disabled={guardando}
                    className="rounded-md bg-wc-green px-5 py-2 text-sm font-semibold text-white transition hover:bg-wc-green-dark disabled:cursor-wait disabled:opacity-60"
                  >
                    {guardando ? 'Confirmando…' : 'Confirmar planificación'}
                  </button>
                </div>
              </div>
            </div>
          )}
        </>
      )}

      {mostrarConfirmCancelar && (
        <ModalConfirmacion
          titulo="¿Cancelar planificación?"
          mensaje="Se va a borrar la planificación en curso. ¿Querés continuar?"
          onCerrar={() => setMostrarConfirmCancelar(false)}
          acciones={[
            { label: 'Sí, borrar y salir', variante: 'peligro', onClick: confirmarCancelar },
            { label: 'No, seguir editando', variante: 'secundaria', onClick: () => setMostrarConfirmCancelar(false) },
          ]}
        />
      )}
    </div>
  );
}
