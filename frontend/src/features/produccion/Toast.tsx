import { useEffect } from 'react';

export interface ToastItem {
  id: number;
  tipo: 'error' | 'exito';
  mensaje: string;
}

const DURACION_MS = 4500;

/**
 * Notificación transitoria autodesaparece — no existía nada parecido en el proyecto (el resto
 * de las pantallas usa un banner de error local por acción, ver NuevaPlanificacionView), pero
 * acá hay demasiadas acciones independientes en paralelo (un checkbox por etapa por producto)
 * como para que cada una tenga su propio banner sin saturar la grilla.
 */
export default function ToastContainer({ toasts, onDescartar }: { toasts: ToastItem[]; onDescartar: (id: number) => void }) {
  return (
    <div className="pointer-events-none fixed bottom-4 right-4 z-50 flex flex-col gap-2">
      {toasts.map((toast) => (
        <Toast key={toast.id} toast={toast} onDescartar={() => onDescartar(toast.id)} />
      ))}
    </div>
  );
}

function Toast({ toast, onDescartar }: { toast: ToastItem; onDescartar: () => void }) {
  useEffect(() => {
    const timer = window.setTimeout(onDescartar, DURACION_MS);
    return () => window.clearTimeout(timer);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  return (
    <div
      role="status"
      className={`pointer-events-auto flex max-w-sm items-start gap-2 rounded-lg border px-3 py-2.5 text-sm shadow-lg ${
        toast.tipo === 'error' ? 'border-red-200 bg-red-50 text-red-700' : 'border-wc-green/30 bg-white text-wc-green-dark'
      }`}
    >
      <span className="flex-1">{toast.mensaje}</span>
      <button type="button" onClick={onDescartar} className="text-xs font-semibold opacity-60 hover:opacity-100">
        ✕
      </button>
    </div>
  );
}
