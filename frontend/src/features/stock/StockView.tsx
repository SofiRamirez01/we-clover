import { useEffect, useMemo, useState } from 'react';
import AppHeader from '../../components/AppHeader';
import ModalConfirmacion from '../../components/ModalConfirmacion';
import FilaStockGuardadaRow from './FilaStockGuardadaRow';
import type { FilaExistenteGuardar, FilaStockGuardada } from './FilaStockGuardadaRow';
import FilaStockNuevaRow from './FilaStockNuevaRow';
import type { NuevaFilaGuardar } from './FilaStockNuevaRow';
import { listarPaletaColores } from '../../services/paletaColoresService';
import { listarProveedores } from '../../services/proveedorService';
import { eliminarFilaStock, guardarCambiosStock, listarStock } from '../../services/stockService';
import { listarTiposTela } from '../../services/tipoTelaService';
import { extraerMensajeError } from '../../utils/errores';
import type { PaletaColorResponse } from '../../types/paletaColores';
import type { ProveedorResponse } from '../../types/proveedor';
import type { StockResponse } from '../../types/stock';
import type { TipoTelaCatalogo } from '../../types/tipoTela';

/** Mismo orden pedido por el negocio que usa Carta de colores para las pestañas más usadas. */
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

function claveFila(f: { idPaletaColor: number; idProveedor: number | null }): string {
  return `${f.idPaletaColor}::${f.idProveedor ?? 'sin-proveedor'}`;
}

function aFilaGuardada(s: StockResponse): FilaStockGuardada {
  return {
    clave: claveFila(s),
    id: s.id,
    idPaletaColor: s.idPaletaColor,
    nombreColor: s.nombreColor,
    hexColor: s.hexColor,
    codigoTipoTela: s.codigoTipoTela,
    idProveedor: s.idProveedor,
    nombreProveedor: s.nombreProveedor,
    cantidad: s.cantidad,
    unidadMedida: s.unidadMedida,
    fechaUltimaActualizacion: s.fechaUltimaActualizacion,
    nombreActualizadoPor: s.nombreActualizadoPor,
  };
}

let contadorFilaNueva = 0;

export default function StockView() {
  const [tiposTela, setTiposTela] = useState<TipoTelaCatalogo[]>([]);
  const [coloresActivos, setColoresActivos] = useState<PaletaColorResponse[]>([]);
  const [proveedoresActivos, setProveedoresActivos] = useState<ProveedorResponse[]>([]);
  const [filas, setFilas] = useState<FilaStockGuardada[]>([]);
  const [tabActiva, setTabActiva] = useState<string | null>(null);
  const [estadoCarga, setEstadoCarga] = useState<'cargando' | 'listo' | 'error'>('cargando');

  /** Claves temporales (`nueva-N`) de filas todavía sin guardar, por pestaña de tipo de tela. */
  const [clavesFilasNuevas, setClavesFilasNuevas] = useState<string[]>([]);

  const [filaAEliminar, setFilaAEliminar] = useState<FilaStockGuardada | null>(null);
  const [eliminando, setEliminando] = useState(false);
  const [errorEliminar, setErrorEliminar] = useState<string | null>(null);

  useEffect(() => {
    let cancelado = false;
    Promise.all([listarStock(), listarPaletaColores(), listarProveedores(true), listarTiposTela()])
      .then(([stock, colores, proveedores, tipos]) => {
        if (cancelado) return;
        const tiposOrdenados = ordenarPorPrioridad(tipos);
        setFilas(stock.map(aFilaGuardada));
        setColoresActivos(colores);
        setProveedoresActivos(proveedores);
        setTiposTela(tiposOrdenados);
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
  const filasDeLaTab = useMemo(() => filas.filter((f) => f.codigoTipoTela === tabActiva), [filas, tabActiva]);
  const coloresDeLaTab = useMemo(() => coloresActivos.filter((c) => c.tipoTela === tabActiva), [coloresActivos, tabActiva]);
  const clavesExistentes = useMemo(() => new Set(filas.map((f) => f.clave)), [filas]);

  function handleCambiarTab(codigo: string) {
    setTabActiva(codigo);
    setClavesFilasNuevas([]);
  }

  function agregarFilaNueva() {
    contadorFilaNueva += 1;
    setClavesFilasNuevas((prev) => [...prev, `nueva-${contadorFilaNueva}`]);
  }

  function descartarFilaNueva(clave: string) {
    setClavesFilasNuevas((prev) => prev.filter((c) => c !== clave));
  }

  async function handleGuardarFilaNueva(claveTemporal: string, datos: NuevaFilaGuardar) {
    const [guardada] = await guardarCambiosStock({ items: [datos] });
    setFilas((prev) => [...prev, aFilaGuardada(guardada)]);
    setClavesFilasNuevas((prev) => prev.filter((c) => c !== claveTemporal));
  }

  /** Si solo cambió la cantidad, es un upsert simple sobre la misma fila. Si cambió color y/o
   *  proveedor, la identidad de la fila cambió (Stock se identifica por articulo+proveedor en
   *  el backend, no tiene "renombrar") — hay que crear la fila con la nueva identidad y recién
   *  después borrar la vieja, en ese orden, para no perder datos si la creación falla. */
  async function handleGuardarFilaExistente(filaOriginal: FilaStockGuardada, nuevo: FilaExistenteGuardar) {
    const claveNueva = claveFila(nuevo);
    const cambioIdentidad = claveNueva !== filaOriginal.clave;

    const [guardada] = await guardarCambiosStock({ items: [nuevo] });
    const actualizada = aFilaGuardada(guardada);

    if (cambioIdentidad) {
      await eliminarFilaStock(filaOriginal.id);
      setFilas((prev) => [...prev.filter((f) => f.id !== filaOriginal.id), actualizada]);
    } else {
      setFilas((prev) => prev.map((f) => (f.id === filaOriginal.id ? actualizada : f)));
    }
  }

  function pedirEliminar(fila: FilaStockGuardada) {
    setErrorEliminar(null);
    setFilaAEliminar(fila);
  }

  async function confirmarEliminar() {
    if (!filaAEliminar) return;
    setEliminando(true);
    setErrorEliminar(null);
    try {
      await eliminarFilaStock(filaAEliminar.id);
      setFilas((prev) => prev.filter((f) => f.clave !== filaAEliminar.clave));
      setFilaAEliminar(null);
    } catch (err) {
      setErrorEliminar(extraerMensajeError(err, 'No se pudo eliminar la fila.'));
    } finally {
      setEliminando(false);
    }
  }

  return (
    <div className="tw-scope px-8 pt-7 pb-12">
      <AppHeader title="Stock" />

      {estadoCarga === 'cargando' && <p className="text-sm text-wc-text-muted">Cargando…</p>}
      {estadoCarga === 'error' && <p className="text-sm text-wc-text-muted">No se pudo cargar el stock.</p>}

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
                onClick={() => handleCambiarTab(tipo.codigo)}
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
            <div className="flex flex-col gap-3">
              <div className="flex items-center justify-between">
                <p className="text-xs text-wc-text-muted">
                  {filasDeLaTab.length} fila{filasDeLaTab.length === 1 ? '' : 's'} de stock · se compra por{' '}
                  {tipoTelaActivo.esPorPeso ? 'kilogramo' : 'unidad'}
                </p>
              </div>

              {errorEliminar && <p className="text-xs font-medium text-red-600">{errorEliminar}</p>}

              <div className="overflow-x-auto rounded-lg border border-wc-border bg-white">
                <table className="w-full text-left text-sm">
                  <thead>
                    <tr className="border-b border-wc-border text-xs text-wc-text-muted">
                      <th className="px-3 py-2 font-semibold">Color</th>
                      <th className="px-3 py-2 font-semibold">Proveedor</th>
                      <th className="px-3 py-2 font-semibold">Cantidad</th>
                      <th className="px-3 py-2 font-semibold">Última actualización</th>
                      <th className="px-3 py-2 font-semibold">Actualizado por</th>
                      <th className="px-3 py-2 font-semibold" />
                    </tr>
                  </thead>
                  <tbody>
                    {filasDeLaTab.length === 0 && clavesFilasNuevas.length === 0 && (
                      <tr>
                        <td colSpan={6} className="px-3 py-4 text-center text-sm text-wc-text-muted">
                          Todavía no hay stock cargado para {tipoTelaActivo.nombre}.
                        </td>
                      </tr>
                    )}
                    {filasDeLaTab.map((fila) => (
                      <FilaStockGuardadaRow
                        key={fila.clave}
                        fila={fila}
                        coloresDisponibles={coloresDeLaTab}
                        proveedoresActivos={proveedoresActivos}
                        clavesExistentes={clavesExistentes}
                        onGuardar={handleGuardarFilaExistente}
                        onPedirEliminar={pedirEliminar}
                      />
                    ))}
                    {clavesFilasNuevas.map((clave) => (
                      <FilaStockNuevaRow
                        key={clave}
                        coloresDisponibles={coloresDeLaTab}
                        proveedoresActivos={proveedoresActivos}
                        unidadMedida={tipoTelaActivo.esPorPeso ? 'KG' : 'UNIDAD'}
                        clavesExistentes={clavesExistentes}
                        onGuardar={(datos) => handleGuardarFilaNueva(clave, datos)}
                        onDescartar={() => descartarFilaNueva(clave)}
                      />
                    ))}
                    <tr>
                      <td colSpan={6} className="px-3 py-2">
                        <button
                          type="button"
                          onClick={agregarFilaNueva}
                          className="text-xs font-semibold text-wc-green underline"
                        >
                          + Agregar fila
                        </button>
                      </td>
                    </tr>
                  </tbody>
                </table>
              </div>
            </div>
          )}
        </div>
      )}

      {filaAEliminar && (
        <ModalConfirmacion
          titulo="¿Eliminar fila de stock?"
          mensaje={`Se va a eliminar la fila de "${filaAEliminar.nombreColor}" (${filaAEliminar.nombreProveedor ?? 'sin proveedor'}). Esta acción no se puede deshacer.`}
          onCerrar={() => setFilaAEliminar(null)}
          acciones={[
            { label: eliminando ? 'Eliminando…' : 'Sí, eliminar', variante: 'peligro', onClick: confirmarEliminar },
            { label: 'Cancelar', variante: 'secundaria', onClick: () => setFilaAEliminar(null) },
          ]}
        />
      )}
    </div>
  );
}
