import iconoKommo from '../../assets/kommo.webp';
import iconoExcel from '../../assets/excel.svg';
import type { PedidoResponse } from '../../types/pedido';

const ICONO_POR_ORIGEN = {
  KOMMO: { src: iconoKommo, texto: 'Importado de Kommo' },
  EXCEL: { src: iconoExcel, texto: 'Importado de Excel' },
} as const;

const TEXTO_NUEVO = 'Nuevo: importado y sin revisar. Abrí Editar, revisá los datos y guardá.';

/** Ícono de la app de origen (los pedidos manuales no llevan) y punto naranja si el pedido
 *  importado todavía no se revisó. El texto aparece al pasar el mouse. */
export default function IndicadoresPedido({ pedido }: { pedido: PedidoResponse }) {
  const origen = pedido.origen === 'MANUAL' ? null : ICONO_POR_ORIGEN[pedido.origen];
  return (
    <>
      {origen && <img className="indicador-origen" src={origen.src} alt={origen.texto} title={origen.texto} />}
      {!pedido.revisadoImportacion && (
        <span className="indicador-nuevo" role="img" aria-label={TEXTO_NUEVO} title={TEXTO_NUEVO} />
      )}
    </>
  );
}
