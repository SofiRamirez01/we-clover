import { ESTADOS_BANDERA, ESTADO_BANDERA_LABELS, ETAPAS_PRODUCCION } from '../../types/produccion';
import type { ProduccionProductoResponse, UsuarioResumen } from '../../types/produccion';
import type { EstadoBandera } from '../../types/pedido';
import EtapaCelda from './EtapaCelda';

interface FilaProductoProduccionProps {
  producto: ProduccionProductoResponse;
  empleados: UsuarioResumen[];
  guardandoClave: string | null;
  onMarcarEtapa: (idProducto: number, etapa: (typeof ETAPAS_PRODUCCION)[number], completado: boolean, idEmpleado: number | null) => void;
  onMarcarBandera: (idProducto: number, estadoBandera: EstadoBandera) => void;
}

function formatearFecha(iso: string | null): string {
  if (!iso) return '—';
  const [anio, mes, dia] = iso.split('-');
  return `${dia}/${mes}/${anio}`;
}

export default function FilaProductoProduccion({
  producto,
  empleados,
  guardandoClave,
  onMarcarEtapa,
  onMarcarBandera,
}: FilaProductoProduccionProps) {
  return (
    <tr className="border-t border-wc-border">
      <td className="whitespace-nowrap px-2 py-2 text-sm font-medium text-wc-text">{producto.tipoPrenda ?? 'Sin tipo'}</td>
      <td className="px-2 py-2 text-center text-sm text-wc-text">{producto.cantidadTotal}</td>

      {producto.esBandera ? (
        <td colSpan={ETAPAS_PRODUCCION.length} className="px-2 py-2">
          <div className="flex flex-wrap items-center gap-3">
            <select
              value={producto.estadoBandera ?? 'PENDIENTE'}
              disabled={guardandoClave === `bandera-${producto.id}`}
              onChange={(e) => onMarcarBandera(producto.id, e.target.value as EstadoBandera)}
              className="rounded border border-wc-border bg-white px-2 py-1 text-xs text-wc-text disabled:opacity-50"
            >
              {ESTADOS_BANDERA.map((estado) => (
                <option key={estado} value={estado}>
                  {ESTADO_BANDERA_LABELS[estado]}
                </option>
              ))}
            </select>
            <span className="text-[11px] text-wc-text-muted">Pedido al proveedor: {formatearFecha(producto.fechaPedidoProveedor)}</span>
            <span className="text-[11px] text-wc-text-muted">Recibido: {formatearFecha(producto.fechaRecibido)}</span>
          </div>
        </td>
      ) : (
        ETAPAS_PRODUCCION.map((etapaNombre) => {
          const etapa = producto.etapas.find((e) => e.etapa === etapaNombre);
          if (!etapa) return <td key={etapaNombre} className="px-2 py-2 text-center text-wc-text-muted">—</td>;
          return (
            <td key={etapaNombre} className="px-2 py-2 text-center">
              <EtapaCelda
                etapa={etapa}
                empleados={empleados}
                guardando={guardandoClave === `etapa-${producto.id}-${etapaNombre}`}
                onCambiar={(completado, idEmpleado) => onMarcarEtapa(producto.id, etapaNombre, completado, idEmpleado)}
              />
            </td>
          );
        })
      )}
    </tr>
  );
}
