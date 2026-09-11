import { useEffect, useMemo, useState } from 'react';
import { obtenerPieza } from '../../services/piezaService';
import { listarTablasTalle } from '../../services/tablaTalleService';
import { guardarTallePieza, listarTallesPieza, revertirTallePieza } from '../../services/piezaTalleService';
import { escalarPieza, segmentosDesdeCoordenadas } from './geometriaPieza';
import EditorTalleModal from './EditorTalleModal';
import type { ResultadoEdicionTalle } from './EditorTalleModal';
import { extraerMensajeError } from '../../utils/errores';
import type { PiezaDetalleResponse, PiezaTalleResponse, Punto, TablaTalleOption } from '../../types/pieza';

interface GraduacionTalleViewProps {
  piezaId: number;
}

type EstadoTalle = 'Base' | 'Generado automático' | 'Editado manualmente' | 'Pendiente';

function estadoDeFila(fila: PiezaTalleResponse | null): EstadoTalle {
  if (!fila) return 'Pendiente';
  if (fila.esBase) return 'Base';
  return fila.editadoManualmente ? 'Editado manualmente' : 'Generado automático';
}

const ESTILO_BADGE: Record<EstadoTalle, string> = {
  Base: 'bg-wc-green/10 text-wc-green-dark',
  'Generado automático': 'bg-blue-50 text-blue-700',
  'Editado manualmente': 'bg-amber-50 text-amber-700',
  Pendiente: 'bg-wc-bg text-wc-text-muted',
};

function MiniaturaContorno({ coordenadas }: { coordenadas: Punto[] }) {
  if (coordenadas.length === 0) {
    return <div className="flex h-14 w-14 items-center justify-center text-[10px] text-wc-text-muted">—</div>;
  }
  const xs = coordenadas.map(([x]) => x);
  const ys = coordenadas.map(([, y]) => -y);
  const minX = Math.min(...xs);
  const maxX = Math.max(...xs);
  const minY = Math.min(...ys);
  const maxY = Math.max(...ys);
  const margen = Math.max(maxX - minX, maxY - minY, 1) * 0.12;
  const viewBox = `${minX - margen} ${minY - margen} ${maxX - minX + margen * 2} ${maxY - minY + margen * 2}`;
  const d = `M ${coordenadas.map(([x, y]) => `${x} ${-y}`).join(' L ')} Z`;
  const grosor = Math.max((maxX - minX + maxY - minY) / 120, 0.15);
  return (
    <svg viewBox={viewBox} className="h-14 w-14 shrink-0">
      <path d={d} fill="#2f855a22" stroke="#2f855a" strokeWidth={grosor} />
    </svg>
  );
}

/**
 * Requisito 4.1, Parte 3: una fila por cada TablaTalle del grupo de la Pieza (no solo los que ya
 * tienen PiezaTalle generado) — se cruza el listado de PiezaTalle contra la tabla de talles
 * completa acá mismo, en el frontend, para poder mostrar "Pendiente" donde falta.
 *
 * El talle base no se edita desde acá (eso pasa desde el formulario de la Pieza, que ya dispara
 * la recarga en cadena de todos los talles automáticos): esta pantalla solo muestra su badge y
 * su miniatura, sin acciones.
 */
export default function GraduacionTalleView({ piezaId }: GraduacionTalleViewProps) {
  const [pieza, setPieza] = useState<PiezaDetalleResponse | null>(null);
  const [tablasTalle, setTablasTalle] = useState<TablaTalleOption[]>([]);
  const [tallesPieza, setTallesPieza] = useState<PiezaTalleResponse[]>([]);
  const [cargando, setCargando] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [objetivos, setObjetivos] = useState<Record<number, { ancho: string; largo: string }>>({});
  const [talleOcupado, setTalleOcupado] = useState<number | null>(null);
  const [talleEnEdicion, setTalleEnEdicion] = useState<TablaTalleOption | null>(null);

  useEffect(() => {
    let cancelado = false;
    async function cargarTodo() {
      setCargando(true);
      setError(null);
      try {
        const detalle = await obtenerPieza(piezaId);
        const [tablas, talles] = await Promise.all([
          listarTablasTalle(detalle.idGrupoTalle),
          listarTallesPieza(piezaId),
        ]);
        if (cancelado) return;
        setPieza(detalle);
        setTablasTalle(tablas);
        setTallesPieza(talles);
        setObjetivos((prev) => {
          const siguiente = { ...prev };
          for (const t of tablas) {
            if (siguiente[t.id]) continue;
            const filaExistente = talles.find((f) => f.idTalle === t.id);
            siguiente[t.id] = {
              ancho: String(filaExistente?.anchoCm ?? t.anchoCm),
              largo: String(filaExistente?.largoCm ?? t.largoCm),
            };
          }
          return siguiente;
        });
      } catch (err) {
        if (!cancelado) setError(extraerMensajeError(err, 'No se pudo cargar la graduación de esta pieza.'));
      } finally {
        if (!cancelado) setCargando(false);
      }
    }
    cargarTodo();
    return () => {
      cancelado = true;
    };
  }, [piezaId]);

  const filaBase = tallesPieza.find((f) => f.esBase) ?? null;

  const filas = useMemo(
    () => tablasTalle.map((talle) => ({ talle, fila: tallesPieza.find((f) => f.idTalle === talle.id) ?? null })),
    [tablasTalle, tallesPieza],
  );

  function reemplazarFila(actualizada: PiezaTalleResponse) {
    setTallesPieza((prev) => [...prev.filter((f) => f.idTalle !== actualizada.idTalle), actualizada]);
  }

  async function aplicarObjetivo(talle: TablaTalleOption) {
    if (!filaBase) return;
    const objetivo = objetivos[talle.id];
    const ancho = Number(objetivo?.ancho);
    const largo = Number(objetivo?.largo);
    if (!Number.isFinite(ancho) || ancho <= 0 || !Number.isFinite(largo) || largo <= 0) {
      setError(`El ancho y el largo objetivo del talle ${talle.talle} deben ser números mayores a 0.`);
      return;
    }
    setTalleOcupado(talle.id);
    setError(null);
    try {
      const resultado = escalarPieza(filaBase.coordenadas, ancho, largo);
      const guardado = await guardarTallePieza(piezaId, talle.id, { ...resultado, esBase: false, editadoManualmente: false });
      reemplazarFila(guardado);
    } catch (err) {
      setError(extraerMensajeError(err, `No se pudo generar el talle ${talle.talle}.`));
    } finally {
      setTalleOcupado(null);
    }
  }

  async function guardarEdicionManual(talle: TablaTalleOption, resultado: ResultadoEdicionTalle) {
    const guardado = await guardarTallePieza(piezaId, talle.id, { ...resultado, esBase: false, editadoManualmente: true });
    reemplazarFila(guardado);
    setObjetivos((prev) => ({ ...prev, [talle.id]: { ancho: String(guardado.anchoCm), largo: String(guardado.largoCm) } }));
    setTalleEnEdicion(null);
  }

  async function revertir(talle: TablaTalleOption) {
    setTalleOcupado(talle.id);
    setError(null);
    try {
      const revertido = await revertirTallePieza(piezaId, talle.id);
      let filaFinal = revertido;
      if (filaBase && revertido.anchoCm > 0 && revertido.largoCm > 0) {
        const resultado = escalarPieza(filaBase.coordenadas, revertido.anchoCm, revertido.largoCm);
        filaFinal = await guardarTallePieza(piezaId, talle.id, { ...resultado, esBase: false, editadoManualmente: false });
      }
      reemplazarFila(filaFinal);
      setObjetivos((prev) => ({ ...prev, [talle.id]: { ancho: String(filaFinal.anchoCm), largo: String(filaFinal.largoCm) } }));
    } catch (err) {
      setError(extraerMensajeError(err, `No se pudo revertir el talle ${talle.talle}.`));
    } finally {
      setTalleOcupado(null);
    }
  }

  if (cargando) {
    return <p className="text-sm text-wc-text-muted">Cargando graduación…</p>;
  }
  if (error && !pieza) {
    return <p className="text-sm text-red-600">{error}</p>;
  }
  if (!pieza) return null;

  return (
    <div className="flex flex-col gap-4">
      <div>
        <h2 className="text-lg font-bold text-wc-text">Graduación por talle — {pieza.nombre}</h2>
        <p className="mt-1 text-sm text-wc-text-muted">
          Grupo de talle {pieza.nombreGrupoTalle}. El talle base ({pieza.talleBase}) se define y se edita desde el
          formulario de la pieza; acá se genera y corrige el resto.
        </p>
      </div>

      {error && (
        <div className="rounded-lg border border-red-300 bg-red-50 px-4 py-3 text-sm font-medium text-red-700">{error}</div>
      )}

      <div className="overflow-hidden rounded-xl border border-wc-border bg-white shadow-sm">
        <table className="w-full text-left text-sm">
          <thead className="bg-wc-bg text-xs font-semibold uppercase text-wc-text-muted">
            <tr>
              <th className="px-4 py-3">Talle</th>
              <th className="px-4 py-3">Contorno</th>
              <th className="px-4 py-3">Ancho objetivo</th>
              <th className="px-4 py-3">Largo objetivo</th>
              <th className="px-4 py-3 text-right">Área</th>
              <th className="px-4 py-3">Estado</th>
              <th className="px-4 py-3 text-right">Acciones</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-wc-border">
            {filas.map(({ talle, fila }) => {
              const esBase = fila?.esBase ?? false;
              const estado = estadoDeFila(fila);
              const ocupado = talleOcupado === talle.id;
              return (
                <tr key={talle.id}>
                  <td className="px-4 py-3 font-semibold text-wc-text">{talle.talle}</td>
                  <td className="px-4 py-3">
                    <MiniaturaContorno coordenadas={fila?.coordenadas ?? []} />
                  </td>
                  <td className="px-4 py-3">
                    {esBase ? (
                      <span className="text-wc-text-muted">{fila?.anchoCm.toFixed(1)}cm</span>
                    ) : (
                      <input
                        type="number"
                        min={0}
                        step="0.1"
                        value={objetivos[talle.id]?.ancho ?? ''}
                        onChange={(e) => setObjetivos((prev) => ({ ...prev, [talle.id]: { ...prev[talle.id], ancho: e.target.value } }))}
                        disabled={ocupado}
                        className="w-20 rounded-lg border border-wc-border bg-white px-2 py-1.5 text-sm text-wc-text outline-none focus:border-wc-green focus:ring-2 focus:ring-wc-green/20"
                      />
                    )}
                  </td>
                  <td className="px-4 py-3">
                    {esBase ? (
                      <span className="text-wc-text-muted">{fila?.largoCm.toFixed(1)}cm</span>
                    ) : (
                      <input
                        type="number"
                        min={0}
                        step="0.1"
                        value={objetivos[talle.id]?.largo ?? ''}
                        onChange={(e) => setObjetivos((prev) => ({ ...prev, [talle.id]: { ...prev[talle.id], largo: e.target.value } }))}
                        disabled={ocupado}
                        className="w-20 rounded-lg border border-wc-border bg-white px-2 py-1.5 text-sm text-wc-text outline-none focus:border-wc-green focus:ring-2 focus:ring-wc-green/20"
                      />
                    )}
                  </td>
                  <td className="px-4 py-3 text-right text-wc-text-muted">{fila ? `${fila.areaCm2.toFixed(1)}cm²` : '—'}</td>
                  <td className="px-4 py-3">
                    <span className={`inline-block rounded-full px-2.5 py-1 text-xs font-semibold ${ESTILO_BADGE[estado]}`}>{estado}</span>
                  </td>
                  <td className="px-4 py-3">
                    <div className="flex justify-end gap-2">
                      {!esBase && (
                        <button
                          type="button"
                          onClick={() => aplicarObjetivo(talle)}
                          disabled={ocupado || !filaBase}
                          className="rounded-lg border border-wc-border bg-white px-3 py-1.5 text-xs font-semibold text-wc-text-muted transition hover:border-wc-green hover:bg-wc-green/10 hover:text-wc-green-dark disabled:cursor-not-allowed disabled:opacity-60"
                        >
                          {ocupado ? 'Generando…' : 'Aplicar'}
                        </button>
                      )}
                      {!esBase && (
                        <button
                          type="button"
                          onClick={() =>
                            setTalleEnEdicion(talle)
                          }
                          disabled={ocupado || (!fila && !filaBase)}
                          className="rounded-lg border border-wc-border bg-white px-3 py-1.5 text-xs font-semibold text-wc-text-muted transition hover:border-wc-green hover:bg-wc-green/10 hover:text-wc-green-dark disabled:cursor-not-allowed disabled:opacity-60"
                        >
                          Editar
                        </button>
                      )}
                      {!esBase && fila?.editadoManualmente && (
                        <button
                          type="button"
                          onClick={() => revertir(talle)}
                          disabled={ocupado}
                          className="rounded-lg border border-wc-border bg-white px-3 py-1.5 text-xs font-semibold text-wc-text-muted transition hover:border-amber-400 hover:bg-amber-50 hover:text-amber-700 disabled:cursor-not-allowed disabled:opacity-60"
                        >
                          Revertir a automático
                        </button>
                      )}
                    </div>
                  </td>
                </tr>
              );
            })}
          </tbody>
        </table>
      </div>

      {talleEnEdicion && (
        <EditorTalleModal
          talle={talleEnEdicion.talle}
          segmentosIniciales={segmentosDesdeCoordenadas(
            filas.find((f) => f.talle.id === talleEnEdicion.id)?.fila?.coordenadas ?? filaBase?.coordenadas ?? [],
          )}
          onGuardar={(resultado) => guardarEdicionManual(talleEnEdicion, resultado)}
          onCerrar={() => setTalleEnEdicion(null)}
        />
      )}
    </div>
  );
}
