import { useEffect, useMemo, useState } from 'react';
import AppHeader from '../../components/AppHeader';
import FiltrosReportes from './FiltrosReportes';
import type { PeriodoReporte } from './FiltrosReportes';
import SeccionProduccion from './SeccionProduccion';
import SeccionVentas from './SeccionVentas';
import { rangoUltimosMeses } from './formato';
import { listarTiposPrenda } from '../../services/pedidoService';
import type { TipoPrendaOption } from '../../types/pedido';
import type { ReporteFiltros } from '../../types/reportes';

/** Bandera no participa de ningún reporte (decisión de negocio), así que tampoco se ofrece
 *  como filtro. */
const TIPO_PRENDA_EXCLUIDO = 'bandera';

/** Único tipo de prenda al que le aplica la etapa OJAL (ver EtapaProduccionAplicabilidad). */
const TIPO_PRENDA_CON_OJAL = 'chomba';

/**
 * Módulo 5 — Reportes (solo ROLE_ADMINISTRATIVO, ver Sidebar). Los filtros globales viven acá
 * y se pasan a cada sección; cada sección carga sus propios datos, así un error en una no tira
 * abajo a la otra.
 */
export default function ReportesView() {
  const [periodo, setPeriodo] = useState<PeriodoReporte>('12');
  const [rangoDesde, setRangoDesde] = useState('');
  const [rangoHasta, setRangoHasta] = useState('');
  const [idTipoPrenda, setIdTipoPrenda] = useState<number | null>(null);
  const [tiposPrenda, setTiposPrenda] = useState<TipoPrendaOption[]>([]);

  useEffect(() => {
    let vigente = true;
    listarTiposPrenda()
      .then((tipos) => {
        if (vigente) setTiposPrenda(tipos.filter((tipo) => tipo.nombre.toLowerCase() !== TIPO_PRENDA_EXCLUIDO));
      })
      // Sin el catálogo igual se puede usar el reporte con "Todas".
      .catch(() => undefined);
    return () => {
      vigente = false;
    };
  }, []);

  const filtros = useMemo<ReporteFiltros | null>(() => {
    if (periodo === 'custom') {
      if (!rangoDesde || !rangoHasta) return null;
      return { desde: rangoDesde, hasta: rangoHasta, idTipoPrenda };
    }
    return { ...rangoUltimosMeses(Number(periodo)), idTipoPrenda };
  }, [periodo, rangoDesde, rangoHasta, idTipoPrenda]);

  const tipoPrendaElegido = tiposPrenda.find((tipo) => tipo.id === idTipoPrenda);
  const mostrarOjal = idTipoPrenda === null || tipoPrendaElegido?.nombre.toLowerCase() === TIPO_PRENDA_CON_OJAL;

  return (
    <div className="tw-scope flex flex-col px-8 pb-12 pt-7">
      <AppHeader title="Reportes" />

      <div className="flex flex-col gap-6">
        <FiltrosReportes
          periodo={periodo}
          onPeriodoChange={setPeriodo}
          rangoDesde={rangoDesde}
          rangoHasta={rangoHasta}
          onRangoChange={(desde, hasta) => {
            setRangoDesde(desde);
            setRangoHasta(hasta);
          }}
          tiposPrenda={tiposPrenda}
          idTipoPrenda={idTipoPrenda}
          onTipoPrendaChange={setIdTipoPrenda}
        />

        <SeccionVentas filtros={filtros} />

        <SeccionProduccion filtros={filtros} mostrarOjal={mostrarOjal} />
      </div>
    </div>
  );
}
