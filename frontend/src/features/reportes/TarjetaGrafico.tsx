import { useState } from 'react';
import type { ReactNode } from 'react';

export interface TablaDatos {
  columnas: string[];
  filas: (string | number)[][];
}

interface TarjetaGraficoProps {
  titulo: string;
  descripcion?: string;
  /** Los mismos datos del gráfico en forma de tabla: alternativa accesible y para leer valores
   *  exactos sin depender del tooltip. */
  tabla: TablaDatos;
  /** Nota al pie (ej. aviso de datos incompletos). */
  nota?: ReactNode;
  children: ReactNode;
}

/** Marco común de un gráfico: título, alternar gráfico/tabla, y nota al pie. */
export default function TarjetaGrafico({ titulo, descripcion, tabla, nota, children }: TarjetaGraficoProps) {
  const [verTabla, setVerTabla] = useState(false);

  return (
    <section className="flex min-w-0 flex-col gap-3 rounded-lg border border-wc-border bg-white p-4">
      <header className="flex items-start justify-between gap-3">
        <div className="flex flex-col gap-0.5">
          <h3 className="m-0 text-sm font-semibold text-wc-text">{titulo}</h3>
          {descripcion && <p className="m-0 text-xs text-wc-text-muted">{descripcion}</p>}
        </div>
        <button
          type="button"
          onClick={() => setVerTabla((v) => !v)}
          aria-pressed={verTabla}
          className="shrink-0 rounded-lg border border-wc-border bg-white px-2.5 py-1 text-xs text-wc-text hover:bg-wc-bg"
        >
          {verTabla ? 'Ver gráfico' : 'Ver tabla'}
        </button>
      </header>

      {verTabla ? (
        <div className="max-h-72 overflow-auto">
          <table className="w-full border-collapse text-sm">
            <thead>
              <tr>
                {tabla.columnas.map((columna, i) => (
                  <th
                    key={columna}
                    className={`border-b border-wc-border px-2 py-1.5 text-xs font-semibold text-wc-text-muted ${i === 0 ? 'text-left' : 'text-right'}`}
                  >
                    {columna}
                  </th>
                ))}
              </tr>
            </thead>
            <tbody>
              {tabla.filas.map((fila, i) => (
                <tr key={i}>
                  {fila.map((celda, j) => (
                    <td
                      key={j}
                      className={`border-b border-wc-border px-2 py-1.5 text-wc-text ${j === 0 ? 'text-left' : 'text-right tabular-nums'}`}
                    >
                      {celda}
                    </td>
                  ))}
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      ) : (
        children
      )}

      {nota && <p className="m-0 text-xs text-wc-text-muted">{nota}</p>}
    </section>
  );
}
