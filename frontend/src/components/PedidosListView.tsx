import { useEffect, useMemo, useState } from 'react';
import './PedidosListView.css';
import AppHeader from './AppHeader';
import DateRangePicker from './DateRangePicker';
import CambiarEstadoPopover from './CambiarEstadoPopover';
import { listarPedidos } from '../services/pedidoService';
import { BUCKET_POR_ESTADO, ESTADO_PEDIDO_LABELS } from '../types/pedido';
import type { BucketEstadoPedido, EstadoPedido, PedidoResponse } from '../types/pedido';

const TIPOS_BUZO_CAMPERA = ['Buzo', 'Campera'];

interface Filtros {
  buscar: string;
  desde: string;
  hasta: string;
  estado: EstadoPedido | '';
  anio: string;
}

const filtrosIniciales: Filtros = { buscar: '', desde: '', hasta: '', estado: '', anio: '' };

interface Contadores {
  total: number;
  pendiente: number;
  en_produccion: number;
  entregado: number;
}

function sumarUnidadesPorTipos(pedido: PedidoResponse, tipos: string[]): number {
  return pedido.productos
    .filter((p) => p.tipoPrenda && tipos.includes(p.tipoPrenda))
    .reduce((acc, p) => acc + p.cantidadTotal, 0);
}

function totalUnidades(pedido: PedidoResponse): number {
  return pedido.productos.reduce((acc, p) => acc + p.cantidadTotal, 0);
}

function precioUnitarioPromedio(pedido: PedidoResponse): number {
  const unidades = totalUnidades(pedido);
  return unidades > 0 ? pedido.precioTotal / unidades : 0;
}

function contarPorBucket(pedidos: PedidoResponse[], valor: (p: PedidoResponse) => number): Contadores {
  const acc: Record<BucketEstadoPedido, number> = { pendiente: 0, en_produccion: 0, entregado: 0 };
  for (const pedido of pedidos) {
    acc[BUCKET_POR_ESTADO[pedido.estadoActual]] += valor(pedido);
  }
  return { ...acc, total: acc.pendiente + acc.en_produccion + acc.entregado };
}

function formatearMoneda(valor: number): string {
  return valor.toLocaleString('es-AR', { minimumFractionDigits: 0, maximumFractionDigits: 0 });
}

function formatearFecha(iso: string): string {
  const [anio, mes, dia] = iso.split('-');
  return `${dia}/${mes}/${anio}`;
}

interface StatTileProps {
  label: string;
  value: number;
  variante: 'neutral' | 'pendiente' | 'produccion' | 'entregado';
}

function StatTile({ label, value, variante }: StatTileProps) {
  return (
    <div className={`stat-tile stat-tile--${variante}`}>
      <span className="stat-tile-label">{label}</span>
      <span className="stat-tile-valor">{value.toLocaleString('es-AR')}</span>
    </div>
  );
}

interface PedidosListViewProps {
  onNuevoPedido: () => void;
  mensajeExito?: string | null;
}

export default function PedidosListView({ onNuevoPedido, mensajeExito }: PedidosListViewProps) {
  const [pedidos, setPedidos] = useState<PedidoResponse[]>([]);
  const [estadoCarga, setEstadoCarga] = useState<'cargando' | 'listo' | 'error'>('cargando');
  const [filtros, setFiltros] = useState<Filtros>(filtrosIniciales);

  useEffect(() => {
    let cancelado = false;
    listarPedidos()
      .then((data) => {
        if (cancelado) return;
        setPedidos(data);
        setEstadoCarga('listo');
      })
      .catch(() => {
        if (!cancelado) setEstadoCarga('error');
      });
    return () => {
      cancelado = true;
    };
  }, []);

  function actualizarFiltro<K extends keyof Filtros>(campo: K, valor: Filtros[K]) {
    setFiltros((prev) => ({ ...prev, [campo]: valor }));
  }

  function manejarCambioEstado(pedidoActualizado: PedidoResponse) {
    setPedidos((prev) => prev.map((p) => (p.id === pedidoActualizado.id ? pedidoActualizado : p)));
  }

  const aniosDisponibles = useMemo(() => {
    const anios = new Set(pedidos.map((p) => p.fechaVenta.slice(0, 4)));
    return Array.from(anios).sort((a, b) => b.localeCompare(a));
  }, [pedidos]);

  const pedidosFiltrados = useMemo(() => {
    const buscar = filtros.buscar.trim().toLowerCase();
    return pedidos.filter((p) => {
      if (buscar) {
        const coincide =
          p.codigoInterno.toLowerCase().includes(buscar) ||
          p.nombreColegio.toLowerCase().includes(buscar) ||
          p.nombreVendedor.toLowerCase().includes(buscar);
        if (!coincide) return false;
      }
      if (filtros.desde && p.fechaVenta < filtros.desde) return false;
      if (filtros.hasta && p.fechaVenta > filtros.hasta) return false;
      if (filtros.estado && p.estadoActual !== filtros.estado) return false;
      if (filtros.anio && !p.fechaVenta.startsWith(filtros.anio)) return false;
      return true;
    });
  }, [pedidos, filtros]);

  const contadoresPedidos = useMemo(() => contarPorBucket(pedidosFiltrados, () => 1), [pedidosFiltrados]);
  const contadoresUnidades = useMemo(
    () => contarPorBucket(pedidosFiltrados, (p) => sumarUnidadesPorTipos(p, TIPOS_BUZO_CAMPERA)),
    [pedidosFiltrados],
  );

  return (
    <div className="pedidos-list-view">
      <AppHeader title="Pedidos" />

      {mensajeExito && (
        <div className="alerta alerta--exito" role="status">
          {mensajeExito}
        </div>
      )}

      <h2 className="pedidos-stat-titulo">Colegios</h2>
      <div className="pedidos-stats-row">
        <StatTile label="Total vendido" value={contadoresPedidos.total} variante="neutral" />
        <StatTile label="Pendientes" value={contadoresPedidos.pendiente} variante="pendiente" />
        <StatTile label="En producción" value={contadoresPedidos.en_produccion} variante="produccion" />
        <StatTile label="Entregados" value={contadoresPedidos.entregado} variante="entregado" />
      </div>

      <h2 className="pedidos-stat-titulo">Unidades (Buzos/Camperas)</h2>
      <div className="pedidos-stats-row">
        <StatTile label="Total vendido" value={contadoresUnidades.total} variante="neutral" />
        <StatTile label="Pendientes" value={contadoresUnidades.pendiente} variante="pendiente" />
        <StatTile label="En producción" value={contadoresUnidades.en_produccion} variante="produccion" />
        <StatTile label="Entregados" value={contadoresUnidades.entregado} variante="entregado" />
      </div>

      

      <section className="card pedidos-filtros">
          <input
            className="pedidos-filtro-buscar"
            value={filtros.buscar}
            onChange={(e) => actualizarFiltro('buscar', e.target.value)}
            placeholder="Buscar por ficha, colegio o vendedor…"
          />
          <DateRangePicker
            desde={filtros.desde}
            hasta={filtros.hasta}
            onChange={(desde, hasta) => setFiltros((prev) => ({ ...prev, desde, hasta }))}
          />
          <select
            value={filtros.estado}
            onChange={(e) => actualizarFiltro('estado', e.target.value as EstadoPedido | '')}
          >
            <option value="">Estado (todos)</option>
            {(Object.keys(ESTADO_PEDIDO_LABELS) as EstadoPedido[]).map((estado) => (
              <option key={estado} value={estado}>
                {ESTADO_PEDIDO_LABELS[estado]}
              </option>
            ))}
          </select>
          <select value={filtros.anio} onChange={(e) => actualizarFiltro('anio', e.target.value)}>
            <option value="">Año (todos)</option>
            {aniosDisponibles.map((anio) => (
              <option key={anio} value={anio}>
                {anio}
              </option>
            ))}
          </select>
          <button type="button" className="btn-guardar pedidos-btn-nuevo" onClick={onNuevoPedido}>
            + Nuevo Pedido
          </button>
        <div className="pedidos-tabla-wrapper">
          <table className="pedidos-tabla">
            <thead>
              <tr>
                <th>Nº Ficha</th>
                <th>Fecha venta</th>
                <th>Fecha est. entrega</th>
                <th>Colegio</th>
                <th>Localidad</th>
                <th>Vendedor</th>
                <th>Buzo/Camperas</th>
                <th>Remeras</th>
                <th>Chombas</th>
                <th>Precio Unitario</th>
                <th>Total</th>
                <th>%Pago</th>
                <th>Estado</th>
              </tr>
            </thead>
            <tbody>
              {estadoCarga === 'listo' &&
                pedidosFiltrados.map((pedido) => (
                  <tr key={pedido.id}>
                    <td>{pedido.codigoInterno}</td>
                    <td>{formatearFecha(pedido.fechaVenta)}</td>
                    <td>{formatearFecha(pedido.fechaEstimadaEntrega)}</td>
                    <td>{pedido.nombreColegio}</td>
                    <td>{pedido.localidadColegio ?? '—'}</td>
                    <td>{pedido.nombreVendedor}</td>
                    <td>{sumarUnidadesPorTipos(pedido, TIPOS_BUZO_CAMPERA)}</td>
                    <td>{sumarUnidadesPorTipos(pedido, ['Remera'])}</td>
                    <td>{sumarUnidadesPorTipos(pedido, ['Chomba'])}</td>
                    <td>${formatearMoneda(precioUnitarioPromedio(pedido))}</td>
                    <td>${formatearMoneda(pedido.precioTotal)}</td>
                    <td>{pedido.porcentajePagado.toFixed(0)}%</td>
                    <td>
                      <CambiarEstadoPopover pedido={pedido} onCambiado={manejarCambioEstado} />
                    </td>
                  </tr>
                ))}
            </tbody>
          </table>
          {estadoCarga === 'cargando' && <p className="pedidos-tabla-vacio">Cargando pedidos…</p>}
          {estadoCarga === 'listo' && pedidosFiltrados.length === 0 && (
            <p className="pedidos-tabla-vacio">No hay pedidos que coincidan con los filtros.</p>
          )}
          {estadoCarga === 'error' && (
            <p className="pedidos-tabla-vacio">No se pudo cargar el listado de pedidos.</p>
          )}
        </div>
      </section>
    </div>
  );
}
