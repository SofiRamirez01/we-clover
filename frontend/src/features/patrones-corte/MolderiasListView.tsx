import { useEffect, useMemo, useState } from 'react';
import { listarPatronesCorte, urlImagenPatronCorte } from '../../services/patronCorteService';
import { listarTiposPrenda } from '../../services/pedidoService';
import VistaPortfolioMenu from './VistaPortfolioMenu';
import type { TamanoVista } from './VistaPortfolioMenu';
import type { PatronCorteResponse } from '../../types/patronCorte';
import type { TipoPrendaOption } from '../../types/pedido';

const CLAVE_TAMANO_VISTA = 'wc-molderias-tamano-vista';

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

const ImagePlaceholderIcon = ({ size = 40 }: { size?: number }) => (
  <svg width={size} height={size} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth={1.5} strokeLinecap="round" strokeLinejoin="round" className="text-wc-border">
    <rect x="3" y="3" width="18" height="18" rx="2" />
    <circle cx="8.5" cy="8.5" r="1.5" />
    <path d="m21 15-5-5L5 21" />
  </svg>
);

interface Filtros {
  buscar: string;
  idTipoPrenda: string;
  cantidadColores: string;
}

const filtrosIniciales: Filtros = { buscar: '', idTipoPrenda: '', cantidadColores: '' };

const CONFIG_TAMANO: Record<Exclude<TamanoVista, 'lista'>, { grid: string; gap: string; padding: string; titulo: string; detalle: 'completo' | 'compacto' }> = {
  'muy-grande': { grid: 'grid-cols-1 sm:grid-cols-2 lg:grid-cols-3', gap: 'gap-5', padding: 'p-4', titulo: 'text-base', detalle: 'completo' },
  grande: { grid: 'grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4', gap: 'gap-5', padding: 'p-4', titulo: 'text-sm', detalle: 'completo' },
  mediano: { grid: 'grid-cols-2 sm:grid-cols-3 lg:grid-cols-4 xl:grid-cols-6', gap: 'gap-4', padding: 'p-3', titulo: 'text-xs', detalle: 'completo' },
  pequeno: { grid: 'grid-cols-3 sm:grid-cols-4 lg:grid-cols-6 xl:grid-cols-8', gap: 'gap-3', padding: 'p-2', titulo: 'text-[11px]', detalle: 'compacto' },
};

/**
 * Separación fija del portfolio pedida por el negocio: primero Buzo/Campera (comparten
 * moldería), después Chomba/Remera (ídem), y una tercera sección "Otros diseños" para
 * cualquier tipo de prenda que se agregue al catálogo más adelante (hoy sería Bandera). Si
 * una moldería aplicara a tipos de más de una familia (no pasa hoy en los datos reales, pero
 * el modelo Many-to-Many lo permitiría), se prioriza en el orden de esta lista para no
 * duplicarla en dos secciones.
 */
const FAMILIAS_MOLDERIA: { clave: string; titulo: string; tiposPrenda: string[] }[] = [
  { clave: 'buzo-campera', titulo: 'Buzos y Camperas', tiposPrenda: ['Buzo', 'Campera'] },
  { clave: 'chomba-remera', titulo: 'Chombas y Remeras', tiposPrenda: ['Chomba', 'Remera'] },
];

interface GrupoMolderias {
  clave: string;
  titulo: string;
  patrones: PatronCorteResponse[];
}

/** Una sección solo aparece si tiene al menos una moldería después de aplicar los filtros. */
function agruparPorFamilia(patrones: PatronCorteResponse[]): GrupoMolderias[] {
  const yaAgrupados = new Set<number>();
  const grupos: GrupoMolderias[] = [];

  for (const familia of FAMILIAS_MOLDERIA) {
    const delGrupo = patrones.filter((p) => p.tiposPrenda.some((t) => familia.tiposPrenda.includes(t.nombre)));
    delGrupo.forEach((p) => yaAgrupados.add(p.id));
    if (delGrupo.length > 0) {
      grupos.push({ clave: familia.clave, titulo: familia.titulo, patrones: delGrupo });
    }
  }

  const otros = patrones.filter((p) => !yaAgrupados.has(p.id));
  if (otros.length > 0) {
    grupos.push({ clave: 'otros', titulo: 'Otros diseños', patrones: otros });
  }

  return grupos;
}

function SeccionEncabezado({ titulo, cantidad }: { titulo: string; cantidad: number }) {
  return (
    <div className="flex items-center gap-2.5">
      <span className="h-4 w-1 rounded-full bg-wc-green" aria-hidden="true" />
      <h2 className="text-sm font-bold text-wc-text">{titulo}</h2>
      <span className="text-xs text-wc-text-muted">({cantidad})</span>
      <div className="h-px flex-1 bg-wc-border" />
    </div>
  );
}

function formatearPesos(patron: PatronCorteResponse): string {
  return patron.colores.map((c) => `${c.gramos} g`).join(', ');
}

function formatearTipos(patron: PatronCorteResponse): string {
  return patron.tiposPrenda.map((t) => t.nombre).join(' / ');
}

function leerTamanoGuardado(): TamanoVista {
  try {
    const guardado = localStorage.getItem(CLAVE_TAMANO_VISTA);
    if (guardado === 'muy-grande' || guardado === 'grande' || guardado === 'mediano' || guardado === 'pequeno' || guardado === 'lista') {
      return guardado;
    }
  } catch {
    // localStorage puede no estar disponible (modo privado, etc.); usamos el default.
  }
  return 'grande';
}

function PatronImagen({ patron, size }: { patron: PatronCorteResponse; size?: number }) {
  const [error, setError] = useState(false);

  if (error) {
    return (
      <div className="flex aspect-square w-full items-center justify-center rounded-lg bg-wc-bg">
        <ImagePlaceholderIcon size={size} />
      </div>
    );
  }

  return (
    <img
      src={urlImagenPatronCorte(patron.imagenUrl)}
      alt={patron.nombre}
      onError={() => setError(true)}
      className="aspect-square w-full rounded-lg bg-wc-bg object-cover"
    />
  );
}

interface MolderiasListViewProps {
  onNueva: () => void;
  mensajeExito?: string | null;
}

export default function MolderiasListView({ onNueva, mensajeExito }: MolderiasListViewProps) {
  const [patrones, setPatrones] = useState<PatronCorteResponse[]>([]);
  const [tiposPrenda, setTiposPrenda] = useState<TipoPrendaOption[]>([]);
  const [estadoCarga, setEstadoCarga] = useState<'cargando' | 'listo' | 'error'>('cargando');
  const [filtros, setFiltros] = useState<Filtros>(filtrosIniciales);
  const [tamanoVista, setTamanoVista] = useState<TamanoVista>(leerTamanoGuardado);

  useEffect(() => {
    let cancelado = false;
    listarPatronesCorte()
      .then((data) => {
        if (cancelado) return;
        setPatrones(data);
        setEstadoCarga('listo');
      })
      .catch(() => {
        if (!cancelado) setEstadoCarga('error');
      });
    listarTiposPrenda()
      .then((data) => {
        if (!cancelado) setTiposPrenda(data);
      })
      .catch(() => {});
    return () => {
      cancelado = true;
    };
  }, []);

  function actualizarFiltro<K extends keyof Filtros>(campo: K, valor: Filtros[K]) {
    setFiltros((prev) => ({ ...prev, [campo]: valor }));
  }

  function cambiarTamanoVista(valor: TamanoVista) {
    setTamanoVista(valor);
    try {
      localStorage.setItem(CLAVE_TAMANO_VISTA, valor);
    } catch {
      // Si no se puede persistir, la vista simplemente no sobrevive un refresh.
    }
  }

  const patronesFiltrados = useMemo(() => {
    const buscar = filtros.buscar.trim().toLowerCase();
    return patrones
      .filter((patron) => {
        if (buscar) {
          const coincide =
            patron.nombre.toLowerCase().includes(buscar) || String(patron.numeroInterno).includes(buscar);
          if (!coincide) return false;
        }
        if (filtros.idTipoPrenda && !patron.tiposPrenda.some((t) => String(t.id) === filtros.idTipoPrenda)) return false;
        if (filtros.cantidadColores && String(patron.cantidadColores) !== filtros.cantidadColores) return false;
        return true;
      })
      .sort((a, b) => a.numeroInterno - b.numeroInterno);
  }, [patrones, filtros]);

  const grupos = useMemo(() => agruparPorFamilia(patronesFiltrados), [patronesFiltrados]);

  const config = tamanoVista === 'lista' ? null : CONFIG_TAMANO[tamanoVista];

  return (
    <div className="flex flex-col gap-6">
      {mensajeExito && (
        <div className="rounded-lg border border-wc-green/30 bg-wc-green/10 px-4 py-3 text-sm font-medium text-wc-green" role="status">
          {mensajeExito}
        </div>
      )}

      <div className="flex flex-wrap items-center gap-3">
        <div className="relative flex-1 min-w-[12rem]">
          <span className="pointer-events-none absolute left-3 top-1/2 -translate-y-1/2 text-wc-text-muted">
            <SearchIcon />
          </span>
          <input
            value={filtros.buscar}
            onChange={(e) => actualizarFiltro('buscar', e.target.value)}
            placeholder="Buscar"
            className="w-full rounded-lg border border-wc-border bg-white py-2 pl-9 pr-3 text-sm text-wc-text outline-none transition focus:border-wc-green focus:ring-2 focus:ring-wc-green/20"
          />
        </div>

        <div className="relative">
          <span className="pointer-events-none absolute left-3 top-1/2 -translate-y-1/2 text-wc-text-muted">
            <FilterIcon />
          </span>
          <select
            value={filtros.idTipoPrenda}
            onChange={(e) => actualizarFiltro('idTipoPrenda', e.target.value)}
            className="rounded-lg border border-wc-border bg-white py-2 pl-8 pr-8 text-sm text-wc-text outline-none transition focus:border-wc-green focus:ring-2 focus:ring-wc-green/20"
          >
            <option value="">Tipo de Prenda</option>
            {tiposPrenda.map((tipo) => (
              <option key={tipo.id} value={tipo.id}>
                {tipo.nombre}
              </option>
            ))}
          </select>
        </div>

        <div className="relative">
          <span className="pointer-events-none absolute left-3 top-1/2 -translate-y-1/2 text-wc-text-muted">
            <FilterIcon />
          </span>
          <select
            value={filtros.cantidadColores}
            onChange={(e) => actualizarFiltro('cantidadColores', e.target.value)}
            className="rounded-lg border border-wc-border bg-white py-2 pl-8 pr-8 text-sm text-wc-text outline-none transition focus:border-wc-green focus:ring-2 focus:ring-wc-green/20"
          >
            <option value="">Cantidad de colores</option>
            {[1, 2, 3, 4, 5].map((n) => (
              <option key={n} value={n}>
                {n}
              </option>
            ))}
          </select>
        </div>

        <VistaPortfolioMenu valor={tamanoVista} onCambiar={cambiarTamanoVista} />

        <button
          type="button"
          onClick={onNueva}
          className="rounded-lg bg-wc-green/10 px-4 py-2 text-sm font-semibold text-wc-green transition hover:bg-wc-green/20"
        >
          + Nueva Moldería
        </button>
      </div>

      {estadoCarga === 'cargando' && <p className="text-sm text-wc-text-muted">Cargando molderías…</p>}
      {estadoCarga === 'error' && (
        <p className="text-sm text-wc-text-muted">No se pudo cargar el listado de molderías.</p>
      )}
      {estadoCarga === 'listo' && patronesFiltrados.length === 0 && (
        <p className="text-sm text-wc-text-muted">No hay molderías que coincidan con los filtros.</p>
      )}

      {estadoCarga === 'listo' && patronesFiltrados.length > 0 && tamanoVista === 'lista' && (
        <div className="flex flex-col gap-6">
          {grupos.map((grupo) => (
            <div key={grupo.clave} className="flex flex-col gap-3">
              <SeccionEncabezado titulo={grupo.titulo} cantidad={grupo.patrones.length} />
              <div className="flex flex-col gap-2">
                {grupo.patrones.map((patron) => (
                  <div
                    key={patron.id}
                    className="flex items-center gap-4 rounded-lg border border-wc-border bg-white px-4 py-2.5 shadow-sm"
                  >
                    <div className="h-12 w-12 flex-shrink-0">
                      <PatronImagen patron={patron} size={22} />
                    </div>
                    <div className="min-w-0 flex-1">
                      <p className="truncate text-sm font-bold text-wc-text">
                        #{patron.numeroInterno} {patron.nombre}
                      </p>
                      <p className="text-xs text-wc-text-muted sm:hidden">
                        {formatearTipos(patron)} · {patron.cantidadColores} color{patron.cantidadColores > 1 ? 'es' : ''} ·{' '}
                        {formatearPesos(patron)}
                      </p>
                    </div>
                    <div className="hidden w-28 shrink-0 text-xs text-wc-text-muted sm:block">{formatearTipos(patron)}</div>
                    <div className="hidden w-16 shrink-0 text-center text-xs text-wc-text-muted sm:block">
                      {patron.cantidadColores}
                    </div>
                    <div className="hidden w-40 shrink-0 text-right text-xs text-wc-text-muted sm:block">
                      {formatearPesos(patron)}
                    </div>
                  </div>
                ))}
              </div>
            </div>
          ))}
        </div>
      )}

      {estadoCarga === 'listo' && patronesFiltrados.length > 0 && config && (
        <div className="flex flex-col gap-6">
          {grupos.map((grupo) => (
            <div key={grupo.clave} className="flex flex-col gap-3">
              <SeccionEncabezado titulo={grupo.titulo} cantidad={grupo.patrones.length} />
              <div className={`grid ${config.grid} ${config.gap}`}>
                {grupo.patrones.map((patron) => (
                  <div
                    key={patron.id}
                    className={`flex flex-col gap-3 rounded-xl border border-wc-border bg-white ${config.padding} shadow-sm`}
                  >
                    <PatronImagen patron={patron} />
                    <div>
                      <h3 className={`${config.titulo} font-bold text-wc-text`}>
                        #{patron.numeroInterno} {patron.nombre}
                      </h3>
                      {config.detalle === 'completo' ? (
                        <dl className="mt-1.5 flex flex-col gap-1 text-xs text-wc-text-muted">
                          <div className="flex items-baseline justify-between gap-2">
                            <dt className="font-medium text-wc-text">Tipo</dt>
                            <dd className="text-right">{formatearTipos(patron)}</dd>
                          </div>
                          <div className="flex items-baseline justify-between gap-2">
                            <dt className="font-medium text-wc-text">Cantidad de colores</dt>
                            <dd className="text-right">{patron.cantidadColores}</dd>
                          </div>
                          <div className="flex items-baseline justify-between gap-2">
                            <dt className="font-medium text-wc-text">Peso por color</dt>
                            <dd className="text-right">{formatearPesos(patron)}</dd>
                          </div>
                        </dl>
                      ) : (
                        <p className="mt-1 text-[10px] leading-snug text-wc-text-muted">
                          {formatearTipos(patron)} · {patron.cantidadColores} col. · {formatearPesos(patron)}
                        </p>
                      )}
                    </div>
                  </div>
                ))}
              </div>
            </div>
          ))}
        </div>
      )}
    </div>
  );
}
