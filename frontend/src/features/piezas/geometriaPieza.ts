import type { Punto, Segmento, SegmentoArco, SegmentoCirculo, SegmentoRecta } from '../../types/pieza';

/**
 * Réplica en TS de services/pieza-geometria/app/geometria.py: arma las mismas cadenas y
 * tesselación que el servicio de geometría, para que la vista previa del editor coincida con
 * lo que se va a calcular (mismos puntos que se mandan a /piezas/calcular-base).
 *
 * Los tramos NO se conectan por el orden en que se cargaron: se cosen entre sí únicamente
 * donde sus extremos coinciden (dentro de EPSILON_CM), igual que en el servicio de geometría.
 * Esto permite cargar tramos "sueltos" en cualquier orden y ver dónde falta cerrar el contorno.
 */
const DENSIDAD_CM_POR_PUNTO = 1;
const MINIMO_PUNTOS_ARCO = 8;
const EPSILON_CM = 1e-6;

function distancia(a: Punto, b: Punto): number {
  return Math.hypot(a[0] - b[0], a[1] - b[1]);
}

function cantidadPuntos(longitudArcoCm: number): number {
  return Math.max(MINIMO_PUNTOS_ARCO, Math.ceil(longitudArcoCm / DENSIDAD_CM_POR_PUNTO) + 1);
}

function tesselarArcoPorCentro(centro: Punto, radio: number, anguloInicialRad: number, anguloFinalRad: number): Punto[] {
  const longitudArco = radio * Math.abs(anguloFinalRad - anguloInicialRad);
  const cantidad = cantidadPuntos(longitudArco);
  const puntos: Punto[] = [];
  for (let i = 0; i < cantidad; i++) {
    const t = i / (cantidad - 1);
    const angulo = anguloInicialRad + t * (anguloFinalRad - anguloInicialRad);
    puntos.push([centro[0] + radio * Math.cos(angulo), centro[1] + radio * Math.sin(angulo)]);
  }
  return puntos;
}

function tesselarRecta(segmento: SegmentoRecta): Punto[] {
  return [segmento.puntoInicial, segmento.puntoFinal];
}

function tesselarArco(segmento: SegmentoArco): Punto[] | null {
  const [p0, p1] = [segmento.puntoInicial, segmento.puntoFinal];
  const radio = segmento.radioCm;
  const dist = distancia(p0, p1);

  if (dist < EPSILON_CM || radio < dist / 2 - EPSILON_CM) {
    return null;
  }

  const medio: Punto = [(p0[0] + p1[0]) / 2, (p0[1] + p1[1]) / 2];
  const ux = (p1[0] - p0[0]) / dist;
  const uy = (p1[1] - p0[1]) / dist;
  const a = Math.sqrt(Math.max(radio * radio - (dist / 2) ** 2, 0));

  const perpIzquierda: Punto = [-uy, ux];
  const perpDerecha: Punto = [uy, -ux];
  const perp = segmento.lado === 'IZQUIERDA' ? perpIzquierda : perpDerecha;
  const centro: Punto = [medio[0] + perp[0] * a, medio[1] + perp[1] * a];

  const anguloP0 = Math.atan2(p0[1] - centro[1], p0[0] - centro[0]);
  const anguloP1 = Math.atan2(p1[1] - centro[1], p1[0] - centro[0]);

  let diferencia = anguloP1 - anguloP0;
  while (diferencia <= -Math.PI) diferencia += 2 * Math.PI;
  while (diferencia > Math.PI) diferencia -= 2 * Math.PI;

  return tesselarArcoPorCentro(centro, radio, anguloP0, anguloP0 + diferencia);
}

function tesselarCirculo(segmento: SegmentoCirculo): Punto[] {
  const anguloInicialRad = (segmento.anguloInicial * Math.PI) / 180;
  const anguloFinalRad = (segmento.anguloFinal * Math.PI) / 180;
  return tesselarArcoPorCentro(segmento.centro, segmento.radioCm, anguloInicialRad, anguloFinalRad);
}

function tesselarSegmento(segmento: Segmento): Punto[] | null {
  if (segmento.tipo === 'RECTA') return tesselarRecta(segmento);
  if (segmento.tipo === 'ARCO') return tesselarArco(segmento);
  return tesselarCirculo(segmento);
}

/**
 * Un arco MENOR (el único que arma tesselarArco, por diseño: "siempre se tesela el arco
 * menor") no puede curvar más que un semicírculo — y en ese punto límite exacto la sagita
 * (la separación respecto de la recta) es igual a la mitad de la cuerda. Pedir una sagita
 * mayor no da "un arco más curvado": matemáticamente cae en el arco MAYOR de otro círculo más
 * chico, que acá no se soporta, y el arco resultante quedaría más chato que lo pedido. Por eso
 * se limita a un poco menos del semicírculo (factor de seguridad para no pisar el límite exacto).
 */
const FACTOR_SEGURIDAD_SAGITA = 0.97;

/** Máxima sagita (en valor absoluto) representable como arco menor entre estos dos puntos. */
export function sagitaMaxima(p0: Punto, p1: Punto): number {
  return (distancia(p0, p1) / 2) * FACTOR_SEGURIDAD_SAGITA;
}

/** Recorta una sagita al rango representable como arco menor entre p0 y p1. */
export function clamparSagita(p0: Punto, p1: Punto, sagitaFirmada: number): number {
  const maximo = sagitaMaxima(p0, p1);
  if (maximo < EPSILON_CM) return 0;
  return Math.max(-maximo, Math.min(maximo, sagitaFirmada));
}

/**
 * Convierte una "sagita" (cuánto se separa el arco de la recta entre dos puntos, en su punto
 * medio) a radioCm + lado — así el usuario no necesita medir ni el radio ni el centro, que para
 * arcos muy abiertos/círculos grandes suelen caer muy lejos de las medidas reales de la pieza.
 * La sagita se mide sobre el eje perpIzquierda (mismo eje que usa tesselarArco para "lado"):
 * positiva = hacia la izquierda del sentido p0->p1, negativa = hacia la derecha.
 *
 * Como el arco siempre queda del lado OPUESTO al centro (ver tesselarArco), una sagita positiva
 * (bulto a la izquierda) implica centro a la derecha => lado "DERECHA", y viceversa.
 *
 * Devuelve null si la sagita es 0, la cuerda es 0, o la sagita supera el máximo de
 * `sagitaMaxima` (ver comentario ahí — no hay arco menor posible con esa separación).
 */
export function radioYLadoPorSagita(
  p0: Punto,
  p1: Punto,
  sagitaFirmada: number,
): { radioCm: number; lado: 'IZQUIERDA' | 'DERECHA' } | null {
  const cuerda = distancia(p0, p1);
  const sagita = Math.abs(sagitaFirmada);
  if (cuerda < EPSILON_CM || sagita < EPSILON_CM) return null;
  if (sagita > sagitaMaxima(p0, p1)) return null;

  const radioCm = (sagita * sagita + (cuerda / 2) ** 2) / (2 * sagita);
  const lado: 'IZQUIERDA' | 'DERECHA' = sagitaFirmada > 0 ? 'DERECHA' : 'IZQUIERDA';
  return { radioCm, lado };
}

/**
 * Punto del "bulto" del arco para una sagita dada, usado para dibujar/arrastrar el handle.
 * Recorta la sagita al máximo representable para que el handle nunca se "despegue" de la
 * curva real (ver clamparSagita/sagitaMaxima) — si se pide más, el handle se queda clavado en
 * el límite (el semicírculo), igual que al arrastrar contra una pared.
 */
export function puntoPorSagita(p0: Punto, p1: Punto, sagitaFirmada: number): Punto {
  const medio: Punto = [(p0[0] + p1[0]) / 2, (p0[1] + p1[1]) / 2];
  const cuerda = distancia(p0, p1);
  if (cuerda < EPSILON_CM) return medio;
  const sagitaRecortada = clamparSagita(p0, p1, sagitaFirmada);
  const ux = (p1[0] - p0[0]) / cuerda;
  const uy = (p1[1] - p0[1]) / cuerda;
  const perpIzquierda: Punto = [-uy, ux];
  return [medio[0] + perpIzquierda[0] * sagitaRecortada, medio[1] + perpIzquierda[1] * sagitaRecortada];
}

/** Proyecta un punto arbitrario (ej. el cursor al arrastrar) sobre el eje de la sagita. */
export function sagitaDePunto(p0: Punto, p1: Punto, punto: Punto): number {
  const cuerda = distancia(p0, p1);
  if (cuerda < EPSILON_CM) return 0;
  const ux = (p1[0] - p0[0]) / cuerda;
  const uy = (p1[1] - p0[1]) / cuerda;
  const perpIzquierda: Punto = [-uy, ux];
  const medio: Punto = [(p0[0] + p1[0]) / 2, (p0[1] + p1[1]) / 2];
  const rel: Punto = [punto[0] - medio[0], punto[1] - medio[1]];
  return rel[0] * perpIzquierda[0] + rel[1] * perpIzquierda[1];
}

/**
 * Contorno del arco resultante para una sagita dada, para previsualizarlo mientras se arrastra.
 * Recorta la sagita igual que puntoPorSagita, para que la curva dibujada siempre pase
 * exactamente por el handle (nunca se "despega" aunque se pida más de lo representable).
 */
export function previsualizarArcoPorSagita(p0: Punto, p1: Punto, sagitaFirmada: number): Punto[] | null {
  const resultado = radioYLadoPorSagita(p0, p1, clamparSagita(p0, p1, sagitaFirmada));
  if (!resultado) return null;
  return tesselarArco({ tipo: 'ARCO', puntoInicial: p0, puntoFinal: p1, radioCm: resultado.radioCm, lado: resultado.lado });
}

/**
 * Centro y radio del único círculo que pasa por 3 puntos — alternativa a tipear centro+radio
 * para círculos grandes, donde el centro cae muy lejos de las medidas reales de la pieza.
 * Devuelve null si los 3 puntos están alineados (no hay un círculo único).
 */
export function circunferenciaPorTresPuntos(a: Punto, b: Punto, c: Punto): { centro: Punto; radio: number } | null {
  const d = 2 * (a[0] * (b[1] - c[1]) + b[0] * (c[1] - a[1]) + c[0] * (a[1] - b[1]));
  if (Math.abs(d) < EPSILON_CM) return null;

  const aSq = a[0] ** 2 + a[1] ** 2;
  const bSq = b[0] ** 2 + b[1] ** 2;
  const cSq = c[0] ** 2 + c[1] ** 2;
  const cx = (aSq * (b[1] - c[1]) + bSq * (c[1] - a[1]) + cSq * (a[1] - b[1])) / d;
  const cy = (aSq * (c[0] - b[0]) + bSq * (a[0] - c[0]) + cSq * (b[0] - a[0])) / d;
  const centro: Punto = [cx, cy];
  return { centro, radio: distancia(centro, a) };
}

/**
 * Ángulos (en grados) para ir de `a` a `c` alrededor de `centro` pasando por `b` — así el
 * usuario no tiene que calcular a mano en qué ángulo cae cada punto ni para qué lado girar.
 */
export function angulosArcoPorTresPuntos(
  centro: Punto,
  a: Punto,
  b: Punto,
  c: Punto,
): { anguloInicial: number; anguloFinal: number } {
  const anguloA = Math.atan2(a[1] - centro[1], a[0] - centro[0]);
  const anguloB = Math.atan2(b[1] - centro[1], b[0] - centro[0]);
  const anguloC = Math.atan2(c[1] - centro[1], c[0] - centro[0]);

  const normalizar = (angulo: number) => {
    let diferencia = angulo - anguloA;
    while (diferencia < 0) diferencia += 2 * Math.PI;
    while (diferencia >= 2 * Math.PI) diferencia -= 2 * Math.PI;
    return diferencia;
  };

  const deltaB = normalizar(anguloB);
  const deltaC = normalizar(anguloC);
  const deltaFinal = deltaB <= deltaC ? deltaC : deltaC - 2 * Math.PI;

  return {
    anguloInicial: (anguloA * 180) / Math.PI,
    anguloFinal: ((anguloA + deltaFinal) * 180) / Math.PI,
  };
}

/** Los dos puntos "de enganche" de un tramo, sin tesselar (para decidir cómo se cosen entre sí). */
export function extremosSegmento(segmento: Segmento): [Punto, Punto] {
  if (segmento.tipo === 'CIRCULO') {
    const anguloInicialRad = (segmento.anguloInicial * Math.PI) / 180;
    const anguloFinalRad = (segmento.anguloFinal * Math.PI) / 180;
    const p1: Punto = [
      segmento.centro[0] + segmento.radioCm * Math.cos(anguloInicialRad),
      segmento.centro[1] + segmento.radioCm * Math.sin(anguloInicialRad),
    ];
    const p2: Punto = [
      segmento.centro[0] + segmento.radioCm * Math.cos(anguloFinalRad),
      segmento.centro[1] + segmento.radioCm * Math.sin(anguloFinalRad),
    ];
    return [p1, p2];
  }
  return [segmento.puntoInicial, segmento.puntoFinal];
}

interface TramoEnCadena {
  indice: number;
  invertido: boolean;
}

interface CadenaEnConstruccion {
  tramos: TramoEnCadena[];
  extremoInicio: Punto;
  extremoFin: Punto;
  cerrada: boolean;
}

function invertirTramos(tramos: TramoEnCadena[]): TramoEnCadena[] {
  return [...tramos].reverse().map(({ indice, invertido }) => ({ indice, invertido: !invertido }));
}

/** Cose los tramos entre sí solo donde sus extremos coinciden — nunca por orden de carga. */
function construirCadenas(segmentos: Segmento[]): CadenaEnConstruccion[] {
  const extremos = segmentos.map(extremosSegmento);
  let cadenas: CadenaEnConstruccion[] = segmentos.map((_, indice) => {
    const [a, b] = extremos[indice];
    return {
      tramos: [{ indice, invertido: false }],
      extremoInicio: a,
      extremoFin: b,
      cerrada: distancia(a, b) < EPSILON_CM,
    };
  });

  let cambiado = true;
  while (cambiado) {
    cambiado = false;
    for (let i = 0; i < cadenas.length && !cambiado; i++) {
      const c1 = cadenas[i];
      if (c1.cerrada) continue;
      for (let j = 0; j < cadenas.length; j++) {
        if (i === j) continue;
        const c2 = cadenas[j];
        if (c2.cerrada) continue;

        if (distancia(c1.extremoFin, c2.extremoInicio) < EPSILON_CM) {
          c1.tramos = [...c1.tramos, ...c2.tramos];
          c1.extremoFin = c2.extremoFin;
        } else if (distancia(c1.extremoFin, c2.extremoFin) < EPSILON_CM) {
          c1.tramos = [...c1.tramos, ...invertirTramos(c2.tramos)];
          c1.extremoFin = c2.extremoInicio;
        } else if (distancia(c1.extremoInicio, c2.extremoFin) < EPSILON_CM) {
          c1.tramos = [...c2.tramos, ...c1.tramos];
          c1.extremoInicio = c2.extremoInicio;
        } else if (distancia(c1.extremoInicio, c2.extremoInicio) < EPSILON_CM) {
          c1.tramos = [...invertirTramos(c2.tramos), ...c1.tramos];
          c1.extremoInicio = c2.extremoFin;
        } else {
          continue;
        }

        cadenas = cadenas.filter((_, k) => k !== j);
        if (distancia(c1.extremoInicio, c1.extremoFin) < EPSILON_CM) {
          c1.cerrada = true;
        }
        cambiado = true;
        break;
      }
    }
  }

  return cadenas;
}

export interface CadenaContorno {
  /** Vacío si algún tramo de esta cadena es geométricamente inválido (ej. arco con radio imposible). */
  puntos: Punto[];
  cerrada: boolean;
  indicesSegmentos: number[];
}

export interface EvaluacionContorno {
  cadenas: CadenaContorno[];
  /** true solo si TODOS los tramos forman una única cadena cerrada (lista para calcular/guardar). */
  cerradoCompleto: boolean;
}

export function evaluarContorno(segmentos: Segmento[]): EvaluacionContorno {
  if (segmentos.length === 0) {
    return { cadenas: [], cerradoCompleto: false };
  }

  const cadenas = construirCadenas(segmentos);

  const resultado: CadenaContorno[] = cadenas.map((cadena) => {
    const puntos: Punto[] = [];
    for (const { indice, invertido } of cadena.tramos) {
      let tramo = tesselarSegmento(segmentos[indice]);
      if (!tramo) return { puntos: [], cerrada: cadena.cerrada, indicesSegmentos: cadena.tramos.map((t) => t.indice) };
      if (invertido) tramo = [...tramo].reverse();
      if (puntos.length > 0 && distancia(puntos[puntos.length - 1], tramo[0]) < EPSILON_CM) {
        tramo = tramo.slice(1);
      }
      puntos.push(...tramo);
    }
    if (cadena.cerrada && puntos.length > 1 && distancia(puntos[0], puntos[puntos.length - 1]) < EPSILON_CM) {
      puntos.pop();
    }
    return { puntos, cerrada: cadena.cerrada, indicesSegmentos: cadena.tramos.map((t) => t.indice) };
  });

  const cerradoCompleto =
    resultado.length === 1 &&
    resultado[0].cerrada &&
    resultado[0].puntos.length >= 3 &&
    resultado[0].indicesSegmentos.length === segmentos.length;

  return { cadenas: resultado, cerradoCompleto };
}
