import BarChartMensual from './BarChartMensual';
import DonutEstados from './DonutEstados';
import EstadoSeccion from './EstadoSeccion';
import KpiCard from './KpiCard';
import TarjetaGrafico from './TarjetaGrafico';
import { COLORES_ESTADO_PRODUCCION } from './coloresReportes';
import { formatoEntero, mesLargo } from './formato';
import { useReporte } from './useReporte';
import { obtenerReporteProduccion } from '../../services/reportesService';
import { ESTADO_PRODUCCION_LABELS } from '../../types/produccion';
import type { ReporteFiltros, ReporteProduccionResponse } from '../../types/reportes';

interface SeccionProduccionProps {
  /** null = todavía no hay un período completo elegido (rango a medida a medio cargar). */
  filtros: ReporteFiltros | null;
  /** OJAL solo existe para Chomba: se muestra con "Todas" o con Chomba filtrada, y se oculta
   *  con cualquier otro tipo de prenda (ahí siempre sería 0). */
  mostrarOjal: boolean;
}

const MENSAJE_ERROR = 'No se pudo cargar el reporte de producción.';

/**
 * Sección Producción de Reportes. Muestra siempre el agregado según el filtro global (todas las
 * prendas o una): a propósito no hay series por tipo de prenda acá.
 */
export default function SeccionProduccion({ filtros, mostrarOjal }: SeccionProduccionProps) {
  const { datos, cargando, error, reintentar } = useReporte<ReporteProduccionResponse>(
    obtenerReporteProduccion,
    filtros,
    MENSAJE_ERROR,
  );

  let contenido;
  if (!filtros) {
    contenido = <EstadoSeccion tipo="sin-filtros" mensaje="Elegí la fecha de inicio y de fin del rango para ver el reporte." />;
  } else if (error) {
    contenido = <EstadoSeccion tipo="error" mensaje={error} onReintentar={reintentar} />;
  } else if (!datos) {
    contenido = <EstadoSeccion tipo="cargando" mensaje="Cargando producción…" />;
  } else {
    const estados = datos.porEstado.filter((fila) => mostrarOjal || fila.estado !== 'OJAL');
    const sinPrendas = estados.every((fila) => fila.unidades === 0);
    const sinTerminadas = datos.terminadasPorMes.every((mes) => mes.unidades === 0);

    if (sinPrendas && sinTerminadas) {
      contenido = <EstadoSeccion tipo="vacio" mensaje="No hay prendas en producción para el tipo de prenda seleccionado." />;
    } else {
      // La torta es "qué hay en planta": ENTREGADO ya salió, así que queda solo como tarjeta.
      const enPlanta = estados.filter((fila) => fila.estado !== 'ENTREGADO');

      contenido = (
        <div className={`flex flex-col gap-4 transition-opacity ${cargando ? 'opacity-60' : ''}`} aria-busy={cargando}>
          <div className="grid grid-cols-1 gap-3 min-[30rem]:grid-cols-2 md:grid-cols-3 xl:grid-cols-5">
            {estados.map((fila) => (
              <KpiCard
                key={fila.estado}
                label={ESTADO_PRODUCCION_LABELS[fila.estado]}
                valor={formatoEntero(fila.unidades)}
                color={COLORES_ESTADO_PRODUCCION[fila.estado]}
                disposicion="en-linea"
                detalle={fila.estado === 'ENTREGADO' ? 'Ya salió de planta.' : fila.estado === 'TERMINADO' ? 'Lista, todavía en planta.' : undefined}
              />
            ))}
          </div>

          <div className="grid grid-cols-1 gap-3 xl:grid-cols-2">
            <TarjetaGrafico
              titulo="Unidades en planta por estado"
              descripcion="Situación actual (no depende del período). No incluye las entregadas."
              tabla={{
                columnas: ['Estado', 'Unidades'],
                filas: enPlanta.map((fila) => [ESTADO_PRODUCCION_LABELS[fila.estado], formatoEntero(fila.unidades)]),
              }}
            >
              <DonutEstados
                etiquetaTotal="en planta"
                nombreValor="Unidades"
                datos={enPlanta.map((fila) => ({
                  clave: fila.estado,
                  nombre: ESTADO_PRODUCCION_LABELS[fila.estado],
                  valor: fila.unidades,
                  color: COLORES_ESTADO_PRODUCCION[fila.estado],
                }))}
              />
            </TarjetaGrafico>

            <TarjetaGrafico
              titulo="Unidades terminadas por mes"
              descripcion="Por la fecha en que la prenda pasó a Terminado."
              tabla={{
                columnas: ['Mes', 'Unidades'],
                filas: datos.terminadasPorMes.map((mes) => [mesLargo(mes.mes), formatoEntero(mes.unidades)]),
              }}
            >
              <BarChartMensual
                nombreValor="Unidades terminadas"
                color={COLORES_ESTADO_PRODUCCION.TERMINADO}
                datos={datos.terminadasPorMes.map((mes) => ({ mes: mes.mes, valor: mes.unidades }))}
              />
            </TarjetaGrafico>
          </div>
        </div>
      );
    }
  }

  return (
    <section aria-label="Producción" className="flex flex-col gap-3">
      {contenido}
    </section>
  );
}
