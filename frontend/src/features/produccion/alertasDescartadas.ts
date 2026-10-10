/**
 * Avisos de priorización que el usuario descartó. Los avisos se calculan en el backend y no se
 * persisten, así que "descartar" es una preferencia de este navegador: se guarda el id del
 * pedido. Si el pedido recibe tanda el aviso deja de existir solo.
 */
const CLAVE = 'weclover.alertasTandaDescartadas';

export function leerAlertasDescartadas(): Set<number> {
  try {
    const crudo = localStorage.getItem(CLAVE);
    return new Set(crudo ? (JSON.parse(crudo) as number[]) : []);
  } catch {
    return new Set();
  }
}

export function guardarAlertasDescartadas(ids: Set<number>): void {
  try {
    localStorage.setItem(CLAVE, JSON.stringify(Array.from(ids)));
  } catch {
    // localStorage puede no estar disponible (modo privado, etc.): el descarte vale solo
    // mientras el popup está abierto.
  }
}
