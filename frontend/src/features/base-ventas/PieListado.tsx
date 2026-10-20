import type { useListadoPedidos } from './useListadoPedidos';

interface PieListadoProps {
  listado: ReturnType<typeof useListadoPedidos>;
  textoVacio: string;
}

/** Debajo de la tabla: cargando / vacío / error, y "Cargar más" cuando hay más páginas. */
export default function PieListado({ listado, textoVacio }: PieListadoProps) {
  if (listado.estadoCarga === 'cargando') {
    return <p className="pedidos-tabla-vacio">Cargando pedidos…</p>;
  }
  if (listado.estadoCarga === 'error') {
    return <p className="pedidos-tabla-vacio">No se pudo cargar el listado de pedidos.</p>;
  }
  if (listado.pedidos.length === 0) {
    return <p className="pedidos-tabla-vacio">{textoVacio}</p>;
  }
  return (
    <div className="pedidos-pie">
      <span>
        Mostrando {listado.pedidos.length.toLocaleString('es-AR')} de {listado.total.toLocaleString('es-AR')} pedido
        {listado.total === 1 ? '' : 's'}
      </span>
      {listado.hayMas && (
        <button type="button" className="btn-secondary" onClick={listado.cargarMas} disabled={listado.cargandoMas}>
          {listado.cargandoMas ? 'Cargando…' : 'Cargar más'}
        </button>
      )}
      {listado.errorCargarMas && <span className="pedidos-pie-error">No se pudieron cargar más pedidos.</span>}
    </div>
  );
}
