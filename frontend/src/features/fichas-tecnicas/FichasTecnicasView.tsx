import { useEffect, useMemo, useState } from 'react';
import AppHeader from '../../components/AppHeader';
import { useAuth } from '../../context/AuthContext';
import { listarPedidos } from '../../services/pedidoService';
import { listarPaletaColores } from '../../services/paletaColoresService';
import { urlArchivoSubido } from '../../utils/urlArchivos';
import EstadoBadge from './EstadoBadge';
import EstadoProductoControl from './EstadoProductoControl';
import ImagenPreviewModal from './ImagenPreviewModal';
import ModalColoresGotero from './ModalColoresGotero';
import { ESTADOS_PEDIDO, ESTADO_PEDIDO_LABELS } from '../../types/pedido';
import type { EstadoPedido, PedidoResponse, ProductoResponse } from '../../types/pedido';
import { TIPOS_TELA_PRENDA, TIPO_TELA_LABELS } from '../../types/paletaColores';
import type { PaletaColorResponse, TipoTela } from '../../types/paletaColores';
import { TIPO_PRENDA_CAMPERA, telaEfectiva } from './telaUtils';

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

const PencilIcon = () => (
  <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth={2} strokeLinecap="round" strokeLinejoin="round">
    <path d="M11 4H4a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2v-7" />
    <path d="M18.5 2.5a2.121 2.121 0 0 1 3 3L12 15l-4 1 1-4Z" />
  </svg>
);

interface Filtros {
  buscar: string;
  estado: EstadoPedido | '';
  nombreColor: string;
  tipoTela: TipoTela | '';
}

const filtrosIniciales: Filtros = { buscar: '', estado: '', nombreColor: '', tipoTela: '' };

interface ImagenProductoSlotProps {
  producto: ProductoResponse;
  puedeCargar: boolean;
  puedeCambiarEstado: boolean;
  coloresCierre: PaletaColorResponse[];
  onActualizado: (producto: ProductoResponse) => void;
}

function ImagenProductoSlot({ producto, puedeCargar, puedeCambiarEstado, coloresCierre, onActualizado }: ImagenProductoSlotProps) {
  const [errorImagen, setErrorImagen] = useState(false);
  const [previewAbierto, setPreviewAbierto] = useState(false);
  const [modalAbierto, setModalAbierto] = useState(false);

  const tieneImagen = Boolean(producto.imagenDisenoUrl) && !errorImagen;
  const urlImagen = tieneImagen ? urlArchivoSubido(producto.imagenDisenoUrl as string) : null;
  const esCampera = producto.tipoPrenda === TIPO_PRENDA_CAMPERA;
  const telaActual = telaEfectiva(producto);
  const posiciones = producto.patronCorteColores ?? [];

  return (
    <div className="relative flex items-center gap-3 rounded-lg border border-wc-border p-2">
      {puedeCargar && (
        <button
          type="button"
          onClick={() => setModalAbierto(true)}
          aria-label="Editar ficha de colores"
          title="Editar ficha de colores"
          className="absolute right-1.5 top-1.5 z-10 flex h-6 w-6 items-center justify-center rounded-full border border-wc-border bg-white text-wc-text-muted shadow-sm transition hover:bg-wc-bg hover:text-wc-text"
        >
          <PencilIcon />
        </button>
      )}

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

      {modalAbierto && (
        <ModalColoresGotero
          producto={producto}
          coloresCierre={coloresCierre}
          onActualizado={onActualizado}
          onCerrar={() => setModalAbierto(false)}
        />
      )}

      <div className="min-w-0 flex-1">
        <p className="truncate text-xs font-semibold text-wc-text">{producto.tipoPrenda ?? 'Sin tipo'}</p>
        <p className="text-[11px] text-wc-text-muted">{producto.cantidadTotal} unidades</p>

        <div className="mt-1 flex flex-col gap-0.5 text-[11px] text-wc-text-muted">
          <span>
            Tela: <span className="font-medium text-wc-text">{telaActual ? TIPO_TELA_LABELS[telaActual] : 'Sin definir'}</span>
          </span>

          {esCampera && (
            <span className="flex items-center gap-1.5">
              Cierre: <span className="font-medium text-wc-text">{producto.nombreColorCierre ?? 'Sin definir'}</span>
              {producto.hexColorCierre && (
                <span
                  title={producto.hexColorCierre}
                  className="h-3 w-3 shrink-0 rounded-full border border-wc-border"
                  style={{ backgroundColor: producto.hexColorCierre }}
                />
              )}
            </span>
          )}

          {posiciones.length > 0 &&
            [...posiciones]
              .sort((a, b) => a.orden - b.orden)
              .map((posicion) => {
                const asignado = producto.colores.find((c) => c.idPatronCorteColor === posicion.id);
                return (
                  <span key={posicion.id} className="flex items-center gap-1.5">
                    Color {posicion.orden} ({posicion.gramos} g):{' '}
                    <span className="font-medium text-wc-text">{asignado?.nombreColor ?? 'Sin definir'}</span>
                    {asignado && (
                      <span
                        title={asignado.hexColor}
                        className="h-3 w-3 shrink-0 rounded-full border border-wc-border"
                        style={{ backgroundColor: asignado.hexColor }}
                      />
                    )}
                  </span>
                );
              })}
        </div>

        <div className="mt-1">
          <EstadoProductoControl
            producto={producto}
            puedeEditar={puedeCambiarEstado}
            onActualizado={onActualizado}
          />
        </div>
      </div>
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
  const [paletaColores, setPaletaColores] = useState<PaletaColorResponse[]>([]);
  const [coloresCierre, setColoresCierre] = useState<PaletaColorResponse[]>([]);

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

  useEffect(() => {
    let cancelado = false;
    listarPaletaColores()
      .then((data) => {
        if (!cancelado) setPaletaColores(data);
      })
      .catch(() => {
        /* si falla, el filtro de color queda vacío pero el resto de la pantalla funciona igual */
      });
    return () => {
      cancelado = true;
    };
  }, []);

  useEffect(() => {
    let cancelado = false;
    listarPaletaColores('CIERRE')
      .then((data) => {
        if (!cancelado) setColoresCierre(data);
      })
      .catch(() => {
        /* si falla, el selector de color de cierre queda vacío */
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

  const nombresColorFiltro = useMemo(
    () =>
      Array.from(new Set(paletaColores.filter((c) => c.tipoTela !== 'CIERRE').map((c) => c.nombre))).sort((a, b) =>
        a.localeCompare(b),
      ),
    [paletaColores],
  );

  const pedidosFiltrados = useMemo(() => {
    const buscar = filtros.buscar.trim().toLowerCase();
    return pedidos
      .filter((pedido) => {
        if (buscar) {
          const coincide =
            pedido.codigoInterno.toLowerCase().includes(buscar) ||
            pedido.nombreColegio.toLowerCase().includes(buscar);
          if (!coincide) return false;
        }
        if (filtros.estado && pedido.estadoActual !== filtros.estado) return false;
        if (
          filtros.nombreColor !== '' &&
          !pedido.productos.some((producto) =>
            producto.colores.some((color) => color.nombreColor === filtros.nombreColor),
          )
        ) {
          return false;
        }
        return true;
      })
      .map((pedido) => {
        if (!filtros.tipoTela) return pedido;
        return {
          ...pedido,
          productos: pedido.productos.filter((producto) => telaEfectiva(producto) === filtros.tipoTela),
        };
      })
      .filter((pedido) => pedido.productos.length > 0);
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

          <div className="relative">
            <select
              value={filtros.nombreColor}
              onChange={(e) => actualizarFiltro('nombreColor', e.target.value)}
              className="rounded-lg border border-wc-border bg-white py-2 pl-3 pr-8 text-sm text-wc-text outline-none transition focus:border-wc-green focus:ring-2 focus:ring-wc-green/20"
            >
              <option value="">Color (todos)</option>
              {nombresColorFiltro.map((nombre) => (
                <option key={nombre} value={nombre}>
                  {nombre}
                </option>
              ))}
            </select>
          </div>

          <div className="relative">
            <select
              value={filtros.tipoTela}
              onChange={(e) => actualizarFiltro('tipoTela', e.target.value as TipoTela | '')}
              className="rounded-lg border border-wc-border bg-white py-2 pl-3 pr-8 text-sm text-wc-text outline-none transition focus:border-wc-green focus:ring-2 focus:ring-wc-green/20"
            >
              <option value="">Tela (todas)</option>
              {TIPOS_TELA_PRENDA.map((tela) => (
                <option key={tela} value={tela}>
                  {TIPO_TELA_LABELS[tela]}
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
                      coloresCierre={coloresCierre}
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
