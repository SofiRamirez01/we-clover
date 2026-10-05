import { Bar, BarChart, CartesianGrid, LabelList, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts';
import TooltipGrafico from './TooltipGrafico';
import { COLOR_CURSOR, COLOR_GRILLA, COLOR_SERIE, COLOR_TEXTO_EJE } from './coloresReportes';
import { formatoEntero } from './formato';

export interface PuntoCategoria {
  nombre: string;
  valor: number;
  /** Línea extra del tooltip (ej. "Monto: $ 1.200.000"). */
  detalle?: string;
}

interface BarChartCategoriasProps {
  datos: PuntoCategoria[];
  nombreValor: string;
  color?: string;
}

const ESTILO_TICK = { fill: COLOR_TEXTO_EJE, fontSize: 12 };
const ALTO_POR_FILA = 40;

/**
 * Barras horizontales de un valor por categoría (ej. unidades por tipo de prenda), ordenadas de
 * mayor a menor. Un solo eje a propósito: un segundo dato (el monto) va en el tooltip, no en un
 * eje doble. El valor va escrito en la punta de cada barra.
 */
export default function BarChartCategorias({ datos, nombreValor, color = COLOR_SERIE }: BarChartCategoriasProps) {
  if (datos.length === 0 || datos.every((punto) => punto.valor === 0)) {
    return (
      <div className="flex h-40 items-center justify-center text-sm text-wc-text-muted">
        Sin datos en el período seleccionado.
      </div>
    );
  }

  const ordenados = [...datos].sort((a, b) => b.valor - a.valor);

  return (
    <div style={{ height: ordenados.length * ALTO_POR_FILA + 32 }}>
      <ResponsiveContainer width="100%" height="100%">
        <BarChart data={ordenados} layout="vertical" margin={{ top: 0, right: 48, bottom: 0, left: 0 }}>
          <CartesianGrid horizontal={false} stroke={COLOR_GRILLA} />
          <XAxis
            type="number"
            allowDecimals={false}
            tickFormatter={formatoEntero}
            tick={{ ...ESTILO_TICK, fontSize: 11 }}
            tickLine={false}
            axisLine={false}
          />
          <YAxis
            type="category"
            dataKey="nombre"
            tick={ESTILO_TICK}
            tickLine={false}
            axisLine={{ stroke: COLOR_GRILLA }}
            width={84}
          />
          <Tooltip
            cursor={{ fill: COLOR_CURSOR }}
            isAnimationActive={false}
            content={({ active, payload }) => {
              const punto = payload?.[0]?.payload as PuntoCategoria | undefined;
              if (!active || !punto) return null;
              return (
                <TooltipGrafico
                  titulo={punto.nombre}
                  color={color}
                  nombreValor={nombreValor}
                  valor={formatoEntero(punto.valor)}
                  detalle={punto.detalle}
                />
              );
            }}
          />
          <Bar dataKey="valor" fill={color} radius={[0, 4, 4, 0]} maxBarSize={24} isAnimationActive={false}>
            <LabelList
              dataKey="valor"
              position="right"
              formatter={(valor) => formatoEntero(Number(valor))}
              style={{ fill: '#1f2421', fontSize: 12, fontWeight: 600 }}
            />
          </Bar>
        </BarChart>
      </ResponsiveContainer>
    </div>
  );
}
