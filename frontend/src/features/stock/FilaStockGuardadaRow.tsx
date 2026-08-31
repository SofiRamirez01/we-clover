import { useState } from 'react';
import ComboboxColor from '../../components/ComboboxColor';
import ModalConfirmacion from '../../components/ModalConfirmacion';
import { extraerMensajeError } from '../../utils/errores';
import type { PaletaColorResponse } from '../../types/paletaColores';
import type { ProveedorResponse, UnidadMedida } from '../../types/proveedor';

export interface FilaStockGuardada {
  clave: string;
  id: number;
  idPaletaColor: number;
  nombreColor: string;
  hexColor: string;
  codigoTipoTela: string;
  idProveedor: number | null;
  nombreProveedor: string | null;
  cantidad: number;
  unidadMedida: UnidadMedida;
  fechaUltimaActualizacion: string;
  nombreActualizadoPor: string;
}

export interface FilaExistenteGuardar {
  idPaletaColor: number;
  idProveedor: number | null;
  cantidad: number;
}

interface FilaStockGuardadaRowProps {
  fila: FilaStockGuardada;
  /** Colores activos de la tela de esta pestaña — el color solo se puede cambiar dentro de la
   *  misma tela (para cambiar de tela hay que borrar y agregar en la pestaña correspondiente). */
  coloresDisponibles: PaletaColorResponse[];
  proveedoresActivos: ProveedorResponse[];
  /** Claves `idPaletaColor::idProveedor` de toda la grilla (incluida esta fila), para no dejar
   *  editar hacia una combinación que ya usa otra fila. */
  clavesExistentes: Set<string>;
  onGuardar: (fila: FilaStockGuardada, nuevo: FilaExistenteGuardar) => Promise<void>;
  onPedirEliminar: (fila: FilaStockGuardada) => void;
}

const SIN_PROVEEDOR = 'SIN_PROVEEDOR';

function formatearCantidad(cantidad: number, unidadMedida: UnidadMedida): string {
  return `${cantidad.toLocaleString('es-AR', { maximumFractionDigits: 2 })} ${unidadMedida === 'KG' ? 'kg' : 'u.'}`;
}

function formatearFecha(fecha: string): string {
  return new Date(fecha).toLocaleString('es-AR', { dateStyle: 'short', timeStyle: 'short' });
}

/**
 * Fila ya persistida: el lápiz desbloquea la fila completa (color, proveedor y cantidad), no
 * solo la cantidad — por si al auditar se dieron cuenta de que cargaron mal el proveedor y
 * quieren corregirlo ahí mismo, sin borrar y crear una fila nueva. El desbloqueo explícito
 * sigue siendo a propósito: así "última actualización"/"actualizado por" reflejan una acción
 * real del usuario, no cada tecla tipeada. Cambiar color/proveedor cambia la identidad de la
 * fila en el backend (Stock se identifica por articulo+proveedor) — el padre se encarga de
 * crear la fila nueva y borrar la vieja cuando eso pasa (ver StockView.handleGuardarFilaExistente).
 */
export default function FilaStockGuardadaRow({
  fila,
  coloresDisponibles,
  proveedoresActivos,
  clavesExistentes,
  onGuardar,
  onPedirEliminar,
}: FilaStockGuardadaRowProps) {
  const [editando, setEditando] = useState(false);
  const [idPaletaColor, setIdPaletaColor] = useState<number | ''>(fila.idPaletaColor);
  const [proveedorTexto, setProveedorTexto] = useState(fila.idProveedor != null ? String(fila.idProveedor) : SIN_PROVEEDOR);
  const [cantidadTexto, setCantidadTexto] = useState(String(fila.cantidad));
  const [guardando, setGuardando] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [mostrarConfirmacion, setMostrarConfirmacion] = useState(false);

  function iniciarEdicion() {
    setIdPaletaColor(fila.idPaletaColor);
    setProveedorTexto(fila.idProveedor != null ? String(fila.idProveedor) : SIN_PROVEEDOR);
    setCantidadTexto(String(fila.cantidad));
    setError(null);
    setEditando(true);
  }

  function cancelarEdicion() {
    setEditando(false);
    setError(null);
  }

  function datosIngresados(): FilaExistenteGuardar | null {
    if (idPaletaColor === '' || !proveedorTexto) return null;
    const cantidad = Number(cantidadTexto);
    if (cantidadTexto.trim() === '' || Number.isNaN(cantidad) || cantidad < 0) return null;
    return {
      idPaletaColor,
      idProveedor: proveedorTexto === SIN_PROVEEDOR ? null : Number(proveedorTexto),
      cantidad,
    };
  }

  function pedirGuardar() {
    setError(null);
    if (idPaletaColor === '') {
      setError('Elegí un color.');
      return;
    }
    if (!proveedorTexto) {
      setError('Elegí un proveedor, o "Sin proveedor" si no se sabe con certeza cuál es.');
      return;
    }
    const cantidad = Number(cantidadTexto);
    if (cantidadTexto.trim() === '' || Number.isNaN(cantidad) || cantidad < 0) {
      setError('Ingresá una cantidad válida (no negativa).');
      return;
    }
    const idProveedor = proveedorTexto === SIN_PROVEEDOR ? null : Number(proveedorTexto);

    const sinCambios = idPaletaColor === fila.idPaletaColor && idProveedor === fila.idProveedor && cantidad === fila.cantidad;
    if (sinCambios) {
      setEditando(false);
      return;
    }

    const claveNueva = `${idPaletaColor}::${idProveedor ?? 'sin-proveedor'}`;
    if (claveNueva !== fila.clave && clavesExistentes.has(claveNueva)) {
      setError('Ya existe una fila para ese color y proveedor — no se puede duplicar.');
      return;
    }

    setMostrarConfirmacion(true);
  }

  async function confirmarGuardar() {
    const datos = datosIngresados();
    if (!datos) return;
    setMostrarConfirmacion(false);
    setGuardando(true);
    setError(null);
    try {
      await onGuardar(fila, datos);
      setEditando(false);
    } catch (err) {
      setError(extraerMensajeError(err, 'No se pudo guardar.'));
    } finally {
      setGuardando(false);
    }
  }

  const datosParaConfirmar = mostrarConfirmacion ? datosIngresados() : null;
  const colorNuevo = datosParaConfirmar ? coloresDisponibles.find((c) => c.id === datosParaConfirmar.idPaletaColor) : null;
  const proveedorNuevo = datosParaConfirmar?.idProveedor != null
    ? proveedoresActivos.find((p) => p.id === datosParaConfirmar.idProveedor)
    : null;

  return (
    <>
      <tr className="border-b border-wc-border last:border-0">
        <td className="px-3 py-2 text-wc-text">
          {editando ? (
            <ComboboxColor colores={coloresDisponibles} value={idPaletaColor} onChange={setIdPaletaColor} disabled={guardando} />
          ) : (
            <div className="flex items-center gap-2">
              <span className="h-3.5 w-3.5 shrink-0 rounded-full border border-wc-border" style={{ backgroundColor: fila.hexColor }} />
              <span>{fila.nombreColor}</span>
            </div>
          )}
        </td>
        <td className="px-3 py-2 text-wc-text-muted">
          {editando ? (
            <select
              value={proveedorTexto}
              onChange={(e) => setProveedorTexto(e.target.value)}
              disabled={guardando}
              className="rounded-lg border border-wc-border bg-white px-2 py-1.5 text-sm text-wc-text"
            >
              <option value="">Elegí un proveedor…</option>
              <option value={SIN_PROVEEDOR}>Sin proveedor (desconocido)</option>
              {proveedoresActivos.map((proveedor) => (
                <option key={proveedor.id} value={proveedor.id}>
                  {proveedor.nombre}
                </option>
              ))}
            </select>
          ) : (
            fila.nombreProveedor ?? 'Sin proveedor'
          )}
        </td>
        <td className="px-3 py-2">
          {editando ? (
            <div className="flex items-center gap-1.5">
              <input
                type="number"
                min={0}
                step="0.01"
                value={cantidadTexto}
                onChange={(e) => setCantidadTexto(e.target.value)}
                disabled={guardando}
                className="w-24 rounded-lg border border-wc-green bg-wc-green/5 px-2 py-1 text-sm text-wc-text"
              />
              <span className="text-xs text-wc-text-muted">{fila.unidadMedida === 'KG' ? 'kg' : 'u.'}</span>
            </div>
          ) : (
            <span className="text-wc-text">{formatearCantidad(fila.cantidad, fila.unidadMedida)}</span>
          )}
        </td>
        <td className="px-3 py-2 text-wc-text-muted">{formatearFecha(fila.fechaUltimaActualizacion)}</td>
        <td className="px-3 py-2 text-wc-text-muted">{fila.nombreActualizadoPor}</td>
        <td className="px-3 py-2 text-right">
          {editando ? (
            <div className="inline-flex items-center gap-1">
              <button
                type="button"
                onClick={pedirGuardar}
                disabled={guardando}
                aria-label="Guardar fila"
                className="rounded-md p-1.5 text-wc-green transition hover:bg-wc-green/10 disabled:cursor-wait disabled:opacity-60"
              >
                <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.2" strokeLinecap="round" strokeLinejoin="round">
                  <path d="M4 12.5 9.5 18 20 6" />
                </svg>
              </button>
              <button
                type="button"
                onClick={cancelarEdicion}
                disabled={guardando}
                aria-label="Cancelar edición"
                className="rounded-md p-1.5 text-wc-text-muted transition hover:bg-wc-bg disabled:cursor-not-allowed disabled:opacity-60"
              >
                <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.2" strokeLinecap="round" strokeLinejoin="round">
                  <path d="M6 6l12 12M18 6 6 18" />
                </svg>
              </button>
            </div>
          ) : (
            <div className="inline-flex items-center gap-1">
              <button
                type="button"
                onClick={iniciarEdicion}
                aria-label="Editar fila"
                className="rounded-md p-1.5 text-wc-text-muted transition hover:bg-wc-bg hover:text-wc-text"
              >
                <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round">
                  <path d="M16.5 3.5a2.1 2.1 0 0 1 3 3L7 19 3 20l1-4Z" />
                </svg>
              </button>
              <button
                type="button"
                onClick={() => onPedirEliminar(fila)}
                aria-label="Eliminar fila"
                className="rounded-md p-1.5 text-wc-text-muted transition hover:bg-red-50 hover:text-red-600"
              >
                <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round">
                  <path d="M4 7h16" />
                  <path d="M9 7V4.5A1.5 1.5 0 0 1 10.5 3h3A1.5 1.5 0 0 1 15 4.5V7" />
                  <path d="M6 7l1 13.5A1.5 1.5 0 0 0 8.5 22h7a1.5 1.5 0 0 0 1.5-1.5L18 7" />
                </svg>
              </button>
            </div>
          )}
        </td>
      </tr>

      {editando && error && (
        <tr>
          <td colSpan={6} className="px-3 pb-2">
            <p className="text-xs font-medium text-red-600">{error}</p>
          </td>
        </tr>
      )}

      {mostrarConfirmacion && datosParaConfirmar && (
        <tr>
          <td colSpan={6} className="p-0">
            <ModalConfirmacion
              titulo="¿Guardar cambios de esta fila?"
              mensaje={`${fila.nombreColor} (${fila.nombreProveedor ?? 'sin proveedor'}) · ${formatearCantidad(fila.cantidad, fila.unidadMedida)} → ${colorNuevo?.nombre ?? fila.nombreColor} (${datosParaConfirmar.idProveedor != null ? proveedorNuevo?.nombre ?? '' : 'sin proveedor'}) · ${formatearCantidad(datosParaConfirmar.cantidad, fila.unidadMedida)}`}
              onCerrar={() => setMostrarConfirmacion(false)}
              acciones={[
                { label: 'Sí, guardar', variante: 'primaria', onClick: confirmarGuardar },
                { label: 'Seguir editando', variante: 'secundaria', onClick: () => setMostrarConfirmacion(false) },
              ]}
            />
          </td>
        </tr>
      )}
    </>
  );
}
