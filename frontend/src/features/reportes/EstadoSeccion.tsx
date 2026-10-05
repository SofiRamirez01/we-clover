interface EstadoSeccionProps {
  tipo: 'cargando' | 'vacio' | 'error' | 'sin-filtros';
  mensaje: string;
  onReintentar?: () => void;
}

/** Estados de carga / vacío / error de una sección de Reportes, con el mismo aspecto en todas. */
export default function EstadoSeccion({ tipo, mensaje, onReintentar }: EstadoSeccionProps) {
  const esError = tipo === 'error';
  return (
    <div
      role={esError ? 'alert' : 'status'}
      className={`flex min-h-40 flex-col items-center justify-center gap-3 rounded-lg border p-6 text-center text-sm ${
        esError ? 'border-red-200 bg-red-50 text-red-900' : 'border-wc-border bg-white text-wc-text-muted'
      }`}
    >
      <span>{mensaje}</span>
      {esError && onReintentar && (
        <button
          type="button"
          onClick={onReintentar}
          className="rounded-lg border border-red-300 bg-white px-3 py-1.5 text-sm font-semibold text-red-900 hover:bg-red-100"
        >
          Reintentar
        </button>
      )}
    </div>
  );
}
