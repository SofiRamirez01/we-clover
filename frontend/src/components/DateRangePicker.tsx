import { useEffect, useRef, useState } from 'react';
import './DateRangePicker.css';

const DIAS_SEMANA = ['L', 'M', 'M', 'J', 'V', 'S', 'D'];
const MESES = [
  'Enero', 'Febrero', 'Marzo', 'Abril', 'Mayo', 'Junio',
  'Julio', 'Agosto', 'Septiembre', 'Octubre', 'Noviembre', 'Diciembre',
];

function pad(n: number): string {
  return String(n).padStart(2, '0');
}

function toISO(date: Date): string {
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`;
}

function fromISO(iso: string): Date {
  const [y, m, d] = iso.split('-').map(Number);
  return new Date(y, m - 1, d);
}

function formatDisplay(iso: string): string {
  const [y, m, d] = iso.split('-');
  return `${d}/${m}/${y}`;
}

function generarDiasDelMes(mesVisible: Date): (Date | null)[] {
  const anio = mesVisible.getFullYear();
  const mes = mesVisible.getMonth();
  const primerDia = new Date(anio, mes, 1);
  const offset = (primerDia.getDay() + 6) % 7; // semana arranca en lunes
  const diasEnMes = new Date(anio, mes + 1, 0).getDate();

  const dias: (Date | null)[] = [];
  for (let i = 0; i < offset; i++) dias.push(null);
  for (let d = 1; d <= diasEnMes; d++) dias.push(new Date(anio, mes, d));
  return dias;
}

interface DateRangePickerProps {
  desde: string;
  hasta: string;
  onChange: (desde: string, hasta: string) => void;
}

export default function DateRangePicker({ desde, hasta, onChange }: DateRangePickerProps) {
  const [abierto, setAbierto] = useState(false);
  const [mesVisible, setMesVisible] = useState<Date>(() => (desde ? fromISO(desde) : new Date()));
  const contenedorRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    function alClickearFuera(e: MouseEvent) {
      if (contenedorRef.current && !contenedorRef.current.contains(e.target as Node)) {
        setAbierto(false);
      }
    }
    document.addEventListener('mousedown', alClickearFuera);
    return () => document.removeEventListener('mousedown', alClickearFuera);
  }, []);

  function manejarClickDia(dia: Date) {
    const iso = toISO(dia);
    if (!desde || (desde && hasta)) {
      onChange(iso, '');
    } else if (iso < desde) {
      onChange(iso, '');
    } else {
      onChange(desde, iso);
    }
  }

  function limpiar() {
    onChange('', '');
  }

  const dias = generarDiasDelMes(mesVisible);
  const etiqueta = !desde
    ? 'Seleccionar fechas'
    : !hasta
      ? `Desde ${formatDisplay(desde)}`
      : `${formatDisplay(desde)} – ${formatDisplay(hasta)}`;

  return (
    <div className="date-range-picker" ref={contenedorRef}>
      <label className="date-range-picker-label">
        Fecha
        <button
          type="button"
          className="date-range-picker-trigger"
          onClick={() => setAbierto((v) => !v)}
        >
          {etiqueta}
        </button>
      </label>

      {abierto && (
        <div className="date-range-picker-popover">
          <div className="date-range-picker-header">
            <button
              type="button"
              onClick={() => setMesVisible((m) => new Date(m.getFullYear(), m.getMonth() - 1, 1))}
              aria-label="Mes anterior"
            >
              ‹
            </button>
            <strong>
              {MESES[mesVisible.getMonth()]} {mesVisible.getFullYear()}
            </strong>
            <button
              type="button"
              onClick={() => setMesVisible((m) => new Date(m.getFullYear(), m.getMonth() + 1, 1))}
              aria-label="Mes siguiente"
            >
              ›
            </button>
          </div>

          <div className="date-range-picker-dias-semana">
            {DIAS_SEMANA.map((d, i) => (
              <span key={i}>{d}</span>
            ))}
          </div>

          <div className="date-range-picker-grid">
            {dias.map((dia, i) => {
              if (!dia) return <span key={i} />;
              const iso = toISO(dia);
              const esSeleccionado = iso === desde || iso === hasta;
              const enRango = Boolean(desde && hasta && iso > desde && iso < hasta);
              const clases = [
                'date-range-picker-dia',
                esSeleccionado ? 'date-range-picker-dia--seleccionado' : '',
                enRango ? 'date-range-picker-dia--en-rango' : '',
              ]
                .filter(Boolean)
                .join(' ');
              return (
                <button key={i} type="button" className={clases} onClick={() => manejarClickDia(dia)}>
                  {dia.getDate()}
                </button>
              );
            })}
          </div>

          <div className="date-range-picker-footer">
            <span className="date-range-picker-ayuda">
              Elegí una fecha, o dos para un rango.
            </span>
            <button type="button" className="date-range-picker-limpiar" onClick={limpiar}>
              Limpiar
            </button>
          </div>
        </div>
      )}
    </div>
  );
}
