import { Bar, BarChart, CartesianGrid, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts';
import TooltipGrafico from './TooltipGrafico';
import { COLOR_CURSOR, COLOR_GRILLA, COLOR_SERIE, COLOR_TEXTO_EJE } from './coloresReportes';
import { formatoEntero, mesCorto, mesLargo } from './formato';

export interface PuntoMensual {
  /** YYYY-MM */
  mes: string;
  valor: number;
  /** Línea extra del tooltip (ej. "Monto: $ 1.200.000"). */
  detalle?: string;
}

interface BarChartMensualProps {
  datos: PuntoMensual[];
  /** Qué se está contando, para el tooltip (ej. "Unidades vendidas"). */
  nombreValor: string;
  color?: string;
  alto?: number;
}

const ESTILO_TICK = { fill: COLOR_TEXTO_EJE, fontSize: 11 };

/**
 * Barras verticales de un valor por mes (una sola serie, por eso sin leyenda: el título de la
 * tarjeta ya dice qué se grafica). Reutilizable para cualquier "X por mes" de Reportes.
 */
export default function BarChartMensual({ datos, nombreValor, color = COLOR_SERIE, alto = 260 }: BarChartMensualProps) {
  if (datos.every((punto) => punto.valor === 0)) {
    return (
      <div className="flex items-center justify-center text-sm text-wc-text-muted" style={{ height: alto }}>
        Sin datos en el período seleccionado.
      </div>
    );
  }

  return (
    <div style={{ height: alto }}>
      <ResponsiveContainer width="100%" height="100%">
        <BarChart data={datos} margin={{ top: 8, right: 8, bottom: 0, left: 0 }}>
          <CartesianGrid vertical={false} stroke={COLOR_GRILLA} />
          <XAxis
            dataKey="mes"
            tickFormatter={mesCorto}
            tick={ESTILO_TICK}
            tickLine={false}
            axisLine={{ stroke: COLOR_GRILLA }}
            minTickGap={12}
          />
          <YAxis
            allowDecimals={false}
            tickFormatter={formatoEntero}
            tick={ESTILO_TICK}
            tickLine={false}
            axisLine={false}
            width={48}
          />
          <Tooltip
            cursor={{ fill: COLOR_CURSOR }}
            isAnimationActive={false}
            content={({ active, payload }) => {
              const punto = payload?.[0]?.payload as PuntoMensual | undefined;
              if (!active || !punto) return null;
              return (
                <TooltipGrafico
                  titulo={mesLargo(punto.mes)}
                  color={color}
                  nombreValor={nombreValor}
                  valor={formatoEntero(punto.valor)}
                  detalle={punto.detalle}
                />
              );
            }}
          />
          <Bar dataKey="valor" fill={color} radius={[4, 4, 0, 0]} maxBarSize={24} isAnimationActive={false} />
        </BarChart>
      </ResponsiveContainer>
    </div>
  );
}
