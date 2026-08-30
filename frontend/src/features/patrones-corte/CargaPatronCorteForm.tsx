import { useEffect, useRef, useState } from 'react';
import type { ChangeEvent, FormEvent } from 'react';
import { crearPatronCorte } from '../../services/patronCorteService';
import { listarTiposPrenda } from '../../services/pedidoService';
import { extraerMensajeError } from '../../utils/errores';
import type { TipoPrendaOption } from '../../types/pedido';

const MAX_COLORES = 5;
const TIPOS_IMAGEN_PERMITIDOS = ['image/jpeg', 'image/png'];

interface FormErrors {
  numeroInterno?: string;
  nombre?: string;
  idsTipoPrenda?: string;
  imagen?: string;
  gramos?: Record<number, string>;
  general?: string;
}

interface CargaPatronCorteFormProps {
  onCreado?: () => void;
}

export default function CargaPatronCorteForm({ onCreado }: CargaPatronCorteFormProps) {
  const [numeroInterno, setNumeroInterno] = useState('');
  const [nombre, setNombre] = useState('');
  const [idsTipoPrenda, setIdsTipoPrenda] = useState<number[]>([]);
  const [tiposPrenda, setTiposPrenda] = useState<TipoPrendaOption[]>([]);
  const [imagenFile, setImagenFile] = useState<File | null>(null);
  const [imagenPreview, setImagenPreview] = useState<string | null>(null);
  const [cantidadColores, setCantidadColores] = useState(1);
  const [gramosPorColor, setGramosPorColor] = useState<string[]>(Array(MAX_COLORES).fill(''));
  const [errors, setErrors] = useState<FormErrors>({});
  const [enviando, setEnviando] = useState(false);
  const [mensajeExito, setMensajeExito] = useState<string | null>(null);
  const fileInputRef = useRef<HTMLInputElement | null>(null);

  useEffect(() => {
    listarTiposPrenda()
      .then(setTiposPrenda)
      .catch(() => {
        setErrors((prev) => ({ ...prev, general: 'No se pudieron cargar los tipos de prenda.' }));
      });
  }, []);

  useEffect(() => {
    return () => {
      if (imagenPreview) URL.revokeObjectURL(imagenPreview);
    };
  }, [imagenPreview]);

  function limpiarSeleccionImagen() {
    setImagenFile(null);
    setImagenPreview((prev) => {
      if (prev) URL.revokeObjectURL(prev);
      return null;
    });
    if (fileInputRef.current) fileInputRef.current.value = '';
  }

  function limpiarFormulario() {
    setNumeroInterno('');
    setNombre('');
    setIdsTipoPrenda([]);
    limpiarSeleccionImagen();
    setCantidadColores(1);
    setGramosPorColor(Array(MAX_COLORES).fill(''));
    setErrors({});
  }

  function handleImagenChange(e: ChangeEvent<HTMLInputElement>) {
    const file = e.target.files?.[0] ?? null;
    if (!file) {
      limpiarSeleccionImagen();
      return;
    }

    if (!TIPOS_IMAGEN_PERMITIDOS.includes(file.type)) {
      setErrors((prev) => ({
        ...prev,
        imagen: 'Formato de archivo no válido. Solo se aceptan imágenes JPG o PNG.',
      }));
      limpiarSeleccionImagen();
      return;
    }

    setErrors((prev) => ({ ...prev, imagen: undefined }));
    setImagenFile(file);
    setImagenPreview((prev) => {
      if (prev) URL.revokeObjectURL(prev);
      return URL.createObjectURL(file);
    });
  }

  function handleToggleTipoPrenda(id: number) {
    setIdsTipoPrenda((prev) => (prev.includes(id) ? prev.filter((x) => x !== id) : [...prev, id]));
    setErrors((prev) => ({ ...prev, idsTipoPrenda: undefined }));
  }

  function handleGramosChange(index: number, value: string) {
    setGramosPorColor((prev) => {
      const next = [...prev];
      next[index] = value;
      return next;
    });
    setErrors((prev) => {
      if (!prev.gramos?.[index]) return prev;
      const gramos = { ...prev.gramos };
      delete gramos[index];
      return { ...prev, gramos };
    });
  }

  function validar(): boolean {
    const nuevosErrores: FormErrors = {};

    const numeroInternoValor = Number(numeroInterno);
    if (!numeroInterno.trim() || !Number.isInteger(numeroInternoValor) || numeroInternoValor <= 0) {
      nuevosErrores.numeroInterno = 'Ingresá un número entero mayor a 0';
    }

    if (!nombre.trim()) {
      nuevosErrores.nombre = 'El nombre del patrón es obligatorio';
    }

    if (idsTipoPrenda.length === 0) {
      nuevosErrores.idsTipoPrenda = 'Debe seleccionar al menos un tipo de prenda';
    }

    if (!imagenFile) {
      nuevosErrores.imagen = 'Debe seleccionar una imagen del patrón';
    }

    const erroresGramos: Record<number, string> = {};
    for (let i = 0; i < cantidadColores; i++) {
      const valor = Number(gramosPorColor[i]);
      if (!gramosPorColor[i] || Number.isNaN(valor) || valor <= 0) {
        erroresGramos[i] = 'Debe ser mayor a 0';
      }
    }
    if (Object.keys(erroresGramos).length > 0) {
      nuevosErrores.gramos = erroresGramos;
    }

    setErrors(nuevosErrores);
    return Object.keys(nuevosErrores).length === 0;
  }

  async function handleSubmit(e: FormEvent) {
    e.preventDefault();
    setMensajeExito(null);

    if (!validar() || !imagenFile) return;

    setEnviando(true);
    try {
      const gramos = gramosPorColor.slice(0, cantidadColores).map(Number);
      await crearPatronCorte(Number(numeroInterno), nombre.trim(), idsTipoPrenda, imagenFile, gramos);
      setMensajeExito('Patrón de corte cargado correctamente.');
      limpiarFormulario();
      onCreado?.();
    } catch (error) {
      setErrors({
        general: extraerMensajeError(error, 'No se pudo guardar el patrón de corte. Intente nuevamente.'),
      });
    } finally {
      setEnviando(false);
    }
  }

  return (
    <form
      onSubmit={handleSubmit}
      className="mx-auto flex w-full max-w-2xl flex-col gap-6 rounded-xl border border-wc-border bg-white p-6 shadow-sm sm:p-8"
    >
      <div>
        <h2 className="text-lg font-bold text-wc-text">Nuevo patrón de corte</h2>
        <p className="mt-1 text-sm text-wc-text-muted">
          Cargá una moldería (ej: "Bicolor 50-50") con los gramos de Friza que necesita cada color.
        </p>
      </div>

      {mensajeExito && (
        <div className="rounded-lg border border-wc-green/30 bg-wc-green/10 px-4 py-3 text-sm font-medium text-wc-green">
          {mensajeExito}
        </div>
      )}
      {errors.general && (
        <div className="rounded-lg border border-red-300 bg-red-50 px-4 py-3 text-sm font-medium text-red-700">
          {errors.general}
        </div>
      )}

      <div className="grid grid-cols-1 gap-4 sm:grid-cols-[8rem_1fr]">
        <div className="flex flex-col gap-1.5">
          <label htmlFor="numero-interno-patron" className="text-sm font-semibold text-wc-text">
            Número interno
          </label>
          <input
            id="numero-interno-patron"
            type="number"
            min={1}
            step={1}
            value={numeroInterno}
            onChange={(e) => {
              setNumeroInterno(e.target.value);
              setErrors((prev) => ({ ...prev, numeroInterno: undefined }));
            }}
            placeholder="Ej: 12"
            className={`w-full rounded-lg border px-3 py-2 text-sm text-wc-text outline-none transition focus:border-wc-green focus:ring-2 focus:ring-wc-green/20 ${
              errors.numeroInterno ? 'border-red-400' : 'border-wc-border'
            }`}
          />
          {errors.numeroInterno && <span className="text-xs font-medium text-red-600">{errors.numeroInterno}</span>}
        </div>

        <div className="flex flex-col gap-1.5">
          <label htmlFor="nombre-patron" className="text-sm font-semibold text-wc-text">
            Nombre del patrón
          </label>
          <input
            id="nombre-patron"
            type="text"
            value={nombre}
            onChange={(e) => {
              setNombre(e.target.value);
              setErrors((prev) => ({ ...prev, nombre: undefined }));
            }}
            placeholder='Ej: Bicolor 50-50'
            className={`w-full rounded-lg border px-3 py-2 text-sm text-wc-text outline-none transition focus:border-wc-green focus:ring-2 focus:ring-wc-green/20 ${
              errors.nombre ? 'border-red-400' : 'border-wc-border'
            }`}
          />
          {errors.nombre && <span className="text-xs font-medium text-red-600">{errors.nombre}</span>}
        </div>
      </div>

      <div className="flex flex-col gap-1.5">
        <span className="text-sm font-semibold text-wc-text">Tipos de prenda</span>
        <p className="text-xs text-wc-text-muted">
          Tildá todos los tipos de prenda que usan esta misma moldería (ej: Buzo y Campera comparten patrón).
        </p>
        <div
          className={`flex flex-wrap gap-x-5 gap-y-2 rounded-lg border px-3 py-2.5 ${
            errors.idsTipoPrenda ? 'border-red-400' : 'border-wc-border'
          }`}
        >
          {tiposPrenda.map((tipo) => (
            <label key={tipo.id} className="flex cursor-pointer items-center gap-2 text-sm text-wc-text">
              <input
                type="checkbox"
                checked={idsTipoPrenda.includes(tipo.id)}
                onChange={() => handleToggleTipoPrenda(tipo.id)}
                className="h-4 w-4 rounded border-wc-border text-wc-green focus:ring-wc-green/40"
              />
              {tipo.nombre}
            </label>
          ))}
        </div>
        {errors.idsTipoPrenda && <span className="text-xs font-medium text-red-600">{errors.idsTipoPrenda}</span>}
      </div>

      <div className="flex flex-col gap-1.5">
        <label htmlFor="imagen-patron" className="text-sm font-semibold text-wc-text">
          Imagen de la moldería (JPG o PNG)
        </label>
        <input
          id="imagen-patron"
          ref={fileInputRef}
          type="file"
          accept=".jpg,.jpeg,.png,image/jpeg,image/png"
          onChange={handleImagenChange}
          className="w-full rounded-lg border border-wc-border px-3 py-2 text-sm text-wc-text file:mr-3 file:rounded-md file:border-0 file:bg-wc-green file:px-3 file:py-1.5 file:text-sm file:font-semibold file:text-white hover:file:bg-wc-green-dark"
        />
        {errors.imagen && <span className="text-xs font-medium text-red-600">{errors.imagen}</span>}

        {imagenPreview && (
          <div className="mt-2 w-fit overflow-hidden rounded-lg border border-wc-border">
            <img src={imagenPreview} alt="Vista previa del patrón" className="h-40 w-40 object-cover" />
          </div>
        )}
      </div>

      <div className="flex flex-col gap-1.5">
        <label htmlFor="cantidad-colores" className="text-sm font-semibold text-wc-text">
          Cantidad de colores
        </label>
        <select
          id="cantidad-colores"
          value={cantidadColores}
          onChange={(e) => setCantidadColores(Number(e.target.value))}
          className="w-full max-w-[10rem] rounded-lg border border-wc-border px-3 py-2 text-sm text-wc-text outline-none focus:border-wc-green focus:ring-2 focus:ring-wc-green/20"
        >
          {Array.from({ length: MAX_COLORES }, (_, i) => i + 1).map((n) => (
            <option key={n} value={n}>
              {n}
            </option>
          ))}
        </select>
      </div>

      <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
        {Array.from({ length: cantidadColores }, (_, i) => i).map((i) => (
          <div key={i} className="flex flex-col gap-1.5">
            <label htmlFor={`gramos-color-${i}`} className="text-sm font-semibold text-wc-text">
              Gramos - Color {i + 1}
            </label>
            <input
              id={`gramos-color-${i}`}
              type="number"
              min={1}
              value={gramosPorColor[i]}
              onChange={(e) => handleGramosChange(i, e.target.value)}
              placeholder="Gramos de Friza"
              className={`w-full rounded-lg border px-3 py-2 text-sm text-wc-text outline-none transition focus:border-wc-green focus:ring-2 focus:ring-wc-green/20 ${
                errors.gramos?.[i] ? 'border-red-400' : 'border-wc-border'
              }`}
            />
            {errors.gramos?.[i] && <span className="text-xs font-medium text-red-600">{errors.gramos[i]}</span>}
          </div>
        ))}
      </div>

      <div className="flex justify-end gap-3 border-t border-wc-border pt-4">
        <button
          type="submit"
          disabled={enviando}
          className="rounded-lg bg-wc-green px-5 py-2.5 text-sm font-semibold text-white transition hover:bg-wc-green-dark disabled:cursor-not-allowed disabled:opacity-60"
        >
          {enviando ? 'Guardando...' : 'Guardar patrón de corte'}
        </button>
      </div>
    </form>
  );
}
