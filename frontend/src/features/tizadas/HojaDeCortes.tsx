import type { CorteResultado } from '../../types/corte';
import { colorDePieza, porColor, total } from './formato';

interface FilaHoja {
  clave: string;
  idPieza: number;
  nombre: string;
  talle: string;
  cortes: { numero: number; tela: string; capas: string; porCapa: number; salen: string; deMas: number }[];
}

function ordenTalle(talle: string): number {
  const numero = Number(talle);
  return Number.isNaN(numero) ? 999 : numero;
}

/** Qué pieza sale de qué corte: una fila por pieza y talle, con cada corte donde se corta. */
export default function HojaDeCortes({ cortes }: { cortes: CorteResultado[] }) {
  const filas = new Map<string, FilaHoja>();
  for (const corte of cortes) {
    const capas = corte.capasPorColor.map((c) => `${c.nombreColor ?? c.idPaletaColor} ×${c.capas}`).join(', ');
    for (const item of corte.items) {
      const clave = `${item.idPieza}-${item.idTalle}`;
      const fila = filas.get(clave) ?? { clave, idPieza: item.idPieza, nombre: item.nombre, talle: item.talle, cortes: [] };
      fila.cortes.push({
        numero: corte.numero,
        tela: corte.nombreTela,
        capas,
        porCapa: item.unidadesPorCapa,
        salen: porColor(item.produccion),
        deMas: total(item.excedente),
      });
      filas.set(clave, fila);
    }
  }
  const ordenadas = [...filas.values()].sort(
    (a, b) => a.nombre.localeCompare(b.nombre, 'es') || ordenTalle(a.talle) - ordenTalle(b.talle),
  );

  return (
    <section className="flex flex-col gap-3 rounded-lg border border-wc-border bg-white p-4">
      <header>
        <h3 className="m-0 text-base font-semibold text-wc-text">Hoja de cortes</h3>
        <p className="m-0 text-xs text-wc-text-muted">
          De qué corte sale cada pieza. Una pieza de un mismo talle puede salir de más de un corte.
        </p>
      </header>
      <div className="overflow-x-auto">
        <table className="w-full border-collapse text-sm">
          <thead>
            <tr className="border-b border-wc-border text-left text-xs text-wc-text-muted">
              <th className="py-1.5 pr-3 font-semibold">Pieza</th>
              <th className="py-1.5 pr-3 font-semibold">Talle</th>
              <th className="py-1.5 pr-3 font-semibold">Corte</th>
              <th className="py-1.5 pr-3 font-semibold">Capas</th>
              <th className="py-1.5 pr-3 text-right font-semibold">Por capa</th>
              <th className="py-1.5 pr-3 text-right font-semibold">Salen</th>
              <th className="py-1.5 text-right font-semibold">De más</th>
            </tr>
          </thead>
          <tbody>
            {ordenadas.map((fila) =>
              fila.cortes.map((corte, indice) => (
                <tr
                  key={`${fila.clave}-${corte.numero}`}
                  className={indice === fila.cortes.length - 1 ? 'border-b border-wc-border last:border-0' : ''}
                >
                  {indice === 0 && (
                    <>
                      <td className="py-1.5 pr-3 align-top" rowSpan={fila.cortes.length}>
                        <span className="inline-flex items-center gap-2">
                          <span className="h-3 w-3 shrink-0 rounded-sm border border-black/20" style={{ backgroundColor: colorDePieza(fila.idPieza) }} aria-hidden="true" />
                          {fila.nombre}
                        </span>
                      </td>
                      <td className="py-1.5 pr-3 align-top" rowSpan={fila.cortes.length}>
                        T{fila.talle}
                      </td>
                    </>
                  )}
                  <td className="py-1.5 pr-3">
                    Corte {corte.numero} <span className="text-wc-text-muted">({corte.tela})</span>
                  </td>
                  <td className="py-1.5 pr-3 text-wc-text-muted">{corte.capas}</td>
                  <td className="py-1.5 pr-3 text-right">{corte.porCapa}</td>
                  <td className="py-1.5 pr-3 text-right font-semibold">{corte.salen}</td>
                  <td className="py-1.5 text-right">{corte.deMas || '—'}</td>
                </tr>
              )),
            )}
          </tbody>
        </table>
      </div>
    </section>
  );
}
