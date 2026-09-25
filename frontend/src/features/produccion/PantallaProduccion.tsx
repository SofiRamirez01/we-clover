import { useEffect, useMemo, useState } from 'react';
import AppHeader from '../../components/AppHeader';
import { asignarPrioridadManualPedido, quitarPrioridadManualPedido } from '../../services/pedidoService';
import { marcarEstadoBanderaProducto, marcarEtapaProducto } from '../../services/productoService';
import { listarProduccionPedidos } from '../../services/produccionService';
import { listarUsuariosPorRol } from '../../services/usuarioService';
import { extraerMensajeError } from '../../utils/errores';
import { ESTADOS_PEDIDO, ESTADO_PEDIDO_LABELS } from '../../types/pedido';
import type { EstadoBandera, EstadoPedido } from '../../types/pedido';
import { ETAPAS_PRODUCCION, ETAPA_PRODUCCION_LABELS } from '../../types/produccion';
import type { EtapaProduccion, ProduccionPedidoResponse, UsuarioResumen } from '../../types/produccion';
import FilaPedidoProduccion from './FilaPedidoProduccion';
import FiltroMultiSelect from './FiltroMultiSelect';
import ModalCargaMasiva from './ModalCargaMasiva';
import ToastContainer from './Toast';
import type { ToastItem } from './Toast';

/** Rol real de "Producción" (ver doc/pantallas-pendientes.md / memoria de la Entrega 1): no
 *  existe un ROLE_PRODUCCION propio, se reusó ROLE_PLANTA en todo el backend de etapas/Bandera. */
const ROL_PRODUCCION = 'ROLE_PLANTA';

/** Por defecto se esconden PRESUPUESTADO/SENADO (pedidos que todavía no arrancaron
 *  producción) — el filtro de Estado sigue permitiendo elegir cualquier combinación. */
const ESTADOS_INICIALES: EstadoPedido[] = ['LISTO_PARA_PRODUCCION', 'EN_PRODUCCION'];

const DEMORA_FILTRO_PAGO_MS = 500;

let proximoIdToast = 1;

const EyeIcon = () => (
  <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth={2} strokeLinecap="round" strokeLinejoin="round">
    <path d="M2 12s3.5-7 10-7 10 7 10 7-3.5 7-10 7-10-7-10-7Z" />
    <circle cx="12" cy="12" r="3" />
  </svg>
);

const EyeOffIcon = () => (
  <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth={2} strokeLinecap="round" strokeLinejoin="round">
    <path d="M9.9 4.24A9.12 9.12 0 0 1 12 4c6.5 0 10 7 10 7a17.3 17.3 0 0 1-2.9 3.9M6.3 6.3C3.7 8 2 11 2 11s3.5 7 10 7a9.24 9.24 0 0 0 4.9-1.4" />
    <path d="M14.12 14.12a3 3 0 1 1-4.24-4.24" />
    <path d="M2 2l20 20" />
  </svg>
);

export default function PantallaProduccion() {
  const [pedidos, setPedidos] = useState<ProduccionPedidoResponse[] | null>(null);
  const [cargandoInicial, setCargandoInicial] = useState(true);
  const [actualizando, setActualizando] = useState(false);
  const [errorCarga, setErrorCarga] = useState<string | null>(null);

  const [estadosSeleccionados, setEstadosSeleccionados] = useState<Set<string>>(new Set(ESTADOS_INICIALES));
  const [etapaPendiente, setEtapaPendiente] = useState<EtapaProduccion | ''>('');
  const [pagoMinInput, setPagoMinInput] = useState('');
  const [pagoMaxInput, setPagoMaxInput] = useState('');
  const [pagoMin, setPagoMin] = useState('');
  const [pagoMax, setPagoMax] = useState('');

  const [expandidos, setExpandidos] = useState<Set<number>>(new Set());
  /** Fuerza a que todos los pedidos muestren su subtabla de productos, sin importar el estado
   *  individual de `expandidos` — al destildarla vuelve a comportarse como antes. */
  const [mostrarTodosLosProductos, setMostrarTodosLosProductos] = useState(false);
  const [guardandoClave, setGuardandoClave] = useState<string | null>(null);
  const [empleados, setEmpleados] = useState<UsuarioResumen[]>([]);
  const [mostrarCargaMasiva, setMostrarCargaMasiva] = useState(false);
  const [toasts, setToasts] = useState<ToastItem[]>([]);

  useEffect(() => {
    listarUsuariosPorRol(ROL_PRODUCCION)
      .then(setEmpleados)
      .catch(() => {
        // No bloquea la pantalla: sin la lista de empleados, el selector queda vacío
        // (siempre se puede completar la etapa "sin asignar").
      });
  }, []);

  // Debounce corto para no mandar un request por cada tecla en los inputs de % de pago.
  useEffect(() => {
    const timer = window.setTimeout(() => setPagoMin(pagoMinInput), DEMORA_FILTRO_PAGO_MS);
    return () => window.clearTimeout(timer);
  }, [pagoMinInput]);
  useEffect(() => {
    const timer = window.setTimeout(() => setPagoMax(pagoMaxInput), DEMORA_FILTRO_PAGO_MS);
    return () => window.clearTimeout(timer);
  }, [pagoMaxInput]);

  async function cargar(esInicial: boolean) {
    if (esInicial) setCargandoInicial(true);
    else setActualizando(true);
    setErrorCarga(null);
    try {
      const data = await listarProduccionPedidos({
        estado: Array.from(estadosSeleccionados) as EstadoPedido[],
        etapaPendiente: etapaPendiente || undefined,
        pagoMin: pagoMin.trim() ? Number(pagoMin) : undefined,
        pagoMax: pagoMax.trim() ? Number(pagoMax) : undefined,
      });
      setPedidos(data);
    } catch (err) {
      setErrorCarga(extraerMensajeError(err, 'No se pudo cargar la grilla de producción.'));
    } finally {
      setCargandoInicial(false);
      setActualizando(false);
    }
  }

  useEffect(() => {
    cargar(pedidos === null);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [estadosSeleccionados, etapaPendiente, pagoMin, pagoMax]);

  function agregarToast(tipo: ToastItem['tipo'], mensaje: string) {
    setToasts((prev) => [...prev, { id: proximoIdToast++, tipo, mensaje }]);
  }

  function descartarToast(id: number) {
    setToasts((prev) => prev.filter((t) => t.id !== id));
  }

  function toggleExpandir(idPedido: number) {
    setExpandidos((prev) => {
      const copia = new Set(prev);
      if (copia.has(idPedido)) copia.delete(idPedido);
      else copia.add(idPedido);
      return copia;
    });
  }

  async function handleMarcarEtapa(idProducto: number, etapa: EtapaProduccion, completado: boolean, idEmpleado: number | null) {
    if (!pedidos) return;
    const clave = `etapa-${idProducto}-${etapa}`;
    const anterior = pedidos;
    const empleadoNombre = empleados.find((e) => e.id === idEmpleado)?.nombre ?? null;

    setPedidos((prev) =>
      (prev ?? []).map((pedido) => ({
        ...pedido,
        productos: pedido.productos.map((producto) => {
          if (producto.id !== idProducto) return producto;
          return {
            ...producto,
            etapas: producto.etapas.map((e) =>
              e.etapa === etapa
                ? {
                    ...e,
                    completado,
                    fechaCompletado: completado ? new Date().toISOString().slice(0, 10) : null,
                    empleadoId: idEmpleado,
                    empleadoNombre,
                  }
                : e,
            ),
          };
        }),
      })),
    );
    setGuardandoClave(clave);
    try {
      await marcarEtapaProducto(idProducto, etapa, completado, idEmpleado);
      // El backend puede haber disparado EN_PRODUCCION/TERMINADO en el pedido — se refresca la
      // grilla entera para reflejarlo (ver la consigna: "el frontend simplemente vuelve a pedir").
      await cargar(false);
    } catch (err) {
      setPedidos(anterior);
      agregarToast('error', extraerMensajeError(err, 'No se pudo guardar la etapa.'));
    } finally {
      setGuardandoClave(null);
    }
  }

  async function handleMarcarBandera(idProducto: number, estadoBandera: EstadoBandera) {
    if (!pedidos) return;
    const clave = `bandera-${idProducto}`;
    const anterior = pedidos;
    const hoy = new Date().toISOString().slice(0, 10);

    setPedidos((prev) =>
      (prev ?? []).map((pedido) => ({
        ...pedido,
        productos: pedido.productos.map((producto) => {
          if (producto.id !== idProducto) return producto;
          return {
            ...producto,
            estadoBandera,
            fechaPedidoProveedor: estadoBandera === 'PEDIDO' ? hoy : producto.fechaPedidoProveedor,
            fechaRecibido: estadoBandera === 'RECIBIDO' ? hoy : producto.fechaRecibido,
          };
        }),
      })),
    );
    setGuardandoClave(clave);
    try {
      // No dispara recalculo de estado del pedido (ver PedidoService en el backend) — alcanza
      // con el update optimista, sin refrescar toda la grilla.
      await marcarEstadoBanderaProducto(idProducto, estadoBandera);
    } catch (err) {
      setPedidos(anterior);
      agregarToast('error', extraerMensajeError(err, 'No se pudo guardar el estado de la bandera.'));
    } finally {
      setGuardandoClave(null);
    }
  }

  async function handleCambiarPrioridad(idPedido: number, prioridad: number) {
    if (!pedidos) return;
    const anterior = pedidos;
    setPedidos((prev) => (prev ?? []).map((p) => (p.id === idPedido ? { ...p, prioridadManual: prioridad } : p)));
    try {
      await asignarPrioridadManualPedido(idPedido, prioridad);
    } catch (err) {
      setPedidos(anterior);
      agregarToast('error', extraerMensajeError(err, 'No se pudo fijar la prioridad manual.'));
    }
  }

  async function handleQuitarPrioridad(idPedido: number) {
    if (!pedidos) return;
    const anterior = pedidos;
    setPedidos((prev) => (prev ?? []).map((p) => (p.id === idPedido ? { ...p, prioridadManual: null } : p)));
    try {
      await quitarPrioridadManualPedido(idPedido);
    } catch (err) {
      setPedidos(anterior);
      agregarToast('error', extraerMensajeError(err, 'No se pudo quitar la prioridad manual.'));
    }
  }

  const opcionesEstado = useMemo(
    () => ESTADOS_PEDIDO.map((estado) => ({ value: estado, label: ESTADO_PEDIDO_LABELS[estado] })),
    [],
  );

  return (
    <div className="tw-scope flex flex-col px-8 pb-12 pt-7">
      <AppHeader title="Producción" />

      <div className="flex flex-col gap-5">
        <div className="flex flex-wrap items-end gap-3 rounded-lg border border-wc-border bg-white p-4">
          <FiltroMultiSelect
            label="Estado"
            opciones={opcionesEstado}
            seleccionados={estadosSeleccionados}
            onCambiar={setEstadosSeleccionados}
          />

          <div className="flex flex-col gap-1">
            <label className="text-xs font-semibold text-wc-text">Etapa pendiente</label>
            <select
              value={etapaPendiente}
              onChange={(e) => setEtapaPendiente(e.target.value as EtapaProduccion | '')}
              className="rounded-lg border border-wc-border bg-white px-2 py-1.5 text-sm text-wc-text"
            >
              <option value="">Todas</option>
              {ETAPAS_PRODUCCION.map((etapa) => (
                <option key={etapa} value={etapa}>
                  {ETAPA_PRODUCCION_LABELS[etapa]}
                </option>
              ))}
            </select>
          </div>

          <div className="flex flex-col gap-1">
            <label className="text-xs font-semibold text-wc-text">% pagado, desde</label>
            <input
              type="number"
              min={0}
              max={100}
              value={pagoMinInput}
              onChange={(e) => setPagoMinInput(e.target.value)}
              placeholder="0"
              className="w-20 rounded-lg border border-wc-border bg-white px-2 py-1.5 text-sm text-wc-text"
            />
          </div>
          <div className="flex flex-col gap-1">
            <label className="text-xs font-semibold text-wc-text">hasta</label>
            <input
              type="number"
              min={0}
              max={100}
              value={pagoMaxInput}
              onChange={(e) => setPagoMaxInput(e.target.value)}
              placeholder="100"
              className="w-20 rounded-lg border border-wc-border bg-white px-2 py-1.5 text-sm text-wc-text"
            />
          </div>

          <button
            type="button"
            onClick={() => setMostrarTodosLosProductos((v) => !v)}
            aria-pressed={mostrarTodosLosProductos}
            className={`flex items-center gap-1.5 rounded-lg border px-2.5 py-1.5 text-sm font-medium transition ${
              mostrarTodosLosProductos
                ? 'border-wc-green bg-wc-green/10 text-wc-green-dark'
                : 'border-wc-border bg-white text-wc-text'
            }`}
          >
            {mostrarTodosLosProductos ? <EyeIcon /> : <EyeOffIcon />}
            Ver productos
          </button>

          {actualizando && <span className="pb-1.5 text-xs text-wc-text-muted">Actualizando…</span>}

          <button
            type="button"
            onClick={() => setMostrarCargaMasiva(true)}
            className="ml-auto rounded-md bg-wc-green px-4 py-2 text-sm font-semibold text-white transition hover:bg-wc-green-dark"
          >
            Carga masiva
          </button>
        </div>

        {cargandoInicial ? (
          <div className="flex flex-col gap-2">
            {[0, 1, 2].map((i) => (
              <div key={i} className="h-14 animate-pulse rounded-lg bg-wc-bg" />
            ))}
          </div>
        ) : errorCarga ? (
          <p className="text-sm font-medium text-red-600">{errorCarga}</p>
        ) : !pedidos || pedidos.length === 0 ? (
          <p className="text-sm text-wc-text-muted">No hay pedidos que coincidan con los filtros.</p>
        ) : (
          <div className="flex flex-col gap-2">
            {pedidos.map((pedido) => (
              <FilaPedidoProduccion
                key={pedido.id}
                pedido={pedido}
                expandido={mostrarTodosLosProductos || expandidos.has(pedido.id)}
                empleados={empleados}
                guardandoClave={guardandoClave}
                onToggleExpandir={() => toggleExpandir(pedido.id)}
                onMarcarEtapa={handleMarcarEtapa}
                onMarcarBandera={handleMarcarBandera}
                onCambiarPrioridad={(prioridad) => handleCambiarPrioridad(pedido.id, prioridad)}
                onQuitarPrioridad={() => handleQuitarPrioridad(pedido.id)}
              />
            ))}
          </div>
        )}
      </div>

      {mostrarCargaMasiva && (
        <ModalCargaMasiva
          empleados={empleados}
          onCerrar={() => setMostrarCargaMasiva(false)}
          onConfirmado={() => {
            setMostrarCargaMasiva(false);
            cargar(false);
          }}
        />
      )}

      <ToastContainer toasts={toasts} onDescartar={descartarToast} />
    </div>
  );
}
