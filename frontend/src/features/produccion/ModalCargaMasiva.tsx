import { useMemo, useState } from 'react';
import { marcarEtapasBulk } from '../../services/productoService';
import { listarProduccionPedidos } from '../../services/produccionService';
import { extraerMensajeError } from '../../utils/errores';
import { ETAPAS_PRODUCCION, ETAPA_PRODUCCION_LABELS } from '../../types/produccion';
import type { EtapaProduccion, ProduccionPedidoResponse, UsuarioResumen } from '../../types/produccion';

interface FilaCarga {
  idPedido: number;
  codigoInterno: string;
  colegio: string;
  idProducto: number;
  tipoPrenda: string | null;
  cantidadTotal: number;
  completado: boolean;
}

interface ModalCargaMasivaProps {
  empleados: UsuarioResumen[];
  onCerrar: () => void;
  onConfirmado: () => void;
}

/**
 * Carga semanal masiva de una sola etapa contra un lote de productos, con un solo empleado
 * para todo el lote (ver la consigna de la pantalla: "un bordador carga su lote de la semana
 * con un solo empleado seleccionado"). Reusa GET /produccion/pedidos (sin filtrar por
 * etapaPendiente a propósito: filtrar en el backend excluiría de plano los pedidos ya
 * completos, y el checkbox "mostrar también los ya cargados" necesita esa data disponible del
 * lado del cliente para poder revelarla) y PUT /productos/etapas/bulk — no hace falta ningún
 * endpoint nuevo para esto.
 */
export default function ModalCargaMasiva({ empleados, onCerrar, onConfirmado }: ModalCargaMasivaProps) {
  const [etapa, setEtapa] = useState<EtapaProduccion | ''>('');
  const [idEmpleado, setIdEmpleado] = useState<number | ''>('');
  const [pedidos, setPedidos] = useState<ProduccionPedidoResponse[] | null>(null);
  const [cargando, setCargando] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [mostrarYaCargados, setMostrarYaCargados] = useState(false);
  const [busqueda, setBusqueda] = useState('');
  const [seleccionados, setSeleccionados] = useState<Set<number>>(new Set());
  const [confirmando, setConfirmando] = useState(false);

  async function elegirEtapa(nuevaEtapa: EtapaProduccion) {
    setEtapa(nuevaEtapa);
    setSeleccionados(new Set());
    setError(null);
    if (pedidos != null) return; // ya se trajo la lista completa una vez, no hace falta pedirla de nuevo
    setCargando(true);
    try {
      const data = await listarProduccionPedidos({});
      setPedidos(data);
    } catch (err) {
      setError(extraerMensajeError(err, 'No se pudo traer el listado de pedidos.'));
    } finally {
      setCargando(false);
    }
  }

  const filas: FilaCarga[] = useMemo(() => {
    if (!pedidos || !etapa) return [];
    const resultado: FilaCarga[] = [];
    for (const pedido of pedidos) {
      for (const producto of pedido.productos) {
        if (producto.esBandera) continue;
        const etapaProducto = producto.etapas.find((e) => e.etapa === etapa);
        if (!etapaProducto || !etapaProducto.aplica) continue;
        resultado.push({
          idPedido: pedido.id,
          codigoInterno: pedido.codigoInterno,
          colegio: pedido.colegio,
          idProducto: producto.id,
          tipoPrenda: producto.tipoPrenda,
          cantidadTotal: producto.cantidadTotal,
          completado: etapaProducto.completado,
        });
      }
    }
    return resultado;
  }, [pedidos, etapa]);

  const filasVisibles = useMemo(() => {
    const texto = busqueda.trim().toLowerCase();
    return filas.filter((fila) => {
      if (!mostrarYaCargados && fila.completado) return false;
      if (!texto) return true;
      return fila.colegio.toLowerCase().includes(texto) || fila.codigoInterno.toLowerCase().includes(texto);
    });
  }, [filas, mostrarYaCargados, busqueda]);

  const gruposPorPedido = useMemo(() => {
    const mapa = new Map<number, { codigoInterno: string; colegio: string; filas: FilaCarga[] }>();
    for (const fila of filasVisibles) {
      const grupo = mapa.get(fila.idPedido);
      if (grupo) grupo.filas.push(fila);
      else mapa.set(fila.idPedido, { codigoInterno: fila.codigoInterno, colegio: fila.colegio, filas: [fila] });
    }
    return Array.from(mapa.values());
  }, [filasVisibles]);

  function toggleProducto(idProducto: number) {
    setSeleccionados((prev) => {
      const copia = new Set(prev);
      if (copia.has(idProducto)) copia.delete(idProducto);
      else copia.add(idProducto);
      return copia;
    });
  }

  function seleccionarTodosLosVisibles() {
    setSeleccionados(new Set(filasVisibles.filter((f) => !f.completado).map((f) => f.idProducto)));
  }

  async function confirmar() {
    if (!etapa || seleccionados.size === 0) return;
    setConfirmando(true);
    setError(null);
    try {
      await marcarEtapasBulk(
        Array.from(seleccionados).map((idProducto) => ({
          idProducto,
          etapa,
          completado: true,
          idEmpleado: idEmpleado === '' ? null : idEmpleado,
        })),
      );
      onConfirmado();
    } catch (err) {
      setError(extraerMensajeError(err, 'No se pudo cargar el lote.'));
      setConfirmando(false);
    }
  }

  return (
    <div className="tw-scope fixed inset-0 z-50 flex items-center justify-center bg-black/70 p-6" onClick={onCerrar}>
      <div
        className="flex max-h-[85vh] w-full max-w-2xl flex-col gap-4 rounded-xl bg-white p-6 shadow-2xl"
        onClick={(e) => e.stopPropagation()}
      >
        <div className="flex items-center justify-between">
          <h2 className="text-base font-bold text-wc-text">Carga masiva de etapa</h2>
          <button type="button" onClick={onCerrar} className="text-sm font-semibold text-wc-text-muted hover:text-wc-text">
            Cerrar
          </button>
        </div>

        <div className="flex flex-wrap items-end gap-3">
          <div className="flex flex-col gap-1">
            <label className="text-xs font-semibold text-wc-text">Etapa</label>
            <select
              value={etapa}
              onChange={(e) => elegirEtapa(e.target.value as EtapaProduccion)}
              className="rounded-lg border border-wc-border bg-white px-2 py-1.5 text-sm text-wc-text"
            >
              <option value="">Elegir etapa…</option>
              {ETAPAS_PRODUCCION.map((op) => (
                <option key={op} value={op}>
                  {ETAPA_PRODUCCION_LABELS[op]}
                </option>
              ))}
            </select>
          </div>

          <div className="flex flex-col gap-1">
            <label className="text-xs font-semibold text-wc-text">Empleado (todo el lote)</label>
            <select
              value={idEmpleado}
              disabled={!etapa}
              onChange={(e) => setIdEmpleado(e.target.value ? Number(e.target.value) : '')}
              className="rounded-lg border border-wc-border bg-white px-2 py-1.5 text-sm text-wc-text disabled:opacity-50"
            >
              <option value="">Sin asignar</option>
              {empleados.map((empleado) => (
                <option key={empleado.id} value={empleado.id}>
                  {empleado.nombre}
                </option>
              ))}
            </select>
          </div>
        </div>

        {etapa && (
          <>
            <div className="flex flex-wrap items-center gap-3">
              <input
                type="text"
                value={busqueda}
                onChange={(e) => setBusqueda(e.target.value)}
                placeholder="Buscar por colegio o código de pedido…"
                className="flex-1 min-w-[12rem] rounded-lg border border-wc-border bg-white px-2 py-1.5 text-sm text-wc-text"
              />
              <div className="flex items-center gap-1.5 text-sm text-wc-text">
                <input
                  id="mostrarYaCargados"
                  type="checkbox"
                  checked={mostrarYaCargados}
                  onChange={(e) => setMostrarYaCargados(e.target.checked)}
                  className="h-4 w-4 accent-wc-green"
                />
                <label htmlFor="mostrarYaCargados" className="cursor-pointer whitespace-nowrap">
                  Mostrar también los ya cargados
                </label>
              </div>
              <button type="button" onClick={seleccionarTodosLosVisibles} className="text-xs font-semibold text-wc-green underline">
                Seleccionar todos los visibles
              </button>
            </div>

            {cargando ? (
              <p className="text-sm text-wc-text-muted">Cargando…</p>
            ) : gruposPorPedido.length === 0 ? (
              <p className="text-sm text-wc-text-muted">No hay productos que coincidan.</p>
            ) : (
              <div className="flex flex-col gap-3 overflow-y-auto" style={{ maxHeight: '45vh' }}>
                {gruposPorPedido.map((grupo) => (
                  <div key={grupo.codigoInterno} className="rounded-lg border border-wc-border p-2.5">
                    <p className="mb-1.5 text-xs font-bold text-wc-text">
                      #{grupo.codigoInterno} · {grupo.colegio}
                    </p>
                    <div className="flex flex-col gap-1">
                      {grupo.filas.map((fila) => (
                        <label
                          key={fila.idProducto}
                          className={`flex items-center gap-2 rounded px-1.5 py-1 text-sm ${
                            fila.completado ? 'text-wc-text-muted' : 'text-wc-text hover:bg-wc-bg cursor-pointer'
                          }`}
                        >
                          <input
                            type="checkbox"
                            checked={fila.completado || seleccionados.has(fila.idProducto)}
                            disabled={fila.completado}
                            onChange={() => toggleProducto(fila.idProducto)}
                            className="h-4 w-4 accent-wc-green disabled:opacity-50"
                          />
                          {fila.tipoPrenda ?? 'Sin tipo'} ({fila.cantidadTotal})
                          {fila.completado && <span className="text-[11px]">ya cargado</span>}
                        </label>
                      ))}
                    </div>
                  </div>
                ))}
              </div>
            )}
          </>
        )}

        {error && <p className="text-xs font-medium text-red-600">{error}</p>}

        <div className="flex items-center justify-between">
          <p className="text-xs text-wc-text-muted">{seleccionados.size} producto{seleccionados.size === 1 ? '' : 's'} seleccionado{seleccionados.size === 1 ? '' : 's'}</p>
          <div className="flex items-center gap-2">
            <button
              type="button"
              onClick={onCerrar}
              disabled={confirmando}
              className="rounded-md border border-wc-border bg-white px-4 py-2 text-sm font-semibold text-wc-text-muted transition hover:bg-wc-bg disabled:cursor-not-allowed disabled:opacity-60"
            >
              Cancelar
            </button>
            <button
              type="button"
              onClick={confirmar}
              disabled={confirmando || seleccionados.size === 0}
              className="rounded-md bg-wc-green px-5 py-2 text-sm font-semibold text-white transition hover:bg-wc-green-dark disabled:cursor-not-allowed disabled:opacity-60"
            >
              {confirmando ? 'Confirmando…' : 'Confirmar carga'}
            </button>
          </div>
        </div>
      </div>
    </div>
  );
}
