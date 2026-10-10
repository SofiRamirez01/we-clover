import { useEffect, useMemo, useState } from 'react';
import EstadoBadge from '../fichas-tecnicas/EstadoBadge';
import { listarProduccionPedidos } from '../../services/produccionService';
import {
  eliminarTanda,
  guardarPriorizacion,
  listarAlertasPriorizacion,
  listarTandas,
  renombrarTanda,
} from '../../services/tandaService';
import { extraerMensajeError } from '../../utils/errores';
import { ESTADO_TANDA_LABELS } from '../../types/produccion';
import type {
  AlertaPriorizacion,
  EstadoTanda,
  MovimientoTanda,
  ProduccionPedidoResponse,
  TandaRef,
  TandaResponse,
} from '../../types/produccion';
import { guardarAlertasDescartadas, leerAlertasDescartadas } from './alertasDescartadas';
import { CLASES_ESTADO_TANDA, COLOR_SIN_TANDA, colorDeTanda } from './tandaVisual';

const CandadoIcon = () => (
  <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth={2} strokeLinecap="round" strokeLinejoin="round">
    <rect x="4" y="11" width="16" height="10" rx="2" />
    <path d="M8 11V7a4 4 0 0 1 8 0v4" />
  </svg>
);

/** Tanda tal como la ve el borrador del popup: una existente o una creada en esta sesión.
 *  `clave` identifica a ambas por igual (por id o por id temporal, nunca por nombre). */
interface TandaBorrador {
  clave: string;
  id: number | null;
  idTemporal: string | null;
  nombre: string;
  /** El de antes de esta sesión: el congelamiento se evalúa contra esto, igual que el backend. */
  estado: EstadoTanda;
  esNueva: boolean;
}

interface ModalPriorizarProps {
  onCerrar: () => void;
  onGuardado: () => void;
}

const claveExistente = (id: number) => `e${id}`;
const claveNueva = (idTemporal: string) => `n${idTemporal}`;

/** A, B… Z y después AA, AB… (como las columnas de Excel). */
function compararNombres(a: string, b: string): number {
  return a.length - b.length || a.localeCompare(b);
}

function siguienteNombreLibre(usados: Set<string>): string {
  for (let n = 0; n < 702; n++) {
    const nombre =
      n < 26 ? String.fromCharCode(65 + n) : String.fromCharCode(64 + Math.floor(n / 26)) + String.fromCharCode(65 + (n % 26));
    if (!usados.has(nombre)) return nombre;
  }
  return '';
}

const soloLetras = (valor: string) => valor.toUpperCase().replace(/[^A-Z]/g, '').slice(0, 5);

function tieneEtapasCompletadas(pedido: ProduccionPedidoResponse): boolean {
  return pedido.productos.some((producto) => producto.etapas.some((etapa) => etapa.completado));
}

const estaFinalizado = (pedido: ProduccionPedidoResponse) =>
  pedido.estadoActual === 'ENTREGADO' || pedido.estadoActual === 'CANCELADO';

/**
 * Popup de priorización (solo ROLE_ADMINISTRATIVO). Todo lo que se toca acá es un borrador en
 * memoria: "Guardar" lo manda en un solo request que el backend aplica completo o no aplica
 * (POST /api/tandas/priorizacion) y "Cancelar" lo descarta. Las dos excepciones, aclaradas en
 * pantalla, son renombrar y eliminar una tanda ya existente, que se aplican al instante.
 */
export default function ModalPriorizar({ onCerrar, onGuardado }: ModalPriorizarProps) {
  const [cargando, setCargando] = useState(true);
  const [errorCarga, setErrorCarga] = useState<string | null>(null);
  const [existentes, setExistentes] = useState<TandaResponse[]>([]);
  const [pedidos, setPedidos] = useState<ProduccionPedidoResponse[]>([]);
  const [alertas, setAlertas] = useState<AlertaPriorizacion[]>([]);
  const [descartadas, setDescartadas] = useState<Set<number>>(leerAlertasDescartadas);

  // ---- Borrador ----
  const [nuevas, setNuevas] = useState<{ idTemporal: string; nombre: string }[]>([]);
  const [contadorNuevas, setContadorNuevas] = useState(1);
  /** Claves de las tandas abiertas, en el orden de cola que se va a guardar. */
  const [orden, setOrden] = useState<string[]>([]);
  const [ordenOriginal, setOrdenOriginal] = useState<string[]>([]);
  /** Solo los pedidos que cambian: id de pedido → clave de tanda destino (null = sin tanda). */
  const [destinos, setDestinos] = useState<Record<number, string | null>>({});
  const [motivos, setMotivos] = useState<Record<number, string>>({});
  const [nota, setNota] = useState('');

  const [expandidas, setExpandidas] = useState<Set<string>>(new Set());
  const [verCerradas, setVerCerradas] = useState(false);
  const [renombrando, setRenombrando] = useState<{ id: number; valor: string } | null>(null);
  const [confirmandoEliminar, setConfirmandoEliminar] = useState<number | null>(null);
  const [guardando, setGuardando] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let cancelado = false;
    Promise.all([listarTandas(true), listarProduccionPedidos({}), listarAlertasPriorizacion()])
      .then(([dataTandas, dataPedidos, dataAlertas]) => {
        if (cancelado) return;
        setExistentes(dataTandas);
        setPedidos(dataPedidos);
        setAlertas(dataAlertas);
        const abiertas = dataTandas.filter((t) => t.estado !== 'CERRADA').map((t) => claveExistente(t.id));
        setOrden(abiertas);
        setOrdenOriginal(abiertas);
        setExpandidas(new Set(abiertas));
      })
      .catch((err) => {
        if (!cancelado) setErrorCarga(extraerMensajeError(err, 'No se pudo cargar la priorización.'));
      })
      .finally(() => {
        if (!cancelado) setCargando(false);
      });
    return () => {
      cancelado = true;
    };
  }, []);

  const tandasPorClave = useMemo(() => {
    const mapa = new Map<string, TandaBorrador>();
    for (const t of existentes) {
      mapa.set(claveExistente(t.id), {
        clave: claveExistente(t.id),
        id: t.id,
        idTemporal: null,
        nombre: t.nombre,
        estado: t.estado,
        esNueva: false,
      });
    }
    for (const n of nuevas) {
      mapa.set(claveNueva(n.idTemporal), {
        clave: claveNueva(n.idTemporal),
        id: null,
        idTemporal: n.idTemporal,
        nombre: n.nombre,
        estado: 'PLANIFICADA',
        esNueva: true,
      });
    }
    return mapa;
  }, [existentes, nuevas]);

  const abiertas = useMemo(
    () => orden.map((clave) => tandasPorClave.get(clave)).filter((t): t is TandaBorrador => t != null),
    [orden, tandasPorClave],
  );
  const cerradas = useMemo(() => existentes.filter((t) => t.estado === 'CERRADA'), [existentes]);

  const claveOriginal = (pedido: ProduccionPedidoResponse) => (pedido.tanda ? claveExistente(pedido.tanda.id) : null);
  const claveEfectiva = (pedido: ProduccionPedidoResponse) =>
    pedido.id in destinos ? destinos[pedido.id] : claveOriginal(pedido);

  /** Mismas reglas que valida el backend, para no ofrecer movimientos que va a rechazar. */
  function puedeSalirDeSuTanda(pedido: ProduccionPedidoResponse): boolean {
    const original = pedido.tanda;
    if (!original) return true;
    if (original.estado === 'CERRADA') return false;
    return !(original.estado === 'EN_PRODUCCION' && tieneEtapasCompletadas(pedido));
  }

  /** Una tanda nueva acepta cualquier pedido activo; una existente solo si está PLANIFICADA. */
  const aceptaPedidos = (tanda: TandaBorrador) => tanda.esNueva || tanda.estado === 'PLANIFICADA';

  const pedidosDe = (clave: string) => pedidos.filter((p) => claveEfectiva(p) === clave);
  const sinTanda = useMemo(
    () =>
      pedidos
        .filter((p) => !estaFinalizado(p) && claveEfectiva(p) === null)
        .sort((a, b) => (a.prioridadAutomatica ?? Infinity) - (b.prioridadAutomatica ?? Infinity)),
    // eslint-disable-next-line react-hooks/exhaustive-deps
    [pedidos, destinos],
  );

  function mover(pedido: ProduccionPedidoResponse, clave: string | null) {
    setError(null);
    setDestinos((prev) => {
      const copia = { ...prev };
      if (clave === claveOriginal(pedido)) delete copia[pedido.id];
      else copia[pedido.id] = clave;
      return copia;
    });
  }

  /** Crea una tanda en el borrador con la primera letra libre, en su lugar alfabético de la
   *  cola, y devuelve su clave. */
  function crearTanda(): string {
    const usados = new Set(abiertas.map((t) => t.nombre));
    const nombre = siguienteNombreLibre(usados);
    const idTemporal = `t${contadorNuevas}`;
    const clave = claveNueva(idTemporal);
    setContadorNuevas((n) => n + 1);
    setNuevas((prev) => [...prev, { idTemporal, nombre }]);
    setOrden((prev) => {
      const indice = prev.findIndex((c) => compararNombres(tandasPorClave.get(c)?.nombre ?? '', nombre) > 0);
      const copia = [...prev];
      copia.splice(indice === -1 ? copia.length : indice, 0, clave);
      return copia;
    });
    setExpandidas((prev) => new Set(prev).add(clave));
    return clave;
  }

  function quitarTandaNueva(tanda: TandaBorrador) {
    setNuevas((prev) => prev.filter((n) => n.idTemporal !== tanda.idTemporal));
    setOrden((prev) => prev.filter((c) => c !== tanda.clave));
  }

  function subirOBajar(clave: string, delta: -1 | 1) {
    setOrden((prev) => {
      const indice = prev.indexOf(clave);
      const destino = indice + delta;
      if (indice === -1 || destino < 0 || destino >= prev.length) return prev;
      const copia = [...prev];
      [copia[indice], copia[destino]] = [copia[destino], copia[indice]];
      return copia;
    });
  }

  function toggleExpandida(clave: string) {
    setExpandidas((prev) => {
      const copia = new Set(prev);
      if (copia.has(clave)) copia.delete(clave);
      else copia.add(clave);
      return copia;
    });
  }

  function descartarAlerta(idPedido: number) {
    setDescartadas((prev) => {
      const copia = new Set(prev).add(idPedido);
      guardarAlertasDescartadas(copia);
      return copia;
    });
  }

  async function confirmarRenombrar() {
    if (!renombrando) return;
    setError(null);
    try {
      const actualizada = await renombrarTanda(renombrando.id, renombrando.valor);
      setExistentes((prev) => prev.map((t) => (t.id === actualizada.id ? { ...t, nombre: actualizada.nombre } : t)));
      setRenombrando(null);
    } catch (err) {
      setError(extraerMensajeError(err, 'No se pudo renombrar la tanda.'));
    }
  }

  async function confirmarEliminar(tanda: TandaBorrador) {
    if (tanda.id == null) return;
    setError(null);
    try {
      await eliminarTanda(tanda.id);
      setExistentes((prev) => prev.filter((t) => t.id !== tanda.id));
      setOrden((prev) => prev.filter((c) => c !== tanda.clave));
      setOrdenOriginal((prev) => prev.filter((c) => c !== tanda.clave));
    } catch (err) {
      setError(extraerMensajeError(err, 'No se pudo eliminar la tanda.'));
    } finally {
      setConfirmandoEliminar(null);
    }
  }

  // ---- Qué se va a guardar ----
  const pedidosMovidos = pedidos.filter((p) => p.id in destinos);
  const cambioElOrden = nuevas.length > 0 || orden.join('|') !== ordenOriginal.join('|');
  const hayCambios = pedidosMovidos.length > 0 || cambioElOrden || nota.trim() !== '';

  /** Sacar un pedido de una tanda en la que ya estaba exige motivo (también si pasa a otra). */
  const necesitaMotivo = (pedido: ProduccionPedidoResponse) => pedido.id in destinos && pedido.tanda != null;
  const faltanMotivos = pedidosMovidos.filter((p) => necesitaMotivo(p) && !(motivos[p.id] ?? '').trim());

  function validar(): string | null {
    const nombres = abiertas.map((t) => t.nombre);
    if (nombres.some((nombre) => nombre === '')) return 'Hay una tanda nueva sin nombre.';
    if (new Set(nombres).size !== nombres.length) return 'Hay dos tandas abiertas con el mismo nombre.';
    if (faltanMotivos.length > 0) {
      return `Falta el motivo para sacar de su tanda al pedido #${faltanMotivos[0].codigoInterno}.`;
    }
    return null;
  }

  const aRef = (tanda: TandaBorrador): TandaRef =>
    tanda.id != null ? { id: tanda.id } : { idTemporal: tanda.idTemporal as string };

  async function guardar() {
    const problema = validar();
    if (problema) {
      setError(problema);
      return;
    }
    setGuardando(true);
    setError(null);
    const movimientos: MovimientoTanda[] = pedidosMovidos.map((pedido) => {
      const destino = destinos[pedido.id] ? tandasPorClave.get(destinos[pedido.id] as string) : undefined;
      return {
        idPedido: pedido.id,
        idTandaOrigenEsperada: pedido.tanda?.id ?? null,
        destino: destino ? aRef(destino) : null,
        motivo: (motivos[pedido.id] ?? '').trim() || null,
      };
    });
    try {
      await guardarPriorizacion({
        nota: nota.trim() || null,
        tandasNuevas: nuevas,
        ordenTandas: abiertas.map(aRef),
        movimientos,
      });
      onGuardado();
    } catch (err) {
      setError(extraerMensajeError(err, 'No se pudo guardar la priorización. No se aplicó ningún cambio.'));
      setGuardando(false);
    }
  }

  const alertasVisibles = alertas.filter((alerta) => {
    if (descartadas.has(alerta.idPedido)) return false;
    const pedido = pedidos.find((p) => p.id === alerta.idPedido);
    return pedido != null && claveEfectiva(pedido) === null;
  });

  /** Selector de tanda de un pedido. Las tandas que no aceptan pedidos quedan deshabilitadas,
   *  salvo la propia (para poder "volver" si se lo había movido en este borrador). */
  function selectorTanda(pedido: ProduccionPedidoResponse, etiquetaVacia: string) {
    const efectiva = claveEfectiva(pedido);
    const original = claveOriginal(pedido);
    return (
      <select
        value={efectiva ?? ''}
        onChange={(e) => mover(pedido, e.target.value || null)}
        aria-label={`Tanda del pedido ${pedido.codigoInterno}`}
        className="rounded border border-wc-border bg-white px-1.5 py-1 text-xs text-wc-text"
      >
        <option value="">{etiquetaVacia}</option>
        {abiertas.map((tanda) => (
          <option key={tanda.clave} value={tanda.clave} disabled={!aceptaPedidos(tanda) && tanda.clave !== original}>
            Tanda {tanda.nombre || '(sin nombre)'}
            {!aceptaPedidos(tanda) && tanda.clave !== original ? ' (en producción)' : ''}
          </option>
        ))}
      </select>
    );
  }

  function filaPedido(pedido: ProduccionPedidoResponse, enTanda: boolean) {
    const movido = pedido.id in destinos;
    const bloqueado = estaFinalizado(pedido) || !puedeSalirDeSuTanda(pedido);
    const noListo = pedido.estadoActual === 'PRESUPUESTADO' || pedido.estadoActual === 'SENADO';
    const saldoCero = pedido.porcentajePagado >= 99.5;

    return (
      <div key={pedido.id} className={`flex flex-col gap-1 rounded border px-2 py-1.5 ${movido ? 'border-wc-green bg-wc-green/5' : 'border-wc-border bg-white'}`}>
        <div className="flex flex-wrap items-center gap-x-2 gap-y-1">
          <span className="text-sm font-bold text-wc-text">#{pedido.codigoInterno}</span>
          <span className="min-w-0 max-w-[16rem] truncate text-xs text-wc-text">{pedido.colegio}</span>
          <span className="text-[11px] text-wc-text-muted">{pedido.curso}</span>

          <span className={`rounded px-1.5 py-0.5 text-[11px] font-semibold ${saldoCero ? 'bg-emerald-100 text-emerald-800' : 'bg-slate-100 text-slate-700'}`}>
            {saldoCero ? 'Saldo 0' : `${Math.round(pedido.porcentajePagado)}% pago`}
          </span>
          <EstadoBadge estado={pedido.estadoActual} />
          {pedido.ubicacionActual && <span className="text-[11px] text-wc-text">📍 {pedido.ubicacionActual}</span>}
          {!enTanda && pedido.prioridadAutomatica != null && (
            <span className="text-[11px] text-wc-text-muted" title="Puntaje sugerido: orden por % de pago">
              sugerido #{pedido.prioridadAutomatica}
            </span>
          )}

          <span className="ml-auto flex flex-wrap items-center gap-1.5">
            {bloqueado ? (
              <span
                className="flex items-center gap-1 text-[11px] text-wc-text-muted"
                title={estaFinalizado(pedido) ? 'Pedido finalizado' : 'Ya tiene etapas completadas en una tanda en producción'}
              >
                <CandadoIcon /> No se puede mover
              </span>
            ) : (
              <>
                {selectorTanda(pedido, enTanda ? 'Sacar de la tanda' : 'Asignar a tanda…')}
                {!enTanda && (
                  <button
                    type="button"
                    onClick={() => mover(pedido, crearTanda())}
                    className="rounded border border-wc-green px-1.5 py-1 text-xs font-semibold text-wc-green hover:bg-wc-green/10"
                  >
                    Nueva tanda
                  </button>
                )}
              </>
            )}
          </span>
        </div>

        {noListo && claveEfectiva(pedido) !== null && (
          <p className="text-[11px] font-semibold text-amber-700">
            ⚠ Este pedido todavía no está listo para producción (falta diseño, talles o pago).
          </p>
        )}

        {necesitaMotivo(pedido) && (
          <div className="flex flex-wrap items-center gap-1.5">
            <label htmlFor={`motivo-${pedido.id}`} className="text-[11px] font-semibold text-wc-text">
              Motivo para sacarlo de la tanda {pedido.tanda?.nombre} (obligatorio)
            </label>
            <input
              id={`motivo-${pedido.id}`}
              type="text"
              value={motivos[pedido.id] ?? ''}
              maxLength={500}
              onChange={(e) => {
                setError(null);
                setMotivos((prev) => ({ ...prev, [pedido.id]: e.target.value }));
              }}
              className={`min-w-[12rem] flex-1 rounded border bg-white px-1.5 py-1 text-xs text-wc-text ${
                (motivos[pedido.id] ?? '').trim() ? 'border-wc-border' : 'border-red-400'
              }`}
            />
          </div>
        )}
      </div>
    );
  }

  function bloqueTanda(tanda: TandaBorrador, indice: number) {
    const color = tanda.id != null ? colorDeTanda(tanda.id) : COLOR_SIN_TANDA;
    const pedidosTanda = pedidosDe(tanda.clave);
    const expandida = expandidas.has(tanda.clave);
    const congelada = !aceptaPedidos(tanda);
    const vaciaDeVerdad = pedidosTanda.length === 0 && !pedidos.some((p) => claveOriginal(p) === tanda.clave);

    return (
      <div key={tanda.clave} className="rounded-lg border border-wc-border" style={{ borderLeft: `4px solid ${color}` }}>
        <div className="flex flex-wrap items-center gap-2 px-2 py-1.5">
          <div className="flex flex-col">
            <button
              type="button"
              onClick={() => subirOBajar(tanda.clave, -1)}
              disabled={indice === 0}
              aria-label={`Subir la tanda ${tanda.nombre}`}
              className="px-1 text-xs leading-none text-wc-text-muted hover:text-wc-text disabled:opacity-30"
            >
              ▲
            </button>
            <button
              type="button"
              onClick={() => subirOBajar(tanda.clave, 1)}
              disabled={indice === abiertas.length - 1}
              aria-label={`Bajar la tanda ${tanda.nombre}`}
              className="px-1 text-xs leading-none text-wc-text-muted hover:text-wc-text disabled:opacity-30"
            >
              ▼
            </button>
          </div>

          <span className="text-xs font-bold text-wc-text-muted">{indice + 1}.</span>

          {tanda.esNueva ? (
            <input
              type="text"
              value={tanda.nombre}
              onChange={(e) =>
                setNuevas((prev) =>
                  prev.map((n) => (n.idTemporal === tanda.idTemporal ? { ...n, nombre: soloLetras(e.target.value) } : n)),
                )
              }
              aria-label="Nombre de la tanda nueva"
              className="w-16 rounded border border-wc-border bg-white px-1.5 py-0.5 text-lg font-black text-wc-text"
            />
          ) : renombrando?.id === tanda.id ? (
            <span className="flex items-center gap-1">
              <input
                type="text"
                value={renombrando.valor}
                autoFocus
                onChange={(e) => setRenombrando({ id: renombrando.id, valor: soloLetras(e.target.value) })}
                onKeyDown={(e) => {
                  if (e.key === 'Enter') confirmarRenombrar();
                  if (e.key === 'Escape') setRenombrando(null);
                }}
                aria-label={`Nuevo nombre de la tanda ${tanda.nombre}`}
                className="w-16 rounded border border-wc-border bg-white px-1.5 py-0.5 text-lg font-black text-wc-text"
              />
              <button type="button" onClick={confirmarRenombrar} className="text-xs font-semibold text-wc-green underline">
                Renombrar ya
              </button>
              <button type="button" onClick={() => setRenombrando(null)} className="text-xs text-wc-text-muted">
                Cancelar
              </button>
            </span>
          ) : (
            <span className="text-2xl font-black leading-none" style={{ color }}>
              {tanda.nombre}
            </span>
          )}

          {tanda.esNueva ? (
            <span className="rounded-full bg-wc-green/10 px-2 py-0.5 text-[11px] font-bold text-wc-green">Nueva</span>
          ) : (
            <span className={`rounded-full px-2 py-0.5 text-[11px] font-bold ${CLASES_ESTADO_TANDA[tanda.estado]}`}>
              {ESTADO_TANDA_LABELS[tanda.estado]}
            </span>
          )}

          {congelada && (
            <span className="flex items-center gap-1 text-[11px] text-wc-text-muted" title="En producción: no se le pueden agregar pedidos">
              <CandadoIcon /> No admite pedidos nuevos
            </span>
          )}

          <button type="button" onClick={() => toggleExpandida(tanda.clave)} className="text-xs font-semibold text-wc-text underline">
            {pedidosTanda.length} pedido{pedidosTanda.length === 1 ? '' : 's'} {expandida ? '▴' : '▾'}
          </button>

          <span className="ml-auto flex items-center gap-2">
            {tanda.esNueva && pedidosTanda.length === 0 && (
              <button type="button" onClick={() => quitarTandaNueva(tanda)} className="text-xs font-semibold text-red-600 underline">
                Quitar
              </button>
            )}
            {!tanda.esNueva && renombrando?.id !== tanda.id && (
              <button
                type="button"
                onClick={() => setRenombrando({ id: tanda.id as number, valor: tanda.nombre })}
                className="text-xs text-wc-text-muted underline"
              >
                Renombrar
              </button>
            )}
            {!tanda.esNueva &&
              vaciaDeVerdad &&
              (confirmandoEliminar === tanda.id ? (
                <span className="flex items-center gap-1 text-xs">
                  ¿Eliminar ya?
                  <button type="button" onClick={() => confirmarEliminar(tanda)} className="font-semibold text-red-600 underline">
                    Sí
                  </button>
                  <button type="button" onClick={() => setConfirmandoEliminar(null)} className="text-wc-text-muted underline">
                    No
                  </button>
                </span>
              ) : (
                <button type="button" onClick={() => setConfirmandoEliminar(tanda.id)} className="text-xs text-red-600 underline">
                  Eliminar
                </button>
              ))}
          </span>
        </div>

        {expandida && (
          <div className="flex flex-col gap-1 border-t border-wc-border bg-wc-bg/50 p-2">
            {pedidosTanda.length === 0 ? (
              <p className="text-xs text-wc-text-muted">Sin pedidos. Asigná pedidos desde la lista "Sin tanda".</p>
            ) : (
              pedidosTanda.map((pedido) => filaPedido(pedido, true))
            )}
          </div>
        )}
      </div>
    );
  }

  return (
    <div className="tw-scope fixed inset-0 z-50 flex items-center justify-center bg-black/70 p-4">
      <div className="flex max-h-[92vh] w-full max-w-4xl flex-col rounded-xl bg-white shadow-2xl" role="dialog" aria-modal="true" aria-label="Priorizar">
        <div className="flex items-center justify-between border-b border-wc-border px-5 py-3">
          <div>
            <h2 className="text-base font-bold text-wc-text">Priorizar</h2>
            <p className="text-xs text-wc-text-muted">Nada se guarda hasta apretar "Guardar". El puntaje sugerido es solo una referencia.</p>
          </div>
        </div>

        <div className="flex flex-1 flex-col gap-5 overflow-y-auto px-5 py-4">
          {cargando ? (
            <p className="text-sm text-wc-text-muted">Cargando…</p>
          ) : errorCarga ? (
            <p className="text-sm font-medium text-red-600">{errorCarga}</p>
          ) : (
            <>
              {alertasVisibles.length > 0 && (
                <section className="flex flex-col gap-1.5 rounded-lg border border-amber-300 bg-amber-50 p-3">
                  <h3 className="text-xs font-bold uppercase tracking-wide text-amber-800">
                    Avisos ({alertasVisibles.length})
                  </h3>
                  {alertasVisibles.map((alerta) => {
                    const pedido = pedidos.find((p) => p.id === alerta.idPedido) as ProduccionPedidoResponse;
                    return (
                      <div key={alerta.idPedido} className="flex flex-wrap items-center gap-2 text-xs text-wc-text">
                        <span>
                          <strong>#{alerta.codigoInterno}</strong> {alerta.colegio} ya está listo para producción y no tiene tanda.
                        </span>
                        <span className="ml-auto flex items-center gap-1.5">
                          {selectorTanda(pedido, 'Asignar a tanda…')}
                          <button type="button" onClick={() => descartarAlerta(alerta.idPedido)} className="text-wc-text-muted underline">
                            Descartar
                          </button>
                        </span>
                      </div>
                    );
                  })}
                </section>
              )}

              <section className="flex flex-col gap-2">
                <div className="flex items-center justify-between">
                  <h3 className="text-xs font-bold uppercase tracking-wide text-wc-text-muted">Cola de tandas</h3>
                  <button
                    type="button"
                    onClick={() => crearTanda()}
                    className="rounded-md border border-wc-green px-2.5 py-1 text-xs font-semibold text-wc-green hover:bg-wc-green/10"
                  >
                    + Nueva tanda
                  </button>
                </div>
                {abiertas.length === 0 ? (
                  <p className="text-xs text-wc-text-muted">Todavía no hay tandas abiertas.</p>
                ) : (
                  abiertas.map((tanda, indice) => bloqueTanda(tanda, indice))
                )}
                <p className="text-[11px] text-wc-text-muted">
                  Renombrar o eliminar una tanda que ya existe se aplica al instante, sin esperar a "Guardar".
                </p>
              </section>

              {cerradas.length > 0 && (
                <section className="flex flex-col gap-1.5">
                  <button
                    type="button"
                    onClick={() => setVerCerradas((v) => !v)}
                    className="flex items-center gap-1.5 self-start text-xs font-bold uppercase tracking-wide text-wc-text-muted"
                  >
                    <CandadoIcon /> Tandas cerradas ({cerradas.length}) {verCerradas ? '▴' : '▾'}
                  </button>
                  {verCerradas &&
                    cerradas.map((tanda) => (
                      <div key={tanda.id} className="flex flex-wrap items-center gap-2 rounded border border-wc-border bg-wc-bg px-2 py-1 text-xs text-wc-text-muted">
                        <CandadoIcon />
                        <span className="text-base font-black">{tanda.nombre}</span>
                        <span>
                          {tanda.cantidadPedidos} pedido{tanda.cantidadPedidos === 1 ? '' : 's'} · todos entregados
                        </span>
                      </div>
                    ))}
                </section>
              )}

              <section className="flex flex-col gap-1.5">
                <h3 className="text-xs font-bold uppercase tracking-wide text-wc-text-muted">
                  Sin tanda ({sinTanda.length}) · ordenados por puntaje sugerido
                </h3>
                {sinTanda.length === 0 ? (
                  <p className="text-xs text-wc-text-muted">Todos los pedidos activos tienen tanda.</p>
                ) : (
                  sinTanda.map((pedido) => filaPedido(pedido, false))
                )}
              </section>

              <section className="flex flex-col gap-1">
                <label htmlFor="nota-sesion" className="text-xs font-bold uppercase tracking-wide text-wc-text-muted">
                  Nota de esta priorización (opcional)
                </label>
                <input
                  id="nota-sesion"
                  type="text"
                  value={nota}
                  maxLength={500}
                  onChange={(e) => setNota(e.target.value)}
                  placeholder="Ej.: priorización de la semana, llega la tela el jueves…"
                  className="rounded-lg border border-wc-border bg-white px-2 py-1.5 text-sm text-wc-text"
                />
              </section>
            </>
          )}
        </div>

        <div className="flex flex-wrap items-center justify-between gap-2 border-t border-wc-border px-5 py-3">
          <div className="min-w-0 flex-1">
            {error ? (
              <p className="text-xs font-medium text-red-600">{error}</p>
            ) : (
              <p className="text-xs text-wc-text-muted">
                {hayCambios
                  ? `${pedidosMovidos.length} pedido${pedidosMovidos.length === 1 ? '' : 's'} movido${pedidosMovidos.length === 1 ? '' : 's'}` +
                    `${nuevas.length > 0 ? ` · ${nuevas.length} tanda${nuevas.length === 1 ? '' : 's'} nueva${nuevas.length === 1 ? '' : 's'}` : ''}` +
                    `${cambioElOrden && nuevas.length === 0 ? ' · orden cambiado' : ''}`
                  : 'Sin cambios para guardar.'}
              </p>
            )}
          </div>
          <div className="flex items-center gap-2">
            <button
              type="button"
              onClick={onCerrar}
              disabled={guardando}
              className="rounded-md border border-wc-border bg-white px-4 py-2 text-sm font-semibold text-wc-text-muted transition hover:bg-wc-bg disabled:opacity-60"
            >
              Cancelar
            </button>
            <button
              type="button"
              onClick={guardar}
              disabled={guardando || !hayCambios || cargando}
              className="rounded-md bg-wc-green px-5 py-2 text-sm font-semibold text-white transition hover:bg-wc-green-dark disabled:cursor-not-allowed disabled:opacity-60"
            >
              {guardando ? 'Guardando…' : 'Guardar'}
            </button>
          </div>
        </div>
      </div>
    </div>
  );
}
