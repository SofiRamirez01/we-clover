import { useEffect, useState } from 'react';
import './HistorialPedidoModal.css';
import { obtenerHistorialPedido } from '../services/pedidoService';
import { extraerMensajeError } from '../utils/errores';
import { ESTADO_PEDIDO_LABELS } from '../types/pedido';
import type { HistorialEstadoPedidoResponse, PedidoResponse } from '../types/pedido';

function formatearFechaHora(iso: string): string {
  const fecha = new Date(iso);
  if (Number.isNaN(fecha.getTime())) return iso;
  const dia = String(fecha.getDate()).padStart(2, '0');
  const mes = String(fecha.getMonth() + 1).padStart(2, '0');
  const anio = fecha.getFullYear();
  const horas = String(fecha.getHours()).padStart(2, '0');
  const minutos = String(fecha.getMinutes()).padStart(2, '0');
  return `${dia}/${mes}/${anio} ${horas}:${minutos}`;
}

interface HistorialPedidoModalProps {
  pedido: PedidoResponse;
  onClose: () => void;
}

export default function HistorialPedidoModal({ pedido, onClose }: HistorialPedidoModalProps) {
  const [estado, setEstado] = useState<'cargando' | 'listo' | 'error'>('cargando');
  const [historial, setHistorial] = useState<HistorialEstadoPedidoResponse[]>([]);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let cancelado = false;
    obtenerHistorialPedido(pedido.id)
      .then((data) => {
        if (cancelado) return;
        setHistorial(data);
        setEstado('listo');
      })
      .catch((err) => {
        if (cancelado) return;
        setError(extraerMensajeError(err, 'No se pudo cargar el historial de cambios.'));
        setEstado('error');
      });
    return () => {
      cancelado = true;
    };
  }, [pedido.id]);

  useEffect(() => {
    function alPresionarTecla(e: KeyboardEvent) {
      if (e.key === 'Escape') onClose();
    }
    document.addEventListener('keydown', alPresionarTecla);
    return () => document.removeEventListener('keydown', alPresionarTecla);
  }, [onClose]);

  return (
    <div className="historial-modal-backdrop" onClick={onClose}>
      <div
        className="historial-modal-dialog"
        role="dialog"
        aria-modal="true"
        onClick={(e) => e.stopPropagation()}
      >
        <div className="historial-modal-header">
          <h3>Historial de cambios — {pedido.codigoInterno}</h3>
          <button type="button" className="historial-modal-cerrar" onClick={onClose} aria-label="Cerrar">
            ×
          </button>
        </div>

        <div className="historial-modal-body">
          {estado === 'cargando' && <p className="historial-modal-vacio">Cargando historial…</p>}
          {estado === 'error' && <p className="historial-modal-vacio">{error}</p>}
          {estado === 'listo' && historial.length === 0 && (
            <p className="historial-modal-vacio">Este pedido todavía no tiene cambios registrados.</p>
          )}
          {estado === 'listo' && historial.length > 0 && (
            <table className="historial-modal-tabla">
              <thead>
                <tr>
                  <th>Fecha</th>
                  <th>Estado</th>
                  <th>Observación</th>
                  <th>Usuario</th>
                </tr>
              </thead>
              <tbody>
                {historial.map((h) => (
                  <tr key={h.id}>
                    <td>{formatearFechaHora(h.fechaCambio)}</td>
                    <td>{ESTADO_PEDIDO_LABELS[h.estado]}</td>
                    <td>{h.observaciones ?? '—'}</td>
                    <td>{h.emailUsuario}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          )}
        </div>
      </div>
    </div>
  );
}
