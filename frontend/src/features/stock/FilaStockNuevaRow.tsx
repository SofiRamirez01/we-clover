import { useState } from 'react';
import ComboboxColor from '../../components/ComboboxColor';
import { extraerMensajeError } from '../../utils/errores';
import type { PaletaColorResponse } from '../../types/paletaColores';
import type { ProveedorResponse, UnidadMedida } from '../../types/proveedor';

export interface NuevaFilaGuardar {
  idPaletaColor: number;
  idProveedor: number | null;
  cantidad: number;
}

interface FilaStockNuevaRowProps {
  /** Colores activos de la tela de la pestaña activa — ya vienen filtrados por el padre. */
  coloresDisponibles: PaletaColorResponse[];
  proveedoresActivos: ProveedorResponse[];
  unidadMedida: UnidadMedida;
  /** Claves `idPaletaColor::idProveedor` ya presentes en la grilla, para no duplicar una fila
   *  que ya se edita inline. */
  clavesExistentes: Set<string>;
  onGuardar: (datos: NuevaFilaGuardar) => Promise<void>;
  onDescartar: () => void;
}

const SIN_PROVEEDOR = 'SIN_PROVEEDOR';

export default function FilaStockNuevaRow({
  coloresDisponibles,
  proveedoresActivos,
  unidadMedida,
  clavesExistentes,
  onGuardar,
  onDescartar,
}: FilaStockNuevaRowProps) {
  const [idPaletaColor, setIdPaletaColor] = useState<number | ''>('');
  const [proveedorTexto, setProveedorTexto] = useState('');
  const [cantidadTexto, setCantidadTexto] = useState('');
  const [guardando, setGuardando] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function handleGuardar() {
    if (idPaletaColor === '') {
      setError('Elegí un color.');
      return;
    }
    if (!proveedorTexto) {
      setError('Elegí un proveedor, o "Sin proveedor" si no se sabe con certeza cuál es.');
      return;
    }
    const idProveedor = proveedorTexto === SIN_PROVEEDOR ? null : Number(proveedorTexto);
    const cantidad = Number(cantidadTexto);
    if (cantidadTexto.trim() === '' || Number.isNaN(cantidad) || cantidad < 0) {
      setError('Ingresá una cantidad válida (no negativa).');
      return;
    }

    const clave = `${idPaletaColor}::${idProveedor ?? 'sin-proveedor'}`;
    if (clavesExistentes.has(clave)) {
      setError('Ya existe una fila para este color y proveedor — editá la cantidad directamente en esa fila.');
      return;
    }

    setGuardando(true);
    setError(null);
    try {
      await onGuardar({ idPaletaColor, idProveedor, cantidad });
    } catch (err) {
      setError(extraerMensajeError(err, 'No se pudo guardar la fila.'));
    } finally {
      setGuardando(false);
    }
  }

  return (
    <tr className="border-b border-wc-border bg-wc-green/5 last:border-0">
      <td className="px-3 py-2">
        <ComboboxColor colores={coloresDisponibles} value={idPaletaColor} onChange={setIdPaletaColor} disabled={guardando} />
      </td>
      <td className="px-3 py-2">
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
      </td>
      <td className="px-3 py-2">
        <div className="flex flex-col gap-1">
          <div className="flex items-center gap-1.5">
            <input
              type="number"
              min={0}
              step="0.01"
              value={cantidadTexto}
              onChange={(e) => setCantidadTexto(e.target.value)}
              disabled={guardando}
              placeholder="0"
              className="w-24 rounded-lg border border-wc-border bg-white px-2 py-1 text-sm text-wc-text"
            />
            <span className="text-xs text-wc-text-muted">{unidadMedida === 'KG' ? 'kg' : 'u.'}</span>
            <button
              type="button"
              onClick={handleGuardar}
              disabled={guardando}
              aria-label="Guardar fila nueva"
              className="rounded-md p-1.5 text-wc-green transition hover:bg-wc-green/10 disabled:cursor-wait disabled:opacity-60"
            >
              <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.2" strokeLinecap="round" strokeLinejoin="round">
                <path d="M4 12.5 9.5 18 20 6" />
              </svg>
            </button>
            <button
              type="button"
              onClick={onDescartar}
              disabled={guardando}
              aria-label="Descartar fila"
              className="rounded-md p-1.5 text-wc-text-muted transition hover:bg-wc-bg disabled:cursor-not-allowed disabled:opacity-60"
            >
              <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.2" strokeLinecap="round" strokeLinejoin="round">
                <path d="M6 6l12 12M18 6 6 18" />
              </svg>
            </button>
          </div>
          {error && <p className="text-xs font-medium text-red-600">{error}</p>}
        </div>
      </td>
      <td className="px-3 py-2 text-xs text-wc-text-muted">—</td>
      <td className="px-3 py-2 text-xs text-wc-text-muted">—</td>
      <td className="px-3 py-2" />
    </tr>
  );
}
