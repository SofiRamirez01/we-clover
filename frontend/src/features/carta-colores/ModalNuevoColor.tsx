import { useState } from 'react';
import ColorGoteroInput from './ColorGoteroInput';
import { crearColorCarta } from '../../services/cartaColoresService';
import { agregarArticuloProveedor, crearProveedor } from '../../services/proveedorService';
import { extraerMensajeError } from '../../utils/errores';
import type { PaletaColorResponse } from '../../types/paletaColores';
import type { ProveedorResponse } from '../../types/proveedor';
import type { TipoTelaCatalogo } from '../../types/tipoTela';

interface ModalNuevoColorProps {
  /** Tela de la pestaña activa: el color nuevo se crea siempre para esa tela. */
  tipoTela: TipoTelaCatalogo;
  proveedores: ProveedorResponse[];
  onCreado: (color: PaletaColorResponse) => void;
  onProveedorCreado: (proveedor: ProveedorResponse) => void;
  onCerrar: () => void;
}

export default function ModalNuevoColor({ tipoTela, proveedores, onCreado, onProveedorCreado, onCerrar }: ModalNuevoColorProps) {
  const unidadMedida = tipoTela.esPorPeso ? 'KG' : 'UNIDAD';

  const [nombre, setNombre] = useState('');
  const [hex, setHex] = useState('#000000');
  /** idProveedor -> precio estimado (string crudo del input, puede quedar vacío). */
  const [seleccionados, setSeleccionados] = useState<Record<number, string>>({});
  const [guardando, setGuardando] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const [creandoProveedor, setCreandoProveedor] = useState(false);
  const [nuevoNombre, setNuevoNombre] = useState('');
  const [nuevoCuit, setNuevoCuit] = useState('');
  const [guardandoProveedor, setGuardandoProveedor] = useState(false);
  const [errorProveedor, setErrorProveedor] = useState<string | null>(null);

  function toggleProveedor(id: number) {
    setSeleccionados((prev) => {
      const copia = { ...prev };
      if (id in copia) delete copia[id];
      else copia[id] = '';
      return copia;
    });
  }

  async function handleCrearProveedor() {
    if (!nuevoNombre.trim() || !/^\d{11}$/.test(nuevoCuit)) {
      setErrorProveedor('Completá nombre y CUIT (11 dígitos, sin guiones).');
      return;
    }
    setGuardandoProveedor(true);
    setErrorProveedor(null);
    try {
      const creado = await crearProveedor({ nombre: nuevoNombre.trim(), cuit: nuevoCuit });
      onProveedorCreado(creado);
      setSeleccionados((prev) => ({ ...prev, [creado.id]: '' }));
      setCreandoProveedor(false);
      setNuevoNombre('');
      setNuevoCuit('');
    } catch (err) {
      setErrorProveedor(extraerMensajeError(err, 'No se pudo crear el proveedor.'));
    } finally {
      setGuardandoProveedor(false);
    }
  }

  async function handleGuardar() {
    if (!nombre.trim()) {
      setError('Ingresá un nombre para el color.');
      return;
    }
    setGuardando(true);
    setError(null);
    try {
      const color = await crearColorCarta({ nombre: nombre.trim(), hex, tipoTela: tipoTela.codigo });

      const erroresArticulos: string[] = [];
      for (const [idTexto, precioTexto] of Object.entries(seleccionados)) {
        const idProveedor = Number(idTexto);
        try {
          await agregarArticuloProveedor(idProveedor, {
            idPaletaColor: color.id,
            unidadMedida,
            precioEstimado: precioTexto.trim() ? Number(precioTexto) : undefined,
          });
        } catch (err) {
          const proveedor = proveedores.find((p) => p.id === idProveedor);
          erroresArticulos.push(`${proveedor?.nombre ?? idProveedor}: ${extraerMensajeError(err, 'no se pudo cargar')}`);
        }
      }

      if (erroresArticulos.length > 0) {
        setGuardando(false);
        setError(`El color se creó, pero hubo problemas con algunos proveedores — ${erroresArticulos.join('; ')}`);
        onCreado(color);
        return;
      }

      onCreado(color);
    } catch (err) {
      setError(extraerMensajeError(err, 'No se pudo crear el color.'));
    } finally {
      setGuardando(false);
    }
  }

  return (
    <div className="tw-scope fixed inset-0 z-50 flex items-center justify-center bg-black/70 p-6" role="dialog" aria-modal="true">
      <div className="flex max-h-[90vh] w-full max-w-lg flex-col gap-4 overflow-y-auto rounded-xl bg-white p-6 shadow-2xl">
        <div>
          <h2 className="text-base font-bold text-wc-text">Nuevo color · {tipoTela.nombre}</h2>
          <p className="text-xs text-wc-text-muted">Se compra por {unidadMedida === 'KG' ? 'kilogramo' : 'unidad'}</p>
        </div>

        <div className="flex flex-col gap-1.5">
          <label className="text-xs font-semibold text-wc-text">Nombre del color</label>
          <input
            value={nombre}
            onChange={(e) => setNombre(e.target.value)}
            placeholder="Ej: Marino"
            className="rounded-lg border border-wc-border bg-white px-3 py-2 text-sm text-wc-text outline-none focus:border-wc-green focus:ring-2 focus:ring-wc-green/20"
          />
        </div>

        <div className="flex flex-col gap-1.5">
          <label className="text-xs font-semibold text-wc-text">Color</label>
          <ColorGoteroInput hex={hex} onCambiar={setHex} />
        </div>

        <div className="flex flex-col gap-2 rounded-lg border border-wc-border p-3">
          <span className="text-xs font-semibold text-wc-text">Proveedores (opcional)</span>

          {proveedores.length === 0 && <p className="text-xs text-wc-text-muted">Todavía no hay proveedores cargados.</p>}

          <div className="flex flex-col gap-1.5">
            {proveedores.map((proveedor) => {
              const marcado = proveedor.id in seleccionados;
              return (
                <div key={proveedor.id} className="flex items-center gap-2">
                  <input
                    type="checkbox"
                    checked={marcado}
                    onChange={() => toggleProveedor(proveedor.id)}
                    className="h-4 w-4 accent-wc-green"
                  />
                  <span className="flex-1 text-sm text-wc-text">{proveedor.nombre}</span>
                  {marcado && (
                    <input
                      type="number"
                      min={0}
                      step="0.01"
                      value={seleccionados[proveedor.id]}
                      onChange={(e) => setSeleccionados((prev) => ({ ...prev, [proveedor.id]: e.target.value }))}
                      placeholder="Precio (opcional)"
                      className="w-32 rounded-lg border border-wc-border bg-white px-2 py-1 text-xs text-wc-text outline-none"
                    />
                  )}
                </div>
              );
            })}
          </div>

          {!creandoProveedor ? (
            <button type="button" onClick={() => setCreandoProveedor(true)} className="self-start text-xs font-semibold text-wc-green underline">
              + Crear proveedor nuevo
            </button>
          ) : (
            <div className="flex flex-col gap-2 border-t border-wc-border pt-2">
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
              </div>
              <div className="flex items-center gap-2">
                <button
                  type="button"
                  onClick={handleCrearProveedor}
                  disabled={guardandoProveedor}
                  className="rounded-md bg-wc-green px-3 py-1.5 text-xs font-semibold text-white transition hover:bg-wc-green-dark disabled:cursor-wait disabled:opacity-60"
                >
                  {guardandoProveedor ? 'Creando…' : 'Crear y usar'}
                </button>
                <button type="button" onClick={() => setCreandoProveedor(false)} className="text-xs text-wc-text-muted underline">
                  Cancelar
                </button>
              </div>
              {errorProveedor && <p className="text-xs font-medium text-red-600">{errorProveedor}</p>}
            </div>
          )}
        </div>

        {error && <p className="text-xs font-medium text-red-600">{error}</p>}

        <div className="flex items-center justify-end gap-2 border-t border-wc-border pt-4">
          <button
            type="button"
            onClick={onCerrar}
            disabled={guardando}
            className="rounded-md border border-wc-border bg-white px-4 py-2 text-sm font-semibold text-wc-text-muted transition hover:bg-wc-bg disabled:cursor-not-allowed disabled:opacity-60"
          >
            Cancelar
          </button>
          <button
            type="button"
            onClick={handleGuardar}
            disabled={guardando}
            className="rounded-md bg-wc-green px-5 py-2 text-sm font-semibold text-white transition hover:bg-wc-green-dark disabled:cursor-wait disabled:opacity-60"
          >
            {guardando ? 'Guardando…' : 'Crear color'}
          </button>
        </div>
      </div>
    </div>
  );
}
