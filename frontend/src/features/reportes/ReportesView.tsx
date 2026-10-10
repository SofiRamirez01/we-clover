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

type PestanaReporte = 'ventas' | 'produccion';

/** Una pestaña por sección. Ventas incluye el bloque de presupuestados. La futura sección de
 *  planta/empleados se suma acá como una pestaña más. */
const PESTANAS: { clave: PestanaReporte; etiqueta: string }[] = [
  { clave: 'ventas', etiqueta: 'Ventas' },
  { clave: 'produccion', etiqueta: 'Producción' },
];

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
  const [pestanaActiva, setPestanaActiva] = useState<PestanaReporte>('ventas');

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

        {/* Mismo aspecto de pestañas que Carta de colores. Los filtros de arriba son globales:
            valen para la pestaña que esté abierta y se conservan al cambiar de una a otra. */}
        <div className="flex flex-col gap-5">
          <div role="tablist" aria-label="Secciones de reportes" className="flex flex-wrap items-center gap-1 border-b border-wc-border">
            {PESTANAS.map((pestana) => (
              <button
                key={pestana.clave}
                type="button"
                role="tab"
                id={`reportes-tab-${pestana.clave}`}
                aria-selected={pestanaActiva === pestana.clave}
                aria-controls="reportes-panel"
                onClick={() => setPestanaActiva(pestana.clave)}
                className={`-mb-px rounded-t-lg border border-b-0 px-4 py-2 text-sm font-semibold transition ${
                  pestanaActiva === pestana.clave
                    ? 'border-wc-border bg-white text-wc-green'
                    : 'border-transparent text-wc-text-muted hover:text-wc-text'
                }`}
              >
                {pestana.etiqueta}
              </button>
            ))}
          </div>

          <div id="reportes-panel" role="tabpanel" aria-labelledby={`reportes-tab-${pestanaActiva}`}>
            {pestanaActiva === 'ventas' ? (
              <SeccionVentas filtros={filtros} />
            ) : (
              <SeccionProduccion filtros={filtros} mostrarOjal={mostrarOjal} />
            )}
          </div>
        </div>
      </div>
    </div>
  );
}
