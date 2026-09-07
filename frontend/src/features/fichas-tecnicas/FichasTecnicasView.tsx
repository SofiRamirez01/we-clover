import { useEffect, useMemo, useState } from 'react';
import AppHeader from '../../components/AppHeader';
import { useAuth } from '../../context/AuthContext';
import { listarPedidos } from '../../services/pedidoService';
import { listarPaletaColores } from '../../services/paletaColoresService';
import FichaPedidoCard from './FichaPedidoCard';
import { ESTADOS_PEDIDO, ESTADO_PEDIDO_LABELS } from '../../types/pedido';
import type { EstadoPedido, PedidoResponse, ProductoResponse } from '../../types/pedido';
import { TIPOS_TELA_PRENDA, TIPO_TELA_LABELS } from '../../types/paletaColores';
import type { PaletaColorResponse, TipoTela } from '../../types/paletaColores';
import { telaEfectiva } from './telaUtils';

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
}

const filtrosIniciales: Filtros = { buscar: '', estado: '', nombreColor: '', tipoTela: '' };

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
              <FichaPedidoCard
                key={pedido.id}
                pedido={pedido}
                puedeCargar={puedeCargar}
                puedeCambiarEstado={puedeCambiarEstado}
                coloresCierre={coloresCierre}
                onActualizado={(actualizado) => actualizarProducto(pedido.id, actualizado)}
              />
            ))}
          </div>
        )}
      </div>
    </div>
  );
}
