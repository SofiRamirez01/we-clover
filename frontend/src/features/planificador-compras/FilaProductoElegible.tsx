import { useState } from 'react';
import EstadoBadge from '../fichas-tecnicas/EstadoBadge';
import ImagenPreviewModal from '../fichas-tecnicas/ImagenPreviewModal';
import { urlArchivoSubido } from '../../utils/urlArchivos';
import type { ProductoElegibleResponse } from '../../types/planificacionCompra';

const ImagePlaceholderIcon = () => (
  <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth={1.5} strokeLinecap="round" strokeLinejoin="round" className="text-wc-border">
    <rect x="3" y="3" width="18" height="18" rx="2" />
    <circle cx="8.5" cy="8.5" r="1.5" />
    <path d="m21 15-5-5L5 21" />
  </svg>
);

function formatearFecha(iso: string): string {
  const [anio, mes, dia] = iso.split('-');
  return `${dia}/${mes}/${anio}`;
}

/** "Color 1 (200g): Marino" por cada color marcado — cruza producto.colores (que solo trae el
 *  orden de la posición) con producto.patronCorteColores (que trae los gramos de esa
 *  posición) para poder mostrar el gramaje sin pedir nada más al backend. */
function lineasDeColor(producto: ProductoElegibleResponse['producto']): { key: number; texto: string }[] {
  const gramosPorOrden = new Map((producto.patronCorteColores ?? []).map((p) => [p.orden, p.gramos]));
  return [...producto.colores]
    .sort((a, b) => a.ordenPatronCorteColor - b.ordenPatronCorteColor)
    .map((color) => {
      const gramos = gramosPorOrden.get(color.ordenPatronCorteColor);
      const gramaje = gramos != null ? ` (${gramos}g)` : '';
      return { key: color.id, texto: `Color ${color.ordenPatronCorteColor}${gramaje}: ${color.nombreColor}` };
    });
}

interface FilaProductoElegibleProps {
  elegible: ProductoElegibleResponse;
  seleccionado: boolean;
  onToggle: () => void;
}

export default function FilaProductoElegible({ elegible, seleccionado, onToggle }: FilaProductoElegibleProps) {
  const [imagenAmpliada, setImagenAmpliada] = useState(false);
  const { producto } = elegible;
  const urlImagen = producto.imagenDisenoUrl ? urlArchivoSubido(producto.imagenDisenoUrl) : null;
  const pagoDestacado = elegible.porcentajePagadoPedido >= 50;

  return (
    <div
      className={`flex items-start gap-3 rounded-lg border p-2.5 text-sm ${
        elegible.disenoCompleto ? 'border-wc-border bg-white' : 'border-wc-border bg-wc-bg'
      }`}
    >
      <input
        type="checkbox"
        checked={seleccionado}
        disabled={!elegible.disenoCompleto}
        onChange={onToggle}
        className="mt-1 h-4 w-4 shrink-0 accent-wc-green disabled:opacity-30"
      />

      <button
        type="button"
        onClick={() => urlImagen && setImagenAmpliada(true)}
        disabled={!urlImagen}
        title={urlImagen ? 'Ver imagen completa' : undefined}
        className="flex h-14 w-14 shrink-0 items-center justify-center overflow-hidden rounded-lg border border-wc-border bg-white disabled:cursor-default"
      >
        {urlImagen ? (
          <img src={urlImagen} alt={producto.tipoPrenda ?? 'Prenda'} className="h-full w-full object-cover" />
        ) : (
          <ImagePlaceholderIcon />
        )}
      </button>

      <div className="min-w-0 flex-1">
        <div className="flex flex-wrap items-center gap-2">
          <span className="font-semibold text-wc-text">
            {elegible.codigoInternoPedido} · {producto.tipoPrenda ?? 'Prenda'} ({producto.cantidadTotal})
          </span>
          <EstadoBadge estado={producto.estadoActual} />
        </div>

        <p className="mt-0.5 text-xs text-wc-text">
          {elegible.nombreMolderia ? `Moldería #${elegible.numeroInternoMolderia} ${elegible.nombreMolderia}` : 'Sin moldería'}
        </p>

        {lineasDeColor(producto).map((linea) => (
          <p key={linea.key} className="text-xs text-wc-text">
            {linea.texto}
          </p>
        ))}

        <p className="mt-0.5 text-[11px] text-wc-text-muted">
          {elegible.nombreColegio} · Venta {formatearFecha(elegible.fechaVentaPedido)} · Entrega{' '}
          {formatearFecha(elegible.fechaEstimadaEntregaPedido)}
        </p>

        {!elegible.disenoCompleto && elegible.motivoIncompleto && (
          <p className="mt-0.5 text-xs font-medium text-red-600">{elegible.motivoIncompleto}</p>
        )}

        {elegible.planificacionesQueLoIncluyen.length > 0 && (
          <div className="mt-1 flex flex-wrap gap-1">
            {elegible.planificacionesQueLoIncluyen.map((p) => (
              <span key={p.id} className="rounded-full bg-wc-green/10 px-2 py-0.5 text-[10px] font-semibold text-wc-green">
                Ya en {p.nombre}
              </span>
            ))}
          </div>
        )}
      </div>

      <span
        className={`shrink-0 rounded-full px-2 py-1 text-xs font-semibold ${
          pagoDestacado ? 'bg-wc-green/10 text-wc-green' : 'text-wc-text-muted'
        }`}
      >
        {elegible.porcentajePagadoPedido.toFixed(0)}% pagado
      </span>

      {imagenAmpliada && urlImagen && (
        <ImagenPreviewModal src={urlImagen} alt={producto.tipoPrenda ?? 'Prenda'} onClose={() => setImagenAmpliada(false)} />
      )}
    </div>
  );
}
