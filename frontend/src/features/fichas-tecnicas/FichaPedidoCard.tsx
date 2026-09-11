import { useState } from 'react';
import CargaTallesFichaHeader from '../carga-talles/CargaTallesFichaHeader';
import { useCargaTallesFicha } from '../carga-talles/useCargaTallesFicha';
import { urlArchivoSubido } from '../../utils/urlArchivos';
import BarraProgresoPago from './BarraProgresoPago';
import BarraProgresoTalles from './BarraProgresoTalles';
import EstadoBadge from './EstadoBadge';
import EstadoDisenoBadge from './EstadoDisenoBadge';
import EstadoProductoControl from './EstadoProductoControl';
import ImagenPreviewModal from './ImagenPreviewModal';
import ModalColoresGotero from './ModalColoresGotero';
import type { PedidoResponse, ProductoResponse } from '../../types/pedido';
import type { PaletaColorResponse } from '../../types/paletaColores';
import type { TipoTelaCatalogo } from '../../types/tipoTela';
import { TIPO_PRENDA_CAMPERA, nombreTela, telaEfectiva } from './telaUtils';

const NoCargadoIcon = () => (
  <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth={1.5} strokeLinecap="round" strokeLinejoin="round">
    <rect x="3" y="3" width="18" height="18" rx="2" />
    <circle cx="8.5" cy="8.5" r="1.5" />
    <path d="m21 15-5-5L5 21" />
  </svg>
);

const PencilIcon = () => (
  <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth={2} strokeLinecap="round" strokeLinejoin="round">
    <path d="M11 4H4a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2v-7" />
    <path d="M18.5 2.5a2.121 2.121 0 0 1 3 3L12 15l-4 1 1-4Z" />
  </svg>
);

interface ResumenTalles {
  cantidadCargada: number;
  cantidadTotal: number;
}

function ColorChip({ etiqueta, nombre, hex }: { etiqueta?: string; nombre: string; hex: string | null }) {
  return (
    <span className="inline-flex items-center gap-1 rounded-full border border-wc-border bg-white px-1.5 py-0.5 text-[10px] font-medium text-wc-text">
      {hex && <span className="h-2.5 w-2.5 shrink-0 rounded-full border border-wc-border" style={{ backgroundColor: hex }} />}
      {etiqueta ? `${etiqueta}: ${nombre}` : nombre}
    </span>
  );
}

interface FilaProductoProps {
  producto: ProductoResponse;
  puedeCargar: boolean;
  puedeCambiarEstado: boolean;
  coloresCierre: PaletaColorResponse[];
  tiposTela: TipoTelaCatalogo[];
  resumenTalles: ResumenTalles | null;
  /** Pedido.porcentajePagado — se repite igual en todas las filas del mismo pedido, no hay
   *  desglose de pago por prenda. */
  porcentajePagado: number;
  onActualizado: (producto: ProductoResponse) => void;
}

/** Una fila = una prenda del pedido, con columnas alineadas entre todas las filas de todos los
 *  pedidos (mismos anchos que el encabezado de columnas en FichasTecnicasView). */
function FilaProducto({
  producto,
  puedeCargar,
  puedeCambiarEstado,
  coloresCierre,
  tiposTela,
  resumenTalles,
  porcentajePagado,
  onActualizado,
}: FilaProductoProps) {
  const [errorImagen, setErrorImagen] = useState(false);
  const [previewAbierto, setPreviewAbierto] = useState(false);
  const [modalAbierto, setModalAbierto] = useState(false);

  const tieneImagen = Boolean(producto.imagenDisenoUrl) && !errorImagen;
  const urlImagen = tieneImagen ? urlArchivoSubido(producto.imagenDisenoUrl as string) : null;
  const esCampera = producto.tipoPrenda === TIPO_PRENDA_CAMPERA;
  const telaActual = telaEfectiva(producto);
  const posiciones = producto.patronCorteColores ?? [];

  const chips: { key: string; etiqueta?: string; nombre: string; hex: string | null }[] = [];
  if (esCampera) {
    chips.push({ key: 'cierre', etiqueta: 'Cierre', nombre: producto.nombreColorCierre ?? 'Sin definir', hex: producto.hexColorCierre });
  }
  [...posiciones]
    .sort((a, b) => a.orden - b.orden)
    .forEach((posicion) => {
      const asignado = producto.colores.find((c) => c.idPatronCorteColor === posicion.id);
      chips.push({ key: `pos-${posicion.id}`, nombre: asignado?.nombreColor ?? 'Sin definir', hex: asignado?.hexColor ?? null });
    });

  return (
    // El respaldo de overflow-x-auto para cuando la suma de anchos mínimos supera el ancho
    // disponible vive en un solo contenedor arriba de todo (FichasTecnicasView), no acá — así
    // toda la sección (encabezado + todas las filas de todos los pedidos) scrollea junta como
    // una unidad en vez de que cada fila tenga su propia barra de scroll independiente.
    <div className="flex items-center gap-3 border-t border-wc-border px-3 py-2.5 first:border-t-0">
      <div className="relative h-14 w-14 shrink-0">
        {puedeCargar && (
          <button
            type="button"
            onClick={() => setModalAbierto(true)}
            aria-label="Editar ficha de colores"
            title="Editar ficha de colores"
            className="absolute -right-1.5 -top-1.5 z-10 flex h-5 w-5 items-center justify-center rounded-full border border-wc-border bg-white text-wc-text-muted shadow-sm transition hover:bg-wc-bg hover:text-wc-text"
          >
            <PencilIcon />
          </button>
        )}
        <button
          type="button"
          onClick={() => tieneImagen && setPreviewAbierto(true)}
          aria-label={tieneImagen ? 'Ver imagen en grande' : undefined}
          className={`h-full w-full overflow-hidden rounded-md border-0 bg-wc-bg p-0 outline-none ${tieneImagen ? 'cursor-pointer' : 'cursor-default'}`}
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
            </div>
          )}
        </button>
      </div>

      {previewAbierto && urlImagen && (
        <ImagenPreviewModal src={urlImagen} alt={producto.tipoPrenda ?? 'Prenda'} onClose={() => setPreviewAbierto(false)} />
      )}
      {modalAbierto && (
        <ModalColoresGotero producto={producto} coloresCierre={coloresCierre} onActualizado={onActualizado} onCerrar={() => setModalAbierto(false)} />
      )}

      <div className="min-w-20 flex-1">
        <p className="truncate text-xs font-semibold text-wc-text">{producto.tipoPrenda ?? 'Sin tipo'}</p>
        {/* Resumen compacto para pantallas chicas, donde el resto de las columnas se ocultan
            (mismo breakpoint que la columna real de Tela, para que no haya un rango de anchos
            donde no se vea en ninguno de los dos lados). */}
        <p className="mt-1 text-[10px] leading-snug text-wc-text-muted xl:hidden">
          {telaActual ? nombreTela(telaActual, tiposTela) : 'Sin tela'}
        </p>
      </div>

      <div className="min-w-16 flex-1 text-center">
        <p className="text-lg font-extrabold text-wc-text">{producto.cantidadTotal}</p>
      </div>

      <div className="hidden min-w-24 flex-1 text-sm font-medium text-wc-text-muted xl:block">
        {telaActual ? nombreTela(telaActual, tiposTela) : 'Sin definir'}
      </div>

      <div className="hidden min-w-56 flex-1 flex-wrap gap-1 overflow-hidden xl:flex">
        {chips.length > 0 ? (
          chips.map((chip) => <ColorChip key={chip.key} etiqueta={chip.etiqueta} nombre={chip.nombre} hex={chip.hex} />)
        ) : (
          <span className="text-xs text-wc-text-muted">—</span>
        )}
      </div>

      <div className="hidden min-w-28 flex-1 xl:block">
        <EstadoDisenoBadge producto={producto} />
      </div>

      <div className="hidden min-w-32 flex-1 xl:block">
        {resumenTalles ? (
          <BarraProgresoTalles cargados={resumenTalles.cantidadCargada} total={resumenTalles.cantidadTotal} />
        ) : (
          <span className="text-xs text-wc-text-muted">—</span>
        )}
      </div>

      <div className="hidden min-w-24 flex-1 xl:block">
        <BarraProgresoPago porcentajePagado={porcentajePagado} />
      </div>

      <div className="min-w-32 flex-1">
        <EstadoProductoControl producto={producto} puedeEditar={puedeCambiarEstado} onActualizado={onActualizado} />
      </div>
    </div>
  );
}

interface FichaPedidoCardProps {
  pedido: PedidoResponse;
  puedeCargar: boolean;
  puedeCambiarEstado: boolean;
  coloresCierre: PaletaColorResponse[];
  tiposTela: TipoTelaCatalogo[];
  onActualizado: (producto: ProductoResponse) => void;
}

/** Grupo de un pedido en Ficha Técnica: encabezado (ficha como dato principal, colegio al lado
 *  también en negrita, localidad debajo) + una fila por prenda, todo dentro de un mismo borde
 *  para que se note que son parte del mismo pedido. */
export default function FichaPedidoCard({ pedido, puedeCargar, puedeCambiarEstado, coloresCierre, tiposTela, onActualizado }: FichaPedidoCardProps) {
  const { carga, cargando, accionando, error, cerrarOReabrir } = useCargaTallesFicha(pedido.id);

  const resumenPorProducto = new Map(carga?.productos.map((p) => [p.idProducto, p]) ?? []);

  return (
    <div className="flex flex-col overflow-hidden rounded-xl border border-wc-border bg-white shadow-sm">
      <div className="flex flex-col gap-1.5 border-b border-wc-border bg-wc-bg/60 px-3 py-2.5">
        {/* Debajo de xl (mismo breakpoint que la columna "Talles" de las filas), esa columna
            ni existe — no hay con qué alinear, así que va todo en una línea simple
            (ficha+colegio / estado) y la carga de talles completa (con texto) en una línea
            propia abajo. */}
        <div className="flex flex-wrap items-center justify-between gap-2 xl:hidden">
          <div className="min-w-0">
            <div className="flex flex-wrap items-baseline gap-x-1.5 gap-y-0.5">
              <span className="text-xl font-extrabold text-wc-text">#{pedido.codigoInterno}</span>
              <span className="truncate text-sm font-bold text-wc-text">
                {pedido.nombreColegio}
                {pedido.localidadColegio && <span className="font-medium text-wc-text-muted"> – {pedido.localidadColegio}</span>}
              </span>
            </div>
          </div>
          <EstadoBadge estado={pedido.estadoActual} />
        </div>

        {/* En xl+ va todo en UNA sola fila: ficha+colegio, los controles de carga de talles
            alineados con la columna "Talles" de las filas de abajo, y el estado del pedido.
            Con flexbox no hay forma de que el título "salte" varias columnas y siga alineado
            con precisión (ya lo intenté dos veces y el cálculo de flex-1 se rompe apenas falta
            un elemento) — con CSS Grid sí, usando los mismos anchos de columna que
            EncabezadoColumnas/FilaProducto: el título ocupa las columnas de
            imagen+prenda+cant+tela+colores+diseño (1 a 6), los íconos de talles la columna 7,
            la columna 8 (Pago) queda vacía — no hay ningún control de pago que mostrar acá,
            solo existe como dato por prenda más abajo — y el estado la columna 9. Columnas
            flexibles (minmax con 1fr) para que se repartan el ancho disponible en vez de dejar
            espacio muerto — mismo criterio que las filas. */}
        <div
          className="hidden items-center gap-3 xl:grid"
          style={{
            gridTemplateColumns:
              '56px minmax(80px,1fr) minmax(64px,1fr) minmax(96px,1fr) minmax(224px,1fr) minmax(112px,1fr) minmax(128px,1fr) minmax(96px,1fr) minmax(128px,1fr)',
          }}
        >
          <div className="min-w-0" style={{ gridColumn: '1 / 7' }}>
            <div className="flex flex-wrap items-baseline gap-x-1.5 gap-y-0.5">
              <span className="text-xl font-extrabold text-wc-text">#{pedido.codigoInterno}</span>
              <span className="truncate text-sm font-bold text-wc-text">
                {pedido.nombreColegio}
                {pedido.localidadColegio && <span className="font-medium text-wc-text-muted"> – {pedido.localidadColegio}</span>}
              </span>
            </div>
          </div>
          <div style={{ gridColumn: '7' }}>
            <CargaTallesFichaHeader
              idPedido={pedido.id}
              carga={carga}
              cargando={cargando}
              accionando={accionando}
              error={error}
              onCerrarOReabrir={cerrarOReabrir}
              compacto
            />
          </div>
          <div style={{ gridColumn: '9' }}>
            <EstadoBadge estado={pedido.estadoActual} />
          </div>
        </div>

        <div className="xl:hidden">
          <CargaTallesFichaHeader
            idPedido={pedido.id}
            carga={carga}
            cargando={cargando}
            accionando={accionando}
            error={error}
            onCerrarOReabrir={cerrarOReabrir}
          />
        </div>
      </div>

      <div className="flex flex-col">
        {pedido.productos.map((producto) => {
          const resumen = resumenPorProducto.get(producto.id);
          return (
            <FilaProducto
              key={producto.id}
              producto={producto}
              puedeCargar={puedeCargar}
              puedeCambiarEstado={puedeCambiarEstado}
              coloresCierre={coloresCierre}
              tiposTela={tiposTela}
              resumenTalles={resumen && resumen.tieneTalle ? { cantidadCargada: resumen.cantidadCargada, cantidadTotal: resumen.cantidadTotal } : null}
              porcentajePagado={pedido.porcentajePagado}
              onActualizado={onActualizado}
            />
          );
        })}
      </div>
    </div>
  );
}
