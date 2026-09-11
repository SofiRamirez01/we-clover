import { useEffect, useRef, useState } from 'react';
import type { FormEvent } from 'react';
import { listarGruposTalle } from '../../services/grupoTalleService';
import { listarTablasTalle } from '../../services/tablaTalleService';
import { actualizarPieza, crearPieza, obtenerPieza } from '../../services/piezaService';
import { guardarTallesPiezaEnLote, listarTallesPieza } from '../../services/piezaTalleService';
import { extraerMensajeError } from '../../utils/errores';
import SegmentoEditor from './SegmentoEditor';
import { escalarPieza, evaluarContorno } from './geometriaPieza';
import type { GrupoTalleOption, Segmento, TablaTalleOption } from '../../types/pieza';

interface FormErrors {
  nombre?: string;
  idGrupoTalle?: string;
  idTalleBase?: string;
  segmentos?: string;
  general?: string;
}

type ModoPiezaForm = 'crear' | 'editar' | 'duplicar' | 'ver';

const TEXTOS_MODO: Record<ModoPiezaForm, { titulo: string; descripcion: string; boton: string; botonEnviando: string }> = {
  crear: {
    titulo: 'Nueva pieza',
    descripcion: 'Cargá una pieza reutilizable de moldería (ej: "Manga", "Espalda") con la forma de su talle base.',
    boton: 'Guardar pieza',
    botonEnviando: 'Guardando...',
  },
  editar: {
    titulo: 'Editar pieza',
    descripcion: 'Modificá la forma o los datos de esta pieza.',
    boton: 'Guardar cambios',
    botonEnviando: 'Guardando...',
  },
  duplicar: {
    titulo: 'Duplicar pieza',
    descripcion: 'Se precargó la forma de la pieza original — cambiá el nombre y ajustá lo que necesites antes de guardar.',
    boton: 'Guardar como pieza nueva',
    botonEnviando: 'Guardando...',
  },
  ver: {
    titulo: 'Ver pieza',
    descripcion: 'Detalle de la pieza, solo lectura.',
    boton: '',
    botonEnviando: '',
  },
};

interface PiezaFormProps {
  modo?: ModoPiezaForm;
  /** Requerido para 'editar' y 'duplicar': de qué pieza traer los datos para precargar el formulario. */
  piezaId?: number;
  /** Preselecciona y bloquea el grupo de talle (ej. alta rápida de una Pieza desde el picker de
   *  una moldería: tiene que quedar en el mismo grupoTalle que esa moldería, si no el backend
   *  rechaza la asignación del pin). Solo tiene efecto en modo 'crear'. */
  idGrupoTalleFijo?: number;
  onGuardada?: () => void;
}

export default function PiezaForm({ modo = 'crear', piezaId, idGrupoTalleFijo, onGuardada }: PiezaFormProps) {
  const [nombre, setNombre] = useState('');
  const [gruposTalle, setGruposTalle] = useState<GrupoTalleOption[]>([]);
  const [idGrupoTalle, setIdGrupoTalle] = useState(idGrupoTalleFijo != null ? String(idGrupoTalleFijo) : '');
  const [tablasTalle, setTablasTalle] = useState<TablaTalleOption[]>([]);
  const [idTalleBase, setIdTalleBase] = useState('');
  const [segmentos, setSegmentos] = useState<Segmento[]>([]);
  const [simetrica, setSimetrica] = useState(false);
  const [errors, setErrors] = useState<FormErrors>({});
  const [enviando, setEnviando] = useState(false);
  const [mensajeExito, setMensajeExito] = useState<string | null>(null);
  const [cargandoDetalle, setCargandoDetalle] = useState(modo !== 'crear');

  // Cuando se precarga idGrupoTalle+idTalleBase juntos (editar/duplicar), el efecto de abajo no
  // debe pisar el idTalleBase recién precargado solo porque cambió idGrupoTalle.
  const evitarResetTalleBase = useRef(false);

  useEffect(() => {
    listarGruposTalle()
      .then(setGruposTalle)
      .catch(() => setErrors((prev) => ({ ...prev, general: 'No se pudieron cargar los grupos de talle.' })));
  }, []);

  useEffect(() => {
    if (modo === 'crear' || !piezaId) return;
    let cancelado = false;
    obtenerPieza(piezaId)
      .then((detalle) => {
        if (cancelado) return;
        evitarResetTalleBase.current = true;
        setNombre(modo === 'duplicar' ? `${detalle.nombre} (copia)` : detalle.nombre);
        setIdGrupoTalle(String(detalle.idGrupoTalle));
        setIdTalleBase(String(detalle.idTalleBase));
        setSegmentos(detalle.segmentos);
        setSimetrica(detalle.simetrica);
        setCargandoDetalle(false);
      })
      .catch(() => {
        if (!cancelado) {
          setErrors((prev) => ({ ...prev, general: 'No se pudo cargar la pieza de origen.' }));
          setCargandoDetalle(false);
        }
      });
    return () => {
      cancelado = true;
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [modo, piezaId]);

  useEffect(() => {
    if (evitarResetTalleBase.current) {
      evitarResetTalleBase.current = false;
    } else {
      setIdTalleBase('');
    }
    if (!idGrupoTalle) {
      setTablasTalle([]);
      return;
    }
    listarTablasTalle(Number(idGrupoTalle))
      .then(setTablasTalle)
      .catch(() => setErrors((prev) => ({ ...prev, general: 'No se pudieron cargar los talles del grupo.' })));
  }, [idGrupoTalle]);

  function limpiarFormulario() {
    setNombre('');
    setIdGrupoTalle('');
    setIdTalleBase('');
    setSegmentos([]);
    setSimetrica(false);
    setErrors({});
  }

  function validar(): boolean {
    const nuevosErrores: FormErrors = {};

    if (!nombre.trim()) {
      nuevosErrores.nombre = 'El nombre de la pieza es obligatorio';
    }
    if (!idGrupoTalle) {
      nuevosErrores.idGrupoTalle = 'Seleccioná un grupo de talle';
    }
    if (!idTalleBase) {
      nuevosErrores.idTalleBase = 'Seleccioná el talle base';
    }
    if (segmentos.length === 0) {
      nuevosErrores.segmentos = 'Agregá al menos un tramo para definir el contorno';
    } else if (!evaluarContorno(segmentos).cerradoCompleto) {
      nuevosErrores.segmentos = 'El contorno debe cerrarse: revisá que todos los tramos se conecten en un único lazo';
    }

    setErrors(nuevosErrores);
    return Object.keys(nuevosErrores).length === 0;
  }

  /**
   * Requisito 4.1 Parte 3 (CAMBIO 4): al editar el contorno del talle base, todo talle generado
   * automáticamente (editadoManualmente=false, sin contar el propio base) se recalcula contra el
   * NUEVO contorno base, apuntando a SU PROPIO ancho/largo ya guardado (no al del catálogo de
   * talles) — así una graduación ya hecha no se "resetea" al tocar la base, solo se reproyecta.
   * Los talles corregidos a mano (editadoManualmente=true) quedan intactos.
   */
  async function recalcularGraduacionAutomatica(idPieza: number) {
    const talles = await listarTallesPieza(idPieza);
    const base = talles.find((t) => t.esBase);
    if (!base) return;

    const aRecalcular = talles.filter((t) => !t.esBase && !t.editadoManualmente);
    if (aRecalcular.length === 0) return;

    const items = aRecalcular.map((t) => {
      const resultado = escalarPieza(base.coordenadas, t.anchoCm, t.largoCm);
      return { idTalle: t.idTalle, ...resultado, editadoManualmente: false };
    });
    await guardarTallesPiezaEnLote(idPieza, items);
  }

  async function handleSubmit(e: FormEvent) {
    e.preventDefault();
    if (modo === 'ver') return;
    setMensajeExito(null);

    if (!validar()) return;

    setEnviando(true);
    try {
      if (modo === 'editar' && piezaId) {
        await actualizarPieza(piezaId, nombre.trim(), Number(idGrupoTalle), Number(idTalleBase), segmentos, simetrica);
        await recalcularGraduacionAutomatica(piezaId);
        setMensajeExito('Cambios guardados correctamente.');
      } else {
        await crearPieza(nombre.trim(), Number(idGrupoTalle), Number(idTalleBase), segmentos, simetrica);
        setMensajeExito('Pieza guardada correctamente.');
        limpiarFormulario();
      }
      onGuardada?.();
    } catch (error) {
      setErrors({ general: extraerMensajeError(error, 'No se pudo guardar la pieza. Intente nuevamente.') });
    } finally {
      setEnviando(false);
    }
  }

  const textos = TEXTOS_MODO[modo];
  const soloLectura = modo === 'ver';

  if (cargandoDetalle) {
    return (
      <div className="mx-auto w-full max-w-4xl rounded-xl border border-wc-border bg-white p-6 shadow-sm sm:p-8">
        <p className="text-sm text-wc-text-muted">Cargando pieza…</p>
      </div>
    );
  }

  return (
    <form
      onSubmit={handleSubmit}
      className="mx-auto flex w-full max-w-4xl flex-col gap-6 rounded-xl border border-wc-border bg-white p-6 shadow-sm sm:p-8"
    >
      <div>
        <h2 className="text-lg font-bold text-wc-text">{textos.titulo}</h2>
        <p className="mt-1 text-sm text-wc-text-muted">{textos.descripcion}</p>
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

      <div className="grid grid-cols-1 gap-4 sm:grid-cols-3">
        <div className="flex flex-col gap-1.5">
          <label htmlFor="nombre-pieza" className="text-sm font-semibold text-wc-text">
            Nombre de la pieza
          </label>
          <input
            id="nombre-pieza"
            type="text"
            value={nombre}
            onChange={(e) => {
              setNombre(e.target.value);
              setErrors((prev) => ({ ...prev, nombre: undefined }));
            }}
            placeholder="Ej: Manga"
            disabled={soloLectura}
            className={`w-full rounded-lg border px-3 py-2 text-sm text-wc-text outline-none transition focus:border-wc-green focus:ring-2 focus:ring-wc-green/20 disabled:cursor-not-allowed disabled:bg-wc-bg ${
              errors.nombre ? 'border-red-400' : 'border-wc-border'
            }`}
          />
          {errors.nombre && <span className="text-xs font-medium text-red-600">{errors.nombre}</span>}
        </div>

        <div className="flex flex-col gap-1.5">
          <label htmlFor="grupo-talle-pieza" className="text-sm font-semibold text-wc-text">
            Grupo de talle
          </label>
          <select
            id="grupo-talle-pieza"
            value={idGrupoTalle}
            onChange={(e) => {
              setIdGrupoTalle(e.target.value);
              setErrors((prev) => ({ ...prev, idGrupoTalle: undefined }));
            }}
            disabled={soloLectura || idGrupoTalleFijo != null}
            className={`w-full rounded-lg border px-3 py-2 text-sm text-wc-text outline-none transition focus:border-wc-green focus:ring-2 focus:ring-wc-green/20 disabled:cursor-not-allowed disabled:bg-wc-bg ${
              errors.idGrupoTalle ? 'border-red-400' : 'border-wc-border'
            }`}
          >
            <option value="">Seleccionar…</option>
            {gruposTalle.map((grupo) => (
              <option key={grupo.id} value={grupo.id}>
                {grupo.nombre}
              </option>
            ))}
          </select>
          {errors.idGrupoTalle && <span className="text-xs font-medium text-red-600">{errors.idGrupoTalle}</span>}
        </div>

        <div className="flex flex-col gap-1.5">
          <label htmlFor="talle-base-pieza" className="text-sm font-semibold text-wc-text">
            Talle base
          </label>
          <select
            id="talle-base-pieza"
            value={idTalleBase}
            onChange={(e) => {
              setIdTalleBase(e.target.value);
              setErrors((prev) => ({ ...prev, idTalleBase: undefined }));
            }}
            disabled={soloLectura || !idGrupoTalle}
            className={`w-full rounded-lg border px-3 py-2 text-sm text-wc-text outline-none transition focus:border-wc-green focus:ring-2 focus:ring-wc-green/20 disabled:cursor-not-allowed disabled:bg-wc-bg ${
              errors.idTalleBase ? 'border-red-400' : 'border-wc-border'
            }`}
          >
            <option value="">Seleccionar…</option>
            {tablasTalle.map((talle) => (
              <option key={talle.id} value={talle.id}>
                {talle.talle} ({talle.anchoCm}cm x {talle.largoCm}cm)
              </option>
            ))}
          </select>
          {errors.idTalleBase && <span className="text-xs font-medium text-red-600">{errors.idTalleBase}</span>}
        </div>
      </div>

      <label className="flex w-fit items-center gap-2 text-sm font-semibold text-wc-text">
        <input
          type="checkbox"
          checked={simetrica}
          onChange={(e) => setSimetrica(e.target.checked)}
          disabled={soloLectura}
          className="h-4 w-4 rounded border-wc-border text-wc-green focus:ring-wc-green/40 disabled:cursor-not-allowed"
        />
        ¿Es simétrica?
      </label>

      <div className="flex flex-col gap-1.5">
        <span className="text-sm font-semibold text-wc-text">Forma del talle base</span>
        <SegmentoEditor
          segmentos={segmentos}
          onChange={(nuevos) => {
            setSegmentos(nuevos);
            setErrors((prev) => ({ ...prev, segmentos: undefined }));
          }}
          soloLectura={soloLectura}
        />
        {errors.segmentos && <span className="text-xs font-medium text-red-600">{errors.segmentos}</span>}
      </div>

      {!soloLectura && (
        <div className="flex justify-end gap-3 border-t border-wc-border pt-4">
          <button
            type="submit"
            disabled={enviando}
            className="rounded-lg bg-wc-green px-5 py-2.5 text-sm font-semibold text-white transition hover:bg-wc-green-dark disabled:cursor-not-allowed disabled:opacity-60"
          >
            {enviando ? textos.botonEnviando : textos.boton}
          </button>
        </div>
      )}
    </form>
  );
}
