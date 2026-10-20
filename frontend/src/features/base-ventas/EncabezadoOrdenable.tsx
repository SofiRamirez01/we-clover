import type { CampoOrdenPedidos } from '../../types/pedido';
import type { OrdenListado } from './formato';

interface EncabezadoOrdenableProps {
  etiqueta: string;
  campo: CampoOrdenPedidos;
  orden: OrdenListado | null;
  onOrdenar: (campo: CampoOrdenPedidos) => void;
}

/** Encabezado de columna que ordena el listado (en el backend) al hacer clic. */
export default function EncabezadoOrdenable({ etiqueta, campo, orden, onOrdenar }: EncabezadoOrdenableProps) {
  const activa = orden?.campo === campo ? orden.direccion : null;
  const ariaSort = activa === 'ASC' ? 'ascending' : activa === 'DESC' ? 'descending' : 'none';
  const proxima = activa === 'ASC' ? 'de mayor a menor' : 'de menor a mayor';

  return (
    <th aria-sort={ariaSort}>
      <button type="button" className="th-orden" onClick={() => onOrdenar(campo)} title={`Ordenar ${proxima}`}>
        <span>{etiqueta}</span>
        <span className="th-orden-flechas" aria-hidden="true">
          <span className={activa === 'ASC' ? 'th-orden-flecha--activa' : undefined}>▲</span>
          <span className={activa === 'DESC' ? 'th-orden-flecha--activa' : undefined}>▼</span>
        </span>
      </button>
    </th>
  );
}
