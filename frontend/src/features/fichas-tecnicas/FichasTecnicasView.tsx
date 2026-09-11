import { useEffect, useMemo, useState } from 'react';
import AppHeader from '../../components/AppHeader';
import { useAuth } from '../../context/AuthContext';
import { listarPedidos } from '../../services/pedidoService';
import { listarPaletaColores } from '../../services/paletaColoresService';
import { obtenerCargaTallesInterno } from '../../services/cargaTallesService';
import { listarTiposTela } from '../../services/tipoTelaService';
import FichaPedidoCard from './FichaPedidoCard';
import { ESTADOS_PEDIDO, ESTADO_PEDIDO_LABELS } from '../../types/pedido';
import type { EstadoPedido, PedidoResponse, ProductoResponse } from '../../types/pedido';
import { TIPOS_TELA_PRENDA } from '../../types/paletaColores';
import type { PaletaColorResponse, TipoTela } from '../../types/paletaColores';
import type { CargaTallesResponse, ProductoPedidoResumenResponse } from '../../types/cargaTalles';
import type { TipoTelaCatalogo } from '../../types/tipoTela';
import { estadoDiseno, nombreTela, telaEfectiva } from './telaUtils';

type FiltroCompletitud = '' | 'FALTA_DISENO' | 'FALTAN_TALLES' | 'LISTO';

const OPCIONES_COMPLETITUD: { value: FiltroCompletitud; label: string }[] = [
  { value: '', label: 'Producción (todos)' },
  { value: 'FALTA_DISENO', label: 'Falta diseño' },
  { value: 'FALTAN_TALLES', label: 'Faltan talles' },
  { value: 'LISTO', label: 'Listo para producción' },
];

/** true si a la prenda le faltan talles por cargar — solo aplica a prendas que trackean talle
 *  (tieneTalle); una Bandera, por ejemplo, nunca "falta" talles porque no le corresponde. */
function faltanTalles(resumen: ProductoPedidoResumenResponse | undefined): boolean {
  if (!resumen || !resumen.tieneTalle) return false;
  return resumen.cantidadCargada < resumen.cantidadTotal;
}

function productoCumpleCompletitud(
  producto: ProductoResponse,
  resumen: ProductoPedidoResumenResponse | undefined,
  filtro: FiltroCompletitud,
): boolean {
  if (!filtro) return true;
  const disenoCompleto = estadoDiseno(producto).completo;
  const tallesIncompletos = faltanTalles(resumen);
  if (filtro === 'FALTA_DISENO') return !disenoCompleto;
  if (filtro === 'FALTAN_TALLES') return tallesIncompletos;
  return disenoCompleto && !tallesIncompletos;
}

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

interface Filtros {
  buscar: string;
  estado: EstadoPedido | '';
  nombreColor: string;
  tipoTela: TipoTela | '';
  pagoDesde: string;
  pagoHasta: string;
  completitud: FiltroCompletitud;
}

/** Encabezados de columna de la lista de prendas — mismos anchos y breakpoint que
 *  FilaProducto en FichaPedidoCard.tsx, para que quede alineado como una tabla real. Todas las
 *  columnas aparecen juntas recién en xl (por debajo, Tela/Colores/Diseño/Talles no se ven en
 *  las filas, así que un encabezado suelto no aportaría nada). */
function EncabezadoColumnas() {
  return (
    <div className="hidden items-center gap-3 overflow-x-auto px-3 text-[11px] font-bold uppercase tracking-wide text-wc-text-muted xl:flex">
      <div className="w-14 shrink-0" />
      <div className="min-w-28 flex-1">Prenda</div>
      <div className="min-w-16 flex-1 text-center">Cant.</div>
      <div className="min-w-24 flex-1">Tela</div>
      <div className="min-w-56 flex-1 truncate">Colores</div>
      <div className="min-w-28 flex-1">Diseño</div>
      <div className="min-w-32 flex-1">Talles</div>
      <div className="min-w-36 flex-1">Estado</div>
    </div>
  );
}

const filtrosIniciales: Filtros = {
  buscar: '',
  estado: '',
  nombreColor: '',
  tipoTela: '',
  pagoDesde: '',
  pagoHasta: '',
  completitud: '',
};

export default function FichasTecnicasView() {
  const { usuario } = useAuth();
  const puedeCargar = Boolean(usuario && ROLES_CARGA_DISENIO.includes(usuario.rol));
  const puedeCambiarEstado = Boolean(usuario && ROLES_CAMBIO_ESTADO.includes(usuario.rol));

  const [pedidos, setPedidos] = useState<PedidoResponse[]>([]);
  const [estadoCarga, setEstadoCarga] = useState<'cargando' | 'listo' | 'error'>('cargando');
  const [filtros, setFiltros] = useState<Filtros>(filtrosIniciales);
  const [paletaColores, setPaletaColores] = useState<PaletaColorResponse[]>([]);
  const [coloresCierre, setColoresCierre] = useState<PaletaColorResponse[]>([]);
  const [tiposTela, setTiposTela] = useState<TipoTelaCatalogo[]>([]);
  /** Resumen de talles por pedido, solo para poder filtrar por "Faltan talles"/"Listo para
   *  producción" — se trae una sola vez acá, en paralelo, independiente del fetch propio que
   *  cada FichaPedidoCard hace para sus controles de cerrar/reabrir/copiar link (ver
   *  useCargaTallesFicha). Duplica el GET, pero evita tener que levantar ese estado hasta acá y
   *  pasarlo para abajo — con la cantidad de pedidos actual no es un problema de performance. */
  const [cargaTallesPorPedido, setCargaTallesPorPedido] = useState<Map<number, CargaTallesResponse>>(new Map());

  useEffect(() => {
    let cancelado = false;
    listarPedidos()
      .then(async (data) => {
        if (cancelado) return;
        setPedidos(data);
        setEstadoCarga('listo');
        const entradas = await Promise.all(
          data.map((pedido) =>
            obtenerCargaTallesInterno(pedido.id)
              .then((carga): [number, CargaTallesResponse] => [pedido.id, carga])
              .catch(() => null),
          ),
        );
        if (!cancelado) {
          setCargaTallesPorPedido(new Map(entradas.filter((e): e is [number, CargaTallesResponse] => e !== null)));
        }
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

  useEffect(() => {
    let cancelado = false;
    listarTiposTela()
      .then((data) => {
        if (!cancelado) setTiposTela(data);
      })
      .catch(() => {
        /* si falla, se muestra el código de la tela como fallback (ver nombreTela) */
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
    const pagoDesde = filtros.pagoDesde.trim() ? Number(filtros.pagoDesde) : null;
    const pagoHasta = filtros.pagoHasta.trim() ? Number(filtros.pagoHasta) : null;
    return pedidos
      .filter((pedido) => {
        if (buscar) {
          const coincide =
            pedido.codigoInterno.toLowerCase().includes(buscar) ||
            pedido.nombreColegio.toLowerCase().includes(buscar);
          if (!coincide) return false;
        }
        if (filtros.estado && pedido.estadoActual !== filtros.estado) return false;
        if (pagoDesde != null && !Number.isNaN(pagoDesde) && pedido.porcentajePagado < pagoDesde) return false;
        if (pagoHasta != null && !Number.isNaN(pagoHasta) && pedido.porcentajePagado > pagoHasta) return false;
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
        if (!filtros.tipoTela && !filtros.completitud) return pedido;
        const resumenPorProducto = new Map(
          cargaTallesPorPedido.get(pedido.id)?.productos.map((r) => [r.idProducto, r]) ?? [],
        );
        return {
          ...pedido,
          productos: pedido.productos
            .filter((producto) => !filtros.tipoTela || telaEfectiva(producto) === filtros.tipoTela)
            .filter((producto) => productoCumpleCompletitud(producto, resumenPorProducto.get(producto.id), filtros.completitud)),
        };
      })
      .filter((pedido) => pedido.productos.length > 0);
  }, [pedidos, filtros, cargaTallesPorPedido]);

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
                  {nombreTela(tela, tiposTela)}
                </option>
              ))}
            </select>
          </div>

          <div className="relative">
            <select
              value={filtros.completitud}
              onChange={(e) => actualizarFiltro('completitud', e.target.value as FiltroCompletitud)}
              className="rounded-lg border border-wc-border bg-white py-2 pl-3 pr-8 text-sm text-wc-text outline-none transition focus:border-wc-green focus:ring-2 focus:ring-wc-green/20"
            >
              {OPCIONES_COMPLETITUD.map((opcion) => (
                <option key={opcion.value} value={opcion.value}>
                  {opcion.label}
                </option>
              ))}
            </select>
          </div>

          <div className="flex items-center gap-1.5">
            <label className="text-xs font-semibold text-wc-text-muted" htmlFor="ficha-pago-desde">
              % Pago desde
            </label>
            <input
              id="ficha-pago-desde"
              type="number"
              min={0}
              max={100}
              value={filtros.pagoDesde}
              onChange={(e) => actualizarFiltro('pagoDesde', e.target.value)}
              placeholder="0"
              className="w-16 rounded-lg border border-wc-border bg-white px-2 py-1.5 text-sm text-wc-text outline-none transition focus:border-wc-green focus:ring-2 focus:ring-wc-green/20"
            />
            <label className="text-xs font-semibold text-wc-text-muted" htmlFor="ficha-pago-hasta">
              hasta
            </label>
            <input
              id="ficha-pago-hasta"
              type="number"
              min={0}
              max={100}
              value={filtros.pagoHasta}
              onChange={(e) => actualizarFiltro('pagoHasta', e.target.value)}
              placeholder="100"
              className="w-16 rounded-lg border border-wc-border bg-white px-2 py-1.5 text-sm text-wc-text outline-none transition focus:border-wc-green focus:ring-2 focus:ring-wc-green/20"
            />
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
          <div className="flex flex-col gap-3">
            <EncabezadoColumnas />
            <div className="flex flex-col gap-4">
              {pedidosFiltrados.map((pedido) => (
                <FichaPedidoCard
                  key={pedido.id}
                  pedido={pedido}
                  puedeCargar={puedeCargar}
                  puedeCambiarEstado={puedeCambiarEstado}
                  coloresCierre={coloresCierre}
                  tiposTela={tiposTela}
                  onActualizado={(actualizado) => actualizarProducto(pedido.id, actualizado)}
                />
              ))}
            </div>
          </div>
        )}
      </div>
    </div>
  );
}
