import { useMemo, useState } from 'react';
import AccionesPedidoMenu from '../../components/AccionesPedidoMenu';
import HistorialPedidoModal from '../../components/HistorialPedidoModal';
import ImagenPreviewModal from '../fichas-tecnicas/ImagenPreviewModal';
import { urlArchivoSubido } from '../../utils/urlArchivos';
import { BUCKET_POR_ESTADO, ESTADO_PEDIDO_LABELS } from '../../types/pedido';
import type {
  CampoOrdenPedidos,
  FiltrosListadoPedidos,
  OpcionMolderia,
  PedidoResponse,
  ProductoResponse,
} from '../../types/pedido';
import EncabezadoOrdenable from './EncabezadoOrdenable';
import { formatearMoneda, siguienteOrden } from './formato';
import type { OrdenListado } from './formato';
import PieListado from './PieListado';
import { useListadoPedidos, useValorDiferido } from './useListadoPedidos';

const SinImagenIcon = () => (
  <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth={1.5} strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
    <rect x="3" y="3" width="18" height="18" rx="2" />
    <circle cx="8.5" cy="8.5" r="1.5" />
    <path d="m21 15-5-5L5 21" />
  </svg>
);

function claveMolderia(idPatronCorte: number, idTipoPrenda: number): string {
  return `${idPatronCorte}-${idTipoPrenda}`;
}

/** "Clasica – Buzo"; si dos molderías del mismo tipo de prenda se llaman igual, se agrega el
 *  número interno para distinguirlas. */
function etiquetasMolderias(molderias: OpcionMolderia[]): Map<string, string> {
  const repeticiones = new Map<string, number>();
  for (const m of molderias) {
    const base = `${m.nombrePatronCorte} – ${m.tipoPrenda}`;
    repeticiones.set(base, (repeticiones.get(base) ?? 0) + 1);
  }
  const etiquetas = new Map<string, string>();
  for (const m of molderias) {
    const base = `${m.nombrePatronCorte} – ${m.tipoPrenda}`;
    etiquetas.set(
      claveMolderia(m.idPatronCorte, m.idTipoPrenda),
      (repeticiones.get(base) ?? 0) > 1 ? `${base} (n° ${m.numeroInterno})` : base,
    );
  }
  return etiquetas;
}

interface PrendaHistoricoProps {
  producto: ProductoResponse;
  nombreMolderia: string | null;
  onVerImagen: (url: string, alt: string) => void;
}

/** Una prenda del pedido: miniatura del diseño (clic = verla grande), tipo, moldería y cantidad. */
function PrendaHistorico({ producto, nombreMolderia, onVerImagen }: PrendaHistoricoProps) {
  const [errorImagen, setErrorImagen] = useState(false);
  const url = producto.imagenDisenoUrl && !errorImagen ? urlArchivoSubido(producto.imagenDisenoUrl) : null;
  const tipo = producto.tipoPrenda ?? 'Sin tipo';

  return (
    <div className="historico-prenda">
      {url ? (
        <button
          type="button"
          className="historico-miniatura"
          onClick={() => onVerImagen(url, tipo)}
          aria-label={`Ver el diseño de ${tipo} en grande`}
        >
          <img src={url} alt={`Diseño de ${tipo}`} onError={() => setErrorImagen(true)} />
        </button>
      ) : (
        <span className="historico-miniatura historico-miniatura--vacia" title="Sin imagen del diseño">
          <SinImagenIcon />
        </span>
      )}
      <span className="historico-prenda-texto">
        <strong>{tipo}</strong>
        {nombreMolderia && <span>{nombreMolderia}</span>}
      </span>
      <span className="historico-prenda-cantidad">{producto.cantidadTotal} u.</span>
    </div>
  );
}

interface PestanaHistoricoProps {
  promociones: number[];
  molderias: OpcionMolderia[];
  anioActual: number;
}

/** Pestaña "Histórico" de Base Ventas (M5.4): todos los pedidos de todas las promociones, de
 *  solo lectura, para reutilizar lo vendido en ventas futuras. Por defecto, la promoción del
 *  año actual. */
export default function PestanaHistorico({ promociones, molderias, anioActual }: PestanaHistoricoProps) {
  const [buscarTexto, setBuscarTexto] = useState('');
  const [promocion, setPromocion] = useState(String(anioActual));
  const [molderia, setMolderia] = useState('');
  const [pedidoHistorial, setPedidoHistorial] = useState<PedidoResponse | null>(null);
  const [imagenAbierta, setImagenAbierta] = useState<{ url: string; alt: string } | null>(null);
  const [orden, setOrden] = useState<OrdenListado | null>(null);
  const buscar = useValorDiferido(buscarTexto.trim());

  const etiquetas = useMemo(() => etiquetasMolderias(molderias), [molderias]);
  // Solo el nombre de la moldería, para cada prenda (el tipo ya se muestra aparte).
  const nombresMolderia = useMemo(() => {
    const nombres = new Map<string, string>();
    for (const m of molderias) nombres.set(claveMolderia(m.idPatronCorte, m.idTipoPrenda), m.nombrePatronCorte);
    return nombres;
  }, [molderias]);

  const filtrosListado = useMemo<FiltrosListadoPedidos>(() => {
    const [idPatronCorte, idTipoPrenda] = molderia ? molderia.split('-').map(Number) : [undefined, undefined];
    return {
      buscar: buscar || undefined,
      promocion: promocion ? Number(promocion) : undefined,
      idPatronCorte,
      idTipoPrenda,
      orden: orden?.campo,
      direccion: orden?.direccion,
    };
  }, [buscar, promocion, molderia, orden]);
  const listado = useListadoPedidos(filtrosListado);

  function columna(etiqueta: string, campo: CampoOrdenPedidos) {
    return (
      <EncabezadoOrdenable
        etiqueta={etiqueta}
        campo={campo}
        orden={orden}
        onOrdenar={(elegido) => setOrden((actual) => siguienteOrden(actual, elegido))}
      />
    );
  }

  return (
    <>
      <section className="card pedidos-filtros">
        <input
          className="pedidos-filtro-buscar"
          value={buscarTexto}
          onChange={(e) => setBuscarTexto(e.target.value)}
          placeholder="Ficha, colegio, localidad o vendedor…"
          aria-label="Buscar por ficha, colegio, localidad o vendedor"
        />
        <select value={promocion} onChange={(e) => setPromocion(e.target.value)} aria-label="Promoción">
          <option value="">Todas las promociones</option>
          {promociones.map((anio) => (
            <option key={anio} value={anio}>
              Promoción {anio}
            </option>
          ))}
        </select>
        <select value={molderia} onChange={(e) => setMolderia(e.target.value)} aria-label="Moldería – tipo de prenda">
          <option value="">Moldería – tipo de prenda (todas)</option>
          {molderias.map((m) => {
            const clave = claveMolderia(m.idPatronCorte, m.idTipoPrenda);
            return (
              <option key={clave} value={clave}>
                {etiquetas.get(clave)}
              </option>
            );
          })}
        </select>

      </section>

      <div className="pedidos-tabla-wrapper">
        <table className="pedidos-tabla">
          <thead>
            <tr>
              {columna('Nº Ficha', 'FICHA')}
              {columna('Colegio', 'COLEGIO')}
              {columna('Localidad', 'LOCALIDAD')}
              {columna('Curso', 'CURSO')}
              {columna('Vendedor', 'VENDEDOR')}
              <th>Prendas</th>
              {columna('Total', 'TOTAL')}
              {columna('Estado', 'ESTADO')}
              <th aria-label="Acciones" />
            </tr>
          </thead>
          <tbody>
            {listado.estadoCarga === 'listo' &&
              listado.pedidos.map((pedido) => (
                <tr key={pedido.id} className="historico-fila">
                  <td>{pedido.codigoInterno}</td>
                  <td className="pedidos-celda-texto pedidos-celda-texto--ancha">{pedido.nombreColegio}</td>
                  <td className="pedidos-celda-texto pedidos-celda-texto--ancha">{pedido.localidadColegio ?? '—'}</td>
                  <td className="pedidos-celda-texto pedidos-celda-texto--corta">{pedido.curso}</td>
                  <td className="pedidos-celda-texto">{pedido.nombreVendedor}</td>
                  <td>
                    <div className="historico-prendas">
                      {pedido.productos.map((producto) => (
                        <PrendaHistorico
                          key={producto.id}
                          producto={producto}
                          nombreMolderia={
                            producto.idPatronCorte && producto.idTipoPrenda
                              ? (nombresMolderia.get(claveMolderia(producto.idPatronCorte, producto.idTipoPrenda)) ?? null)
                              : null
                          }
                          onVerImagen={(url, alt) => setImagenAbierta({ url, alt })}
                        />
                      ))}
                      {pedido.productos.length === 0 && <span className="historico-sin-prendas">Sin prendas</span>}
                    </div>
                  </td>
                  <td>${formatearMoneda(pedido.precioTotal)}</td>
                  <td>
                    <span className={`estado-badge estado-badge--${BUCKET_POR_ESTADO[pedido.estadoActual]}`}>
                      {ESTADO_PEDIDO_LABELS[pedido.estadoActual]}
                    </span>
                  </td>
                  <td>
                    <AccionesPedidoMenu pedido={pedido} onVerHistorial={setPedidoHistorial} />
                  </td>
                </tr>
              ))}
          </tbody>
        </table>
        <PieListado
          listado={listado}
          textoVacio={
            promocion && !buscar && !molderia
              ? `No hay pedidos de la promoción ${promocion}.`
              : 'No hay pedidos que coincidan con los filtros.'
          }
        />
      </div>

      {pedidoHistorial && <HistorialPedidoModal pedido={pedidoHistorial} onClose={() => setPedidoHistorial(null)} />}
      {imagenAbierta && (
        <div className="tw-scope">
          <ImagenPreviewModal src={imagenAbierta.url} alt={imagenAbierta.alt} onClose={() => setImagenAbierta(null)} />
        </div>
      )}
    </>
  );
}
