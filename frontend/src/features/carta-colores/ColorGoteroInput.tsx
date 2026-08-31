import { useEffect, useRef, useState } from 'react';
import type { ChangeEvent, MouseEvent } from 'react';
import { rgbAHex } from '../../utils/colorMatch';

type Modo = 'rgb' | 'imagen';

interface ColorGoteroInputProps {
  hex: string;
  onCambiar: (hex: string) => void;
}

/**
 * Selector de color con dos modos: gotero sobre una imagen de referencia, o el selector RGB
 * nativo del navegador. La imagen elegida nunca se sube al backend ni se persiste en ningún
 * lado — se carga con `URL.createObjectURL` solo para dibujarla en un canvas local y leer un
 * pixel al click; se descarta (`revokeObjectURL`) al cambiarla o cerrar el selector.
 *
 * Es una versión standalone del mismo mecanismo que ya usa ModalColoresGotero (canvas + click
 * para leer el pixel) — no se reusa el componente de ahí porque ese está fuertemente acoplado
 * a un Producto/Moldería (posiciones, asignaciones), mientras que acá hace falta un input de
 * color genérico sin esa dependencia.
 */
export default function ColorGoteroInput({ hex, onCambiar }: ColorGoteroInputProps) {
  const [modo, setModo] = useState<Modo>('rgb');
  const [urlImagen, setUrlImagen] = useState<string | null>(null);
  const [errorLectura, setErrorLectura] = useState<string | null>(null);
  const canvasRef = useRef<HTMLCanvasElement | null>(null);
  const inputRef = useRef<HTMLInputElement | null>(null);

  useEffect(() => {
    return () => {
      if (urlImagen) URL.revokeObjectURL(urlImagen);
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  useEffect(() => {
    if (!urlImagen) return;
    const canvas = canvasRef.current;
    if (!canvas) return;
    const img = new Image();
    img.onload = () => {
      canvas.width = img.naturalWidth;
      canvas.height = img.naturalHeight;
      canvas.getContext('2d')?.drawImage(img, 0, 0);
    };
    img.onerror = () => setErrorLectura('No se pudo cargar la imagen.');
    img.src = urlImagen;
  }, [urlImagen]);

  function handleSubirImagen(e: ChangeEvent<HTMLInputElement>) {
    const file = e.target.files?.[0];
    if (!file) return;
    if (urlImagen) URL.revokeObjectURL(urlImagen);
    setErrorLectura(null);
    setUrlImagen(URL.createObjectURL(file));
  }

  function handleClickCanvas(e: MouseEvent<HTMLCanvasElement>) {
    const canvas = canvasRef.current;
    if (!canvas) return;
    const rect = canvas.getBoundingClientRect();
    const escalaX = canvas.width / rect.width;
    const escalaY = canvas.height / rect.height;
    const x = Math.round((e.clientX - rect.left) * escalaX);
    const y = Math.round((e.clientY - rect.top) * escalaY);

    const ctx = canvas.getContext('2d');
    if (!ctx) return;
    try {
      const pixel = ctx.getImageData(x, y, 1, 1).data;
      onCambiar(rgbAHex({ r: pixel[0], g: pixel[1], b: pixel[2] }));
      setErrorLectura(null);
    } catch {
      setErrorLectura('No se pudo leer el color de la imagen (problema de CORS con el archivo).');
    }
  }

  function quitarImagen() {
    if (urlImagen) URL.revokeObjectURL(urlImagen);
    setUrlImagen(null);
    setErrorLectura(null);
    if (inputRef.current) inputRef.current.value = '';
  }

  return (
    <div className="flex flex-col gap-2">
      <div className="flex items-center gap-2">
        <span className="h-8 w-8 shrink-0 rounded-full border border-wc-border" style={{ backgroundColor: hex }} />
        <div className="flex overflow-hidden rounded-lg border border-wc-border text-xs font-semibold">
          <button
            type="button"
            onClick={() => setModo('rgb')}
            className={`px-3 py-1.5 transition ${modo === 'rgb' ? 'bg-wc-green text-white' : 'bg-white text-wc-text-muted hover:bg-wc-bg'}`}
          >
            Selector RGB
          </button>
          <button
            type="button"
            onClick={() => setModo('imagen')}
            className={`px-3 py-1.5 transition ${modo === 'imagen' ? 'bg-wc-green text-white' : 'bg-white text-wc-text-muted hover:bg-wc-bg'}`}
          >
            Gotero desde imagen
          </button>
        </div>
      </div>

      {modo === 'rgb' && (
        <input
          type="color"
          value={hex}
          onChange={(e) => onCambiar(e.target.value)}
          className="h-9 w-16 cursor-pointer rounded border border-wc-border"
        />
      )}

      {modo === 'imagen' && (
        <div className="flex flex-col gap-2">
          {!urlImagen ? (
            <label className="cursor-pointer self-start rounded-md bg-wc-green/10 px-3 py-1.5 text-xs font-semibold text-wc-green hover:bg-wc-green/20">
              Elegir imagen de referencia…
              <input
                ref={inputRef}
                type="file"
                accept=".jpg,.jpeg,.png,image/jpeg,image/png"
                className="hidden"
                onChange={handleSubirImagen}
              />
            </label>
          ) : (
            <>
              <p className="text-[11px] text-wc-text-muted">
                Hacé click sobre la imagen para tomar la muestra de color. La imagen no se guarda, solo se usa acá.
              </p>
              <canvas
                ref={canvasRef}
                onClick={handleClickCanvas}
                className="max-h-64 max-w-full cursor-crosshair rounded-lg border border-wc-border"
              />
              {errorLectura && <p className="text-xs font-medium text-red-600">{errorLectura}</p>}
              <button type="button" onClick={quitarImagen} className="self-start text-[11px] font-semibold text-wc-text-muted underline">
                Quitar imagen
              </button>
            </>
          )}
        </div>
      )}
    </div>
  );
}
