import { useEffect, useMemo, useState } from 'react';
import AppHeader from '../../components/AppHeader';
import ModalDetalleColor from './ModalDetalleColor';
import ModalNuevoColor from './ModalNuevoColor';
import { listarPaletaColores } from '../../services/paletaColoresService';
import { listarProveedores } from '../../services/proveedorService';
import { listarTiposTela } from '../../services/tipoTelaService';
import type { PaletaColorResponse } from '../../types/paletaColores';
import type { ProveedorResponse } from '../../types/proveedor';
import type { TipoTelaCatalogo } from '../../types/tipoTela';

/**
 * Orden pedido por el negocio para las pestañas más usadas; cualquier tipo de tela que no
 * esté acá (incluido uno nuevo que se agregue al catálogo a futuro) va después, en el orden
 * en que ya venga del backend — no hace falta tocar esta lista salvo que cambie la prioridad.
 */
const ORDEN_PESTANAS_PRIORITARIO = ['FRIZA', 'JERSEY', 'PIQUE', 'CIERRE'];

function ordenarPorPrioridad(tipos: TipoTelaCatalogo[]): TipoTelaCatalogo[] {
  return [...tipos].sort((a, b) => {
    const prioridadA = ORDEN_PESTANAS_PRIORITARIO.indexOf(a.codigo);
    const prioridadB = ORDEN_PESTANAS_PRIORITARIO.indexOf(b.codigo);
    if (prioridadA === -1 && prioridadB === -1) return 0;
    if (prioridadA === -1) return 1;
    if (prioridadB === -1) return -1;
    return prioridadA - prioridadB;
  });
}

/**
 * Pestañas dinámicas por TipoTela: se arman desde el catálogo (GET /api/tipos-tela), no de una
 * lista fija — si se da de alta un tipo de tela nuevo, aparece acá solo, sin tocar código.
 */
export default function CartaColoresView() {
  const [tiposTela, setTiposTela] = useState<TipoTelaCatalogo[]>([]);
  const [colores, setColores] = useState<PaletaColorResponse[]>([]);
  const [proveedores, setProveedores] = useState<ProveedorResponse[]>([]);
  const [tabActiva, setTabActiva] = useState<string | null>(null);
  const [estadoCarga, setEstadoCarga] = useState<'cargando' | 'listo' | 'error'>('cargando');

  const [colorNuevoAbierto, setColorNuevoAbierto] = useState(false);
  const [colorDetalle, setColorDetalle] = useState<PaletaColorResponse | null>(null);

  useEffect(() => {
    let cancelado = false;
    Promise.all([listarTiposTela(), listarPaletaColores(), listarProveedores(true)])
      .then(([tipos, coloresData, proveedoresData]) => {
        if (cancelado) return;
        const tiposOrdenados = ordenarPorPrioridad(tipos);
        setTiposTela(tiposOrdenados);
        setColores(coloresData);
        setProveedores(proveedoresData);
        setTabActiva((actual) => actual ?? tiposOrdenados[0]?.codigo ?? null);
        setEstadoCarga('listo');
      })
      .catch(() => {
        if (!cancelado) setEstadoCarga('error');
      });
    return () => {
      cancelado = true;
    };
  }, []);

  const tipoTelaActivo = useMemo(() => tiposTela.find((t) => t.codigo === tabActiva) ?? null, [tiposTela, tabActiva]);
  const coloresDeLaTab = useMemo(() => colores.filter((c) => c.tipoTela === tabActiva), [colores, tabActiva]);

  function handleColorCreado(color: PaletaColorResponse) {
    setColores((prev) => [...prev, color].sort((a, b) => a.nombre.localeCompare(b.nombre)));
    setColorNuevoAbierto(false);
  }

  function handleProveedorCreado(proveedor: ProveedorResponse) {
    setProveedores((prev) => [...prev, proveedor].sort((a, b) => a.nombre.localeCompare(b.nombre)));
  }

  return (
    <div className="tw-scope px-8 pt-7 pb-12">
      <AppHeader title="Carta de colores" />

      {estadoCarga === 'cargando' && <p className="text-sm text-wc-text-muted">Cargando…</p>}
      {estadoCarga === 'error' && <p className="text-sm text-wc-text-muted">No se pudo cargar la carta de colores.</p>}

      {estadoCarga === 'listo' && tiposTela.length === 0 && (
        <p className="text-sm text-wc-text-muted">No hay tipos de tela activos en el catálogo todavía.</p>
      )}

      {estadoCarga === 'listo' && tiposTela.length > 0 && (
        <div className="flex flex-col gap-5">
          <div className="flex flex-wrap items-center gap-1 border-b border-wc-border">
            {tiposTela.map((tipo) => (
              <button
                key={tipo.codigo}
                type="button"
                onClick={() => setTabActiva(tipo.codigo)}
                className={`-mb-px rounded-t-lg border border-b-0 px-4 py-2 text-sm font-semibold transition ${
                  tabActiva === tipo.codigo
                    ? 'border-wc-border bg-white text-wc-green'
                    : 'border-transparent text-wc-text-muted hover:text-wc-text'
                }`}
              >
                {tipo.nombre}
              </button>
            ))}
          </div>

          {tipoTelaActivo && (
            <div className="flex flex-col gap-4">
              <div className="flex items-center justify-between">
                <p className="text-xs text-wc-text-muted">
                  {coloresDeLaTab.length} color{coloresDeLaTab.length === 1 ? '' : 'es'} · se compra por{' '}
                  {tipoTelaActivo.esPorPeso ? 'kilogramo' : 'unidad'}
                </p>
                <button
                  type="button"
                  onClick={() => setColorNuevoAbierto(true)}
                  className="rounded-lg bg-wc-green/10 px-4 py-2 text-sm font-semibold text-wc-green transition hover:bg-wc-green/20"
                >
                  + Nuevo color
                </button>
              </div>

              {coloresDeLaTab.length === 0 ? (
                <p className="text-sm text-wc-text-muted">Todavía no hay colores cargados para {tipoTelaActivo.nombre}.</p>
              ) : (
                <div className="grid grid-cols-2 gap-4 sm:grid-cols-3 lg:grid-cols-4 xl:grid-cols-6">
                  {coloresDeLaTab.map((color) => (
                    <button
                      key={color.id}
                      type="button"
                      onClick={() => setColorDetalle(color)}
                      className="flex flex-col items-center gap-2 rounded-xl border border-wc-border bg-white p-3 text-center shadow-sm transition hover:border-wc-green"
                    >
                      <span className="h-10 w-10 rounded-full border border-wc-border" style={{ backgroundColor: color.hex }} />
                      <span className="text-xs font-semibold text-wc-text">{color.nombre}</span>
                    </button>
                  ))}
                </div>
              )}
            </div>
          )}
        </div>
      )}

      {colorNuevoAbierto && tipoTelaActivo && (
        <ModalNuevoColor
          tipoTela={tipoTelaActivo}
          proveedores={proveedores}
          onCreado={handleColorCreado}
          onProveedorCreado={handleProveedorCreado}
          onCerrar={() => setColorNuevoAbierto(false)}
        />
      )}

      {colorDetalle && tipoTelaActivo && (
        <ModalDetalleColor
          color={colorDetalle}
          tipoTela={tipoTelaActivo}
          proveedores={proveedores}
          onProveedorCreado={handleProveedorCreado}
          onCerrar={() => setColorDetalle(null)}
        />
      )}
    </div>
  );
}
