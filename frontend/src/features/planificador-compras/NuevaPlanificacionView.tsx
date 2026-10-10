import { useEffect, useMemo, useRef, useState } from 'react';
import ModalConfirmacion from '../../components/ModalConfirmacion';
import FilaProductoElegible from './FilaProductoElegible';
import FiltroTipoPrenda from './FiltroTipoPrenda';
import FiltroMultiSelect from '../produccion/FiltroMultiSelect';
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
import type { PlanificacionCompraBorradorRequest, PlanificacionCompraResponse } from '../../types/planificacionCompra';
import type { ProductoElegibleResponse } from '../../types/planificacionCompra';
import type { TipoTelaCatalogo } from '../../types/tipoTela';

const DEMORA_AUTOGUARDADO_MS = 700;

/** Valor del filtro de tanda para los pedidos que no tienen tanda. */
const SIN_TANDA = 'sin-tanda';

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

  const [nombre, setNombre] = useState('');
  const [seleccionados, setSeleccionados] = useState<Set<number>>(new Set());

  // Filtros de la lista: todos son solo de UI y todos opcionales (cada quien planifica con el
  // criterio que quiera: por tanda, por pagos, por tipo de prenda o por fecha). No se
  // persisten en el borrador ni cambian lo que ya está tildado — el período de la
  // planificación lo calcula el backend con las fechas de entrega de los productos elegidos.
  /** Ids de tanda (como texto) y/o SIN_TANDA. Vacío = todas. */
  const [tandasSeleccionadas, setTandasSeleccionadas] = useState<Set<string>>(new Set());
  const [entregaDesde, setEntregaDesde] = useState('');
  const [entregaHasta, setEntregaHasta] = useState('');
  const [tiposPrendaSeleccionados, setTiposPrendaSeleccionados] = useState<Set<string>>(new Set());
  // Al revés de como se lee: por defecto NO se muestran los ya planificados (hay que tildar
  // para verlos) — antes era al revés (se mostraban salvo que tildaras "excluir").
  const [mostrarYaPlanificados, setMostrarYaPlanificados] = useState(false);
  // Por defecto NO se muestran los incompletos (hay que tildar para verlos) — mismo criterio
  // que "Mostrar ya planificados" arriba.
  const [incluirIncompletos, setIncluirIncompletos] = useState(false);
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
  /** Guarda-en-vuelo: evita que el autoguardado (disparado por el timer) y un guardado
   *  explícito (ej. "Confirmar planificación", que primero cancela el timer y llama de
   *  nuevo) corran en paralelo. Sin esto, si el segundo arranca antes de que el primero
   *  resuelva, `idBorradorLocalRef.current` todavía es null para los dos — ambos toman la
   *  rama de "crear" y terminan creando DOS borradores distintos en la base (uno con el
   *  estado incompleto que tenía en ese momento, ej. sin nombre todavía, que queda huérfano
   *  sin nombre y hace fallar "Confirmar" más tarde contra ese id perdido). */
  const guardadoEnVueloRef = useRef<Promise<number | null> | null>(null);

  useEffect(() => {
    listarTiposTela()
      .then(setTiposTela)
      .catch(() => {});
  }, []);

  // Si se abrió "continuando" un borrador existente, trae nombre/selección guardados.
  // OJO: deps `[]` a propósito, no `[idBorrador]` — este efecto debe correr una sola vez, con
  // el id que trajo el montaje inicial ("continuar editando" desde el listado siempre monta
  // esta pantalla de cero con el id ya puesto). Si dependiera de `idBorrador`, volvería a
  // dispararse cuando ESTE MISMO componente crea el borrador por primera vez con el
  // autoguardado (ver guardarBorradorAhora → onIdBorradorCreado, que le informa el id nuevo al
  // padre y el padre se lo devuelve como prop) — y esa segunda pasada pisaría con un GET lo que
  // el usuario ya tipeó localmente después de esa primera creación (típicamente todavía sin
  // nombre, porque el autoguardado se dispara apenas se tilda algo, antes de escribirlo).
  useEffect(() => {
    if (idBorrador == null) return;
    let cancelado = false;
    obtenerBorradorPlanificacion(idBorrador)
      .then((data) => {
        if (cancelado) return;
        setNombre(data.nombre);
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
  }, []);

  // Trae todos los candidatos una sola vez (apenas se abre la pantalla, o cuando termina de
  // cargarse el borrador): no depende de ningún filtro, así que filtrar nunca vuelve a pedir
  // datos ni toca la selección.
  useEffect(() => {
    if (cargandoBorrador) return;
    buscar();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [cargandoBorrador]);

  // Autoguardado contra la base, con demora corta para no mandar un request por cada tecla —
  // se salta mientras se está cargando un borrador existente (para no pisarlo con el estado
  // todavía vacío) y si no hay nada cargado (pantalla recién abierta, sin cambios), para no
  // crear una fila vacía en la base apenas se entra a la pantalla.
  useEffect(() => {
    if (cargandoBorrador) return;
    if (!nombre.trim() && seleccionados.size === 0) return;

    if (timeoutAutoguardadoRef.current) window.clearTimeout(timeoutAutoguardadoRef.current);
    timeoutAutoguardadoRef.current = window.setTimeout(() => {
      guardarBorradorAhora();
    }, DEMORA_AUTOGUARDADO_MS);

    return () => {
      if (timeoutAutoguardadoRef.current) window.clearTimeout(timeoutAutoguardadoRef.current);
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [nombre, seleccionados, cargandoBorrador]);

  /** Guarda ya mismo (sin esperar la demora del autoguardado) — usado tanto por el timer como
   *  por "Confirmar planificación", que necesita el borrador al día antes de confirmarlo. Si ya
   *  hay un guardado en vuelo, se engancha a ese mismo en vez de disparar uno nuevo en paralelo
   *  (ver comentario de guardadoEnVueloRef). */
  function guardarBorradorAhora(): Promise<number | null> {
    if (guardadoEnVueloRef.current) {
      return guardadoEnVueloRef.current;
    }

    const promesa = (async () => {
      setEstadoGuardado('guardando');
      const payload: PlanificacionCompraBorradorRequest = {
        nombre: nombre.trim() || undefined,
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
      } finally {
        guardadoEnVueloRef.current = null;
      }
    })();

    guardadoEnVueloRef.current = promesa;
    return promesa;
  }

  async function buscar() {
    setBuscando(true);
    setErrorBusqueda(null);
    try {
      setElegibles(await listarProductosElegibles());
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

  /** Tandas presentes entre los candidatos, en orden de cola (las cerradas al final), más
   *  "Sin tanda" si hay algún pedido sin asignar. */
  const opcionesTanda = useMemo(() => {
    if (!elegibles) return [];
    const tandas = new Map<number, NonNullable<ProductoElegibleResponse['tanda']>>();
    let haySinTanda = false;
    for (const e of elegibles) {
      if (e.tanda) tandas.set(e.tanda.id, e.tanda);
      else haySinTanda = true;
    }
    const opciones = Array.from(tandas.values())
      .sort((a, b) => (a.posicion ?? Infinity) - (b.posicion ?? Infinity) || a.id - b.id)
      .map((t) => ({ value: String(t.id), label: `Tanda ${t.nombre}` }));
    if (haySinTanda) opciones.push({ value: SIN_TANDA, label: 'Sin tanda' });
    return opciones;
  }, [elegibles]);

  const errorFechas = entregaDesde && entregaHasta && entregaHasta < entregaDesde
    ? 'La fecha hasta no puede ser anterior a la fecha desde.'
    : null;

  const filtrados = useMemo(() => {
    if (!elegibles) return [];
    const minimo = pagoDesde.trim() ? Number(pagoDesde) : null;
    const maximo = pagoHasta.trim() ? Number(pagoHasta) : null;
    return elegibles.filter((e) => {
      if (tandasSeleccionadas.size > 0 && !tandasSeleccionadas.has(e.tanda ? String(e.tanda.id) : SIN_TANDA)) return false;
      // Fechas ISO (aaaa-mm-dd): se comparan como texto. Cada extremo es opcional.
      if (entregaDesde && e.fechaEstimadaEntregaPedido < entregaDesde) return false;
      if (entregaHasta && e.fechaEstimadaEntregaPedido > entregaHasta) return false;
      if (tiposPrendaSeleccionados.size > 0 && (!e.producto.tipoPrenda || !tiposPrendaSeleccionados.has(e.producto.tipoPrenda))) {
        return false;
      }
      // Estos dos tildes esconden por defecto, pero nunca a un producto ya tildado: al editar
      // una planificación, lo que ya forma parte de ella tiene que verse sin tocar nada.
      const tildado = seleccionados.has(e.producto.id);
      if (!mostrarYaPlanificados && !tildado && e.planificacionesQueLoIncluyen.length > 0) return false;
      if (!incluirIncompletos && !tildado && !e.disenoCompleto) return false;
      if (minimo != null && !Number.isNaN(minimo) && e.porcentajePagadoPedido < minimo) return false;
      if (maximo != null && !Number.isNaN(maximo) && e.porcentajePagadoPedido > maximo) return false;
      return true;
    });
  }, [
    elegibles,
    tandasSeleccionadas,
    entregaDesde,
    entregaHasta,
    tiposPrendaSeleccionados,
    mostrarYaPlanificados,
    incluirIncompletos,
    pagoDesde,
    pagoHasta,
    seleccionados,
  ]);

  /** Tildados que los filtros actuales no dejan ver (siguen formando parte de la planificación). */
  const tildadosOcultos = useMemo(() => {
    const visibles = new Set(filtrados.map((e) => e.producto.id));
    return Array.from(seleccionados).filter((id) => !visibles.has(id)).length;
  }, [filtrados, seleccionados]);

  function toggleProducto(idProducto: number, disenoCompleto: boolean) {
    if (!disenoCompleto) return;
    setSeleccionados((prev) => {
      const copia = new Set(prev);
      if (copia.has(idProducto)) copia.delete(idProducto);
      else copia.add(idProducto);
      return copia;
    });
  }

  /** Suma a lo ya tildado todos los completos que se ven con los filtros actuales (no destilda
   *  nada): así se puede armar la planificación filtrando de a una tanda por vez. */
  function marcarTodosLosCompletos() {
    setSeleccionados((prev) => {
      const copia = new Set(prev);
      filtrados.filter((e) => e.disenoCompleto).forEach((e) => copia.add(e.producto.id));
      return copia;
    });
  }

  function destildarVisibles() {
    setSeleccionados((prev) => {
      const copia = new Set(prev);
      filtrados.forEach((e) => copia.delete(e.producto.id));
      return copia;
    });
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
          <div className="flex flex-col gap-3 rounded-lg border border-wc-border bg-white p-4">
            <p className="text-xs text-wc-text-muted">
              Filtrá con el criterio que prefieras: todos los filtros son opcionales y se pueden combinar. Lo que ya
              tildaste no se pierde al cambiarlos.
            </p>
            <div className="flex flex-wrap items-end gap-x-5 gap-y-3">
              <FiltroMultiSelect
                label="Tanda"
                opciones={opcionesTanda}
                seleccionados={tandasSeleccionadas}
                onCambiar={setTandasSeleccionadas}
              />

              <div className="flex items-end gap-2">
                <div className="flex flex-col gap-1">
                  <label htmlFor="pagoDesde" className="text-xs font-semibold text-wc-text">
                    % pagado, desde
                  </label>
                  <input
                    id="pagoDesde"
                    type="number"
                    value={pagoDesde}
                    onChange={(e) => setPagoDesde(e.target.value)}
                    placeholder="0"
                    className="w-20 rounded-lg border border-wc-border bg-white px-2 py-1.5 text-sm text-wc-text"
                  />
                </div>
                <div className="flex flex-col gap-1">
                  <label htmlFor="pagoHasta" className="text-xs font-semibold text-wc-text">
                    hasta
                  </label>
                  <input
                    id="pagoHasta"
                    type="number"
                    value={pagoHasta}
                    onChange={(e) => setPagoHasta(e.target.value)}
                    placeholder="100"
                    className="w-20 rounded-lg border border-wc-border bg-white px-2 py-1.5 text-sm text-wc-text"
                  />
                </div>
              </div>

              {tiposPrendaDisponibles.length > 0 && (
                <FiltroTipoPrenda
                  opciones={tiposPrendaDisponibles}
                  seleccionados={tiposPrendaSeleccionados}
                  onCambiar={setTiposPrendaSeleccionados}
                />
              )}

              <div className="flex items-end gap-2">
                <div className="flex flex-col gap-1">
                  <label htmlFor="entregaDesde" className="text-xs font-semibold text-wc-text">
                    Entrega desde
                  </label>
                  <input
                    id="entregaDesde"
                    type="date"
                    value={entregaDesde}
                    onChange={(e) => setEntregaDesde(e.target.value)}
                    className="rounded-lg border border-wc-border bg-white px-2 py-1.5 text-sm text-wc-text"
                  />
                </div>
                <div className="flex flex-col gap-1">
                  <label htmlFor="entregaHasta" className="text-xs font-semibold text-wc-text">
                    hasta
                  </label>
                  <input
                    id="entregaHasta"
                    type="date"
                    value={entregaHasta}
                    onChange={(e) => setEntregaHasta(e.target.value)}
                    className="rounded-lg border border-wc-border bg-white px-2 py-1.5 text-sm text-wc-text"
                  />
                </div>
              </div>

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

              <div className="flex flex-nowrap items-center gap-1.5 pb-1.5 text-sm text-wc-text">
                <input
                  id="incluirIncompletos"
                  type="checkbox"
                  checked={incluirIncompletos}
                  onChange={(e) => setIncluirIncompletos(e.target.checked)}
                  className="h-4 w-4 shrink-0 accent-wc-green"
                />
                <label htmlFor="incluirIncompletos" className="whitespace-nowrap cursor-pointer">
                  Incluir incompletos
                </label>
              </div>

              {buscando && <span className="pb-1.5 text-xs text-wc-text-muted">Buscando…</span>}
            </div>
            {errorFechas && <p className="text-xs font-medium text-red-600">{errorFechas}</p>}
          </div>

          {errorBusqueda && <p className="text-xs font-medium text-red-600">{errorBusqueda}</p>}

          {elegibles && (
            <div className="grid grid-cols-1 gap-5 lg:grid-cols-[2fr_1fr]">
              <div className="flex flex-col gap-3">
                <div className="flex items-center justify-between">
                  <p className="text-xs text-wc-text-muted">
                    {filtrados.length} producto{filtrados.length === 1 ? '' : 's'} · {seleccionados.size} tildado
                    {seleccionados.size === 1 ? '' : 's'}
                    {tildadosOcultos > 0 && (
                      <span className="font-semibold text-wc-text">
                        {' '}
                        ({tildadosOcultos} no se ve{tildadosOcultos === 1 ? '' : 'n'} con estos filtros)
                      </span>
                    )}
                  </p>
                  <div className="flex items-center gap-3">
                    <button type="button" onClick={destildarVisibles} className="text-xs font-semibold text-wc-text-muted underline">
                      Destildar los visibles
                    </button>
                    <button type="button" onClick={marcarTodosLosCompletos} className="text-xs font-semibold text-wc-green underline">
                      Tildar todos los completos
                    </button>
                  </div>
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
                          {item.cantidad.toLocaleString('es-AR', { maximumFractionDigits: 1 })} {item.esPorPeso ? 'kg' : 'u.'}
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
