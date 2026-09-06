import { useEffect, useState } from 'react';
import {
  cerrarCargaTalles,
  generarOObtenerLinkCargaTalles,
  obtenerCargaTallesInterno,
  reabrirCargaTalles,
} from '../../services/cargaTallesService';
import { extraerMensajeError } from '../../utils/errores';
import type { CargaTallesResponse } from '../../types/cargaTalles';

interface CargaTallesInternoPanelProps {
  idPedido: number;
}

/** Panel embebido en el detalle/edición del Pedido (Vendedor/Administrativo): generar o copiar
 *  el link público, cerrar/reabrir la carga, y consultar de solo lectura lo que el
 *  representante de curso ya cargó. */
export default function CargaTallesInternoPanel({ idPedido }: CargaTallesInternoPanelProps) {
  const [carga, setCarga] = useState<CargaTallesResponse | null>(null);
  const [cargando, setCargando] = useState(true);
  const [generando, setGenerando] = useState(false);
  const [accionando, setAccionando] = useState(false);
  const [copiado, setCopiado] = useState(false);
  const [mostrarDetalle, setMostrarDetalle] = useState(false);
  const [error, setError] = useState<string | null>(null);

  function recargar() {
    obtenerCargaTallesInterno(idPedido)
      .then(setCarga)
      .catch(() => setCarga(null))
      .finally(() => setCargando(false));
  }

  useEffect(() => {
    recargar();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [idPedido]);

  async function handleGenerar() {
    setGenerando(true);
    setError(null);
    try {
      await generarOObtenerLinkCargaTalles(idPedido);
      recargar();
    } catch (err) {
      setError(extraerMensajeError(err, 'No se pudo generar el link.'));
    } finally {
      setGenerando(false);
    }
  }

  async function handleCerrarOReabrir() {
    if (!carga) return;
    setAccionando(true);
    setError(null);
    try {
      if (carga.estado === 'ABIERTO') {
        await cerrarCargaTalles(idPedido);
      } else {
        await reabrirCargaTalles(idPedido);
      }
      recargar();
    } catch (err) {
      setError(extraerMensajeError(err, 'No se pudo actualizar el estado de la carga.'));
    } finally {
      setAccionando(false);
    }
  }

  function copiarLink() {
    if (!carga) return;
    const url = `${window.location.origin}/carga-talles/${carga.token}`;
    navigator.clipboard.writeText(url).then(() => {
      setCopiado(true);
      setTimeout(() => setCopiado(false), 2000);
    });
  }

  if (cargando) {
    return <p className="tw-scope text-sm text-wc-text-muted">Cargando carga de talles…</p>;
  }

  const productosConTalle = carga?.productos.filter((p) => p.tieneTalle) ?? [];

  return (
    <div className="tw-scope flex flex-col gap-3 rounded-xl border border-wc-border bg-white p-4 shadow-sm">
      <h2 className="text-sm font-bold text-wc-text">Carga de talles (link público)</h2>

      {!carga ? (
        <>
          <p className="text-xs text-wc-text-muted">
            Todavía no se generó el link para que el representante de curso cargue los talles.
          </p>
          <button
            type="button"
            onClick={handleGenerar}
            disabled={generando}
            className="self-start rounded-md bg-wc-green px-4 py-2 text-sm font-semibold text-white transition hover:bg-wc-green-dark disabled:cursor-wait disabled:opacity-60"
          >
            {generando ? 'Generando…' : 'Generar link de carga de talles'}
          </button>
        </>
      ) : (
        <>
          <div className="flex flex-wrap items-center gap-2">
            <code className="max-w-full truncate rounded-md bg-wc-bg px-2 py-1.5 text-xs text-wc-text">
              {`${window.location.origin}/carga-talles/${carga.token}`}
            </code>
            <button
              type="button"
              onClick={copiarLink}
              className="rounded-md border border-wc-border bg-white px-3 py-1.5 text-xs font-semibold text-wc-text transition hover:bg-wc-bg"
            >
              {copiado ? '¡Copiado!' : 'Copiar link'}
            </button>
            <span
              className={`rounded-full px-2.5 py-1 text-xs font-semibold ${
                carga.estado === 'ABIERTO' ? 'bg-wc-green/10 text-wc-green' : 'bg-wc-text-muted/10 text-wc-text-muted'
              }`}
            >
              {carga.estado === 'ABIERTO' ? 'Abierta' : 'Cerrada'}
            </span>
            <button
              type="button"
              onClick={handleCerrarOReabrir}
              disabled={accionando}
              className="rounded-md border border-wc-border bg-white px-3 py-1.5 text-xs font-semibold text-wc-text transition hover:bg-wc-bg disabled:cursor-wait disabled:opacity-60"
            >
              {accionando ? 'Guardando…' : carga.estado === 'ABIERTO' ? 'Cerrar carga' : 'Reabrir carga'}
            </button>
            <button
              type="button"
              onClick={() => setMostrarDetalle((prev) => !prev)}
              className="ml-auto text-xs font-semibold text-wc-green underline"
            >
              {mostrarDetalle ? 'Ocultar lo cargado' : 'Ver lo cargado'}
            </button>
          </div>

          {productosConTalle.length > 0 && (
            <div className="flex flex-wrap gap-2">
              {productosConTalle.map((p) => {
                const completo = p.cantidadCargada === p.cantidadTotal;
                return (
                  <span
                    key={p.idProducto}
                    className={`rounded-full px-2.5 py-1 text-xs font-semibold ${
                      completo ? 'bg-wc-green/10 text-wc-green' : 'bg-amber-100 text-amber-700'
                    }`}
                  >
                    {p.nombreTipoPrenda}: {p.cantidadCargada}/{p.cantidadTotal}
                  </span>
                );
              })}
            </div>
          )}

          {mostrarDetalle && (
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
          )}
        </>
      )}

      {error && <p className="text-xs font-medium text-red-600">{error}</p>}
    </div>
  );
}
