import { useEffect, useState } from 'react';
import type { GrupoTallaResponse } from '../../types/cargaTalles';

interface InstructivoTallesProps {
  tablasTalle: GrupoTallaResponse[];
}

interface ConfigImagenGrupo {
  src: string;
  alt: string;
  labelBoton: string;
}

/** Instructivo de "cómo medir" por grupo de talle — ver `public/carga-talles/`. Solo se
 *  ofrece el/los que correspondan a las prendas de este pedido (ej. un pedido sin chombas no
 *  necesita el botón del instructivo de chomba/remera). */
const CONFIG_POR_GRUPO: Record<string, ConfigImagenGrupo> = {
  'Campera/Buzo': {
    src: '/carga-talles/medidas-campera.jpg',
    alt: 'Cómo medir el talle de campera o buzo',
    labelBoton: 'Ver instructivo de campera',
  },
  'Chomba/Remera': {
    src: '/carga-talles/medidas-chomba-remera.jpg',
    alt: 'Cómo medir el talle de chomba o remera',
    labelBoton: 'Ver instructivo de chomba/remera',
  },
};

/** Instructivo paso a paso — la imagen de cómo medir no se ve inline, se abre en un popup al
 *  tocar el botón del grupo correspondiente (ver `imagenAbierta`). */
export default function InstructivoTalles({ tablasTalle }: InstructivoTallesProps) {
  const [abierto, setAbierto] = useState(true);
  const [imagenAbierta, setImagenAbierta] = useState<ConfigImagenGrupo | null>(null);
  const [imagenRota, setImagenRota] = useState(false);

  // Cerrar el popup con Escape, como el resto de los modales del sistema.
  useEffect(() => {
    if (!imagenAbierta) return;
    function alPresionarTecla(e: KeyboardEvent) {
      if (e.key === 'Escape') setImagenAbierta(null);
    }
    document.addEventListener('keydown', alPresionarTecla);
    return () => document.removeEventListener('keydown', alPresionarTecla);
  }, [imagenAbierta]);

  const gruposConImagen = tablasTalle
    .map((grupo) => CONFIG_POR_GRUPO[grupo.nombreGrupo])
    .filter((config): config is ConfigImagenGrupo => config != null);

  function abrirImagen(config: ConfigImagenGrupo) {
    setImagenRota(false);
    setImagenAbierta(config);
  }

  return (
    <div className="rounded-xl border border-wc-border bg-white p-4 shadow-sm">
      <button
        type="button"
        onClick={() => setAbierto((prev) => !prev)}
        className="flex w-full items-center justify-between text-left"
      >
        <h2 className="text-sm font-bold text-wc-text">Cómo cargar los talles</h2>
        <span className="text-xs font-semibold text-wc-green underline">{abierto ? 'Ocultar' : 'Ver instructivo'}</span>
      </button>

      {abierto && (
        <div className="mt-3 flex flex-col gap-4">
          <ol className="list-decimal space-y-1 pl-5 text-sm text-wc-text">
            <li>
              Agregá a cada alumno con su nombre y elegí cuántas prendas de cada tipo lleva (abajo de todo) — si
              te equivocaste en la cantidad, borrá al alumno con el ícono de tacho de su fila y volvé a agregarlo
              con la composición correcta.
            </li>
            <li>
              Mirá el instructivo de abajo para saber cómo tomar la medida de ancho y largo, y cargalos en
              centímetros — el talle aparece al lado apenas completás los dos datos.
            </li>
            <li>
              Cuando termines de cargar las medidas de un alumno, tocá el ícono de tilde de su fila para guardarlas.
              Los talles quedan fijos y podés tocar el ícono de lápiz para volver a corregirlos.
            </li>
            <li>
              Si la medida es más grande que el talle más grande de la tabla, la prenda queda marcada como{' '}
              <strong>"Personalizado"</strong> — podés dejar una observación (ej. "tiene los brazos muy largos")
              para que se tenga en cuenta al coserla.
            </li>
          </ol>

          {gruposConImagen.length > 0 && (
            <div className="flex flex-wrap gap-2">
              {gruposConImagen.map((config) => (
                <button
                  key={config.src}
                  type="button"
                  onClick={() => abrirImagen(config)}
                  className="rounded-lg bg-wc-green/15 px-3 py-2 text-xs font-semibold text-wc-green transition hover:bg-wc-green/25"
                >
                  {config.labelBoton}
                </button>
              ))}
            </div>
          )}
        </div>
      )}

      {imagenAbierta && (
        <div
          className="fixed inset-0 z-50 flex items-center justify-center bg-black/70 p-6"
          onClick={() => setImagenAbierta(null)}
          role="dialog"
          aria-modal="true"
        >
          <div className="relative max-h-[90vh] max-w-3xl" onClick={(e) => e.stopPropagation()}>
            <button
              type="button"
              onClick={() => setImagenAbierta(null)}
              aria-label="Cerrar"
              className="absolute -top-3 -right-3 flex h-8 w-8 items-center justify-center rounded-full bg-white text-wc-text shadow-md hover:bg-wc-bg"
            >
              ✕
            </button>
            {imagenRota ? (
              <div className="rounded-xl bg-white p-6 text-sm text-wc-text-muted">No se pudo cargar la imagen.</div>
            ) : (
              <img
                src={imagenAbierta.src}
                alt={imagenAbierta.alt}
                onError={() => setImagenRota(true)}
                className="max-h-[90vh] max-w-full rounded-xl object-contain shadow-2xl"
              />
            )}
          </div>
        </div>
      )}
    </div>
  );
}
