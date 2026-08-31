import { useEffect, useMemo, useState } from 'react';
import { obtenerDetallePlanificacion, obtenerResumenPlanificacion } from '../../services/planificacionCompraService';
import { extraerMensajeError } from '../../utils/errores';
import type {
  ArticuloResumenResponse,
  PlanificacionCompraDetalleResponse,
  PlanificacionCompraResponse,
} from '../../types/planificacionCompra';

type Pestana = 'por-pedido' | 'resumen';

interface PlanificacionDetalleViewProps {
  planificacion: PlanificacionCompraResponse;
  onVolver: () => void;
}

function formatearPesos(valor: number): string {
  return valor.toLocaleString('es-AR', { style: 'currency', currency: 'ARS', maximumFractionDigits: 0 });
}

function formatearCantidad(cantidad: number, esKg: boolean): string {
  return `${cantidad.toLocaleString('es-AR', { maximumFractionDigits: 1 })} ${esKg ? 'kg' : 'u.'}`;
}

export default function PlanificacionDetalleView({ planificacion, onVolver }: PlanificacionDetalleViewProps) {
  const [pestana, setPestana] = useState<Pestana>('resumen');
  const [detalle, setDetalle] = useState<PlanificacionCompraDetalleResponse[]>([]);
  const [resumen, setResumen] = useState<ArticuloResumenResponse[]>([]);
  const [cargando, setCargando] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let cancelado = false;
    Promise.all([obtenerDetallePlanificacion(planificacion.id), obtenerResumenPlanificacion(planificacion.id)])
      .then(([detalleData, resumenData]) => {
        if (cancelado) return;
        setDetalle(detalleData);
        setResumen(resumenData);
      })
      .catch((err) => {
        if (!cancelado) setError(extraerMensajeError(err, 'No se pudo cargar la planificación.'));
      })
      .finally(() => {
        if (!cancelado) setCargando(false);
      });
    return () => {
      cancelado = true;
    };
  }, [planificacion.id]);

  const detallePorPedido = useMemo(() => {
    const grupos = new Map<number, { codigoInterno: string; nombreColegio: string; filas: PlanificacionCompraDetalleResponse[] }>();
    for (const fila of detalle) {
      const grupo = grupos.get(fila.idPedido) ?? { codigoInterno: fila.codigoInternoPedido, nombreColegio: fila.nombreColegio, filas: [] };
      grupo.filas.push(fila);
      grupos.set(fila.idPedido, grupo);
    }
    return Array.from(grupos.entries()).sort((a, b) => a[1].codigoInterno.localeCompare(b[1].codigoInterno));
  }, [detalle]);

  const estimadoTotalPlanificacion = useMemo(
    () => resumen.reduce((acc, item) => acc + (item.estimadoTotal ?? 0), 0),
    [resumen],
  );

  return (
    <div className="flex flex-col gap-5">
      <div className="flex items-center justify-between">
        <div>
          <h2 className="text-base font-bold text-wc-text">{planificacion.nombre}</h2>
          <p className="text-xs text-wc-text-muted">
            {planificacion.cantidadProductos} producto{planificacion.cantidadProductos === 1 ? '' : 's'} · creada por{' '}
            {planificacion.nombreCreadoPor}
          </p>
        </div>
        <button
          type="button"
          onClick={onVolver}
          className="rounded-md border border-wc-border bg-white px-4 py-2 text-sm font-semibold text-wc-text-muted transition hover:bg-wc-bg"
        >
          Volver al listado
        </button>
      </div>

      <div className="flex items-center gap-1 border-b border-wc-border">
        {(['resumen', 'por-pedido'] as Pestana[]).map((valor) => (
          <button
            key={valor}
            type="button"
            onClick={() => setPestana(valor)}
            className={`-mb-px rounded-t-lg border border-b-0 px-4 py-2 text-sm font-semibold transition ${
              pestana === valor ? 'border-wc-border bg-white text-wc-green' : 'border-transparent text-wc-text-muted hover:text-wc-text'
            }`}
          >
            {valor === 'resumen' ? 'Resumen unificado' : 'Por producto / pedido'}
          </button>
        ))}
      </div>

      {cargando && <p className="text-sm text-wc-text-muted">Cargando…</p>}
      {error && <p className="text-xs font-medium text-red-600">{error}</p>}

      {!cargando && pestana === 'resumen' && (
        <div className="flex flex-col gap-3">
          {resumen.length === 0 ? (
            <p className="text-sm text-wc-text-muted">Esta planificación no tiene artículos.</p>
          ) : (
            <>
              <div className="overflow-x-auto rounded-lg border border-wc-border bg-white">
                <table className="w-full text-left text-sm">
                  <thead>
                    <tr className="border-b border-wc-border text-xs text-wc-text-muted">
                      <th className="px-3 py-2 font-semibold">Tela</th>
                      <th className="px-3 py-2 font-semibold">Color</th>
                      <th className="px-3 py-2 font-semibold">Necesario</th>
                      <th className="px-3 py-2 font-semibold">Stock disponible</th>
                      <th className="px-3 py-2 font-semibold">A comprar</th>
                      <th className="px-3 py-2 font-semibold">Proveedor preferido</th>
                      <th className="px-3 py-2 text-right font-semibold">Estimado</th>
                    </tr>
                  </thead>
                  <tbody>
                    {resumen.map((item) => {
                      const esKg = item.unidadMedida === 'KG';
                      const cubiertoConStock = item.cantidadAComprar === 0 && item.stockDisponible > 0;
                      return (
                        <tr
                          key={`${item.idTipoTela}::${item.idPaletaColor}`}
                          className={`border-b border-wc-border last:border-0 ${cubiertoConStock ? 'bg-wc-green/5' : ''}`}
                        >
                          <td className="px-3 py-2 text-wc-text">{item.nombreTipoTela}</td>
                          <td className="px-3 py-2">
                            <div className="flex items-center gap-2">
                              <span className="h-3.5 w-3.5 shrink-0 rounded-full border border-wc-border" style={{ backgroundColor: item.hexColor }} />
                              <span className="text-wc-text">{item.nombreColor}</span>
                            </div>
                          </td>
                          <td className="px-3 py-2 text-wc-text">{formatearCantidad(item.cantidadNecesaria, esKg)}</td>
                          <td className="px-3 py-2 text-wc-text-muted">
                            {item.stockDisponible > 0 ? formatearCantidad(item.stockDisponible, esKg) : '—'}
                          </td>
                          <td className="px-3 py-2">
                            {cubiertoConStock ? (
                              <span className="inline-flex items-center gap-1 rounded-full bg-wc-green/10 px-2 py-0.5 text-xs font-semibold text-wc-green">
                                Cubierto con stock
                              </span>
                            ) : (
                              <span className={item.stockDisponible > 0 ? 'font-semibold text-wc-green' : 'text-wc-text'}>
                                {formatearCantidad(item.cantidadAComprar, esKg)}
                              </span>
                            )}
                          </td>
                          <td className="px-3 py-2 text-wc-text-muted">{item.nombreProveedorPreferido ?? 'Sin proveedor preferido'}</td>
                          <td className="px-3 py-2 text-right font-semibold text-wc-text">
                            {item.estimadoTotal != null ? formatearPesos(item.estimadoTotal) : '—'}
                          </td>
                        </tr>
                      );
                    })}
                  </tbody>
                </table>
              </div>
              <p className="self-end text-sm font-semibold text-wc-text">
                Estimado total: {formatearPesos(estimadoTotalPlanificacion)}
              </p>
            </>
          )}
        </div>
      )}

      {!cargando && pestana === 'por-pedido' && (
        <div className="flex flex-col gap-5">
          {detallePorPedido.length === 0 ? (
            <p className="text-sm text-wc-text-muted">Esta planificación no tiene artículos.</p>
          ) : (
            detallePorPedido.map(([idPedido, grupo]) => (
              <div key={idPedido} className="flex flex-col gap-2 rounded-lg border border-wc-border bg-white p-3">
                <p className="text-sm font-bold text-wc-text">
                  {grupo.codigoInterno} · {grupo.nombreColegio}
                </p>
                <div className="flex flex-col gap-1">
                  {grupo.filas.map((fila) => (
                    <div key={fila.id} className="flex items-center gap-2 text-xs text-wc-text-muted">
                      <span className="h-3 w-3 shrink-0 rounded-full border border-wc-border" style={{ backgroundColor: fila.hexColor }} />
                      <span className="flex-1">
                        {fila.tipoPrenda ?? 'Prenda'} · {fila.nombreTipoTela} · {fila.nombreColor}
                      </span>
                      <span className="font-semibold text-wc-text">{formatearCantidad(fila.cantidad, fila.unidadMedida === 'KG')}</span>
                    </div>
                  ))}
                </div>
              </div>
            ))
          )}
        </div>
      )}
    </div>
  );
}
