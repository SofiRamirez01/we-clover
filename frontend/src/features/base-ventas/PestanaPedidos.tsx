import { useCallback, useEffect, useMemo, useState } from 'react';
import DateRangePicker from '../../components/DateRangePicker';
import CambiarEstadoPopover from '../../components/CambiarEstadoPopover';
import AccionesPedidoMenu from '../../components/AccionesPedidoMenu';
import HistorialPedidoModal from '../../components/HistorialPedidoModal';
import ImportarExcelModal from '../../components/ImportarExcelModal';
import SincronizarKommoModal from '../../components/SincronizarKommoModal';
import { obtenerUltimaSincronizacionKommo } from '../../services/kommoService';
import { leerUsuarioGuardado } from '../../utils/authStorage';
import { ESTADO_PEDIDO_LABELS, ESTADOS_PEDIDO } from '../../types/pedido';
import type { CampoOrdenPedidos, EstadoPedido, FiltrosListadoPedidos, OrigenPedido, PedidoResponse } from '../../types/pedido';
import type { SincronizacionKommo } from '../../types/kommo';
import EncabezadoOrdenable from './EncabezadoOrdenable';
import {
  TIPOS_BUZO_CAMPERA,
  contarPorBucket,
  formatearFecha,
  formatearMoneda,
  siguienteOrden,
  sumarUnidadesPorTipos,
  unidadesDeTipos,
} from './formato';
import type { OrdenListado } from './formato';
import PieListado from './PieListado';
import { useListadoPedidos, useValorDiferido } from './useListadoPedidos';

/** 'ACTIVOS' = sin entregados ni cancelados (por defecto); '' = todos los estados. */
type FiltroEstado = EstadoPedido | 'ACTIVOS' | '';

interface Filtros {
  buscar: string;
  desde: string;
  hasta: string;
  estado: FiltroEstado;
  promocion: string;
  /** '' = todos los orígenes. */
  origen: OrigenPedido | '';
}

const filtrosIniciales: Filtros = { buscar: '', desde: '', hasta: '', estado: 'ACTIVOS', promocion: '', origen: '' };

const ROL_ADMINISTRATIVO = 'ROLE_ADMINISTRATIVO';

const ORIGEN_LABELS: Record<OrigenPedido, string> = {
  MANUAL: 'Carga manual',
  EXCEL: 'Excel',
  KOMMO: 'Kommo',
};

/** "21/10/2026 10:30" a partir de la fecha y hora local del servidor (sin zona). */
function formatearFechaHora(fechaHora: string): string {
  return new Date(fechaHora).toLocaleString('es-AR', { dateStyle: 'short', timeStyle: 'short' });
}

/** Última sincronización con Kommo y botón para sincronizar: solo para el rol administrativo. */
function BarraKommo({
  ultima,
  estado,
  onSincronizar,
}: {
  ultima: SincronizacionKommo | null;
  estado: 'cargando' | 'listo' | 'error';
  onSincronizar: () => void;
}) {
  let texto: string;
  if (estado === 'cargando') texto = 'Consultando la última sincronización con Kommo…';
  else if (estado === 'error') texto = 'No se pudo consultar la última sincronización con Kommo.';
  else if (!ultima) texto = 'Nunca se sincronizó con Kommo.';
  else
    texto =
      `Última sincronización con Kommo: ${formatearFechaHora(ultima.fechaHora)} · ` +
      `${ultima.importados} ${ultima.importados === 1 ? 'importado' : 'importados'}`;
  return (
    <section className="card pedidos-kommo">
      <span className="pedidos-kommo-texto">{texto}</span>
      <button type="button" className="btn-secondary" onClick={onSincronizar} disabled={estado === 'cargando'}>
        Sincronizar con Kommo
      </button>
    </section>
  );
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

interface PestanaPedidosProps {
  promociones: number[];
  onEditarPedido: (pedido: PedidoResponse) => void;
  onNuevoPedido: () => void;
  /** Después de importar pedidos pueden aparecer promociones o molderías nuevas. */
  onPedidosImportados: () => void;
}

/** Pestaña "Pedidos" de Base Ventas: lo que está en curso. Por defecto oculta los entregados y
 *  cancelados (se puede quitar con el filtro de estado). */
export default function PestanaPedidos({ promociones, onEditarPedido, onNuevoPedido, onPedidosImportados }: PestanaPedidosProps) {
  const [filtros, setFiltros] = useState<Filtros>(filtrosIniciales);
  const [pedidoHistorial, setPedidoHistorial] = useState<PedidoResponse | null>(null);
  const [mostrarImportar, setMostrarImportar] = useState(false);
  const [mostrarKommo, setMostrarKommo] = useState(false);
  const [orden, setOrden] = useState<OrdenListado | null>(null);
  const esAdministrativo = leerUsuarioGuardado()?.rol === ROL_ADMINISTRATIVO;
  const [ultimaSync, setUltimaSync] = useState<SincronizacionKommo | null>(null);
  const [estadoUltimaSync, setEstadoUltimaSync] = useState<'cargando' | 'listo' | 'error'>('cargando');

  const cargarUltimaSync = useCallback(() => {
    obtenerUltimaSincronizacionKommo()
      .then((data) => {
        setUltimaSync(data);
        setEstadoUltimaSync('listo');
      })
      .catch(() => setEstadoUltimaSync('error'));
  }, []);

  useEffect(() => {
    if (esAdministrativo) cargarUltimaSync();
  }, [esAdministrativo, cargarUltimaSync]);
  const buscar = useValorDiferido(filtros.buscar.trim());

  const filtrosListado = useMemo<FiltrosListadoPedidos>(
    () => ({
      buscar: buscar || undefined,
      desde: filtros.desde || undefined,
      hasta: filtros.hasta || undefined,
      soloActivos: filtros.estado === 'ACTIVOS' || undefined,
      estado: filtros.estado && filtros.estado !== 'ACTIVOS' ? filtros.estado : undefined,
      promocion: filtros.promocion ? Number(filtros.promocion) : undefined,
      origen: filtros.origen || undefined,
      orden: orden?.campo,
      direccion: orden?.direccion,
    }),
    [buscar, filtros.desde, filtros.hasta, filtros.estado, filtros.promocion, filtros.origen, orden],
  );
  const listado = useListadoPedidos(filtrosListado);

  const contadoresPedidos = useMemo(() => contarPorBucket(listado.contadores, (c) => c.pedidos), [listado.contadores]);
  const contadoresUnidades = useMemo(
    () => contarPorBucket(listado.contadores, (c) => unidadesDeTipos(c, TIPOS_BUZO_CAMPERA)),
    [listado.contadores],
  );
  const alcanceContadores = filtros.promocion ? `Promoción ${filtros.promocion}` : 'Todas las promociones';

  function actualizarFiltro<K extends keyof Filtros>(campo: K, valor: Filtros[K]) {
    setFiltros((prev) => ({ ...prev, [campo]: valor }));
  }

  function columna(etiqueta: string, campo: CampoOrdenPedidos) {
    return (
      <EncabezadoOrdenable
        etiqueta={etiqueta}
        campo={campo}
        orden={orden}
        onOrdenar={(elegido) => setOrden((actual) => siguienteOrden(actual, elegido))}
      />
    );
  }

  return (
    <>
      <h2 className="pedidos-stat-titulo">
        Colegios <span className="pedidos-stat-alcance">· {alcanceContadores}</span>
      </h2>
      <div className="pedidos-stats-row">
        <StatTile label="Total vendido" value={contadoresPedidos.total} variante="neutral" />
        <StatTile label="Pendientes" value={contadoresPedidos.pendiente} variante="pendiente" />
        <StatTile label="En producción" value={contadoresPedidos.en_produccion} variante="produccion" />
        <StatTile label="Entregados" value={contadoresPedidos.entregado} variante="entregado" />
      </div>

      <h2 className="pedidos-stat-titulo">
        Unidades (Buzos/Camperas) <span className="pedidos-stat-alcance">· {alcanceContadores}</span>
      </h2>
      <div className="pedidos-stats-row">
        <StatTile label="Total vendido" value={contadoresUnidades.total} variante="neutral" />
        <StatTile label="Pendientes" value={contadoresUnidades.pendiente} variante="pendiente" />
        <StatTile label="En producción" value={contadoresUnidades.en_produccion} variante="produccion" />
        <StatTile label="Entregados" value={contadoresUnidades.entregado} variante="entregado" />
      </div>

      {esAdministrativo && (
        <BarraKommo ultima={ultimaSync} estado={estadoUltimaSync} onSincronizar={() => setMostrarKommo(true)} />
      )}

      <section className="card pedidos-filtros">
        <input
          className="pedidos-filtro-buscar"
          value={filtros.buscar}
          onChange={(e) => actualizarFiltro('buscar', e.target.value)}
          placeholder="Ficha, colegio, localidad o vendedor…"
          aria-label="Buscar por ficha, colegio, localidad o vendedor"
        />
        <DateRangePicker
          desde={filtros.desde}
          hasta={filtros.hasta}
          onChange={(desde, hasta) => setFiltros((prev) => ({ ...prev, desde, hasta }))}
        />
        <select
          value={filtros.estado}
          onChange={(e) => actualizarFiltro('estado', e.target.value as FiltroEstado)}
          aria-label="Estado"
        >
          <option value="ACTIVOS">Activos (sin entregados ni cancelados)</option>
          <option value="">Estado (todos)</option>
          {ESTADOS_PEDIDO.map((estado) => (
            <option key={estado} value={estado}>
              {ESTADO_PEDIDO_LABELS[estado]}
            </option>
          ))}
        </select>
        <select value={filtros.promocion} onChange={(e) => actualizarFiltro('promocion', e.target.value)} aria-label="Promoción">
          <option value="">Promoción (todas)</option>
          {promociones.map((promocion) => (
            <option key={promocion} value={promocion}>
              Promoción {promocion}
            </option>
          ))}
        </select>
        <select
          value={filtros.origen}
          onChange={(e) => actualizarFiltro('origen', e.target.value as OrigenPedido | '')}
          aria-label="Origen"
        >
          <option value="">Origen (todos)</option>
          {(Object.keys(ORIGEN_LABELS) as OrigenPedido[]).map((origen) => (
            <option key={origen} value={origen}>
              {ORIGEN_LABELS[origen]}
            </option>
          ))}
        </select>
        <button type="button" className="btn-secondary pedidos-btn-nuevo" onClick={() => setMostrarImportar(true)}>
          Importar Excel
        </button>
        <button type="button" className="btn-guardar pedidos-btn-nuevo" onClick={onNuevoPedido}>
          + Nuevo Pedido
        </button>
      </section>

      <div className="pedidos-tabla-wrapper">
        <table className="pedidos-tabla">
          <thead>
            <tr>
              {columna('Nº Ficha', 'FICHA')}
              {columna('Fecha venta', 'FECHA_VENTA')}
              {columna('Fecha est. entrega', 'FECHA_ENTREGA')}
              {columna('Colegio', 'COLEGIO')}
              {columna('Localidad', 'LOCALIDAD')}
              {columna('Vendedor', 'VENDEDOR')}
              {columna('Buzo/Camp.', 'UNIDADES_BUZO_CAMPERA')}
              {columna('Remeras', 'UNIDADES_REMERA')}
              {columna('Chombas', 'UNIDADES_CHOMBA')}
              {columna('Precio Unitario', 'PRECIO_UNITARIO')}
              {columna('Total', 'TOTAL')}
              {columna('%Pago', 'PORCENTAJE_PAGO')}
              {columna('Estado', 'ESTADO')}
              <th aria-label="Acciones" />
            </tr>
          </thead>
          <tbody>
            {listado.estadoCarga === 'listo' &&
              listado.pedidos.map((pedido) => (
                <tr key={pedido.id}>
                  <td>
                    <span className="pedidos-ficha">
                      {pedido.codigoInterno}
                      {pedido.origen === 'KOMMO' && <span className="origen-badge origen-badge--kommo">Kommo</span>}
                      {!pedido.revisadoImportacion && (
                        <span
                          className="origen-badge origen-badge--nuevo"
                          title="Importado y sin revisar: abrí Editar, revisá los datos y guardá."
                        >
                          Nuevo
                        </span>
                      )}
                    </span>
                  </td>
                  <td>{formatearFecha(pedido.fechaVenta)}</td>
                  <td>{formatearFecha(pedido.fechaEstimadaEntrega)}</td>
                  <td className="pedidos-celda-texto">{pedido.nombreColegio}</td>
                  <td className="pedidos-celda-texto">{pedido.localidadColegio ?? '—'}</td>
                  <td className="pedidos-celda-texto">{pedido.nombreVendedor}</td>
                  <td>{sumarUnidadesPorTipos(pedido, TIPOS_BUZO_CAMPERA)}</td>
                  <td>{sumarUnidadesPorTipos(pedido, ['Remera'])}</td>
                  <td>{sumarUnidadesPorTipos(pedido, ['Chomba'])}</td>
                  <td>${formatearMoneda(pedido.precioUnitario)}</td>
                  <td>${formatearMoneda(pedido.precioTotal)}</td>
                  <td>{pedido.porcentajePagado.toFixed(0)}%</td>
                  <td>
                    <CambiarEstadoPopover pedido={pedido} onCambiado={listado.reemplazarPedido} />
                  </td>
                  <td>
                    <AccionesPedidoMenu pedido={pedido} onEditar={onEditarPedido} onVerHistorial={setPedidoHistorial} />
                  </td>
                </tr>
              ))}
          </tbody>
        </table>
        <PieListado listado={listado} textoVacio="No hay pedidos que coincidan con los filtros." />
      </div>

      {pedidoHistorial && <HistorialPedidoModal pedido={pedidoHistorial} onClose={() => setPedidoHistorial(null)} />}

      {mostrarKommo && (
        <SincronizarKommoModal
          ultimaSincronizacion={ultimaSync}
          onClose={() => setMostrarKommo(false)}
          onImportado={() => {
            listado.recargar();
            cargarUltimaSync();
            onPedidosImportados();
          }}
        />
      )}

      {mostrarImportar && (
        <ImportarExcelModal
          onClose={() => setMostrarImportar(false)}
          onImportado={() => {
            listado.recargar();
            onPedidosImportados();
          }}
        />
      )}
    </>
  );
}
