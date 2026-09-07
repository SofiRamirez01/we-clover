import type { CargaTallesResponse, ProductoPedidoResumenResponse } from '../../types/cargaTalles';

interface CargaTallesDetalleTablaProps {
  carga: CargaTallesResponse;
  productosConTalle: ProductoPedidoResumenResponse[];
}

/** Tabla alumno por alumno con sus medidas y talles — el detalle que antes vivía en el panel de
 *  edición del Pedido, ahora colapsado detrás de "Ver detalle" en la tarjeta de Ficha Técnica
 *  (el conteo agregado por producto ya se ve directo en cada prenda, sin tener que abrir esto). */
export default function CargaTallesDetalleTabla({ carga, productosConTalle }: CargaTallesDetalleTablaProps) {
  return (
    <div className="overflow-x-auto rounded-lg border border-wc-border">
      <table className="w-full text-left text-sm">
        <thead>
          <tr className="border-b border-wc-border text-xs text-wc-text-muted">
            <th className="px-3 py-2 font-semibold">Alumno</th>
            {productosConTalle.map((p) => (
              <th key={p.idProducto} className="px-3 py-2 font-semibold">{p.nombreTipoPrenda}</th>
            ))}
          </tr>
        </thead>
        <tbody>
          {carga.alumnos.length === 0 && (
            <tr>
              <td colSpan={1 + productosConTalle.length} className="px-3 py-3 text-center text-xs text-wc-text-muted">
                Todavía no hay alumnos cargados.
              </td>
            </tr>
          )}
          {carga.alumnos.flatMap((alumno, index) => {
            const fila = (
              <tr key={alumno.id} className="align-top">
                <td className="px-3 py-3 text-sm font-semibold text-wc-text">{alumno.nombreAlumno}</td>
                {productosConTalle.map((p) => (
                  <td key={p.idProducto} className="px-3 py-3">
                    <div className="flex flex-col gap-1">
                      {alumno.combos
                        .filter((c) => c.idProducto === p.idProducto)
                        .map((c) => (
                          <div key={c.id} className="flex items-center gap-1.5 text-xs">
                            <span className="text-wc-text-muted">
                              {c.anchoCm != null && c.largoCm != null ? `${c.anchoCm} × ${c.largoCm} cm` : 'Sin medir'}
                            </span>
                            {c.talle && !c.personalizado && (
                              <span className="rounded-lg bg-wc-green/15 px-2 py-0.5 font-semibold text-wc-green">
                                Talle {c.talle}
                              </span>
                            )}
                            {c.personalizado && (
                              <span className="rounded-lg bg-amber-100 px-2 py-0.5 font-semibold text-amber-700">
                                Personalizado
                              </span>
                            )}
                          </div>
                        ))}
                    </div>
                  </td>
                ))}
              </tr>
            );
            if (index === 0) return [fila];
            const divisor = (
              <tr key={`divisor-${alumno.id}`} aria-hidden="true">
                <td colSpan={1 + productosConTalle.length} className="p-0">
                  <hr className="mx-auto w-[90%] border-t border-gray-300" />
                </td>
              </tr>
            );
            return [divisor, fila];
          })}
        </tbody>
      </table>
    </div>
  );
}
