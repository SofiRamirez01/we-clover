import { useMemo, useState } from 'react';
import { calcularBasePieza } from '../../services/piezaService';
import { extraerMensajeError } from '../../utils/errores';
import ArcoSagitaEditor from './ArcoSagitaEditor';
import {
  angulosArcoPorTresPuntos,
  circunferenciaPorTresPuntos,
  evaluarContorno,
  extremosSegmento,
  radioYLadoPorSagita,
  sagitaMaxima,
} from './geometriaPieza';
import type { CadenaContorno } from './geometriaPieza';
import type { CalculoBaseResponse, Punto, Segmento } from '../../types/pieza';

type TipoSegmento = Segmento['tipo'];
type ModoCirculo = 'CENTRO_RADIO' | 'TRES_PUNTOS';

interface CamposNuevoSegmento {
  puntoInicialX: string;
  puntoInicialY: string;
  puntoFinalX: string;
  puntoFinalY: string;
  sagitaCm: string;
  centroX: string;
  centroY: string;
  radioCm: string;
  anguloInicial: string;
  anguloFinal: string;
  punto1X: string;
  punto1Y: string;
  punto2X: string;
  punto2Y: string;
  punto3X: string;
  punto3Y: string;
  vueltaCompleta: boolean;
}

const CAMPOS_VACIOS: CamposNuevoSegmento = {
  puntoInicialX: '',
  puntoInicialY: '',
  puntoFinalX: '',
  puntoFinalY: '',
  sagitaCm: '',
  centroX: '0',
  centroY: '0',
  radioCm: '',
  anguloInicial: '0',
  anguloFinal: '360',
  punto1X: '',
  punto1Y: '',
  punto2X: '',
  punto2Y: '',
  punto3X: '',
  punto3Y: '',
  vueltaCompleta: false,
};

function etiquetaSegmento(segmento: Segmento, index: number): string {
  const n = index + 1;
  if (segmento.tipo === 'RECTA') {
    return `${n}. Recta (${segmento.puntoInicial.join(', ')}) → (${segmento.puntoFinal.join(', ')})`;
  }
  if (segmento.tipo === 'ARCO') {
    return `${n}. Arco (${segmento.puntoInicial.join(', ')}) → (${segmento.puntoFinal.join(', ')}), r=${segmento.radioCm}cm, ${segmento.lado.toLowerCase()}`;
  }
  return `${n}. Círculo centro (${segmento.centro.join(', ')}), r=${segmento.radioCm}cm, ${segmento.anguloInicial}°→${segmento.anguloFinal}°`;
}

function construirSubpathSvg(puntos: Punto[], cerrada: boolean): string {
  if (puntos.length === 0) return '';
  const [inicio, ...resto] = puntos;
  const comandoInicial = `M ${inicio[0]} ${-inicio[1]}`;
  const comandosResto = resto.map(([x, y]) => `L ${x} ${-y}`).join(' ');
  return cerrada ? `${comandoInicial} ${comandosResto} Z` : `${comandoInicial} ${comandosResto}`;
}

function construirPathMultiSvg(cadenas: CadenaContorno[], cerradas: boolean): string {
  return cadenas
    .filter((c) => c.cerrada === cerradas && c.puntos.length > 0)
    .map((c) => construirSubpathSvg(c.puntos, c.cerrada))
    .join(' ');
}

/** Los dos extremos (inicio/fin) de cada tramo, sin repetir los que coinciden entre sí. */
function puntosUnicosDeSegmentos(segmentos: Segmento[]): Punto[] {
  const puntos: Punto[] = [];
  for (const segmento of segmentos) {
    for (const punto of extremosSegmento(segmento)) {
      const yaExiste = puntos.some((p) => Math.hypot(p[0] - punto[0], p[1] - punto[1]) < 1e-6);
      if (!yaExiste) puntos.push(punto);
    }
  }
  return puntos;
}

function formatearCm(valor: number): string {
  return (Math.round(valor * 100) / 100).toString();
}

/** "Número lindo" (1, 2, 5, 10, 20, 50...) para el paso de una grilla, al estilo ejes de gráfico. */
function pasoLindoDeGrilla(valorAprox: number): number {
  if (!Number.isFinite(valorAprox) || valorAprox <= 0) return 1;
  const exponente = Math.floor(Math.log10(valorAprox));
  const base = 10 ** exponente;
  const fraccion = valorAprox / base;
  const normalizado = fraccion <= 1 ? 1 : fraccion <= 2 ? 2 : fraccion <= 5 ? 5 : 10;
  return normalizado * base;
}

function multiplosEnRango(min: number, max: number, paso: number): number[] {
  if (paso <= 0) return [];
  const valores: number[] = [];
  const inicio = Math.ceil((min - 1e-6) / paso) * paso;
  for (let v = inicio; v <= max + 1e-6; v += paso) {
    valores.push(Math.round(v / paso) * paso);
  }
  return valores;
}

interface Grilla {
  paso: number;
  /** Rango real de la pieza (todo el viewBox) — se usa para el fondo/las líneas de grilla. */
  realXMin: number;
  realXMax: number;
  realYMin: number;
  realYMax: number;
  /** Rango donde es seguro dibujar ticks/etiquetas sin que se corten contra el borde del viewBox. */
  etiquetaXMin: number;
  etiquetaXMax: number;
  etiquetaYMin: number;
  etiquetaYMax: number;
  lineasX: number[];
  lineasY: number[];
}

/**
 * Grilla en cm que se adapta al tamaño de la pieza: el paso crece de a 1-2-5-10-20-50...
 * (como los ejes de un gráfico) para mantener siempre pocas líneas visibles, así se pueda usar
 * como referencia de escala tanto para una pieza chica (en cm) como una grande (de a 10/20cm).
 *
 * El paso se calcula sobre la dimensión MÁS CHICA del viewBox (no la más grande): si se usara
 * la más grande, una pieza muy angosta (ej. una manga larga y flaca) podía terminar sin ninguna
 * línea vertical, porque ningún múltiplo del paso entraba en un ancho tan chico.
 */
function calcularGrilla(viewBox: string, margenEtiquetas: number): Grilla {
  const [minX, minY, width, height] = viewBox.split(' ').map(Number);
  const paso = pasoLindoDeGrilla(Math.min(width, height) / 5);
  const realXMin = minX;
  const realXMax = minX + width;
  // La Y de pantalla está invertida respecto de la Y real (ver construcción del viewBox).
  const realYMin = -(minY + height);
  const realYMax = -minY;
  const etiquetaXMin = realXMin + margenEtiquetas;
  const etiquetaXMax = realXMax - margenEtiquetas;
  const etiquetaYMin = realYMin + margenEtiquetas;
  const etiquetaYMax = realYMax - margenEtiquetas;
  return {
    paso,
    realXMin,
    realXMax,
    realYMin,
    realYMax,
    etiquetaXMin,
    etiquetaXMax,
    etiquetaYMin,
    etiquetaYMax,
    lineasX: multiplosEnRango(etiquetaXMin, etiquetaXMax, paso),
    lineasY: multiplosEnRango(etiquetaYMin, etiquetaYMax, paso),
  };
}

interface SegmentoEditorProps {
  segmentos: Segmento[];
  onChange: (segmentos: Segmento[]) => void;
  /** Modo "Ver": oculta los controles para agregar/quitar tramos, deja solo la lista y el preview. */
  soloLectura?: boolean;
}

export default function SegmentoEditor({ segmentos, onChange, soloLectura = false }: SegmentoEditorProps) {
  const [tipoNuevo, setTipoNuevo] = useState<TipoSegmento>('RECTA');
  const [modoCirculo, setModoCirculo] = useState<ModoCirculo>('CENTRO_RADIO');
  const [campos, setCampos] = useState<CamposNuevoSegmento>(CAMPOS_VACIOS);
  const [errorSegmento, setErrorSegmento] = useState<string | null>(null);
  const [resultado, setResultado] = useState<CalculoBaseResponse | null>(null);
  const [calculando, setCalculando] = useState(false);
  const [errorCalculo, setErrorCalculo] = useState<string | null>(null);

  function actualizarCampo<K extends keyof CamposNuevoSegmento>(campo: K, valor: CamposNuevoSegmento[K]) {
    setCampos((prev) => ({ ...prev, [campo]: valor }));
  }

  function cambiarTipoNuevo(tipo: TipoSegmento) {
    setTipoNuevo(tipo);
    setErrorSegmento(null);
    const ultimo = segmentos[segmentos.length - 1];
    if (ultimo && (tipo === 'RECTA' || tipo === 'ARCO')) {
      const finAnterior = ultimo.tipo === 'CIRCULO' ? ultimo.centro : ultimo.puntoFinal;
      setCampos({ ...CAMPOS_VACIOS, puntoInicialX: String(finAnterior[0]), puntoInicialY: String(finAnterior[1]) });
    } else {
      setCampos(CAMPOS_VACIOS);
    }
  }

  function numero(valor: string): number | null {
    if (valor.trim() === '') return null;
    const n = Number(valor);
    return Number.isFinite(n) ? n : null;
  }

  function agregarSegmento() {
    setErrorSegmento(null);
    let nuevo: Segmento;

    if (tipoNuevo === 'RECTA') {
      const x0 = numero(campos.puntoInicialX);
      const y0 = numero(campos.puntoInicialY);
      const x1 = numero(campos.puntoFinalX);
      const y1 = numero(campos.puntoFinalY);
      if (x0 === null || y0 === null || x1 === null || y1 === null) {
        setErrorSegmento('Completá los dos puntos de la recta.');
        return;
      }
      nuevo = { tipo: 'RECTA', puntoInicial: [x0, y0], puntoFinal: [x1, y1] };
    } else if (tipoNuevo === 'ARCO') {
      const x0 = numero(campos.puntoInicialX);
      const y0 = numero(campos.puntoInicialY);
      const x1 = numero(campos.puntoFinalX);
      const y1 = numero(campos.puntoFinalY);
      const sagita = numero(campos.sagitaCm);
      if (x0 === null || y0 === null || x1 === null || y1 === null) {
        setErrorSegmento('Completá los dos puntos del arco.');
        return;
      }
      if (sagita === null || sagita === 0) {
        setErrorSegmento('Arrastrá el punto verde (o tipeá cuánto se separa el arco de la recta).');
        return;
      }
      const resultado = radioYLadoPorSagita([x0, y0], [x1, y1], sagita);
      if (!resultado) {
        const maximo = sagitaMaxima([x0, y0], [x1, y1]);
        setErrorSegmento(
          `Esa separación es demasiado grande: como máximo ${maximo.toFixed(1)}cm entre estos dos puntos ` +
            '(más que eso ya sería medio círculo o más, y un arco no puede curvar tanto).',
        );
        return;
      }
      nuevo = { tipo: 'ARCO', puntoInicial: [x0, y0], puntoFinal: [x1, y1], radioCm: resultado.radioCm, lado: resultado.lado };
    } else if (modoCirculo === 'CENTRO_RADIO') {
      const cx = numero(campos.centroX);
      const cy = numero(campos.centroY);
      const radio = numero(campos.radioCm);
      const anguloInicial = numero(campos.anguloInicial);
      const anguloFinal = numero(campos.anguloFinal);
      if (cx === null || cy === null || radio === null || radio <= 0 || anguloInicial === null || anguloFinal === null) {
        setErrorSegmento('Completá el centro, un radio mayor a 0 y los ángulos del círculo.');
        return;
      }
      nuevo = { tipo: 'CIRCULO', centro: [cx, cy], radioCm: radio, anguloInicial, anguloFinal };
    } else {
      const x1 = numero(campos.punto1X);
      const y1 = numero(campos.punto1Y);
      const x2 = numero(campos.punto2X);
      const y2 = numero(campos.punto2Y);
      const x3 = numero(campos.punto3X);
      const y3 = numero(campos.punto3Y);
      if (x1 === null || y1 === null || x2 === null || y2 === null || x3 === null || y3 === null) {
        setErrorSegmento('Completá los 3 puntos sobre la curva.');
        return;
      }
      const a: Punto = [x1, y1];
      const b: Punto = [x2, y2];
      const c: Punto = [x3, y3];
      const circulo = circunferenciaPorTresPuntos(a, b, c);
      if (!circulo) {
        setErrorSegmento('Esos 3 puntos están alineados; no definen un círculo.');
        return;
      }
      let anguloInicial: number;
      let anguloFinal: number;
      if (campos.vueltaCompleta) {
        anguloInicial = (Math.atan2(a[1] - circulo.centro[1], a[0] - circulo.centro[0]) * 180) / Math.PI;
        anguloFinal = anguloInicial + 360;
      } else {
        ({ anguloInicial, anguloFinal } = angulosArcoPorTresPuntos(circulo.centro, a, b, c));
      }
      nuevo = { tipo: 'CIRCULO', centro: circulo.centro, radioCm: circulo.radio, anguloInicial, anguloFinal };
    }

    onChange([...segmentos, nuevo]);
    setResultado(null);
    setErrorCalculo(null);
    cambiarTipoNuevo(tipoNuevo);
  }

  function quitarSegmento(index: number) {
    onChange(segmentos.filter((_, i) => i !== index));
    setResultado(null);
    setErrorCalculo(null);
  }

  const evaluacion = useMemo(() => evaluarContorno(segmentos), [segmentos]);
  const todosLosPuntos = useMemo(
    () => evaluacion.cadenas.flatMap((c) => c.puntos),
    [evaluacion],
  );

  const viewBox = useMemo(() => {
    if (todosLosPuntos.length === 0) return '-10 -10 20 20';
    const xs = todosLosPuntos.map(([x]) => x);
    const ys = todosLosPuntos.map(([, y]) => -y);
    const minX = Math.min(...xs);
    const maxX = Math.max(...xs);
    const minY = Math.min(...ys);
    const maxY = Math.max(...ys);
    // Margen generoso: además de aire alrededor del contorno, tiene que entrar el texto de las
    // etiquetas de coordenadas de los puntos más externos (ver puntosSegmentos más abajo).
    const margen = Math.max(maxX - minX, maxY - minY, 1) * 0.22 + 1.5;
    return `${minX - margen} ${minY - margen} ${maxX - minX + margen * 2} ${maxY - minY + margen * 2}`;
  }, [todosLosPuntos]);

  async function calcular() {
    if (!evaluacion.cerradoCompleto) return;
    setCalculando(true);
    setErrorCalculo(null);
    try {
      const data = await calcularBasePieza(segmentos);
      setResultado(data);
    } catch (error) {
      setResultado(null);
      setErrorCalculo(extraerMensajeError(error, 'No se pudo calcular la geometría de la pieza.'));
    } finally {
      setCalculando(false);
    }
  }

  const puntosSegmentos = useMemo(() => puntosUnicosDeSegmentos(segmentos), [segmentos]);
  const grosorPreview = viewBoxStroke(viewBox);
  const fuentePreview = grosorPreview * 5.5;
  const fuenteGrilla = grosorPreview * 4.5;
  const grilla = useMemo(() => calcularGrilla(viewBox, fuenteGrilla * 3), [viewBox, fuenteGrilla]);

  const arcoP0: Punto | null = numero(campos.puntoInicialX) !== null && numero(campos.puntoInicialY) !== null
    ? [numero(campos.puntoInicialX)!, numero(campos.puntoInicialY)!]
    : null;
  const arcoP1: Punto | null = numero(campos.puntoFinalX) !== null && numero(campos.puntoFinalY) !== null
    ? [numero(campos.puntoFinalX)!, numero(campos.puntoFinalY)!]
    : null;
  const arcoSagita = numero(campos.sagitaCm) ?? 0;

  return (
    <div className="flex flex-col gap-4 rounded-lg border border-wc-border bg-wc-bg p-4">
      <div className="grid grid-cols-1 gap-4 lg:grid-cols-[minmax(0,1fr)_16rem]">
        <div className="flex flex-col gap-3">
          {!soloLectura && (
          <div className="flex flex-wrap items-end gap-3">
            <div className="flex flex-col gap-1.5">
              <label htmlFor="tipo-segmento-nuevo" className="text-xs font-semibold text-wc-text">
                Tipo de tramo
              </label>
              <select
                id="tipo-segmento-nuevo"
                value={tipoNuevo}
                onChange={(e) => cambiarTipoNuevo(e.target.value as TipoSegmento)}
                className="rounded-lg border border-wc-border bg-white px-3 py-2 text-sm text-wc-text outline-none focus:border-wc-green focus:ring-2 focus:ring-wc-green/20"
              >
                <option value="RECTA">Recta</option>
                <option value="ARCO">Arco</option>
                <option value="CIRCULO">Círculo</option>
              </select>
            </div>

            {(tipoNuevo === 'RECTA' || tipoNuevo === 'ARCO') && (
              <>
                <CampoNumero id="segmento-inicio-x" label="Inicio X" value={campos.puntoInicialX} onChange={(v) => actualizarCampo('puntoInicialX', v)} />
                <CampoNumero id="segmento-inicio-y" label="Inicio Y" value={campos.puntoInicialY} onChange={(v) => actualizarCampo('puntoInicialY', v)} />
                <CampoNumero id="segmento-fin-x" label="Fin X" value={campos.puntoFinalX} onChange={(v) => actualizarCampo('puntoFinalX', v)} />
                <CampoNumero id="segmento-fin-y" label="Fin Y" value={campos.puntoFinalY} onChange={(v) => actualizarCampo('puntoFinalY', v)} />
              </>
            )}

            {tipoNuevo === 'ARCO' && (
              arcoP0 && arcoP1 ? (
                <div className="flex items-center gap-3">
                  <ArcoSagitaEditor
                    p0={arcoP0}
                    p1={arcoP1}
                    sagita={arcoSagita}
                    onChange={(v) => actualizarCampo('sagitaCm', String(Math.round(v * 100) / 100))}
                  />
                  <CampoNumero
                    id="segmento-sagita"
                    label="Separación de la recta (cm)"
                    value={campos.sagitaCm}
                    onChange={(v) => actualizarCampo('sagitaCm', v)}
                  />
                </div>
              ) : (
                <p className="text-xs text-wc-text-muted">Completá Inicio y Fin para poder dibujar el arco.</p>
              )
            )}

            {tipoNuevo === 'CIRCULO' && (
              <div className="flex flex-col gap-2">
                <div className="flex flex-col gap-1.5">
                  <label htmlFor="modo-circulo" className="text-xs font-semibold text-wc-text">
                    Definir por
                  </label>
                  <select
                    id="modo-circulo"
                    value={modoCirculo}
                    onChange={(e) => setModoCirculo(e.target.value as ModoCirculo)}
                    className="rounded-lg border border-wc-border bg-white px-3 py-2 text-sm text-wc-text outline-none focus:border-wc-green focus:ring-2 focus:ring-wc-green/20"
                  >
                    <option value="CENTRO_RADIO">Centro y radio</option>
                    <option value="TRES_PUNTOS">3 puntos sobre la curva</option>
                  </select>
                </div>

                {modoCirculo === 'CENTRO_RADIO' ? (
                  <div className="flex flex-wrap items-end gap-3">
                    <CampoNumero id="segmento-centro-x" label="Centro X" value={campos.centroX} onChange={(v) => actualizarCampo('centroX', v)} />
                    <CampoNumero id="segmento-centro-y" label="Centro Y" value={campos.centroY} onChange={(v) => actualizarCampo('centroY', v)} />
                    <CampoNumero id="segmento-radio" label="Radio (cm)" value={campos.radioCm} onChange={(v) => actualizarCampo('radioCm', v)} />
                    <CampoNumero id="segmento-angulo-inicial" label="Ángulo inicial" value={campos.anguloInicial} onChange={(v) => actualizarCampo('anguloInicial', v)} />
                    <CampoNumero id="segmento-angulo-final" label="Ángulo final" value={campos.anguloFinal} onChange={(v) => actualizarCampo('anguloFinal', v)} />
                  </div>
                ) : (
                  <div className="flex flex-wrap items-end gap-3">
                    <CampoNumero id="segmento-p1-x" label="Punto 1 X" value={campos.punto1X} onChange={(v) => actualizarCampo('punto1X', v)} />
                    <CampoNumero id="segmento-p1-y" label="Punto 1 Y" value={campos.punto1Y} onChange={(v) => actualizarCampo('punto1Y', v)} />
                    <CampoNumero id="segmento-p2-x" label="Punto 2 X" value={campos.punto2X} onChange={(v) => actualizarCampo('punto2X', v)} />
                    <CampoNumero id="segmento-p2-y" label="Punto 2 Y" value={campos.punto2Y} onChange={(v) => actualizarCampo('punto2Y', v)} />
                    <CampoNumero id="segmento-p3-x" label="Punto 3 X" value={campos.punto3X} onChange={(v) => actualizarCampo('punto3X', v)} />
                    <CampoNumero id="segmento-p3-y" label="Punto 3 Y" value={campos.punto3Y} onChange={(v) => actualizarCampo('punto3Y', v)} />
                    <label className="flex items-center gap-2 pb-2 text-xs font-semibold text-wc-text">
                      <input
                        type="checkbox"
                        checked={campos.vueltaCompleta}
                        onChange={(e) => actualizarCampo('vueltaCompleta', e.target.checked)}
                        className="h-4 w-4 rounded border-wc-border text-wc-green focus:ring-wc-green/40"
                      />
                      Vuelta completa
                    </label>
                    <p className="w-full text-[10px] text-wc-text-muted">
                      Si no es vuelta completa, el arco va del Punto 1 al Punto 3 pasando por el Punto 2 (el orden importa).
                    </p>
                  </div>
                )}
              </div>
            )}

            <button
              type="button"
              onClick={agregarSegmento}
              className="rounded-lg bg-wc-green/10 px-4 py-2 text-sm font-semibold text-wc-green transition hover:bg-wc-green/20"
            >
              + Agregar tramo
            </button>
          </div>
          )}
          {!soloLectura && errorSegmento && <p className="text-xs font-medium text-red-600">{errorSegmento}</p>}

          <div className="flex flex-col gap-1.5">
            {segmentos.length === 0 && (
              <p className="text-xs text-wc-text-muted">Todavía no agregaste ningún tramo.</p>
            )}
            {segmentos.map((segmento, index) => (
              <div
                key={index}
                className="flex items-center justify-between gap-3 rounded-lg border border-wc-border bg-white px-3 py-1.5 text-xs text-wc-text"
              >
                <span className="truncate">{etiquetaSegmento(segmento, index)}</span>
                {!soloLectura && (
                  <button
                    type="button"
                    onClick={() => quitarSegmento(index)}
                    className="shrink-0 text-red-600 hover:underline"
                  >
                    Quitar
                  </button>
                )}
              </div>
            ))}
          </div>

          <div className="flex items-center gap-3 border-t border-wc-border pt-3">
            <button
              type="button"
              onClick={calcular}
              disabled={!evaluacion.cerradoCompleto || calculando}
              title={!evaluacion.cerradoCompleto ? 'Los tramos todavía no forman un único contorno cerrado' : undefined}
              className="rounded-lg bg-wc-green px-4 py-2 text-sm font-semibold text-white transition hover:bg-wc-green-dark disabled:cursor-not-allowed disabled:opacity-60"
            >
              {calculando ? 'Calculando…' : 'Calcular'}
            </button>
            {resultado && (
              <span className="text-xs text-wc-text-muted">
                Área: {resultado.areaCm2.toFixed(1)}cm² · Ancho: {resultado.anchoCm.toFixed(1)}cm · Largo:{' '}
                {resultado.largoCm.toFixed(1)}cm · Perímetro: {resultado.perimetroCm.toFixed(1)}cm
              </span>
            )}
          </div>
          {errorCalculo && <p className="text-xs font-medium text-red-600">{errorCalculo}</p>}
        </div>

        <div className="flex aspect-square flex-col items-center justify-center gap-2 rounded-lg border border-wc-border bg-white p-3">
          <svg viewBox={viewBox} className="h-full w-full">
            {/*
              Grilla cartesiana en cm, con paso adaptado a la escala de la pieza (ver
              calcularGrilla). Las líneas y sus números se dibujan dentro de un rango recortado
              (etiquetaXMin/Max, etiquetaYMin/Max) un poco más chico que el viewBox real, para
              que el número más externo nunca quede pegado al borde y se corte.
            */}
            <g>
              {grilla.lineasX.map((x) => (
                <line
                  key={`grilla-x-${x}`}
                  x1={x}
                  y1={-grilla.etiquetaYMin}
                  x2={x}
                  y2={-grilla.etiquetaYMax}
                  stroke={Math.abs(x) < 1e-6 ? '#94a3b8' : '#e2e8f0'}
                  strokeWidth={Math.abs(x) < 1e-6 ? grosorPreview * 1.1 : grosorPreview * 0.4}
                />
              ))}
              {grilla.lineasY.map((y) => (
                <line
                  key={`grilla-y-${y}`}
                  x1={grilla.etiquetaXMin}
                  y1={-y}
                  x2={grilla.etiquetaXMax}
                  y2={-y}
                  stroke={Math.abs(y) < 1e-6 ? '#94a3b8' : '#e2e8f0'}
                  strokeWidth={Math.abs(y) < 1e-6 ? grosorPreview * 1.1 : grosorPreview * 0.4}
                />
              ))}
              {grilla.lineasX.map((x) => (
                <text
                  key={`etiqueta-x-${x}`}
                  x={x}
                  y={-grilla.realYMin - fuenteGrilla * 0.8}
                  fontSize={fuenteGrilla}
                  fill="#94a3b8"
                  textAnchor="middle"
                >
                  {formatearCm(x)}
                </text>
              ))}
              {grilla.lineasY.map((y) => (
                <text
                  key={`etiqueta-y-${y}`}
                  x={grilla.realXMin + fuenteGrilla * 0.3}
                  y={-y}
                  fontSize={fuenteGrilla}
                  fill="#94a3b8"
                  textAnchor="start"
                  dominantBaseline="middle"
                >
                  {formatearCm(y)}
                </text>
              ))}
            </g>

            {todosLosPuntos.length > 0 && (
              <>
                <path
                  d={construirPathMultiSvg(evaluacion.cadenas, true)}
                  fill="#2f855a22"
                  stroke="#2f855a"
                  strokeWidth={grosorPreview}
                />
                <path
                  d={construirPathMultiSvg(evaluacion.cadenas, false)}
                  fill="none"
                  stroke="#c05621"
                  strokeDasharray={`${grosorPreview * 3} ${grosorPreview * 2}`}
                  strokeWidth={grosorPreview}
                />
              </>
            )}

            {/*
              Coordenadas de inicio/fin de cada tramo, para ubicarse al cargar el siguiente. El
              anchor se voltea (derecha/izquierda, arriba/abajo) chequeando si el texto entra
              realmente en el espacio disponible hasta el borde del viewBox — no alcanza con
              mirar de qué lado del centro está el punto, porque un punto cerca del borde con
              poco margen igual se cortaba con esa regla.
            */}
            {puntosSegmentos.map(([x, y]) => {
              const separacion = grosorPreview * 2.5;
              const texto = `(${formatearCm(x)}, ${formatearCm(y)})`;
              const anchoTexto = texto.length * fuentePreview * 0.56;
              const cabeADerecha = x + separacion + anchoTexto <= grilla.realXMax;
              const cabeArriba = y + separacion + fuentePreview <= grilla.realYMax;
              return (
                <g key={`punto-${x}-${y}`}>
                  <circle cx={x} cy={-y} r={grosorPreview * 1.6} fill="#1e293b" />
                  <text
                    x={cabeADerecha ? x + separacion : x - separacion}
                    y={cabeArriba ? -y - separacion : -y + separacion + fuentePreview * 0.8}
                    fontSize={fuentePreview}
                    fill="#1e293b"
                    textAnchor={cabeADerecha ? 'start' : 'end'}
                  >
                    {texto}
                  </text>
                </g>
              );
            })}
          </svg>
          {segmentos.length === 0 && (
            <p className="text-center text-xs text-wc-text-muted">Agregá tramos para ver el contorno.</p>
          )}
          {segmentos.length > 0 && !evaluacion.cerradoCompleto && (
            <p className="text-center text-[11px] text-orange-700">
              {evaluacion.cadenas.length > 1
                ? `Hay ${evaluacion.cadenas.length} grupos de tramos sin conectar entre sí (en naranja).`
                : 'El contorno todavía no cierra: falta que el último tramo vuelva al punto de partida.'}
            </p>
          )}
        </div>
      </div>
    </div>
  );
}

/**
 * El contenedor del preview es cuadrado, pero el viewBox no siempre lo es (una pieza angosta y
 * larga da un viewBox alto y flaco). El SVG ajusta por la dimensión MÁS GRANDE para no recortar
 * nada (preserveAspectRatio "meet"), así que la escala real en pantalla la define esa dimensión,
 * no el ancho a secas — si solo se mirara el ancho, una pieza angosta terminaba con texto y
 * trazos microscópicos.
 */
function viewBoxStroke(viewBox: string): number {
  const partes = viewBox.split(' ').map(Number);
  const escala = Math.max(partes[2] || 20, partes[3] || 20);
  return Math.max(escala / 150, 0.05);
}

function CampoNumero({ id, label, value, onChange }: { id: string; label: string; value: string; onChange: (v: string) => void }) {
  return (
    <div className="flex flex-col gap-1.5">
      <label htmlFor={id} className="text-xs font-semibold text-wc-text">
        {label}
      </label>
      <input
        id={id}
        type="number"
        value={value}
        onChange={(e) => onChange(e.target.value)}
        className="w-24 rounded-lg border border-wc-border bg-white px-2 py-2 text-sm text-wc-text outline-none focus:border-wc-green focus:ring-2 focus:ring-wc-green/20"
      />
    </div>
  );
}
