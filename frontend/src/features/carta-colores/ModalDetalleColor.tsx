import { useEffect, useState } from 'react';
import {
  actualizarArticuloProveedor,
  agregarArticuloProveedor,
  crearProveedor,
  listarArticulosPorColor,
} from '../../services/proveedorService';
import { extraerMensajeError } from '../../utils/errores';
import type { PaletaColorResponse } from '../../types/paletaColores';
import type { ArticuloProveedorResponse, ProveedorResponse } from '../../types/proveedor';
import type { TipoTelaCatalogo } from '../../types/tipoTela';

interface ModalDetalleColorProps {
  color: PaletaColorResponse;
  tipoTela: TipoTelaCatalogo;
  proveedores: ProveedorResponse[];
  onProveedorCreado: (proveedor: ProveedorResponse) => void;
  onCerrar: () => void;
}

export default function ModalDetalleColor({ color, tipoTela, proveedores, onProveedorCreado, onCerrar }: ModalDetalleColorProps) {
  const unidadMedida = tipoTela.esPorPeso ? 'KG' : 'UNIDAD';

  const [articulos, setArticulos] = useState<ArticuloProveedorResponse[]>([]);
  const [cargando, setCargando] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const [idProveedorNuevo, setIdProveedorNuevo] = useState('');
  const [precioNuevo, setPrecioNuevo] = useState('');
  const [agregando, setAgregando] = useState(false);
  const [errorAgregar, setErrorAgregar] = useState<string | null>(null);

  const [creandoProveedor, setCreandoProveedor] = useState(false);
  const [nuevoNombre, setNuevoNombre] = useState('');
  const [nuevoCuit, setNuevoCuit] = useState('');
  const [guardandoProveedor, setGuardandoProveedor] = useState(false);

  useEffect(() => {
    let cancelado = false;
    listarArticulosPorColor(color.id)
      .then((data) => {
        if (!cancelado) setArticulos(data);
      })
      .catch((err) => {
        if (!cancelado) setError(extraerMensajeError(err, 'No se pudieron cargar los proveedores de este color.'));
      })
      .finally(() => {
        if (!cancelado) setCargando(false);
      });
    return () => {
      cancelado = true;
    };
  }, [color.id]);

  const idsYaCargados = new Set(articulos.map((a) => a.idProveedor));
  const proveedoresDisponibles = proveedores.filter((p) => !idsYaCargados.has(p.id));

  async function handleActualizarArticulo(articulo: ArticuloProveedorResponse, cambios: { precioEstimado?: number | null; activo?: boolean }) {
    try {
      const actualizado = await actualizarArticuloProveedor(articulo.id, {
        unidadMedida: articulo.unidadMedida,
        precioEstimado: (cambios.precioEstimado ?? articulo.precioEstimado) ?? undefined,
        preferido: articulo.preferido,
        activo: cambios.activo ?? articulo.activo,
      });
      setArticulos((prev) => prev.map((a) => (a.id === actualizado.id ? actualizado : a)));
    } catch (err) {
      setError(extraerMensajeError(err, 'No se pudo actualizar el artículo.'));
    }
  }

  /** A diferencia de precio/activo, marcar preferido puede desmarcar otra fila del lado del
   *  servidor (a lo sumo un preferido por color) — se recarga la lista completa en vez de
   *  parchear localmente solo esta fila. */
  async function handleTogglePreferido(articulo: ArticuloProveedorResponse) {
    try {
      await actualizarArticuloProveedor(articulo.id, {
        unidadMedida: articulo.unidadMedida,
        precioEstimado: articulo.precioEstimado ?? undefined,
        preferido: !articulo.preferido,
        activo: articulo.activo,
      });
      const actualizados = await listarArticulosPorColor(color.id);
      setArticulos(actualizados);
    } catch (err) {
      setError(extraerMensajeError(err, 'No se pudo marcar el proveedor preferido.'));
    }
  }

  async function handleCrearProveedor() {
    if (!nuevoNombre.trim() || !/^\d{11}$/.test(nuevoCuit)) {
      setErrorAgregar('Completá nombre y CUIT (11 dígitos, sin guiones).');
      return;
    }
    setGuardandoProveedor(true);
    setErrorAgregar(null);
    try {
      const creado = await crearProveedor({ nombre: nuevoNombre.trim(), cuit: nuevoCuit });
      onProveedorCreado(creado);
      setIdProveedorNuevo(String(creado.id));
      setCreandoProveedor(false);
      setNuevoNombre('');
      setNuevoCuit('');
    } catch (err) {
      setErrorAgregar(extraerMensajeError(err, 'No se pudo crear el proveedor.'));
    } finally {
      setGuardandoProveedor(false);
    }
  }

  async function handleAgregarArticulo() {
    if (!idProveedorNuevo) {
      setErrorAgregar('Elegí un proveedor.');
      return;
    }
    setAgregando(true);
    setErrorAgregar(null);
    try {
      const creado = await agregarArticuloProveedor(Number(idProveedorNuevo), {
        idPaletaColor: color.id,
        unidadMedida,
        precioEstimado: precioNuevo.trim() ? Number(precioNuevo) : undefined,
      });
      setArticulos((prev) => [...prev, creado]);
      setIdProveedorNuevo('');
      setPrecioNuevo('');
    } catch (err) {
      setErrorAgregar(extraerMensajeError(err, 'No se pudo agregar el proveedor a este color.'));
    } finally {
      setAgregando(false);
    }
  }

  return (
    <div className="tw-scope fixed inset-0 z-50 flex items-center justify-center bg-black/70 p-6" role="dialog" aria-modal="true">
      <div className="flex max-h-[90vh] w-full max-w-lg flex-col gap-4 overflow-y-auto rounded-xl bg-white p-6 shadow-2xl">
        <div className="flex items-center gap-3">
          <span className="h-8 w-8 shrink-0 rounded-full border border-wc-border" style={{ backgroundColor: color.hex }} />
          <div>
            <h2 className="text-base font-bold text-wc-text">{color.nombre}</h2>
            <p className="text-xs text-wc-text-muted">
              {tipoTela.nombre} · se compra por {unidadMedida === 'KG' ? 'kilogramo' : 'unidad'}
            </p>
          </div>
        </div>

        {cargando && <p className="text-sm text-wc-text-muted">Cargando proveedores…</p>}
        {error && <p className="text-xs font-medium text-red-600">{error}</p>}

        {!cargando && (
          <div className="flex flex-col gap-2">
            {articulos.length === 0 && <p className="text-xs text-wc-text-muted">Todavía no hay proveedores cargados para este color.</p>}
            {articulos.map((articulo) => (
              <div key={articulo.id} className="flex items-center gap-2 rounded-lg border border-wc-border p-2">
                <span className={`flex-1 text-sm ${articulo.activo ? 'text-wc-text' : 'text-wc-text-muted line-through'}`}>
                  {articulo.nombreProveedor}
                </span>
                <button
                  type="button"
                  onClick={() => handleTogglePreferido(articulo)}
                  title={articulo.preferido ? 'Proveedor preferido de este color' : 'Marcar como preferido'}
                  className={`shrink-0 text-base leading-none transition ${
                    articulo.preferido ? 'text-amber-500' : 'text-wc-border hover:text-amber-400'
                  }`}
                >
                  {articulo.preferido ? '★' : '☆'}
                </button>
                <input
                  type="number"
                  min={0}
                  step="0.01"
                  defaultValue={articulo.precioEstimado ?? ''}
                  onBlur={(e) => {
                    const valor = e.target.value.trim() ? Number(e.target.value) : null;
                    if (valor !== articulo.precioEstimado) handleActualizarArticulo(articulo, { precioEstimado: valor });
                  }}
                  placeholder="Precio"
                  className="w-24 rounded-lg border border-wc-border bg-white px-2 py-1 text-xs text-wc-text outline-none"
                />
                <label className="flex items-center gap-1 text-[11px] text-wc-text-muted">
                  <input
                    type="checkbox"
                    checked={articulo.activo}
                    onChange={(e) => handleActualizarArticulo(articulo, { activo: e.target.checked })}
                    className="h-3.5 w-3.5 accent-wc-green"
                  />
                  Activo
                </label>
              </div>
            ))}
          </div>
        )}

        <div className="flex flex-col gap-2 border-t border-wc-border pt-3">
          <span className="text-xs font-semibold text-wc-text">Agregar proveedor a este color</span>
          <div className="flex flex-wrap items-center gap-2">
            <select
              value={idProveedorNuevo}
              onChange={(e) => setIdProveedorNuevo(e.target.value)}
              className="min-w-[9rem] flex-1 rounded-lg border border-wc-border bg-white px-2 py-1.5 text-sm text-wc-text"
            >
              <option value="">Elegir proveedor…</option>
              {proveedoresDisponibles.map((p) => (
                <option key={p.id} value={p.id}>
                  {p.nombre}
                </option>
              ))}
            </select>
            <input
              type="number"
              min={0}
              step="0.01"
              value={precioNuevo}
              onChange={(e) => setPrecioNuevo(e.target.value)}
              placeholder="Precio (opcional)"
              className="w-32 rounded-lg border border-wc-border bg-white px-2 py-1.5 text-xs text-wc-text"
            />
            <button
              type="button"
              onClick={handleAgregarArticulo}
              disabled={agregando}
              className="rounded-md bg-wc-green px-3 py-1.5 text-xs font-semibold text-white transition hover:bg-wc-green-dark disabled:cursor-wait disabled:opacity-60"
            >
              {agregando ? 'Agregando…' : '+ Agregar'}
            </button>
          </div>

          {!creandoProveedor ? (
            <button type="button" onClick={() => setCreandoProveedor(true)} className="self-start text-xs font-semibold text-wc-green underline">
              + Crear proveedor nuevo
            </button>
          ) : (
            <div className="flex flex-wrap items-center gap-2">
              <input
                value={nuevoNombre}
                onChange={(e) => setNuevoNombre(e.target.value)}
                placeholder="Nombre del proveedor"
                className="min-w-[10rem] flex-1 rounded-lg border border-wc-border bg-white px-2 py-1.5 text-sm text-wc-text"
              />
              <input
                value={nuevoCuit}
                onChange={(e) => setNuevoCuit(e.target.value.replace(/\D/g, '').slice(0, 11))}
                placeholder="CUIT (11 dígitos)"
                className="w-36 rounded-lg border border-wc-border bg-white px-2 py-1.5 text-sm text-wc-text"
              />
              <button
                type="button"
                onClick={handleCrearProveedor}
                disabled={guardandoProveedor}
                className="rounded-md bg-wc-green px-3 py-1.5 text-xs font-semibold text-white transition hover:bg-wc-green-dark disabled:cursor-wait disabled:opacity-60"
              >
                {guardandoProveedor ? 'Creando…' : 'Crear'}
              </button>
              <button type="button" onClick={() => setCreandoProveedor(false)} className="text-xs text-wc-text-muted underline">
                Cancelar
              </button>
            </div>
          )}
          {errorAgregar && <p className="text-xs font-medium text-red-600">{errorAgregar}</p>}
        </div>

        <div className="flex justify-end border-t border-wc-border pt-4">
          <button
            type="button"
            onClick={onCerrar}
            className="rounded-md border border-wc-border bg-white px-4 py-2 text-sm font-semibold text-wc-text-muted transition hover:bg-wc-bg"
          >
            Cerrar
          </button>
        </div>
      </div>
    </div>
  );
}
