import { useCallback, useEffect, useRef, useState } from 'react';
import AppHeader from '../../components/AppHeader';
import ModalConfirmacion from '../../components/ModalConfirmacion';
import { useAuth } from '../../context/AuthContext';
import {
  cancelarTrabajoCorte,
  consultarTrabajoCorte,
  obtenerPreparacionCorte,
  optimizarCortes,
} from '../../services/corteService';
import { listarTandas } from '../../services/tandaService';
import type { ModoTiempoCorte, ResumenPreparacionCorte, TrabajoCorte } from '../../types/corte';
import type { TandaResponse } from '../../types/produccion';
import { extraerMensajeError } from '../../utils/errores';
import AvanceTrabajo from './AvanceTrabajo';
import HojaDeCortes from './HojaDeCortes';
import TarjetaCorte from './TarjetaCorte';
import { duracion, largoTela } from './formato';

const INTERVALO_MS = 1500;
const ESTADOS_EN_CURSO = ['EN_COLA', 'EJECUTANDO'];
const MODOS: { valor: ModoTiempoCorte; etiqueta: string; detalle: string }[] = [
  { valor: 'RAPIDO', etiqueta: 'Rápido', detalle: 'hasta 1 min por corte' },
  { valor: 'COMPLETO', etiqueta: 'Completo', detalle: 'hasta 3 min y 20 s por corte, aprovecha mejor la tela' },
];

/** El trabajo en curso de cada tanda se recuerda en la sesión del navegador: si se sale de la
 *  pantalla y se vuelve, se retoma el avance (los trabajos viven 1 hora en el optimizador). */
function claveTrabajo(idTanda: number) {
  return `tizadas:trabajo:${idTanda}`;
}
function leerTrabajoGuardado(idTanda: number): string | null {
  try {
    return window.sessionStorage.getItem(claveTrabajo(idTanda));
  } catch {
    return null;
  }
}
function guardarTrabajo(idTanda: number, idTrabajo: string | null) {
  try {
    if (idTrabajo) window.sessionStorage.setItem(claveTrabajo(idTanda), idTrabajo);
    else window.sessionStorage.removeItem(claveTrabajo(idTanda));
  } catch {
    // sin storage solo se pierde el poder retomar el avance
  }
}

/**
 * Motor Tizada (Módulo 4): genera los cortes de una tanda con el optimizador y muestra el avance
 * en vivo y el resultado (dibujo de cada tizada, capas por color y hoja de cortes). Las tizadas
 * todavía no se guardan: el resultado se puede ver mientras el trabajo no venza.
 */
export default function TizadasView() {
  const { usuario } = useAuth();
  const esAdministrativo = usuario?.rol === 'ROLE_ADMINISTRATIVO';
  const [tandas, setTandas] = useState<TandaResponse[]>([]);
  const [idTanda, setIdTanda] = useState<number | null>(null);
  const [preparacion, setPreparacion] = useState<ResumenPreparacionCorte | null>(null);
  const [cargandoPreparacion, setCargandoPreparacion] = useState(false);
  const [modo, setModo] = useState<ModoTiempoCorte>('RAPIDO');
  const [trabajo, setTrabajo] = useState<TrabajoCorte | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [aviso, setAviso] = useState<string | null>(null);
  const [errorSondeo, setErrorSondeo] = useState<string | null>(null);
  const [lanzando, setLanzando] = useState(false);
  const [cancelando, setCancelando] = useState(false);
  const [confirmarCancelar, setConfirmarCancelar] = useState(false);
  const temporizador = useRef<number | null>(null);
  /** Trabajo que se está sondeando ahora: una respuesta que llega de otro (por ejemplo, de la
   *  tanda anterior) se descarta y no programa otra consulta. */
  const sondeoVigente = useRef<string | null>(null);
  /** La tanda elegida, para que volver a elegir la misma no reinicie nada (React, en modo
   *  estricto, corre dos veces la carga inicial de tandas). */
  const tandaElegida = useRef<number | null>(null);

  const detenerSondeo = useCallback(() => {
    sondeoVigente.current = null;
    if (temporizador.current !== null) window.clearTimeout(temporizador.current);
  }, []);

  const consultar = useCallback(function consultarTrabajo(tanda: number, idTrabajo: string) {
    sondeoVigente.current = idTrabajo;
    consultarTrabajoCorte(tanda, idTrabajo)
      .then((vista) => {
        if (sondeoVigente.current !== idTrabajo) return;
        setTrabajo(vista);
        setErrorSondeo(null);
        if (ESTADOS_EN_CURSO.includes(vista.estado)) {
          temporizador.current = window.setTimeout(() => consultarTrabajo(tanda, idTrabajo), INTERVALO_MS);
        }
      })
      .catch((e) => {
        if (sondeoVigente.current !== idTrabajo) return;
        const estado = (e as { response?: { status?: number } })?.response?.status;
        if (estado === 404) {
          guardarTrabajo(tanda, null);
          setTrabajo(null);
          setAviso('El último trabajo de esta tanda ya no está disponible (venció o se reinició el optimizador).');
        } else {
          setErrorSondeo(extraerMensajeError(e, 'No se pudo consultar el avance: se reintenta en unos segundos'));
          temporizador.current = window.setTimeout(() => consultarTrabajo(tanda, idTrabajo), INTERVALO_MS * 4);
        }
      });
  }, []);

  /** Elegir una tanda limpia lo anterior; el efecto de abajo trae su demanda y retoma su trabajo. */
  const elegirTanda = useCallback(
    (id: number | null) => {
      if (id === tandaElegida.current) return;
      tandaElegida.current = id;
      detenerSondeo();
      setIdTanda(id);
      setPreparacion(null);
      setTrabajo(null);
      setError(null);
      setErrorSondeo(null);
      setAviso(null);
      setCargandoPreparacion(id !== null);
    },
    [detenerSondeo],
  );

  useEffect(() => {
    listarTandas(false)
      .then((lista) => {
        setTandas(lista);
        if (lista.length > 0) elegirTanda(lista[0].id);
      })
      .catch((e) => setError(extraerMensajeError(e, 'No se pudieron cargar las tandas')));
  }, [elegirTanda]);

  useEffect(() => {
    if (idTanda === null) return;
    let vigente = true;
    obtenerPreparacionCorte(idTanda)
      .then((datos) => vigente && setPreparacion(datos))
      .catch((e) => vigente && setError(extraerMensajeError(e, 'No se pudo cargar la demanda de la tanda')))
      .finally(() => vigente && setCargandoPreparacion(false));
    const guardado = leerTrabajoGuardado(idTanda);
    if (guardado) consultar(idTanda, guardado);
    return () => {
      vigente = false;
      detenerSondeo();
    };
  }, [idTanda, consultar, detenerSondeo]);

  async function generar() {
    if (idTanda === null) return;
    setError(null);
    setAviso(null);
    setLanzando(true);
    try {
      const creado = await optimizarCortes(idTanda, { modoTiempo: modo });
      guardarTrabajo(idTanda, creado.idTrabajo);
      setTrabajo({
        idTrabajo: creado.idTrabajo, idTanda, estado: creado.estado, posicionEnCola: creado.posicionEnCola,
        progreso: null, creado: null, iniciado: null, terminado: null, venceEn: null, error: null, resultado: null,
      });
      detenerSondeo();
      temporizador.current = window.setTimeout(() => consultar(idTanda, creado.idTrabajo), 500);
    } catch (e) {
      setError(extraerMensajeError(e, 'No se pudo lanzar la optimización'));
    } finally {
      setLanzando(false);
    }
  }

  async function cancelar() {
    if (idTanda === null || !trabajo) return;
    setConfirmarCancelar(false);
    setCancelando(true);
    try {
      setTrabajo(await cancelarTrabajoCorte(idTanda, trabajo.idTrabajo));
    } catch (e) {
      setError(extraerMensajeError(e, 'No se pudo cancelar'));
    } finally {
      setCancelando(false);
    }
  }

  const enCurso = trabajo !== null && ESTADOS_EN_CURSO.includes(trabajo.estado);
  const resultado = trabajo?.estado === 'TERMINADO' ? trabajo.resultado : null;
  const sinDemanda = preparacion !== null && !preparacion.grupos.some((grupo) => grupo.items.length > 0);

  return (
    <div className="tw-scope flex flex-col px-8 pb-12 pt-7">
      <AppHeader title="Motor Tizada" />

      <div className="flex flex-col gap-5">
        <section className="flex flex-col gap-4 rounded-lg border border-wc-border bg-white p-4">
          <div className="flex flex-wrap items-end gap-4">
            <label className="flex flex-col gap-1 text-xs font-semibold text-wc-text-muted">
              Tanda
              <select
                className="min-w-48 rounded-md border border-wc-border bg-white px-3 py-2 text-sm text-wc-text"
                value={idTanda ?? ''}
                disabled={enCurso}
                onChange={(e) => elegirTanda(Number(e.target.value))}
              >
                {tandas.length === 0 && <option value="">No hay tandas abiertas</option>}
                {tandas.map((tanda) => (
                  <option key={tanda.id} value={tanda.id}>
                    {tanda.nombre} · {tanda.cantidadPedidos} pedido{tanda.cantidadPedidos === 1 ? '' : 's'}
                  </option>
                ))}
              </select>
            </label>

            <fieldset className="flex flex-col gap-1 border-0 p-0">
              <legend className="mb-1 text-xs font-semibold text-wc-text-muted">Tiempo por corte</legend>
              <div className="flex overflow-hidden rounded-md border border-wc-border">
                {MODOS.map((opcion) => (
                  <button
                    key={opcion.valor}
                    type="button"
                    disabled={enCurso}
                    onClick={() => setModo(opcion.valor)}
                    title={opcion.detalle}
                    aria-pressed={modo === opcion.valor}
                    className={`px-3 py-2 text-sm font-semibold ${
                      modo === opcion.valor ? 'bg-wc-green text-white' : 'bg-white text-wc-text-muted hover:bg-wc-bg'
                    }`}
                  >
                    {opcion.etiqueta}
                  </button>
                ))}
              </div>
            </fieldset>

            {esAdministrativo && (
              <button
                type="button"
                onClick={generar}
                disabled={lanzando || enCurso || idTanda === null || sinDemanda || cargandoPreparacion}
                className="rounded-md bg-wc-green px-4 py-2 text-sm font-semibold text-white hover:bg-wc-green-dark disabled:cursor-not-allowed disabled:opacity-50"
              >
                {lanzando ? 'Lanzando…' : resultado ? 'Generar de nuevo' : 'Generar cortes'}
              </button>
            )}
          </div>

          <p className="m-0 text-xs text-wc-text-muted">
            {MODOS.find((opcion) => opcion.valor === modo)?.detalle}.{' '}
            {cargandoPreparacion && 'Cargando la demanda de la tanda…'}
            {preparacion && !cargandoPreparacion && (
              <>
                Entran {preparacion.resumen.unidadesIncluidas} unidades ({preparacion.resumen.piezasDistintas} piezas distintas
                en {preparacion.grupos.filter((grupo) => grupo.items.length > 0).length} telas)
                {preparacion.resumen.unidadesPendientes > 0 &&
                  ` · ${preparacion.resumen.unidadesPendientes} unidades quedan afuera por datos incompletos (ver la preparación del corte)`}
                .
              </>
            )}
          </p>
          {sinDemanda && <div className="rounded-md bg-amber-50 px-3 py-2 text-sm text-amber-900">Esta tanda no tiene nada listo para cortar.</div>}
        </section>

        {error && <div className="rounded-md bg-red-50 px-3 py-2 text-sm text-red-800">{error}</div>}
        {aviso && <div className="rounded-md bg-amber-50 px-3 py-2 text-sm text-amber-900">{aviso}</div>}
        {errorSondeo && <div className="rounded-md bg-red-50 px-3 py-2 text-sm text-red-800">{errorSondeo}</div>}

        {trabajo && enCurso && (
          <AvanceTrabajo
            trabajo={trabajo}
            puedeCancelar={esAdministrativo}
            cancelando={cancelando}
            onCancelar={() => setConfirmarCancelar(true)}
          />
        )}
        {trabajo?.estado === 'CANCELADO' && (
          <div className="rounded-md bg-wc-bg px-3 py-2 text-sm text-wc-text-muted">La optimización se canceló.</div>
        )}
        {trabajo?.estado === 'ERROR' && (
          <div className="rounded-md bg-red-50 px-3 py-2 text-sm text-red-800">
            La optimización falló: {trabajo.error ?? 'sin detalle'}
          </div>
        )}

        {resultado && (
          <>
            <section className="grid grid-cols-2 gap-3 sm:grid-cols-3 lg:grid-cols-6">
              {[
                ['Cortes', String(resultado.resumen.cortes)],
                ['Capas en total', String(resultado.resumen.capasTotales)],
                ['Tela (largo × capas)', largoTela(resultado.resumen.telaTotalCm)],
                ['Piezas de más', String(resultado.resumen.excedenteTotal)],
                ['Terminaron por tiempo', `${resultado.resumen.cortesAlLimiteDeTiempo.length} de ${resultado.resumen.cortes}`],
                ['Tiempo total', duracion(resultado.resumen.segundos)],
              ].map(([etiqueta, valor]) => (
                <div key={etiqueta} className="flex flex-col gap-1 rounded-lg border border-wc-border bg-white p-3">
                  <span className="text-xs font-semibold text-wc-text-muted">{etiqueta}</span>
                  <span className="text-xl font-semibold text-wc-text">{valor}</span>
                </div>
              ))}
            </section>

            {resultado.noResueltas.length > 0 && (
              <section className="rounded-lg border border-amber-200 bg-amber-50 p-4 text-sm text-amber-900">
                <h3 className="m-0 mb-2 text-sm font-semibold">Piezas que no se pudieron cortar</h3>
                <ul className="m-0 list-disc pl-5">
                  {resultado.noResueltas.map((nr) => (
                    <li key={`${nr.idPieza}-${nr.idTalle}-${nr.motivo}`}>{nr.detalle}</li>
                  ))}
                </ul>
              </section>
            )}
            {resultado.advertencias.length > 0 && (
              <ul className="m-0 list-disc rounded-lg border border-wc-border bg-white py-3 pl-8 pr-4 text-sm text-wc-text-muted">
                {resultado.advertencias.map((advertencia) => (
                  <li key={advertencia}>{advertencia}</li>
                ))}
              </ul>
            )}

            {resultado.cortes.map((corte) => (
              <TarjetaCorte key={corte.numero} corte={corte} />
            ))}

            <HojaDeCortes cortes={resultado.cortes} />
          </>
        )}
      </div>

      {confirmarCancelar && (
        <ModalConfirmacion
          titulo="¿Cancelar la optimización?"
          mensaje="Se pierde lo calculado hasta ahora de esta tanda."
          acciones={[
            { label: 'Cancelar la optimización', onClick: cancelar, variante: 'peligro' },
            { label: 'Seguir', onClick: () => setConfirmarCancelar(false), variante: 'secundaria' },
          ]}
          onCerrar={() => setConfirmarCancelar(false)}
        />
      )}
    </div>
  );
}
