import BarraProgreso from './BarraProgreso';

interface BarraProgresoPagoProps {
  /** Pedido.porcentajePagado — es un dato del pedido completo, no de la prenda puntual (no
   *  existe un desglose de pago por prenda), así que se repite igual en cada fila del mismo
   *  pedido. Mismo criterio visual que BarraProgresoTalles, a pedido del negocio. */
  porcentajePagado: number;
}

export default function BarraProgresoPago({ porcentajePagado }: BarraProgresoPagoProps) {
  const pct = Math.max(0, Math.min(100, Math.round(porcentajePagado)));
  return <BarraProgreso pct={pct} etiqueta={`${pct}%`} />;
}
