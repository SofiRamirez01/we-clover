import { useEffect, useState } from 'react';
import type { FormEvent } from 'react';
import FilaAlumno from './FilaAlumno';
import InstructivoTalles from './InstructivoTalles';
import { agregarAlumno, agregarUnidadCombo, obtenerCargaTallesPorToken } from '../../services/cargaTallesService';
import { extraerMensajeError } from '../../utils/errores';
import type { CargaTallesResponse, ProductoPedidoResumenResponse } from '../../types/cargaTalles';

interface CargaTallesPublicaViewProps {
  token: string;
}

/** Pantalla pública de carga de talles — accedida por /carga-talles/{token}, sin login (ver
 *  main.tsx, que la renderiza directo sin pasar por AuthProvider/LoginView cuando la URL
 *  matchea esta ruta). El representante elige la composición (cuántas unidades de cada prenda)
 *  al agregar el alumno, y después completa las medidas de cada fila y las guarda de a una. */
export default function CargaTallesPublicaView({ token }: CargaTallesPublicaViewProps) {
  const [data, setData] = useState<CargaTallesResponse | null>(null);
  const [estadoCarga, setEstadoCarga] = useState<'cargando' | 'listo' | 'error'>('cargando');
  const [nombreNuevoAlumno, setNombreNuevoAlumno] = useState('');
  const [composicionNueva, setComposicionNueva] = useState<Record<number, number>>({});
  const [guardandoAlumno, setGuardandoAlumno] = useState(false);
  const [errorAlumno, setErrorAlumno] = useState<string | null>(null);

  function recargar() {
    obtenerCargaTallesPorToken(token)
      .then((res) => {
        setData(res);
        setEstadoCarga('listo');
      })
      .catch(() => setEstadoCarga('error'));
  }

  useEffect(() => {
    recargar();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [token]);

  /** El +/- de la composición no puede pasar de lo que todavía queda sin asignar en el pedido
   *  (cantidadTotal - cantidadCargada) — si ya está todo asignado, el "+" queda deshabilitado y
   *  se avisa que no quedan más unidades de esa prenda (el backend vuelve a validar esto mismo
   *  al crear cada unidad, por si dos representantes cargan al mismo tiempo). */
  function cambiarCantidadComposicion(producto: ProductoPedidoResumenResponse, delta: number) {
    const restante = producto.cantidadTotal - producto.cantidadCargada;
    setComposicionNueva((prev) => {
      const actual = prev[producto.idProducto] ?? 0;
      const siguiente = Math.max(0, Math.min(restante, actual + delta));
      return { ...prev, [producto.idProducto]: siguiente };
    });
  }

  async function handleAgregarAlumno(e: FormEvent) {
    e.preventDefault();
    if (!nombreNuevoAlumno.trim()) return;
    setGuardandoAlumno(true);
    setErrorAlumno(null);
    try {
      const alumnoCreado = await agregarAlumno(token, { nombreAlumno: nombreNuevoAlumno.trim() });
      // Se crea la unidad de a una — no hay un endpoint de alta en lote, así que va secuencial;
      // si alguna falla a mitad de camino (ej. alguien más se llevó la última unidad
      // disponible), el alumno queda creado con lo que sí se alcanzó a agregar y hay que
      // borrarlo y volver a cargarlo (ver el tacho de la fila).
      for (const [idProductoTexto, cantidad] of Object.entries(composicionNueva)) {
        for (let i = 0; i < cantidad; i++) {
          await agregarUnidadCombo(token, alumnoCreado.id, Number(idProductoTexto));
        }
      }
      setNombreNuevoAlumno('');
      setComposicionNueva({});
      recargar();
    } catch (err) {
      setErrorAlumno(extraerMensajeError(err, 'No se pudo agregar el alumno.'));
    } finally {
      setGuardandoAlumno(false);
    }
  }

  if (estadoCarga === 'cargando') {
    return (
      <div className="tw-scope flex min-h-screen items-center justify-center bg-wc-bg">
        <p className="text-sm text-wc-text-muted">Cargando…</p>
      </div>
    );
  }

  if (estadoCarga === 'error' || !data) {
    return (
      <div className="tw-scope flex min-h-screen items-center justify-center bg-wc-bg p-6">
        <div className="max-w-sm rounded-xl border border-wc-border bg-white p-6 text-center shadow-sm">
          <p className="text-sm font-semibold text-wc-text">Este link no es válido.</p>
          <p className="mt-1 text-xs text-wc-text-muted">
            Puede que haya un error al copiarlo — pedile al vendedor que te lo reenvíe.
          </p>
        </div>
      </div>
    );
  }

  const soloLectura = data.estado === 'CERRADO';
  const productosConTalle = data.productos.filter((p) => p.tieneTalle);
  const columnas = 1 + productosConTalle.length + 1;

  return (
    <div className="tw-scope min-h-screen bg-wc-bg px-4 py-6 sm:px-8">
      <div className="mx-auto flex max-w-5xl flex-col gap-5">
        <div className="rounded-xl border border-wc-border bg-white p-4 shadow-sm">
          <h1 className="text-base font-bold text-wc-text">Carga de talles</h1>
          <p className="text-sm text-wc-text-muted">
            {data.nombreColegio} · {data.curso}
          </p>
        </div>

        {soloLectura && (
          <div className="rounded-xl border border-amber-300 bg-amber-50 p-3 text-sm font-medium text-amber-800">
            Esta carga fue cerrada — los datos quedan disponibles para consulta, pero ya no se pueden editar.
          </div>
        )}

        <InstructivoTalles tablasTalle={data.tablasTalle} />

        <div className="rounded-xl border border-wc-border bg-white p-4 shadow-sm">
          <h2 className="mb-2 text-sm font-bold text-wc-text">Resumen del pedido</h2>
          <p className="text-sm text-wc-text">
            Tu pedido: {data.productos.map((p) => `${p.cantidadTotal} ${p.nombreTipoPrenda}`).join(', ')}
          </p>

          {productosConTalle.length > 0 && (
            <div className="mt-3 flex flex-wrap gap-2">
              {productosConTalle.map((p) => {
                const completo = p.cantidadCargada === p.cantidadTotal;
                return (
                  <span
                    key={p.idProducto}
                    className={`rounded-full px-3 py-1 text-xs font-semibold ${
                      completo ? 'bg-wc-green/10 text-wc-green' : 'bg-amber-100 text-amber-700'
                    }`}
                  >
                    {p.nombreTipoPrenda}: {p.cantidadCargada}/{p.cantidadTotal} cargados
                  </span>
                );
              })}
            </div>
          )}
        </div>

        <div className="overflow-x-auto rounded-xl border border-wc-border bg-white shadow-sm">
          <table className="w-full text-left text-sm">
            <thead>
              <tr className="border-b border-wc-border text-xs text-wc-text-muted">
                <th className="px-3 py-2 font-semibold">Alumno</th>
                {productosConTalle.map((p) => (
                  <th key={p.idProducto} className="px-3 py-2 font-semibold">{p.nombreTipoPrenda}</th>
                ))}
                <th className="px-3 py-2 font-semibold" />
              </tr>
            </thead>
            <tbody>
              {data.alumnos.length === 0 && (
                <tr>
                  <td colSpan={columnas} className="px-3 py-4 text-center text-sm text-wc-text-muted">
                    Todavía no hay alumnos cargados.
                  </td>
                </tr>
              )}
              {data.alumnos.flatMap((alumno, index) => {
                const fila = (
                  <tr key={alumno.id} className="align-top">
                    <td className="px-3 py-3 font-semibold text-wc-text">{alumno.nombreAlumno}</td>
                    <FilaAlumno
                      token={token}
                      alumno={alumno}
                      productosConTalle={productosConTalle}
                      tablasTalle={data.tablasTalle}
                      soloLecturaGlobal={soloLectura}
                      onCambio={recargar}
                    />
                  </tr>
                );
                if (index === 0) return [fila];
                const divisor = (
                  <tr key={`divisor-${alumno.id}`} aria-hidden="true">
                    <td colSpan={columnas} className="p-0">
                      <hr className="mx-auto w-[90%] border-t border-gray-300" />
                    </td>
                  </tr>
                );
                return [divisor, fila];
              })}
            </tbody>
          </table>
        </div>

        {!soloLectura && (
          <form onSubmit={handleAgregarAlumno} className="flex flex-col gap-3 rounded-xl border border-wc-border bg-white p-4 shadow-sm">
            <input
              value={nombreNuevoAlumno}
              onChange={(e) => setNombreNuevoAlumno(e.target.value)}
              placeholder="Apodo a bordar"
              className="rounded-lg border border-wc-border bg-white px-3 py-2 text-sm text-wc-text outline-none focus:border-wc-green focus:ring-2 focus:ring-wc-green/20"
            />

            {productosConTalle.length > 0 && (
              <div className="flex flex-col gap-1.5">
                <span className="text-xs font-semibold text-wc-text">¿Cuántas de cada prenda lleva?</span>
                <div className="flex flex-wrap gap-2">
                  {productosConTalle.map((p) => {
                    const cantidad = composicionNueva[p.idProducto] ?? 0;
                    const restante = p.cantidadTotal - p.cantidadCargada;
                    const sinDisponibles = cantidad >= restante;
                    return (
                      <div key={p.idProducto} className="flex flex-col gap-0.5">
                        <div className="flex items-center gap-2 rounded-lg bg-wc-bg px-2 py-1.5">
                          <span className="text-xs text-wc-text">{p.nombreTipoPrenda}</span>
                          <button
                            type="button"
                            onClick={() => cambiarCantidadComposicion(p, -1)}
                            disabled={cantidad === 0}
                            aria-label={`Restar ${p.nombreTipoPrenda}`}
                            className="flex h-6 w-6 items-center justify-center rounded-md bg-white text-sm font-semibold text-wc-text disabled:opacity-40"
                          >
                            −
                          </button>
                          <span className="w-4 text-center text-sm font-semibold text-wc-text">{cantidad}</span>
                          <button
                            type="button"
                            onClick={() => cambiarCantidadComposicion(p, 1)}
                            disabled={sinDisponibles}
                            aria-label={`Sumar ${p.nombreTipoPrenda}`}
                            className="flex h-6 w-6 items-center justify-center rounded-md bg-white text-sm font-semibold text-wc-text disabled:opacity-40"
                          >
                            +
                          </button>
                        </div>
                        {sinDisponibles && (
                          <span className="max-w-[10rem] text-[0.65rem] font-medium text-amber-700">
                            Ya no hay más unidades de {p.nombreTipoPrenda} disponibles para agregar.
                          </span>
                        )}
                      </div>
                    );
                  })}
                </div>
              </div>
            )}

            <button
              type="submit"
              disabled={guardandoAlumno || !nombreNuevoAlumno.trim()}
              className="self-start rounded-lg bg-wc-green/15 px-4 py-2 text-sm font-semibold text-wc-green transition hover:bg-wc-green/25 disabled:cursor-not-allowed disabled:opacity-50"
            >
              {guardandoAlumno ? 'Agregando…' : '+ Agregar alumno'}
            </button>
            {errorAlumno && <p className="text-xs font-medium text-red-600">{errorAlumno}</p>}
          </form>
        )}
      </div>
    </div>
  );
}
