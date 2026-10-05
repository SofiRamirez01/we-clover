import { Cell, Pie, PieChart, ResponsiveContainer, Tooltip } from 'recharts';
import TooltipGrafico from './TooltipGrafico';
import { formatoEntero } from './formato';

export interface PorcionDonut {
  clave: string;
  nombre: string;
  valor: number;
  /** Color fijo de la categoría (ej. COLORES_ESTADO_PRODUCCION): no depende de la posición. */
  color: string;
}

interface DonutEstadosProps {
  datos: PorcionDonut[];
  /** Texto bajo el total del centro (ej. "en planta"). */
  etiquetaTotal: string;
  /** Qué se cuenta, para el tooltip (ej. "Unidades"). */
  nombreValor: string;
}

function porcentaje(valor: number, total: number): string {
  if (total === 0) return '0%';
  const p = (valor / total) * 100;
  return p > 0 && p < 1 ? '<1%' : `${Math.round(p)}%`;
}

/**
 * Torta (donut) de la distribución de un total entre categorías, con el total en el centro. La
 * leyenda de al lado trae nombre, valor y porcentaje de TODAS las categorías (incluidas las que
 * están en 0, que no tienen porción): así la identidad nunca depende solo del color, y un
 * estado sin unidades no "desaparece".
 */
export default function DonutEstados({ datos, etiquetaTotal, nombreValor }: DonutEstadosProps) {
  const total = datos.reduce((suma, porcion) => suma + porcion.valor, 0);
  const conValor = datos.filter((porcion) => porcion.valor > 0);

  if (total === 0) {
    return (
      <div className="flex h-56 items-center justify-center text-sm text-wc-text-muted">
        No hay unidades para mostrar.
      </div>
    );
  }

  return (
    <div className="flex flex-col items-center gap-4 sm:flex-row sm:items-center">
      <div className="relative h-56 w-56 shrink-0">
        {/* El total va DEBAJO del gráfico (que es transparente en el hueco): si fuera encima,
            taparía el tooltip cuando este cae sobre el centro. */}
        <div className="pointer-events-none absolute inset-0 flex flex-col items-center justify-center">
          <span className="text-2xl font-semibold text-wc-text">{formatoEntero(total)}</span>
          <span className="text-xs text-wc-text-muted">{etiquetaTotal}</span>
        </div>
        <div className="relative h-full w-full">
        <ResponsiveContainer width="100%" height="100%">
          <PieChart>
            <Pie
              data={conValor}
              dataKey="valor"
              nameKey="nombre"
              innerRadius="62%"
              outerRadius="100%"
              startAngle={90}
              endAngle={-270}
              // Separación entre porciones: un borde del color de la superficie, no un trazo.
              stroke="#ffffff"
              strokeWidth={2}
              isAnimationActive={false}
            >
              {conValor.map((porcion) => (
                <Cell key={porcion.clave} fill={porcion.color} />
              ))}
            </Pie>
            <Tooltip
              isAnimationActive={false}
              allowEscapeViewBox={{ x: true, y: true }}
              wrapperStyle={{ zIndex: 20 }}
              content={({ active, payload }) => {
                const porcion = payload?.[0]?.payload as PorcionDonut | undefined;
                if (!active || !porcion) return null;
                return (
                  <TooltipGrafico
                    titulo={porcion.nombre}
                    color={porcion.color}
                    nombreValor={nombreValor}
                    valor={formatoEntero(porcion.valor)}
                    detalle={`${porcentaje(porcion.valor, total)} del total`}
                  />
                );
              }}
            />
          </PieChart>
        </ResponsiveContainer>
        </div>
      </div>

      <ul className="m-0 flex w-full min-w-0 list-none flex-col gap-1.5 p-0">
        {datos.map((porcion) => (
          <li key={porcion.clave} className="flex items-center gap-2 text-sm text-wc-text">
            <span className="h-2.5 w-2.5 shrink-0 rounded-sm" style={{ backgroundColor: porcion.color }} aria-hidden="true" />
            <span className={porcion.valor === 0 ? 'text-wc-text-muted' : ''}>{porcion.nombre}</span>
            <span className="ml-auto font-semibold tabular-nums">{formatoEntero(porcion.valor)}</span>
            <span className="w-10 text-right text-xs text-wc-text-muted tabular-nums">{porcentaje(porcion.valor, total)}</span>
          </li>
        ))}
      </ul>
    </div>
  );
}
