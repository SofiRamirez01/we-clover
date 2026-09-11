import { useState } from 'react';
import './HistorialPedidoModal.css';
import { importarPedidosExcel } from '../services/pedidoService';
import { extraerMensajeError } from '../utils/errores';
import type { ImportacionPedidosExcelResponse } from '../types/pedido';

interface ImportarExcelModalProps {
  onClose: () => void;
  onImportado: () => void;
}

type Estado = 'idle' | 'subiendo' | 'listo' | 'error';

export default function ImportarExcelModal({ onClose, onImportado }: ImportarExcelModalProps) {
  const [estado, setEstado] = useState<Estado>('idle');
  const [resultado, setResultado] = useState<ImportacionPedidosExcelResponse | null>(null);
  const [error, setError] = useState<string | null>(null);

  async function manejarArchivoSeleccionado(archivo: File) {
    setEstado('subiendo');
    setError(null);
    try {
      const data = await importarPedidosExcel(archivo);
      setResultado(data);
      setEstado('listo');
      if (data.importados > 0) onImportado();
    } catch (err) {
      setError(extraerMensajeError(err, 'No se pudo importar el archivo. Intentá nuevamente.'));
      setEstado('error');
    }
  }

  function handleChange(e: React.ChangeEvent<HTMLInputElement>) {
    const archivo = e.target.files?.[0];
    if (archivo) manejarArchivoSeleccionado(archivo);
  }

  return (
    <div className="historial-modal-backdrop" onClick={onClose}>
      <div
        className="historial-modal-dialog"
        role="dialog"
        aria-modal="true"
        onClick={(e) => e.stopPropagation()}
      >
        <div className="historial-modal-header">
          <h3>Importar pedidos desde Excel</h3>
          <button type="button" className="historial-modal-cerrar" onClick={onClose} aria-label="Cerrar">
            ×
          </button>
        </div>

        <div className="historial-modal-body">
          {estado === 'idle' && (
            <>
              <p>
                Subí el Excel de exportación de leads de Kommo. Las filas con datos incompletos o
                que no se puedan interpretar (colegio sin localidad, vendedor no encontrado, fecha
                inválida, etc.) se saltean y se listan con el motivo — no se importan con datos
                inventados.
              </p>
              <input
                type="file"
                accept=".xlsx,.xls"
                onChange={handleChange}
              />
            </>
          )}

          {estado === 'subiendo' && <p className="historial-modal-vacio">Importando…</p>}

          {estado === 'error' && (
            <>
              <p className="historial-modal-vacio">{error}</p>
              <button type="button" className="btn-secondary" onClick={() => setEstado('idle')}>
                Reintentar
              </button>
            </>
          )}

          {estado === 'listo' && resultado && (
            <>
              <p>
                {resultado.importados} de {resultado.totalFilas} filas importadas correctamente.
              </p>

              {resultado.pedidosImportados.length > 0 && (
                <table className="historial-modal-tabla">
                  <thead>
                    <tr>
                      <th>Fila</th>
                      <th>Colegio</th>
                      <th>Nº Ficha</th>
                    </tr>
                  </thead>
                  <tbody>
                    {resultado.pedidosImportados.map((p) => (
                      <tr key={p.idPedido}>
                        <td>{p.filaExcel}</td>
                        <td>{p.colegio}</td>
                        <td>{p.codigoInterno}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              )}

              {resultado.filasSalteadas.length > 0 && (
                <>
                  <h4>Filas salteadas ({resultado.filasSalteadas.length})</h4>
                  <table className="historial-modal-tabla">
                    <thead>
                      <tr>
                        <th>Fila</th>
                        <th>Colegio</th>
                        <th>Motivo</th>
                      </tr>
                    </thead>
                    <tbody>
                      {resultado.filasSalteadas.map((f) => (
                        <tr key={f.filaExcel}>
                          <td>{f.filaExcel}</td>
                          <td>{f.colegio ?? '—'}</td>
                          <td>{f.motivo}</td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </>
              )}
            </>
          )}
        </div>
      </div>
    </div>
  );
}
