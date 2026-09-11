export type Punto = [number, number];

export interface SegmentoRecta {
  tipo: 'RECTA';
  puntoInicial: Punto;
  puntoFinal: Punto;
}

export interface SegmentoArco {
  tipo: 'ARCO';
  puntoInicial: Punto;
  puntoFinal: Punto;
  radioCm: number;
  lado: 'IZQUIERDA' | 'DERECHA';
}

export interface SegmentoCirculo {
  tipo: 'CIRCULO';
  centro: Punto;
  radioCm: number;
  anguloInicial: number;
  anguloFinal: number;
}

export type Segmento = SegmentoRecta | SegmentoArco | SegmentoCirculo;

/**
 * Sin margen: área/ancho/largo/perímetro se calculan directamente sobre el polígono tal cual
 * lo definen los segmentos — el margen de costura ya está incluido en las coordenadas medidas
 * de la moldería, no hay nada que sumarle acá.
 */
export interface CalculoBaseResponse {
  coordenadas: Punto[];
  areaCm2: number;
  anchoCm: number;
  largoCm: number;
  perimetroCm: number;
}

export interface PiezaResponse {
  id: number;
  nombre: string;
  idGrupoTalle: number;
  nombreGrupoTalle: string;
  idTalleBase: number;
  talleBase: string;
  simetrica: boolean;
  anchoBaseCm: number;
  largoBaseCm: number;
  activo: boolean;
}

/** Detalle completo (incluye los segmentos originales), para reabrir el editor al editar/duplicar. */
export interface PiezaDetalleResponse {
  id: number;
  nombre: string;
  idGrupoTalle: number;
  nombreGrupoTalle: string;
  idTalleBase: number;
  talleBase: string;
  segmentos: Segmento[];
  simetrica: boolean;
  anchoBaseCm: number;
  largoBaseCm: number;
  activo: boolean;
}

export interface GrupoTalleOption {
  id: number;
  nombre: string;
}

export interface TablaTalleOption {
  id: number;
  talle: string;
  anchoCm: number;
  largoCm: number;
}

/** Una fila existente de PiezaTalle. Un talle de TablaTalleOption sin fila acá está "Pendiente". */
export interface PiezaTalleResponse {
  idTalle: number;
  talle: string;
  coordenadas: Punto[];
  areaCm2: number;
  anchoCm: number;
  largoCm: number;
  perimetroCm: number;
  esBase: boolean;
  editadoManualmente: boolean;
  fechaGeneracion: string;
}

/** El backend no recalcula nada: manda las mismas estadísticas que ya calculó escalarPieza (o el
 * shoelace/perímetro locales, si se editó a mano) del lado del frontend. */
export interface PiezaTalleUpsertPayload {
  coordenadas: Punto[];
  areaCm2: number;
  anchoCm: number;
  largoCm: number;
  perimetroCm: number;
  esBase: boolean;
  editadoManualmente: boolean;
}

export interface PiezaTalleBatchItemPayload {
  idTalle: number;
  coordenadas: Punto[];
  areaCm2: number;
  anchoCm: number;
  largoCm: number;
  perimetroCm: number;
  editadoManualmente: boolean;
}
