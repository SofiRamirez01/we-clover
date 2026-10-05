import BarChartCategorias from './BarChartCategorias';
import BarChartMensual from './BarChartMensual';
import EstadoSeccion from './EstadoSeccion';
import KpiCard from './KpiCard';
import TarjetaGrafico from './TarjetaGrafico';
import { useReporte } from './useReporte';
import { formatoARS, formatoEntero, mesLargo } from './formato';
import { obtenerReporteVentas } from '../../services/reportesService';
import type { ReporteFiltros, ReporteVentasResponse } from '../../types/reportes';

interface SeccionVentasProps {
  /** null = todavía no hay un período completo elegido (rango a medida a medio cargar). */
  filtros: ReporteFiltros | null;
}

const MENSAJE_ERROR = 'No se pudo cargar el reporte de ventas.';

function pluralPedidos(cantidad: number): string {
  return cantidad === 1 ? '1 pedido' : `${formatoEntero(cantidad)} pedidos`;
}

/** Sección Ventas de Reportes: maneja su propia carga (y sus estados de carga/vacío/error). */
export default function SeccionVentas({ filtros }: SeccionVentasProps) {
  const { datos, cargando, error, reintentar } = useReporte<ReporteVentasResponse>(
    obtenerReporteVentas,
    filtros,
    MENSAJE_ERROR,
  );

  let contenido;
  if (!filtros) {
    contenido = <EstadoSeccion tipo="sin-filtros" mensaje="Elegí la fecha de inicio y de fin del rango para ver el reporte." />;
  } else if (error) {
    contenido = <EstadoSeccion tipo="error" mensaje={error} onReintentar={reintentar} />;
  } else if (!datos) {
    contenido = <EstadoSeccion tipo="cargando" mensaje="Cargando ventas…" />;
  } else if (datos.cantidadPedidos === 0 && datos.presupuestados.cantidadPedidos === 0) {
    contenido = <EstadoSeccion tipo="vacio" mensaje="No hay pedidos en el período y tipo de prenda seleccionados." />;
  } else {
    const hayFiltroTipoPrenda = datos.idTipoPrenda !== null;
    const presupuestados = datos.presupuestados;

    contenido = (
      <div className={`flex flex-col gap-4 transition-opacity ${cargando ? 'opacity-60' : ''}`} aria-busy={cargando}>
        <div className="grid grid-cols-1 gap-3 sm:grid-cols-2 xl:grid-cols-4">
          <KpiCard label="Unidades vendidas" valor={formatoEntero(datos.unidadesTotales)} />
          <KpiCard
            label="Monto total"
            valor={formatoARS(datos.montoTotal)}
            aviso={datos.montoIncompleto ? 'Monto incompleto' : undefined}
            detalle={
              datos.montoIncompleto
                ? `No incluye ${pluralPedidos(datos.pedidosSinDesglose)} sin precio por prenda.`
                : undefined
            }
          />
          <KpiCard
            label="Precio promedio por unidad"
            valor={formatoARS(datos.precioPromedioUnidad)}
            detalle={datos.montoIncompleto ? 'Solo sobre las unidades con precio.' : undefined}
          />
          <KpiCard label="Pedidos" valor={formatoEntero(datos.cantidadPedidos)} detalle="Desde señado en adelante." />
        </div>

        <div className={`grid grid-cols-1 gap-3 ${hayFiltroTipoPrenda ? '' : 'xl:grid-cols-2'}`}>
          <TarjetaGrafico
            titulo="Unidades vendidas por mes"
            descripcion="Por fecha de venta del pedido."
            tabla={{
              columnas: ['Mes', 'Unidades', 'Monto'],
              filas: datos.porMes.map((mes) => [mesLargo(mes.mes), formatoEntero(mes.unidades), formatoARS(mes.monto)]),
            }}
          >
            <BarChartMensual
              nombreValor="Unidades vendidas"
              datos={datos.porMes.map((mes) => ({
                mes: mes.mes,
                valor: mes.unidades,
                detalle: `Monto: ${formatoARS(mes.monto)}`,
              }))}
            />
          </TarjetaGrafico>

          {/* Con un tipo de prenda filtrado sería una sola barra: no aporta, se oculta. */}
          {!hayFiltroTipoPrenda && (
            <TarjetaGrafico
              titulo="Unidades por tipo de prenda"
              descripcion="El monto de cada tipo está en el detalle al pasar el cursor."
              tabla={{
                columnas: ['Tipo de prenda', 'Unidades', 'Monto'],
                filas: datos.porTipoPrenda.map((tipo) => [tipo.tipoPrenda, formatoEntero(tipo.unidades), formatoARS(tipo.monto)]),
              }}
              nota={
                datos.pedidosSinDesglose > 0
                  ? `⚠ Montos incompletos: ${pluralPedidos(datos.pedidosSinDesglose)} sin precio por prenda no se puede${datos.pedidosSinDesglose === 1 ? '' : 'n'} repartir entre tipos (las unidades sí están contadas).`
                  : undefined
              }
            >
              <BarChartCategorias
                nombreValor="Unidades vendidas"
                datos={datos.porTipoPrenda.map((tipo) => ({
                  nombre: tipo.tipoPrenda,
                  valor: tipo.unidades,
                  detalle: `Monto: ${formatoARS(tipo.monto)}`,
                }))}
              />
            </TarjetaGrafico>
          )}
        </div>

        <div className="flex flex-col gap-2">
          <h3 className="m-0 text-sm font-semibold text-wc-text">Presupuestados (todavía no vendidos)</h3>
          <div className="grid grid-cols-1 gap-3 lg:grid-cols-3">
            <KpiCard label="Pedidos presupuestados" valor={formatoEntero(presupuestados.cantidadPedidos)} />
            <KpiCard label="Unidades presupuestadas" valor={formatoEntero(presupuestados.unidades)} />
            <KpiCard
              label="Monto presupuestado"
              valor={formatoARS(presupuestados.monto)}
              aviso={presupuestados.montoIncompleto ? 'Monto incompleto' : undefined}
              detalle={presupuestados.montoIncompleto ? 'No incluye pedidos sin precio por prenda.' : undefined}
            />
          </div>
        </div>
      </div>
    );
  }

  return (
    <section className="flex flex-col gap-3">
      <h2 className="m-0 text-lg font-semibold text-wc-text">Ventas</h2>
      {contenido}
    </section>
  );
}
