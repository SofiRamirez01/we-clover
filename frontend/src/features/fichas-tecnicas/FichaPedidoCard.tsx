import { useState } from 'react';
import CargaTallesFichaHeader from '../carga-talles/CargaTallesFichaHeader';
import { useCargaTallesFicha } from '../carga-talles/useCargaTallesFicha';
import { urlArchivoSubido } from '../../utils/urlArchivos';
import EstadoBadge from './EstadoBadge';
import EstadoProductoControl from './EstadoProductoControl';
import ImagenPreviewModal from './ImagenPreviewModal';
import ModalColoresGotero from './ModalColoresGotero';
import type { PedidoResponse, ProductoResponse } from '../../types/pedido';
import type { PaletaColorResponse } from '../../types/paletaColores';
import { TIPO_TELA_LABELS } from '../../types/paletaColores';
import { TIPO_PRENDA_CAMPERA, telaEfectiva } from './telaUtils';

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

interface ResumenTalles {
  cantidadCargada: number;
  cantidadTotal: number;
}

interface ImagenProductoSlotProps {
  producto: ProductoResponse;
  puedeCargar: boolean;
  puedeCambiarEstado: boolean;
  coloresCierre: PaletaColorResponse[];
  resumenTalles: ResumenTalles | null;
  onActualizado: (producto: ProductoResponse) => void;
}

function ImagenProductoSlot({ producto, puedeCargar, puedeCambiarEstado, coloresCierre, resumenTalles, onActualizado }: ImagenProductoSlotProps) {
  const [errorImagen, setErrorImagen] = useState(false);
  const [previewAbierto, setPreviewAbierto] = useState(false);
  const [modalAbierto, setModalAbierto] = useState(false);

  const tieneImagen = Boolean(producto.imagenDisenoUrl) && !errorImagen;
  const urlImagen = tieneImagen ? urlArchivoSubido(producto.imagenDisenoUrl as string) : null;
  const esCampera = producto.tipoPrenda === TIPO_PRENDA_CAMPERA;
  const telaActual = telaEfectiva(producto);
  const posiciones = producto.patronCorteColores ?? [];
  const talleCompleto = resumenTalles != null && resumenTalles.cantidadCargada >= resumenTalles.cantidadTotal;

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

        {resumenTalles && (
          <p className="text-[11px] text-wc-text-muted">
            Talles:{' '}
            <span className={`font-medium ${talleCompleto ? 'text-wc-green' : 'text-amber-700'}`}>
              {resumenTalles.cantidadCargada}/{resumenTalles.cantidadTotal}
            </span>{' '}
            cargados
          </p>
        )}

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

interface FichaPedidoCardProps {
  pedido: PedidoResponse;
  puedeCargar: boolean;
  puedeCambiarEstado: boolean;
  coloresCierre: PaletaColorResponse[];
  onActualizado: (producto: ProductoResponse) => void;
}

/** Tarjeta de un pedido en Ficha Técnica: número de ficha + colegio a la izquierda y el estado
 *  del pedido en una columna angosta a la derecha, para compactar esa info arriba de todo. El
 *  link de carga de talles (copiar y cerrar/reabrir) va debajo, y el conteo de talles cargados
 *  va más abajo, en cada prenda puntual (ver ImagenProductoSlot). */
export default function FichaPedidoCard({ pedido, puedeCargar, puedeCambiarEstado, coloresCierre, onActualizado }: FichaPedidoCardProps) {
  const { carga, cargando, accionando, error, cerrarOReabrir } = useCargaTallesFicha(pedido.id);

  const resumenPorProducto = new Map(carga?.productos.map((p) => [p.idProducto, p]) ?? []);

  return (
    <div className="flex flex-col gap-3 rounded-xl border border-wc-border bg-white p-4 shadow-sm">
      <div className="flex flex-col gap-1.5">
        <div className="flex items-start justify-between gap-2">
          <div className="min-w-0">
            <p className="break-words text-xs text-wc-text-muted">
              <span className="text-sm font-bold text-wc-text">#{pedido.codigoInterno}</span>{' '}
              {pedido.nombreColegio}
              {pedido.localidadColegio ? ` · ${pedido.localidadColegio}` : ''}
            </p>
          </div>
          <div className="shrink-0">
            <EstadoBadge estado={pedido.estadoActual} />
          </div>
        </div>

        <CargaTallesFichaHeader
          idPedido={pedido.id}
          carga={carga}
          cargando={cargando}
          accionando={accionando}
          error={error}
          onCerrarOReabrir={cerrarOReabrir}
        />
      </div>

      <div className="flex flex-col gap-2">
        {pedido.productos.map((producto) => {
          const resumen = resumenPorProducto.get(producto.id);
          return (
            <ImagenProductoSlot
              key={producto.id}
              producto={producto}
              puedeCargar={puedeCargar}
              puedeCambiarEstado={puedeCambiarEstado}
              coloresCierre={coloresCierre}
              resumenTalles={resumen && resumen.tieneTalle ? { cantidadCargada: resumen.cantidadCargada, cantidadTotal: resumen.cantidadTotal } : null}
              onActualizado={onActualizado}
            />
          );
        })}
      </div>
    </div>
  );
}
