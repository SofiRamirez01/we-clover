import { useState } from 'react';
import EstadoBadge, { estiloBadgeEstado } from './EstadoBadge';
import { cambiarEstadoProducto } from '../../services/productoService';
import { extraerMensajeError } from '../../utils/errores';
import { ESTADOS_PEDIDO, ESTADO_PEDIDO_LABELS } from '../../types/pedido';
import type { EstadoPedido, ProductoResponse } from '../../types/pedido';

interface EstadoProductoControlProps {
  producto: ProductoResponse;
  puedeEditar: boolean;
  onActualizado: (producto: ProductoResponse) => void;
}

export default function EstadoProductoControl({ producto, puedeEditar, onActualizado }: EstadoProductoControlProps) {
  const [guardando, setGuardando] = useState(false);
  const [error, setError] = useState<string | null>(null);

  if (!puedeEditar) {
    return <EstadoBadge estado={producto.estadoActual} />;
  }

  async function handleChange(nuevoEstado: EstadoPedido) {
    setError(null);
    setGuardando(true);
    try {
      const actualizado = await cambiarEstadoProducto(producto.id, nuevoEstado);
      onActualizado(actualizado);
    } catch (err) {
      setError(extraerMensajeError(err, 'No se pudo cambiar el estado.'));
    } finally {
      setGuardando(false);
    }
  }

  return (
    <div className="flex flex-col gap-0.5">
      <select
        value={producto.estadoActual}
        disabled={guardando}
        onChange={(e) => handleChange(e.target.value as EstadoPedido)}
        aria-label="Estado de producción de la prenda"
        className={`w-full truncate rounded-full border-0 py-1 pl-2.5 pr-5 text-[11px] font-bold outline-none transition ${estiloBadgeEstado(
          producto.estadoActual,
        )} ${guardando ? 'cursor-wait opacity-60' : 'cursor-pointer hover:brightness-95'}`}
      >
        {ESTADOS_PEDIDO.map((estado) => (
          <option key={estado} value={estado}>
            {ESTADO_PEDIDO_LABELS[estado]}
          </option>
        ))}
      </select>
      {error && <span className="text-[9px] font-medium text-red-600">{error}</span>}
    </div>
  );
}
