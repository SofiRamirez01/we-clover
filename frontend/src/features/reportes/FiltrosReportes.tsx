import DateRangePicker from '../../components/DateRangePicker';
import type { TipoPrendaOption } from '../../types/pedido';

/** Cantidad de meses hacia atrás, o un rango de fechas a medida. */
export type PeriodoReporte = '3' | '6' | '12' | 'custom';

const OPCIONES_PERIODO: { valor: PeriodoReporte; etiqueta: string }[] = [
  { valor: '3', etiqueta: 'Últimos 3 meses' },
  { valor: '6', etiqueta: 'Últimos 6 meses' },
  { valor: '12', etiqueta: 'Últimos 12 meses' },
  { valor: 'custom', etiqueta: 'Rango a medida' },
];

interface FiltrosReportesProps {
  periodo: PeriodoReporte;
  onPeriodoChange: (periodo: PeriodoReporte) => void;
  rangoDesde: string;
  rangoHasta: string;
  onRangoChange: (desde: string, hasta: string) => void;
  /** Tipos de prenda elegibles (sin Bandera, que no participa de los reportes). */
  tiposPrenda: TipoPrendaOption[];
  idTipoPrenda: number | null;
  onTipoPrendaChange: (idTipoPrenda: number | null) => void;
}

const CLASE_SELECT = 'rounded-lg border border-wc-border bg-white px-2 py-1.5 text-sm text-wc-text';
const CLASE_LABEL = 'text-xs font-semibold text-wc-text';

/**
 * Barra de filtros global de Reportes: una sola fila arriba de todo, aplica a todas las
 * secciones de la página.
 */
export default function FiltrosReportes({
  periodo,
  onPeriodoChange,
  rangoDesde,
  rangoHasta,
  onRangoChange,
  tiposPrenda,
  idTipoPrenda,
  onTipoPrendaChange,
}: FiltrosReportesProps) {
  return (
    <div className="flex flex-wrap items-end gap-3 rounded-lg border border-wc-border bg-white p-4">
      <div className="flex flex-col gap-1">
        <label htmlFor="reportes-periodo" className={CLASE_LABEL}>
          Período
        </label>
        <select
          id="reportes-periodo"
          value={periodo}
          onChange={(e) => onPeriodoChange(e.target.value as PeriodoReporte)}
          className={CLASE_SELECT}
        >
          {OPCIONES_PERIODO.map((opcion) => (
            <option key={opcion.valor} value={opcion.valor}>
              {opcion.etiqueta}
            </option>
          ))}
        </select>
      </div>

      {periodo === 'custom' && <DateRangePicker desde={rangoDesde} hasta={rangoHasta} onChange={onRangoChange} />}

      <div className="flex flex-col gap-1">
        <label htmlFor="reportes-tipo-prenda" className={CLASE_LABEL}>
          Tipo de prenda
        </label>
        <select
          id="reportes-tipo-prenda"
          value={idTipoPrenda ?? ''}
          onChange={(e) => onTipoPrendaChange(e.target.value === '' ? null : Number(e.target.value))}
          className={CLASE_SELECT}
        >
          <option value="">Todas</option>
          {tiposPrenda.map((tipo) => (
            <option key={tipo.id} value={tipo.id}>
              {tipo.nombre}
            </option>
          ))}
        </select>
      </div>

      {/* Lugar reservado para el filtro de empleado (reporte de productividad, todavía no
          implementado): cuando exista, va acá y se suma `idEmpleado` a ReporteFiltros. */}
      <div className="flex flex-col gap-1">
        <label htmlFor="reportes-empleado" className={`${CLASE_LABEL} opacity-60`}>
          Empleado
        </label>
        <select id="reportes-empleado" disabled className={`${CLASE_SELECT} cursor-not-allowed opacity-60`}>
          <option>Próximamente</option>
        </select>
      </div>
    </div>
  );
}
