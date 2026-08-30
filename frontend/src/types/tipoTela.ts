/** Catálogo de tipos de tela (backend: entidad TipoTela, GET /api/tipos-tela). */
export interface TipoTelaCatalogo {
  id: number;
  codigo: string;
  nombre: string;
  esPorPeso: boolean;
  telaCuerpo: boolean;
  gramosSugerido: number | null;
}
