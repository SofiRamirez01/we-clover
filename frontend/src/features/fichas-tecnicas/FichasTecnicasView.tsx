import { useEffect, useMemo, useRef, useState } from 'react';
import type { ChangeEvent } from 'react';
import AppHeader from '../../components/AppHeader';
import { useAuth } from '../../context/AuthContext';
import { listarPedidos } from '../../services/pedidoService';
import { subirImagenDisenoProducto } from '../../services/productoService';
import { urlArchivoSubido } from '../../utils/urlArchivos';
import { extraerMensajeError } from '../../utils/errores';
import EstadoBadge from './EstadoBadge';
import EstadoProductoControl from './EstadoProductoControl';
import ImagenPreviewModal from './ImagenPreviewModal';
import { ESTADOS_PEDIDO, ESTADO_PEDIDO_LABELS } from '../../types/pedido';
import type { EstadoPedido, PedidoResponse, ProductoResponse } from '../../types/pedido';

const TIPOS_IMAGEN_PERMITIDOS = ['image/jpeg', 'image/png'];

/** Roles habilitados para cargar/reemplazar la imagen de diseño (debe coincidir con ProductoService.java). */
const ROLES_CARGA_DISENIO = ['ROLE_ADMINISTRATIVO', 'ROLE_VENDEDOR', 'ROLE_DISENADOR'];

/** Roles habilitados para cambiar el estado de producción de una prenda (debe coincidir con ProductoService.java). */
const ROLES_CAMBIO_ESTADO = ['ROLE_ADMINISTRATIVO', 'ROLE_PLANTA'];

const SearchIcon = () => (
  <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth={2} strokeLinecap="round" strokeLinejoin="round">
    <circle cx="11" cy="11" r="7" />
    <path d="m21 21-4.3-4.3" />
  </svg>
);

const FilterIcon = () => (
  <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth={2} strokeLinecap="round" strokeLinejoin="round">
    <path d="M4 5h16M7 12h10M11 19h2" />
  </svg>
);

const NoCargadoIcon = () => (
  <svg width="28" height="28" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth={1.5} strokeLinecap="round" strokeLinejoin="round">
    <rect x="3" y="3" width="18" height="18" rx="2" />
    <circle cx="8.5" cy="8.5" r="1.5" />
    <path d="m21 15-5-5L5 21" />
  </svg>
);

interface Filtros {
  buscar: string;
  estado: EstadoPedido | '';
}

const filtrosIniciales: Filtros = { buscar: '', estado: '' };

interface ImagenProductoSlotProps {
  producto: ProductoResponse;
  puedeCargar: boolean;
  puedeCambiarEstado: boolean;
  onActualizado: (producto: ProductoResponse) => void;
}

function ImagenProductoSlot({ producto, puedeCargar, puedeCambiarEstado, onActualizado }: ImagenProductoSlotProps) {
  const [errorImagen, setErrorImagen] = useState(false);
  const [subiendo, setSubiendo] = useState(false);
  const [errorSubida, setErrorSubida] = useState<string | null>(null);
  const [previewAbierto, setPreviewAbierto] = useState(false);
  const inputRef = useRef<HTMLInputElement | null>(null);

  const tieneImagen = Boolean(producto.imagenDisenoUrl) && !errorImagen;
  const urlImagen = tieneImagen ? urlArchivoSubido(producto.imagenDisenoUrl as string) : null;

  async function handleFile(e: ChangeEvent<HTMLInputElement>) {
    const file = e.target.files?.[0];
    if (!file) return;

    if (!TIPOS_IMAGEN_PERMITIDOS.includes(file.type)) {
      setErrorSubida('Formato no válido: solo JPG o PNG');
      if (inputRef.current) inputRef.current.value = '';
      return;
    }

    setErrorSubida(null);
    setSubiendo(true);
    try {
      const actualizado = await subirImagenDisenoProducto(producto.id, file);
      setErrorImagen(false);
      onActualizado(actualizado);
    } catch (err) {
      setErrorSubida(extraerMensajeError(err, 'No se pudo subir la imagen.'));
    } finally {
      setSubiendo(false);
      if (inputRef.current) inputRef.current.value = '';
    }
  }

  return (
    <div className="flex items-center gap-3 rounded-lg border border-wc-border p-2">
      <button
        type="button"
        onClick={() => tieneImagen && setPreviewAbierto(true)}
        aria-label={tieneImagen ? 'Ver imagen en grande' : undefined}
        className={`aspect-square w-[38%] shrink-0 overflow-hidden rounded-md border-0 bg-wc-bg p-0 outline-none ${
          tieneImagen ? 'cursor-pointer' : 'cursor-default'
        }`}
      >
        {tieneImagen ? (
          <img
            src={urlImagen as string}
            alt={producto.tipoPrenda ?? 'Prenda'}
            onError={() => setErrorImagen(true)}
            className="h-full w-full object-cover transition hover:opacity-90"
          />
        ) : (
          <div className="flex h-full w-full flex-col items-center justify-center gap-0.5 text-wc-text-muted">
            <NoCargadoIcon />
            <span className="text-[10px] font-semibold leading-none">No cargado</span>
          </div>
        )}
      </button>

      {previewAbierto && urlImagen && (
        <ImagenPreviewModal
          src={urlImagen}
          alt={producto.tipoPrenda ?? 'Prenda'}
          onClose={() => setPreviewAbierto(false)}
        />
      )}

      <div className="min-w-0 flex-1">
        <p className="truncate text-xs font-semibold text-wc-text">{producto.tipoPrenda ?? 'Sin tipo'}</p>
        <p className="text-[11px] text-wc-text-muted">{producto.cantidadTotal} unidades</p>
        <div className="mt-1">
          <EstadoProductoControl
            producto={producto}
            puedeEditar={puedeCambiarEstado}
            onActualizado={onActualizado}
          />
        </div>
        {errorSubida && <p className="text-[10px] font-medium text-red-600">{errorSubida}</p>}
      </div>

      {puedeCargar && (
        <label
          className={`shrink-0 rounded-md px-2 py-1 text-[11px] font-semibold transition ${
            subiendo
              ? 'cursor-wait bg-wc-bg text-wc-text-muted'
              : 'cursor-pointer bg-wc-green/10 text-wc-green hover:bg-wc-green/20'
          }`}
        >
          {subiendo ? 'Subiendo…' : tieneImagen ? 'Reemplazar' : 'Cargar'}
          <input
            ref={inputRef}
            type="file"
            accept=".jpg,.jpeg,.png,image/jpeg,image/png"
            className="hidden"
            disabled={subiendo}
            onChange={handleFile}
          />
        </label>
      )}
    </div>
  );
}

export default function FichasTecnicasView() {
  const { usuario } = useAuth();
  const puedeCargar = Boolean(usuario && ROLES_CARGA_DISENIO.includes(usuario.rol));
  const puedeCambiarEstado = Boolean(usuario && ROLES_CAMBIO_ESTADO.includes(usuario.rol));

  const [pedidos, setPedidos] = useState<PedidoResponse[]>([]);
  const [estadoCarga, setEstadoCarga] = useState<'cargando' | 'listo' | 'error'>('cargando');
  const [filtros, setFiltros] = useState<Filtros>(filtrosIniciales);

  useEffect(() => {
    let cancelado = false;
    listarPedidos()
      .then((data) => {
        if (cancelado) return;
        setPedidos(data);
        setEstadoCarga('listo');
      })
      .catch(() => {
        if (!cancelado) setEstadoCarga('error');
      });
    return () => {
      cancelado = true;
    };
  }, []);

  function actualizarFiltro<K extends keyof Filtros>(campo: K, valor: Filtros[K]) {
    setFiltros((prev) => ({ ...prev, [campo]: valor }));
  }

  function actualizarProducto(idPedido: number, productoActualizado: ProductoResponse) {
    setPedidos((prev) =>
      prev.map((pedido) =>
        pedido.id === idPedido
          ? {
              ...pedido,
              productos: pedido.productos.map((producto) =>
                producto.id === productoActualizado.id ? productoActualizado : producto,
              ),
            }
          : pedido,
      ),
    );
  }

  const pedidosFiltrados = useMemo(() => {
    const buscar = filtros.buscar.trim().toLowerCase();
    return pedidos.filter((pedido) => {
      if (buscar) {
        const coincide =
          pedido.codigoInterno.toLowerCase().includes(buscar) ||
          pedido.nombreColegio.toLowerCase().includes(buscar);
        if (!coincide) return false;
      }
      if (filtros.estado && pedido.estadoActual !== filtros.estado) return false;
      return true;
    });
  }, [pedidos, filtros]);

  return (
    <div className="tw-scope px-8 pt-7 pb-12">
      <AppHeader title="Ficha Técnica" />

      <div className="flex flex-col gap-6">
        <div className="flex flex-wrap items-center gap-3">
          <div className="relative flex-1 min-w-[12rem]">
            <span className="pointer-events-none absolute left-3 top-1/2 -translate-y-1/2 text-wc-text-muted">
              <SearchIcon />
            </span>
            <input
              value={filtros.buscar}
              onChange={(e) => actualizarFiltro('buscar', e.target.value)}
              placeholder="Buscar por ficha o colegio"
              className="w-full rounded-lg border border-wc-border bg-white py-2 pl-9 pr-3 text-sm text-wc-text outline-none transition focus:border-wc-green focus:ring-2 focus:ring-wc-green/20"
            />
          </div>

          <div className="relative">
            <span className="pointer-events-none absolute left-3 top-1/2 -translate-y-1/2 text-wc-text-muted">
              <FilterIcon />
            </span>
            <select
              value={filtros.estado}
              onChange={(e) => actualizarFiltro('estado', e.target.value as EstadoPedido | '')}
              className="rounded-lg border border-wc-border bg-white py-2 pl-8 pr-8 text-sm text-wc-text outline-none transition focus:border-wc-green focus:ring-2 focus:ring-wc-green/20"
            >
              <option value="">Estado (todos)</option>
              {ESTADOS_PEDIDO.map((estado) => (
                <option key={estado} value={estado}>
                  {ESTADO_PEDIDO_LABELS[estado]}
                </option>
              ))}
            </select>
          </div>
        </div>

        {estadoCarga === 'cargando' && <p className="text-sm text-wc-text-muted">Cargando pedidos…</p>}
        {estadoCarga === 'error' && (
          <p className="text-sm text-wc-text-muted">No se pudo cargar el listado de pedidos.</p>
        )}
        {estadoCarga === 'listo' && pedidosFiltrados.length === 0 && (
          <p className="text-sm text-wc-text-muted">No hay pedidos que coincidan con los filtros.</p>
        )}

        {estadoCarga === 'listo' && pedidosFiltrados.length > 0 && (
          <div className="grid grid-cols-1 gap-5 sm:grid-cols-2 xl:grid-cols-3">
            {pedidosFiltrados.map((pedido) => (
              <div
                key={pedido.id}
                className="flex flex-col gap-3 rounded-xl border border-wc-border bg-white p-4 shadow-sm"
              >
                <div>
                  <div className="flex flex-wrap items-baseline gap-x-2">
                    <h3 className="text-sm font-bold text-wc-text">#{pedido.codigoInterno}</h3>
                    <span className="truncate text-xs text-wc-text-muted">
                      {pedido.nombreColegio}
                      {pedido.localidadColegio ? ` · ${pedido.localidadColegio}` : ''}
                    </span>
                  </div>
                  <div className="mt-1.5">
                    <EstadoBadge estado={pedido.estadoActual} />
                  </div>
                </div>

                <div className="flex flex-col gap-2">
                  {pedido.productos.map((producto) => (
                    <ImagenProductoSlot
                      key={producto.id}
                      producto={producto}
                      puedeCargar={puedeCargar}
                      puedeCambiarEstado={puedeCambiarEstado}
                      onActualizado={(actualizado) => actualizarProducto(pedido.id, actualizado)}
                    />
                  ))}
                </div>
              </div>
            ))}
          </div>
        )}
      </div>
    </div>
  );
}
