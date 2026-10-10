import { useEffect, useState } from 'react';
import ModalConfirmacion from '../../components/ModalConfirmacion';
import {
  eliminarPlanificacion,
  listarPlanificacionesCompra,
  reabrirPlanificacion,
} from '../../services/planificacionCompraService';
import { extraerMensajeError } from '../../utils/errores';
import type { PlanificacionCompraResponse } from '../../types/planificacionCompra';

interface PlanificacionesListViewProps {
  onNueva: () => void;
  onVerDetalle: (planificacion: PlanificacionCompraResponse) => void;
  onContinuarBorrador: (planificacion: PlanificacionCompraResponse) => void;
  mensajeExito?: string | null;
}

// Mismo ícono/estilo de botón que "Editar ficha de colores" en FichasTecnicasView.tsx.
const PencilIcon = () => (
  <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth={2} strokeLinecap="round" strokeLinejoin="round">
    <path d="M11 4H4a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2v-7" />
    <path d="M18.5 2.5a2.121 2.121 0 0 1 3 3L12 15l-4 1 1-4Z" />
  </svg>
);

// Mismo tacho que el resto de las pantallas (Stock, Usuarios).
const TrashIcon = () => (
  <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth={2} strokeLinecap="round" strokeLinejoin="round">
    <path d="M4 7h16" />
    <path d="M9 7V4.5A1.5 1.5 0 0 1 10.5 3h3A1.5 1.5 0 0 1 15 4.5V7" />
    <path d="M6 7l1 13.5A1.5 1.5 0 0 0 8.5 22h7a1.5 1.5 0 0 0 1.5-1.5L18 7" />
  </svg>
);

const CLASES_BOTON_ACCION =
  'flex h-7 w-7 shrink-0 items-center justify-center rounded-full border border-wc-border bg-white text-wc-text-muted shadow-sm transition disabled:cursor-not-allowed disabled:opacity-50';

function formatearFechaHora(iso: string): string {
  const fecha = new Date(iso);
  return fecha.toLocaleString('es-AR', { day: '2-digit', month: '2-digit', year: 'numeric', hour: '2-digit', minute: '2-digit' });
}

function formatearFecha(iso: string | null): string {
  if (!iso) return 'sin definir';
  const [anio, mes, dia] = iso.split('-');
  return `${dia}/${mes}/${anio}`;
}

export default function PlanificacionesListView({ onNueva, onVerDetalle, onContinuarBorrador, mensajeExito }: PlanificacionesListViewProps) {
  const [planificaciones, setPlanificaciones] = useState<PlanificacionCompraResponse[]>([]);
  const [estadoCarga, setEstadoCarga] = useState<'cargando' | 'listo' | 'error'>('cargando');
  const [error, setError] = useState<string | null>(null);

  const [aReabrir, setAReabrir] = useState<PlanificacionCompraResponse | null>(null);
  const [aEliminar, setAEliminar] = useState<PlanificacionCompraResponse | null>(null);
  const [procesando, setProcesando] = useState(false);
  const [errorAccion, setErrorAccion] = useState<string | null>(null);

  /** Lápiz: un borrador se sigue editando directo; una confirmada primero pide confirmación,
   *  porque editarla la devuelve a borrador. */
  function handleEditar(planificacion: PlanificacionCompraResponse) {
    setErrorAccion(null);
    if (planificacion.estado === 'BORRADOR') onContinuarBorrador(planificacion);
    else setAReabrir(planificacion);
  }

  async function confirmarReabrir() {
    if (!aReabrir) return;
    setProcesando(true);
    try {
      const reabierta = await reabrirPlanificacion(aReabrir.id);
      setAReabrir(null);
      onContinuarBorrador(reabierta);
    } catch (err) {
      setErrorAccion(extraerMensajeError(err, 'No se pudo abrir la planificación para editarla.'));
      setAReabrir(null);
    } finally {
      setProcesando(false);
    }
  }

  async function confirmarEliminar() {
    if (!aEliminar) return;
    setProcesando(true);
    try {
      await eliminarPlanificacion(aEliminar.id);
      setPlanificaciones((prev) => prev.filter((p) => p.id !== aEliminar.id));
    } catch (err) {
      setErrorAccion(extraerMensajeError(err, 'No se pudo eliminar la planificación.'));
    } finally {
      setAEliminar(null);
      setProcesando(false);
    }
  }

  useEffect(() => {
    let cancelado = false;
    listarPlanificacionesCompra()
      .then((data) => {
        if (!cancelado) {
          setPlanificaciones(data);
          setEstadoCarga('listo');
        }
      })
      .catch((err) => {
        if (!cancelado) {
          setError(extraerMensajeError(err, 'No se pudo cargar el listado de planificaciones.'));
          setEstadoCarga('error');
        }
      });
    return () => {
      cancelado = true;
    };
  }, []);

  return (
    <div className="flex flex-col gap-5">
      {mensajeExito && (
        <div className="rounded-lg border border-wc-green/30 bg-wc-green/10 px-4 py-3 text-sm font-medium text-wc-green" role="status">
          {mensajeExito}
        </div>
      )}

      {errorAccion && (
        <p className="text-sm font-medium text-red-600" role="alert">
          {errorAccion}
        </p>
      )}

      <div className="flex items-center justify-between">
        <p className="text-xs text-wc-text-muted">
          {estadoCarga === 'listo' ? `${planificaciones.length} planificación(es)` : ' '}
        </p>
        <button
          type="button"
          onClick={onNueva}
          className="rounded-lg bg-wc-green/10 px-4 py-2 text-sm font-semibold text-wc-green transition hover:bg-wc-green/20"
        >
          + Nueva planificación
        </button>
      </div>

      {estadoCarga === 'cargando' && <p className="text-sm text-wc-text-muted">Cargando…</p>}
      {estadoCarga === 'error' && <p className="text-sm text-wc-text-muted">{error}</p>}
      {estadoCarga === 'listo' && planificaciones.length === 0 && (
        <p className="text-sm text-wc-text-muted">Todavía no se creó ninguna planificación de compra.</p>
      )}

      {estadoCarga === 'listo' && planificaciones.length > 0 && (
        <div className="flex flex-col gap-2">
          {planificaciones.map((p) => {
            const esBorrador = p.estado === 'BORRADOR';
            const nombre = p.nombre || 'Sin nombre todavía';

            const datos = (
              <>
                <div className="min-w-0 flex-1">
                  <div className="flex items-center gap-2">
                    <p className="truncate text-sm font-bold text-wc-text">{nombre}</p>
                    {esBorrador && (
                      <span className="shrink-0 rounded-full bg-wc-green/10 px-2 py-0.5 text-[10px] font-bold text-wc-green">
                        Borrador
                      </span>
                    )}
                  </div>
                  <p className="text-xs text-wc-text-muted">
                    {/* El período lo calcula el backend con los productos elegidos: un borrador
                        sin productos todavía no tiene. */}
                    {p.fechaDesde && p.fechaHasta ? `Entregas ${formatearFecha(p.fechaDesde)} al ${formatearFecha(p.fechaHasta)} · ` : ''}
                    {p.cantidadProductos} producto
                    {p.cantidadProductos === 1 ? '' : 's'}
                  </p>
                </div>
                <div className="shrink-0 text-right text-xs text-wc-text-muted">
                  <p>{p.nombreCreadoPor}</p>
                  <p>{formatearFechaHora(p.fechaCreacion)}</p>
                </div>
              </>
            );

            // La fila es un <div> y no un <button> entero: lleva sus propios botones de acción
            // a la derecha, y anidar un <button> dentro de otro no es HTML válido. En una
            // confirmada, la zona de datos es el botón que abre el detalle (como antes); en un
            // borrador no hay detalle que abrir, se entra con el lápiz.
            return (
              <div
                key={p.id}
                className={`flex items-center gap-3 rounded-lg border bg-white p-3 shadow-sm transition hover:border-wc-green ${
                  esBorrador ? 'border-wc-green' : 'border-wc-border'
                }`}
              >
                {esBorrador ? (
                  <div className="flex min-w-0 flex-1 items-center justify-between gap-3">{datos}</div>
                ) : (
                  <button
                    type="button"
                    onClick={() => onVerDetalle(p)}
                    className="flex min-w-0 flex-1 items-center justify-between gap-3 text-left"
                  >
                    {datos}
                  </button>
                )}

                <div className="flex shrink-0 items-center gap-1.5">
                  <button
                    type="button"
                    onClick={() => handleEditar(p)}
                    disabled={procesando}
                    aria-label={esBorrador ? `Continuar editando ${nombre}` : `Editar ${nombre}`}
                    title={esBorrador ? 'Continuar editando borrador' : 'Editar planificación'}
                    className={`${CLASES_BOTON_ACCION} hover:bg-wc-bg hover:text-wc-text`}
                  >
                    <PencilIcon />
                  </button>
                  <button
                    type="button"
                    onClick={() => {
                      setErrorAccion(null);
                      setAEliminar(p);
                    }}
                    disabled={procesando}
                    aria-label={`Eliminar ${nombre}`}
                    title="Eliminar planificación"
                    className={`${CLASES_BOTON_ACCION} hover:border-red-200 hover:bg-red-50 hover:text-red-600`}
                  >
                    <TrashIcon />
                  </button>
                </div>
              </div>
            );
          })}
        </div>
      )}

      {aReabrir && (
        <ModalConfirmacion
          titulo="¿Editar esta planificación?"
          mensaje={`"${aReabrir.nombre}" ya está confirmada. Para editarla vuelve a quedar como borrador: vas a poder cambiar el nombre y los productos, y el consumo se recalcula cuando la confirmes de nuevo. Hasta entonces sus productos no figuran como ya planificados.`}
          onCerrar={() => setAReabrir(null)}
          acciones={[
            { label: procesando ? 'Abriendo…' : 'Sí, editar', variante: 'primaria', onClick: confirmarReabrir },
            { label: 'Cancelar', variante: 'secundaria', onClick: () => setAReabrir(null) },
          ]}
        />
      )}

      {aEliminar && (
        <ModalConfirmacion
          titulo="¿Eliminar planificación?"
          mensaje={
            aEliminar.estado === 'BORRADOR'
              ? `Se va a borrar el borrador "${aEliminar.nombre || 'Sin nombre todavía'}". No se puede deshacer.`
              : `Se va a borrar "${aEliminar.nombre}" con todo su consumo calculado. Sus productos van a volver a figurar como no planificados. No se puede deshacer.`
          }
          onCerrar={() => setAEliminar(null)}
          acciones={[
            { label: procesando ? 'Eliminando…' : 'Sí, eliminar', variante: 'peligro', onClick: confirmarEliminar },
            { label: 'Cancelar', variante: 'secundaria', onClick: () => setAEliminar(null) },
          ]}
        />
      )}
    </div>
  );
}
