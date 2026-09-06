import type { GrupoTallaResponse } from '../../types/cargaTalles';

/**
 * Espeja `CargaTallesService.calcularTalle` del backend — vista previa del lado del cliente,
 * apenas se completa el largo, sin esperar al guardado (que sigue siendo la fuente de verdad:
 * el backend recalcula igual al guardar). Misma regla: primera fila de la tabla, en orden, cuyo
 * ancho Y largo sean mayores o iguales a la medida ingresada, sin margen.
 */
export function calcularTalleCliente(
  tabla: GrupoTallaResponse | undefined,
  anchoCm: number,
  largoCm: number,
): { talle: string | null; personalizado: boolean } {
  if (!tabla) return { talle: null, personalizado: false };
  const fila = tabla.filas.find((f) => f.anchoCm >= anchoCm && f.largoCm >= largoCm);
  return fila ? { talle: fila.talle, personalizado: false } : { talle: null, personalizado: true };
}
