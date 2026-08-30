import { useEffect, useRef, useState } from 'react';
import type { ChangeEvent, Dispatch, MouseEvent, SetStateAction } from 'react';
import { crearColorPaleta, listarPaletaColores } from '../../services/paletaColoresService';
import { listarPatronesCorte } from '../../services/patronCorteService';
import { listarTiposTela } from '../../services/tipoTelaService';
import {
  actualizarInsumosSecundariosProducto,
  actualizarPatronCorteProducto,
  actualizarTipoTelaProducto,
  asignarColoresProducto,
  subirImagenDisenoProducto,
} from '../../services/productoService';
import { colorMasCercano, rgbAHex } from '../../utils/colorMatch';
import type { ColorRgb } from '../../utils/colorMatch';
import { extraerMensajeError } from '../../utils/errores';
import { urlArchivoSubido } from '../../utils/urlArchivos';
import { TIPOS_TELA_PRENDA, TIPO_TELA_LABELS } from '../../types/paletaColores';
import type {
  PaletaColorResponse,
  ProductoColorItemRequest,
  ProductoInsumoSecundarioItemRequest,
  ProductoInsumoSecundarioResponse,
  TipoTela,
} from '../../types/paletaColores';
import type { ProductoResponse } from '../../types/pedido';
import type { PatronCorteResponse } from '../../types/patronCorte';
import type { TipoTelaCatalogo } from '../../types/tipoTela';
import { TIPO_PRENDA_BUZO, TIPO_PRENDA_CAMPERA, telaEfectiva } from './telaUtils';

const TIPOS_IMAGEN_PERMITIDOS = ['image/jpeg', 'image/png'];
const ZOOM_MIN = 0.1;
const ZOOM_MAX = 3;
const ZOOM_PASO = 0.1;

/**
 * Descripciones fijas (ver ProductoInsumoSecundario.descripcion en el backend: es la
 * identidad real de la fila, no el tipo de tela — dos insumos distintos, ej. Capucha y
 * Cuello, pueden compartir la misma tela). Cierre sigue con su propio selector y estado
 * dedicado (sin cambios de UX), pero su descripcion también viaja por acá al guardar.
 */
const DESCRIPCION_CAPUCHA = 'Capucha';
const DESCRIPCION_PUNOS = 'Puños y cintura';
const DESCRIPCION_CIERRE = 'Cierre';
/** Códigos estables en tipos_tela (ver TipoTela.codigo en el backend). Jersey/Ribb son solo
 *  el tipo de tela por defecto al agregar un color nuevo en Capucha/Puños — el usuario lo
 *  puede cambiar libremente (ej. por Corderito o estampado) desde el mismo selector. */
const CODIGO_TIPO_TELA_CIERRE = 'CIERRE';
const CODIGO_TIPO_TELA_JERSEY = 'JERSEY';
const CODIGO_TIPO_TELA_RIBB = 'RIBB';

interface FilaInsumo {
  idTipoTela: number | '';
  idPaletaColor: number | '';
  /** String crudo del input numérico; se valida y convierte recién al guardar. */
  cantidad: string;
}

interface FilaInsumoLibre extends FilaInsumo {
  id: string;
  /**
   * Qué es este insumo. Para Capucha/Puños queda fija (no se expone un input, ya se ve en
   * el título de la sección); para una fila agregada a mano el usuario la escribe. No hay
   * restricción de unicidad: puede haber más de una fila con la misma descripcion (ej. dos
   * "Puños y cintura" de distinto color, uno por puño) — lo que identifica a cada fila es el
   * conjunto completo (descripcion, tipo de tela, color), no un campo por sí solo.
   */
  descripcion: string;
}

function filaInsumoVacia(): FilaInsumo {
  return { idTipoTela: '', idPaletaColor: '', cantidad: '' };
}

/** Arma las filas iniciales de una sección (Capucha/Puños/libres) a partir de lo que ya
 *  guardó el backend con esa descripcion — puede haber 0, 1 o varias. */
function filasDesdeInsumo(insumos: ProductoInsumoSecundarioResponse[], descripcion: string): FilaInsumoLibre[] {
  return insumos
    .filter((i) => i.descripcion === descripcion)
    .map((i) => ({
      id: crypto.randomUUID(),
      idTipoTela: i.idTipoTela,
      idPaletaColor: i.idPaletaColor,
      cantidad: String(i.cantidad),
      descripcion,
    }));
}

/** Insumos "libres": cualquier fila que no sea Capucha/Puños (con su propia sección fija) ni
 *  Cierre (que tiene su propio selector y estado, sin cambios de UX). */
function filasLibresDesdeInsumos(insumos: ProductoInsumoSecundarioResponse[]): FilaInsumoLibre[] {
  const descripcionesConSeccionPropia = new Set([DESCRIPCION_CAPUCHA, DESCRIPCION_PUNOS, DESCRIPCION_CIERRE]);
  return insumos
    .filter((i) => !descripcionesConSeccionPropia.has(i.descripcion))
    .map((i) => ({
      id: crypto.randomUUID(),
      idTipoTela: i.idTipoTela,
      idPaletaColor: i.idPaletaColor,
      cantidad: String(i.cantidad),
      descripcion: i.descripcion,
    }));
}

/** Al elegir un tipo de tela para un insumo, se resetea el color (la paleta cambia) y se
 *  precarga una cantidad sugerida con el mismo criterio que el backend (gramos si es por
 *  peso, 1 si es por unidad) — el usuario puede corregirla igual. */
function filaConTipoTela(valor: string, tiposTela: TipoTelaCatalogo[]): FilaInsumo {
  if (valor === '') return filaInsumoVacia();
  const idTipoTela = Number(valor);
  const tipo = tiposTela.find((t) => t.id === idTipoTela);
  const cantidad = tipo ? (tipo.esPorPeso ? (tipo.gramosSugerido != null ? String(tipo.gramosSugerido) : '') : '1') : '';
  return { idTipoTela, idPaletaColor: '', cantidad };
}

const inputSelectClase =
  'rounded-lg border border-wc-border bg-white px-2 py-1.5 text-xs text-wc-text outline-none disabled:bg-wc-bg disabled:text-wc-text-muted';

/** Selector de tipo de tela + color + cantidad para un insumo secundario, reutilizado por
 *  Capucha, Puños y cintura, y las filas libres agregadas a mano. */
function FilaInsumoEditor({
  fila,
  tiposTela,
  colores,
  descripcion,
  onCambiarTipo,
  onCambiarColor,
  onCambiarCantidad,
  onEliminar,
}: {
  fila: FilaInsumo;
  tiposTela: TipoTelaCatalogo[];
  colores: PaletaColorResponse[];
  /** Solo para filas libres: campo de texto editable con lo que identifica al insumo (para
   *  Capucha/Puños, esa etiqueta ya es fija y se ve en el título de la sección, así que se omite). */
  descripcion?: { valor: string; onCambiar: (valor: string) => void };
  onCambiarTipo: (valor: string) => void;
  onCambiarColor: (valor: string) => void;
  onCambiarCantidad: (valor: string) => void;
  onEliminar: () => void;
}) {
  const tieneContenido = fila.idTipoTela !== '' || fila.idPaletaColor !== '' || fila.cantidad !== '' || !!descripcion?.valor;

  return (
    <div className="flex flex-wrap items-center gap-1.5">
      {descripcion && (
        <input
          value={descripcion.valor}
          onChange={(e) => descripcion.onCambiar(e.target.value)}
          placeholder="¿Para qué es? (ej: Cuello)"
          className={`${inputSelectClase} min-w-[9rem] flex-[2]`}
        />
      )}
      <select value={fila.idTipoTela} onChange={(e) => onCambiarTipo(e.target.value)} className={`${inputSelectClase} flex-1 min-w-[7rem]`}>
        <option value="">Elegir tela…</option>
        {tiposTela.map((tipo) => (
          <option key={tipo.id} value={tipo.id}>
            {tipo.nombre}
          </option>
        ))}
      </select>
      <select
        value={fila.idPaletaColor}
        onChange={(e) => onCambiarColor(e.target.value)}
        disabled={fila.idTipoTela === ''}
        className={`${inputSelectClase} flex-1 min-w-[7rem]`}
      >
        <option value="">Color…</option>
        {colores.map((color) => (
          <option key={color.id} value={color.id}>
            {color.nombre}
          </option>
        ))}
      </select>
      <input
        type="number"
        min={0}
        step="0.01"
        value={fila.cantidad}
        onChange={(e) => onCambiarCantidad(e.target.value)}
        disabled={fila.idTipoTela === ''}
        placeholder="Cant."
        className={`${inputSelectClase} w-16`}
      />
      <button
        type="button"
        onClick={onEliminar}
        disabled={!descripcion && !tieneContenido}
        aria-label="Eliminar insumo"
        className="h-6 w-6 shrink-0 rounded text-sm font-bold text-wc-text-muted transition hover:bg-wc-bg hover:text-red-600 disabled:opacity-30"
      >
        ×
      </button>
    </div>
  );
}

interface ModalColoresGoteroProps {
  /** Siempre la versión más actualizada del producto (el modal no mantiene su propia copia). */
  producto: ProductoResponse;
  /** Catálogo de colores de cierre (tipoTela=CIERRE), para el selector que solo aplica a Camperas. */
  coloresCierre: PaletaColorResponse[];
  /** Cualquier mutación exitosa dentro del modal (subir imagen, guardar tela/cierre, asignar colores). */
  onActualizado: (productoActualizado: ProductoResponse) => void;
  /** Cierra el modal sin persistir cambios de tela/cierre pendientes (Cancelar). */
  onCerrar: () => void;
}

interface PendienteConfirmar {
  x: number;
  y: number;
  rgb: ColorRgb;
  rgbDetectado: string;
  idPaletaColor: number;
}

export default function ModalColoresGotero({ producto, coloresCierre, onActualizado, onCerrar }: ModalColoresGoteroProps) {
  const posiciones = producto.patronCorteColores ?? [];
  const esCampera = producto.tipoPrenda === TIPO_PRENDA_CAMPERA;
  const mostrarCapuchaYPunos = esCampera || producto.tipoPrenda === TIPO_PRENDA_BUZO;
  const canvasRef = useRef<HTMLCanvasElement | null>(null);
  const contenedorImagenRef = useRef<HTMLDivElement | null>(null);
  const inputImagenRef = useRef<HTMLInputElement | null>(null);

  const [subiendoImagen, setSubiendoImagen] = useState(false);
  const [errorImagen, setErrorImagen] = useState<string | null>(null);
  const [zoom, setZoom] = useState(1);
  const [escalaAjuste, setEscalaAjuste] = useState(1);

  // Tela y color de cierre son "borrador" hasta apretar Guardar: no se persisten solos.
  const [telaDraft, setTelaDraft] = useState<TipoTela | null>(telaEfectiva(producto));
  const [cierreDraft, setCierreDraft] = useState<number | null>(producto.idColorCierre ?? null);
  const [guardandoCambios, setGuardandoCambios] = useState(false);
  const [errorGuardarCambios, setErrorGuardarCambios] = useState<string | null>(null);

  // Moldería (nombre de negocio de PatronCorte): a diferencia de Tela/Cierre, se persiste al
  // toque al elegirla — determina qué posiciones existen para marcar colores, así que no
  // puede quedar como un borrador desincronizado del resto de esta sección.
  const [patronesCorte, setPatronesCorte] = useState<PatronCorteResponse[]>([]);
  const [guardandoMolderia, setGuardandoMolderia] = useState(false);
  const [errorMolderia, setErrorMolderia] = useState<string | null>(null);

  // Insumos secundarios (Capucha=Jersey, Puños y cintura=Ribb, y filas libres): igual que
  // Tela/Cierre, quedan como borrador hasta el Guardar general del modal — ahí viajan todos
  // juntos (más el Cierre) en un solo PUT al endpoint genérico (ver handleGuardarCambios).
  // Capucha y Puños son listas, no una fila única: puede hacer falta más de un color por
  // sección (ej. un puño de un color y el otro de otro).
  const [tiposTela, setTiposTela] = useState<TipoTelaCatalogo[]>([]);
  const [todaLaPaleta, setTodaLaPaleta] = useState<PaletaColorResponse[]>([]);
  const [capuchaFilas, setCapuchaFilas] = useState<FilaInsumoLibre[]>(() =>
    filasDesdeInsumo(producto.insumosSecundarios, DESCRIPCION_CAPUCHA),
  );
  const [punosFilas, setPunosFilas] = useState<FilaInsumoLibre[]>(() =>
    filasDesdeInsumo(producto.insumosSecundarios, DESCRIPCION_PUNOS),
  );
  const [filasLibres, setFilasLibres] = useState<FilaInsumoLibre[]>(() =>
    filasLibresDesdeInsumos(producto.insumosSecundarios),
  );

  const [paleta, setPaleta] = useState<PaletaColorResponse[]>([]);
  const [cargandoPaleta, setCargandoPaleta] = useState(false);
  const [errorCarga, setErrorCarga] = useState<string | null>(null);

  const [editandoColores, setEditandoColores] = useState(false);
  const [pasoActual, setPasoActual] = useState(0);
  const [asignaciones, setAsignaciones] = useState<ProductoColorItemRequest[]>([]);
  const [pendiente, setPendiente] = useState<PendienteConfirmar | null>(null);
  const [errorLectura, setErrorLectura] = useState<string | null>(null);

  const [creandoColor, setCreandoColor] = useState(false);
  const [nuevoColorNombre, setNuevoColorNombre] = useState('');
  const [nuevoColorHex, setNuevoColorHex] = useState('#000000');
  const [guardandoColorNuevo, setGuardandoColorNuevo] = useState(false);
  const [errorColorNuevo, setErrorColorNuevo] = useState<string | null>(null);

  const [guardandoFinal, setGuardandoFinal] = useState(false);
  const [errorGuardado, setErrorGuardado] = useState<string | null>(null);

  const enResumen = editandoColores && pasoActual >= posiciones.length && posiciones.length > 0;
  const posicionActual = posiciones[pasoActual];
  const urlImagen = producto.imagenDisenoUrl ? urlArchivoSubido(producto.imagenDisenoUrl) : null;
  const escalaFinal = escalaAjuste * zoom;

  // Escala "ajustar a la ventana": se calcula sola por imagen, independiente de si se está
  // mostrando como <img> (solo lectura) o como <canvas> (marcando colores). El zoom del
  // usuario (100% por defecto) es un multiplicador sobre esta base, no sobre el tamaño nativo
  // del archivo — si no, una foto de celular de varios miles de px se ve recortada al 100%.
  useEffect(() => {
    if (!urlImagen) return;
    let cancelado = false;
    const img = new Image();
    img.onload = () => {
      if (cancelado) return;
      const contenedor = contenedorImagenRef.current;
      if (!contenedor || contenedor.clientWidth === 0 || contenedor.clientHeight === 0) return;
      const ajuste = Math.min(contenedor.clientWidth / img.naturalWidth, contenedor.clientHeight / img.naturalHeight, 1);
      setEscalaAjuste(ajuste);
      setZoom(1);
    };
    img.src = urlImagen;
    return () => {
      cancelado = true;
    };
  }, [urlImagen]);

  useEffect(() => {
    let cancelado = false;
    listarPatronesCorte()
      .then((data) => {
        if (!cancelado) setPatronesCorte(data);
      })
      .catch(() => {
        /* si falla, el selector de Moldería queda vacío pero el resto del modal funciona igual */
      });
    return () => {
      cancelado = true;
    };
  }, []);

  useEffect(() => {
    let cancelado = false;
    listarTiposTela()
      .then((data) => {
        if (!cancelado) setTiposTela(data);
      })
      .catch(() => {
        /* si falla, los selectores de insumos secundarios quedan vacíos pero el resto del modal funciona igual */
      });
    // Sin filtro: trae los colores de todas las telas, para poder armar la paleta de
    // cualquier insumo secundario sin depender de la tela elegida para el gotero (telaDraft).
    listarPaletaColores()
      .then((data) => {
        if (!cancelado) setTodaLaPaleta(data);
      })
      .catch(() => {});
    return () => {
      cancelado = true;
    };
  }, []);

  useEffect(() => {
    if (!telaDraft) return;
    let cancelado = false;
    setCargandoPaleta(true);
    listarPaletaColores(telaDraft)
      .then((data) => {
        if (cancelado) return;
        setPaleta(data);
        setCargandoPaleta(false);
      })
      .catch((err) => {
        if (cancelado) return;
        setErrorCarga(extraerMensajeError(err, 'No se pudo cargar la paleta de colores.'));
        setCargandoPaleta(false);
      });
    return () => {
      cancelado = true;
    };
  }, [telaDraft]);

  useEffect(() => {
    if (!telaDraft || !urlImagen || cargandoPaleta || errorCarga || !editandoColores || enResumen) return;
    const canvas = canvasRef.current;
    if (!canvas) return;

    const img = new Image();
    img.crossOrigin = 'anonymous';
    img.onload = () => {
      canvas.width = img.naturalWidth;
      canvas.height = img.naturalHeight;
      canvas.getContext('2d')?.drawImage(img, 0, 0);
    };
    img.onerror = () => setErrorCarga('No se pudo cargar la imagen para marcar los colores.');
    img.src = urlImagen;
  }, [urlImagen, telaDraft, cargandoPaleta, errorCarga, editandoColores, enResumen]);

  async function handleSubirImagen(e: ChangeEvent<HTMLInputElement>) {
    const file = e.target.files?.[0];
    if (!file) return;

    if (!TIPOS_IMAGEN_PERMITIDOS.includes(file.type)) {
      setErrorImagen('Formato no válido: solo JPG o PNG');
      if (inputImagenRef.current) inputImagenRef.current.value = '';
      return;
    }

    setErrorImagen(null);
    setSubiendoImagen(true);
    try {
      const actualizado = await subirImagenDisenoProducto(producto.id, file);
      onActualizado(actualizado);
      setEditandoColores(false);
      setPasoActual(0);
      setAsignaciones([]);
      setPendiente(null);
    } catch (err) {
      setErrorImagen(extraerMensajeError(err, 'No se pudo subir la imagen.'));
    } finally {
      setSubiendoImagen(false);
      if (inputImagenRef.current) inputImagenRef.current.value = '';
    }
  }

  /** Cambia la Moldería al toque: si cambia, el back descarta los colores ya marcados
   * (quedaban atados a posiciones de la moldería anterior) — se refleja acá limpiando
   * también el progreso local del click a click. */
  async function handleCambiarMolderia(valor: string) {
    const idPatronCorte = valor === '' ? null : Number(valor);
    setErrorMolderia(null);
    setGuardandoMolderia(true);
    try {
      const actualizado = await actualizarPatronCorteProducto(producto.id, idPatronCorte);
      onActualizado(actualizado);
      setEditandoColores(false);
      setPasoActual(0);
      setAsignaciones([]);
      setPendiente(null);
    } catch (err) {
      setErrorMolderia(extraerMensajeError(err, 'No se pudo actualizar la moldería.'));
    } finally {
      setGuardandoMolderia(false);
    }
  }

  function handleCambiarTelaDraft(nuevaTela: TipoTela) {
    setTelaDraft(nuevaTela);
    // La paleta depende de la tela: cualquier posición ya marcada en esta sesión con la tela
    // anterior deja de ser válida.
    setPasoActual(0);
    setAsignaciones([]);
    setPendiente(null);
  }

  /** Tela y cierre son borrador hasta este punto; se persisten juntos con un click. */
  function coloresParaTipoTela(idTipoTela: number | ''): PaletaColorResponse[] {
    if (idTipoTela === '') return [];
    const tipo = tiposTela.find((t) => t.id === idTipoTela);
    if (!tipo) return [];
    return todaLaPaleta.filter((color) => color.tipoTela === tipo.codigo);
  }

  /**
   * Capucha, Puños y las filas libres son todas listas del mismo tipo (FilaInsumoLibre): no
   * hay restricción de unicidad, así que agregar/editar/quitar funciona igual en las tres —
   * lo único que cambia es qué setter reciben y si arrancan con una descripcion fija.
   */
  /** codigoTipoTelaDefault precarga el tipo de tela más usual de esa sección (Jersey en
   *  Capucha, Ribb en Puños) — el usuario lo puede cambiar en el selector igual, no queda fijo. */
  function agregarFila(setFilas: Dispatch<SetStateAction<FilaInsumoLibre[]>>, descripcionFija: string, codigoTipoTelaDefault?: string) {
    const tipoDefault = codigoTipoTelaDefault ? tiposTela.find((t) => t.codigo === codigoTipoTelaDefault) : undefined;
    const base = tipoDefault ? filaConTipoTela(String(tipoDefault.id), tiposTela) : filaInsumoVacia();
    setFilas((prev) => [...prev, { id: crypto.randomUUID(), ...base, descripcion: descripcionFija }]);
  }

  function eliminarFila(setFilas: Dispatch<SetStateAction<FilaInsumoLibre[]>>, id: string) {
    setFilas((prev) => prev.filter((f) => f.id !== id));
  }

  function actualizarFila(setFilas: Dispatch<SetStateAction<FilaInsumoLibre[]>>, id: string, cambio: Partial<Omit<FilaInsumoLibre, 'id'>>) {
    setFilas((prev) => prev.map((f) => (f.id === id ? { ...f, ...cambio } : f)));
  }

  /**
   * Junta Capucha + Puños + libres + Cierre (si aplica) en un solo array para el PUT
   * genérico, cada uno con su descripcion (identidad real de la fila, ver
   * ProductoInsumoSecundario — no hay problema en que se repita entre filas, ej. dos "Puños
   * y cintura" de distinto color). Valida que toda fila con algo cargado tenga descripcion,
   * tipo de tela, color y cantidad completos — una fila totalmente vacía simplemente no se
   * manda (el usuario la agregó y se arrepintió, o todavía no la completó).
   */
  function construirInsumosParaGuardar(): { items: ProductoInsumoSecundarioItemRequest[] } | { error: string } {
    const items: ProductoInsumoSecundarioItemRequest[] = [];

    for (const fila of [...capuchaFilas, ...punosFilas, ...filasLibres]) {
      const vacia = fila.idTipoTela === '' && fila.idPaletaColor === '' && !fila.cantidad && !fila.descripcion.trim();
      if (vacia) continue;

      if (!fila.descripcion.trim()) {
        return { error: 'Escribí para qué es cada insumo agregado a mano.' };
      }
      if (fila.idTipoTela === '' || fila.idPaletaColor === '') {
        return { error: `Elegí tipo de tela y color para "${fila.descripcion.trim()}".` };
      }
      const cantidad = Number(fila.cantidad);
      if (!fila.cantidad || !Number.isFinite(cantidad) || cantidad <= 0) {
        return { error: `Ingresá una cantidad válida para "${fila.descripcion.trim()}".` };
      }
      items.push({ descripcion: fila.descripcion.trim(), idTipoTela: fila.idTipoTela, idPaletaColor: fila.idPaletaColor, cantidad });
    }

    if (esCampera && cierreDraft != null) {
      const tipoCierre = tiposTela.find((t) => t.codigo === CODIGO_TIPO_TELA_CIERRE);
      if (!tipoCierre) {
        return { error: 'No se pudo determinar el tipo de tela de Cierre. Reintentá en unos segundos.' };
      }
      items.push({ descripcion: DESCRIPCION_CIERRE, idTipoTela: tipoCierre.id, idPaletaColor: cierreDraft, cantidad: 1 });
    }

    return { items };
  }

  async function handleGuardarCambios() {
    setGuardandoCambios(true);
    setErrorGuardarCambios(null);
    try {
      let actualizado = producto;
      if (telaDraft && telaDraft !== telaEfectiva(producto)) {
        actualizado = await actualizarTipoTelaProducto(producto.id, telaDraft);
      }

      const resultado = construirInsumosParaGuardar();
      if ('error' in resultado) {
        setErrorGuardarCambios(resultado.error);
        setGuardandoCambios(false);
        return;
      }
      actualizado = await actualizarInsumosSecundariosProducto(producto.id, resultado.items);

      onActualizado(actualizado);
      onCerrar();
    } catch (err) {
      setErrorGuardarCambios(extraerMensajeError(err, 'No se pudieron guardar los cambios.'));
    } finally {
      setGuardandoCambios(false);
    }
  }

  /** Si la tela todavía no está guardada, hay que persistirla antes: los colores que se van a
   * marcar quedan atados a la paleta de esa tela, no puede quedar como un borrador suelto. */
  async function handleEmpezarEdicionColores() {
    setErrorGuardarCambios(null);
    if (telaDraft && telaDraft !== telaEfectiva(producto)) {
      setGuardandoCambios(true);
      try {
        const actualizado = await actualizarTipoTelaProducto(producto.id, telaDraft);
        onActualizado(actualizado);
      } catch (err) {
        setErrorGuardarCambios(extraerMensajeError(err, 'No se pudo guardar la tela.'));
        setGuardandoCambios(false);
        return;
      }
      setGuardandoCambios(false);
    }
    setEditandoColores(true);
    setPasoActual(0);
    setAsignaciones([]);
    setPendiente(null);
    setErrorGuardado(null);
  }

  function handleClickCanvas(e: MouseEvent<HTMLCanvasElement>) {
    const canvas = canvasRef.current;
    if (!canvas || !posicionActual) return;

    const rect = canvas.getBoundingClientRect();
    const escalaX = canvas.width / rect.width;
    const escalaY = canvas.height / rect.height;
    const x = Math.round((e.clientX - rect.left) * escalaX);
    const y = Math.round((e.clientY - rect.top) * escalaY);

    const ctx = canvas.getContext('2d');
    if (!ctx) return;

    let pixel: Uint8ClampedArray;
    try {
      pixel = ctx.getImageData(x, y, 1, 1).data;
    } catch {
      setErrorLectura('No se pudo leer el color de la imagen (problema de CORS con el archivo subido).');
      return;
    }

    setErrorLectura(null);
    const rgb = { r: pixel[0], g: pixel[1], b: pixel[2] };
    const sugerido = colorMasCercano(rgb, paleta);

    setPendiente({
      x,
      y,
      rgb,
      rgbDetectado: `rgb(${rgb.r}, ${rgb.g}, ${rgb.b})`,
      idPaletaColor: sugerido?.id ?? paleta[0]?.id ?? 0,
    });
    setCreandoColor(false);
    setErrorColorNuevo(null);
  }

  function confirmarPosicionActual() {
    if (!pendiente || !posicionActual) return;

    const nuevaAsignacion: ProductoColorItemRequest = {
      idPatronCorteColor: posicionActual.id,
      idPaletaColor: pendiente.idPaletaColor,
      coordenadaX: pendiente.x,
      coordenadaY: pendiente.y,
      rgbDetectado: pendiente.rgbDetectado,
    };

    // El color de cierre se completa solo con el nombre del Color 1 (misma idea que el
    // default del backend, pero visible acá al toque para poder corregirlo antes de guardar).
    if (posicionActual.orden === 1 && esCampera) {
      const colorElegido = paleta.find((c) => c.id === pendiente.idPaletaColor);
      const match = colorElegido
        ? coloresCierre.find((c) => c.nombre.toLowerCase() === colorElegido.nombre.toLowerCase())
        : undefined;
      if (match) setCierreDraft(match.id);
    }

    setAsignaciones((prev) => [...prev, nuevaAsignacion]);
    setPendiente(null);
    setPasoActual((prev) => prev + 1);
  }

  async function handleCrearColorNuevo() {
    if (!nuevoColorNombre.trim() || !telaDraft) return;
    setGuardandoColorNuevo(true);
    setErrorColorNuevo(null);
    try {
      const creado = await crearColorPaleta({ nombre: nuevoColorNombre.trim(), hex: nuevoColorHex, tipoTela: telaDraft });
      setPaleta((prev) => [...prev, creado].sort((a, b) => a.nombre.localeCompare(b.nombre)));
      // También a todaLaPaleta: si el nuevo color coincide con la tela de algún insumo
      // secundario (Capucha/Puños/libre), tiene que aparecer ahí sin recargar el modal.
      setTodaLaPaleta((prev) => [...prev, creado]);
      setPendiente((prev) => (prev ? { ...prev, idPaletaColor: creado.id } : prev));
      setCreandoColor(false);
      setNuevoColorNombre('');
    } catch (err) {
      setErrorColorNuevo(extraerMensajeError(err, 'No se pudo crear el color.'));
    } finally {
      setGuardandoColorNuevo(false);
    }
  }

  async function handleConfirmarTodo() {
    setGuardandoFinal(true);
    setErrorGuardado(null);
    try {
      const actualizado = await asignarColoresProducto(producto.id, asignaciones);
      // asignarColoresProducto ya sugiere y persiste Cierre/Capucha/Puños del lado del backend
      // (mismo nombre que el Color 1 recién elegido, ver ProductoService.sugerirInsumoSecundario)
      // — acá solo reflejamos esa sugerencia en los borradores locales, sin otro guardado más:
      // si el usuario la corrige, esa corrección recién se persiste con el Guardar general.
      setCierreDraft(actualizado.idColorCierre ?? cierreDraft);
      setCapuchaFilas(filasDesdeInsumo(actualizado.insumosSecundarios, DESCRIPCION_CAPUCHA));
      setPunosFilas(filasDesdeInsumo(actualizado.insumosSecundarios, DESCRIPCION_PUNOS));
      setFilasLibres(filasLibresDesdeInsumos(actualizado.insumosSecundarios));
      onActualizado(actualizado);
      setEditandoColores(false);
    } catch (err) {
      setErrorGuardado(extraerMensajeError(err, 'No se pudieron guardar los colores.'));
    } finally {
      setGuardandoFinal(false);
    }
  }

  return (
    <div className="tw-scope fixed inset-0 z-50 flex items-center justify-center bg-black/70 p-6" role="dialog" aria-modal="true">
      <div className="flex max-h-[92vh] w-full max-w-5xl flex-col gap-4 overflow-hidden rounded-xl bg-white p-6 shadow-2xl">
        <div>
          <h2 className="text-base font-bold text-wc-text">Ficha de colores</h2>
          <p className="text-xs text-wc-text-muted">{producto.tipoPrenda ?? 'Prenda'}</p>
        </div>

        <div className="grid flex-1 grid-cols-1 gap-5 overflow-y-auto md:grid-cols-2">
          {/* Columna izquierda: imagen */}
          <div className="flex flex-col gap-2">
            {urlImagen ? (
              <>
                <div className="flex items-center justify-between gap-2">
                  <span className="text-xs font-semibold text-wc-text">Imagen de diseño</span>
                  <div className="flex items-center gap-1.5">
                    <button
                      type="button"
                      onClick={() => setZoom((z) => Math.max(ZOOM_MIN, +(z - ZOOM_PASO).toFixed(2)))}
                      disabled={zoom <= ZOOM_MIN}
                      className="h-6 w-6 rounded border border-wc-border text-sm font-bold text-wc-text disabled:opacity-40"
                      aria-label="Alejar"
                    >
                      −
                    </button>
                    <span className="w-10 text-center text-[11px] text-wc-text-muted">{Math.round(zoom * 100)}%</span>
                    <button
                      type="button"
                      onClick={() => setZoom((z) => Math.min(ZOOM_MAX, +(z + ZOOM_PASO).toFixed(2)))}
                      disabled={zoom >= ZOOM_MAX}
                      className="h-6 w-6 rounded border border-wc-border text-sm font-bold text-wc-text disabled:opacity-40"
                      aria-label="Acercar"
                    >
                      +
                    </button>
                  </div>
                </div>

                <div ref={contenedorImagenRef} className="h-[55vh] w-full overflow-auto rounded-lg border border-wc-border bg-wc-bg">
                  {editandoColores && !enResumen && telaDraft ? (
                    <canvas
                      ref={canvasRef}
                      onClick={handleClickCanvas}
                      style={{ transform: `scale(${escalaFinal})`, transformOrigin: 'top left' }}
                      className="cursor-crosshair"
                    />
                  ) : (
                    <img
                      src={urlImagen}
                      alt={producto.tipoPrenda ?? 'Prenda'}
                      style={{ transform: `scale(${escalaFinal})`, transformOrigin: 'top left', width: 'max-content' }}
                      className="max-w-none"
                    />
                  )}
                </div>

                <label
                  className={`self-start text-xs font-semibold underline ${
                    subiendoImagen ? 'cursor-wait text-wc-text-muted' : 'cursor-pointer text-wc-green'
                  }`}
                >
                  {subiendoImagen ? 'Subiendo…' : 'Reemplazar imagen'}
                  <input
                    ref={inputImagenRef}
                    type="file"
                    accept=".jpg,.jpeg,.png,image/jpeg,image/png"
                    className="hidden"
                    disabled={subiendoImagen}
                    onChange={handleSubirImagen}
                  />
                </label>
              </>
            ) : (
              <div className="flex h-[55vh] flex-col items-center justify-center gap-3 rounded-lg border border-dashed border-wc-border bg-wc-bg">
                <p className="px-4 text-center text-sm text-wc-text-muted">Todavía no se cargó una imagen de diseño.</p>
                <label
                  className={`rounded-md px-4 py-2 text-sm font-semibold text-white transition ${
                    subiendoImagen ? 'cursor-wait bg-wc-text-muted' : 'cursor-pointer bg-wc-green hover:bg-wc-green-dark'
                  }`}
                >
                  {subiendoImagen ? 'Subiendo…' : 'Cargar imagen'}
                  <input
                    ref={inputImagenRef}
                    type="file"
                    accept=".jpg,.jpeg,.png,image/jpeg,image/png"
                    className="hidden"
                    disabled={subiendoImagen}
                    onChange={handleSubirImagen}
                  />
                </label>
              </div>
            )}
            {errorImagen && <p className="text-xs font-medium text-red-600">{errorImagen}</p>}
          </div>

          {/* Columna derecha: moldería, tela, cierre y colores, en vertical */}
          <div className="flex flex-col gap-4">
            <div className="flex flex-col gap-1.5 rounded-lg border border-wc-border p-3">
              <span className="text-xs font-semibold text-wc-text">Moldería</span>
              <select
                value={producto.idPatronCorte ?? ''}
                disabled={guardandoMolderia}
                onChange={(e) => handleCambiarMolderia(e.target.value)}
                className="rounded-lg border border-wc-border bg-white px-2 py-1.5 text-sm text-wc-text outline-none"
              >
                <option value="">Sin moldería</option>
                {patronesCorte
                  .filter((patron) => patron.tiposPrenda.some((t) => t.nombre === producto.tipoPrenda))
                  .map((patron) => (
                    <option key={patron.id} value={patron.id}>
                      #{patron.numeroInterno} {patron.nombre}
                    </option>
                  ))}
              </select>
              {errorMolderia && <p className="text-xs font-medium text-red-600">{errorMolderia}</p>}
            </div>

            <div className="flex flex-col gap-1.5 rounded-lg border border-wc-border p-3">
              <span className="text-xs font-semibold text-wc-text">Tela</span>
              <select
                value={telaDraft ?? ''}
                onChange={(e) => handleCambiarTelaDraft(e.target.value as TipoTela)}
                className="rounded-lg border border-wc-border bg-white px-2 py-1.5 text-sm text-wc-text outline-none"
              >
                <option value="" disabled>
                  Elegir tela…
                </option>
                {TIPOS_TELA_PRENDA.map((tela) => (
                  <option key={tela} value={tela}>
                    {TIPO_TELA_LABELS[tela]}
                  </option>
                ))}
              </select>
            </div>

            {esCampera && (
              <div className="flex flex-col gap-1.5 rounded-lg border border-wc-border p-3">
                <span className="text-xs font-semibold text-wc-text">Color de cierre</span>
                <div className="flex items-center gap-2">
                  <select
                    value={cierreDraft ?? ''}
                    onChange={(e) => setCierreDraft(Number(e.target.value))}
                    className="flex-1 rounded-lg border border-wc-border bg-white px-2 py-1.5 text-sm text-wc-text outline-none"
                  >
                    <option value="" disabled>
                      Se completa con el Color 1
                    </option>
                    {coloresCierre.map((color) => (
                      <option key={color.id} value={color.id}>
                        {color.nombre}
                      </option>
                    ))}
                  </select>
                  {cierreDraft != null && (
                    <span
                      className="h-5 w-5 shrink-0 rounded-full border border-wc-border"
                      style={{ backgroundColor: coloresCierre.find((c) => c.id === cierreDraft)?.hex }}
                    />
                  )}
                </div>
              </div>
            )}

            <div className="flex flex-col gap-2 rounded-lg border border-wc-border p-3">
              <span className="text-xs font-semibold text-wc-text">Colores del diseño</span>

              {!urlImagen && (
                <p className="text-xs text-wc-text-muted">Subí una imagen para poder marcar los colores.</p>
              )}

              {urlImagen && !producto.idPatronCorte && (
                <p className="text-xs text-wc-text-muted">
                  Elegí la Moldería arriba para poder marcar los colores.
                </p>
              )}

              {urlImagen && producto.idPatronCorte && posiciones.length === 0 && (
                <p className="text-xs text-wc-text-muted">La moldería de esta prenda no tiene posiciones de color definidas.</p>
              )}

              {urlImagen && posiciones.length > 0 && !editandoColores && (
                <>
                  <div className="flex flex-col gap-1">
                    {[...posiciones]
                      .sort((a, b) => a.orden - b.orden)
                      .map((posicion) => {
                        const asignado = producto.colores.find((c) => c.idPatronCorteColor === posicion.id);
                        return (
                          <div key={posicion.id} className="flex items-center gap-1.5 text-xs text-wc-text-muted">
                            <span
                              className="h-3 w-3 shrink-0 rounded-full border border-wc-border"
                              style={{ backgroundColor: asignado?.hexColor ?? 'transparent' }}
                            />
                            <span>
                              Color {posicion.orden} ({posicion.gramos} g):{' '}
                              <span className="font-medium text-wc-text">{asignado?.nombreColor ?? 'Sin definir'}</span>
                            </span>
                          </div>
                        );
                      })}
                  </div>
                  <button
                    type="button"
                    onClick={handleEmpezarEdicionColores}
                    disabled={!telaDraft || guardandoCambios}
                    className="self-start rounded-md bg-wc-green px-3 py-1.5 text-xs font-semibold text-white transition hover:bg-wc-green-dark disabled:cursor-not-allowed disabled:opacity-50"
                    title={!telaDraft ? 'Elegí la tela primero' : undefined}
                  >
                    {producto.colores.length > 0 ? 'Rehacer colores' : 'Marcar colores'}
                  </button>
                </>
              )}

              {urlImagen && editandoColores && !telaDraft && (
                <p className="text-xs text-wc-text-muted">Elegí la tela de esta prenda para poder continuar.</p>
              )}

              {errorCarga && <p className="text-xs font-medium text-red-600">{errorCarga}</p>}

              {urlImagen && editandoColores && telaDraft && cargandoPaleta && !errorCarga && (
                <p className="text-xs text-wc-text-muted">Cargando paleta de colores…</p>
              )}

              {urlImagen && editandoColores && telaDraft && !cargandoPaleta && !errorCarga && !enResumen && posicionActual && (
                <>
                  <p className="text-xs font-semibold text-wc-text">
                    Marcá el color de la posición {pasoActual + 1} de {posiciones.length} ({posicionActual.gramos} g de{' '}
                    {TIPO_TELA_LABELS[telaDraft]}) — hacé click sobre la imagen de la izquierda.
                  </p>

                  {errorLectura && <p className="text-xs font-medium text-red-600">{errorLectura}</p>}

                  {pendiente && (
                    <div className="flex flex-col gap-2 rounded-lg border border-wc-border p-2">
                      <div className="flex flex-wrap items-center gap-2">
                        <span
                          className="h-6 w-6 shrink-0 rounded-full border border-wc-border"
                          style={{ backgroundColor: pendiente.rgbDetectado }}
                        />
                        <span className="text-[11px] text-wc-text-muted">{pendiente.rgbDetectado}</span>
                      </div>

                      {!creandoColor ? (
                        <div className="flex flex-wrap items-center gap-2">
                          <select
                            value={pendiente.idPaletaColor}
                            onChange={(e) => setPendiente({ ...pendiente, idPaletaColor: Number(e.target.value) })}
                            className="rounded-lg border border-wc-border bg-white px-2 py-1.5 text-sm text-wc-text"
                          >
                            {paleta.map((color) => (
                              <option key={color.id} value={color.id}>
                                {color.nombre}
                              </option>
                            ))}
                          </select>
                          <button
                            type="button"
                            onClick={() => {
                              setNuevoColorHex(rgbAHex(pendiente.rgb));
                              setCreandoColor(true);
                            }}
                            className="text-xs font-semibold text-wc-green underline"
                          >
                            + Nuevo color
                          </button>
                          <button
                            type="button"
                            onClick={confirmarPosicionActual}
                            className="w-full rounded-md bg-wc-green px-4 py-1.5 text-xs font-semibold text-white transition hover:bg-wc-green-dark"
                          >
                            Confirmar posición
                          </button>
                        </div>
                      ) : (
                        <div className="flex flex-col gap-2 border-t border-wc-border pt-2">
                          <p className="text-[11px] text-wc-text-muted">
                            El selector arranca con el color detectado; podés dejarlo así o ajustarlo.
                          </p>
                          <div className="flex flex-wrap items-center gap-2">
                            <input
                              value={nuevoColorNombre}
                              onChange={(e) => setNuevoColorNombre(e.target.value)}
                              placeholder="Nombre del color"
                              className="rounded-lg border border-wc-border bg-white px-2 py-1.5 text-sm text-wc-text"
                            />
                            <input
                              type="color"
                              value={nuevoColorHex}
                              onChange={(e) => setNuevoColorHex(e.target.value)}
                              className="h-9 w-12 cursor-pointer rounded border border-wc-border"
                            />
                          </div>
                          {nuevoColorHex.toUpperCase() !== rgbAHex(pendiente.rgb) && (
                            <button
                              type="button"
                              onClick={() => setNuevoColorHex(rgbAHex(pendiente.rgb))}
                              className="self-start text-xs text-wc-text-muted underline"
                            >
                              Usar color detectado
                            </button>
                          )}
                          <div className="flex items-center gap-2">
                            <button
                              type="button"
                              onClick={handleCrearColorNuevo}
                              disabled={guardandoColorNuevo || !nuevoColorNombre.trim()}
                              className="rounded-md bg-wc-green px-3 py-1.5 text-xs font-semibold text-white transition hover:bg-wc-green-dark disabled:cursor-wait disabled:opacity-60"
                            >
                              {guardandoColorNuevo ? 'Creando…' : 'Crear y usar'}
                            </button>
                            <button
                              type="button"
                              onClick={() => setCreandoColor(false)}
                              className="text-xs text-wc-text-muted underline"
                            >
                              Volver a la paleta
                            </button>
                          </div>
                          {errorColorNuevo && <p className="text-xs font-medium text-red-600">{errorColorNuevo}</p>}
                        </div>
                      )}
                    </div>
                  )}

                  {!pendiente && (
                    <p className="text-xs text-wc-text-muted">Esperando click sobre la imagen…</p>
                  )}
                </>
              )}

              {enResumen && (
                <>
                  <p className="text-xs font-semibold text-wc-text">Revisá antes de confirmar:</p>
                  <div className="flex flex-col gap-1.5">
                    {asignaciones.map((asignacion, index) => {
                      const color = paleta.find((c) => c.id === asignacion.idPaletaColor);
                      const posicion = posiciones.find((p) => p.id === asignacion.idPatronCorteColor);
                      return (
                        <div key={asignacion.idPatronCorteColor} className="flex items-center gap-2 rounded-lg border border-wc-border p-1.5">
                          <span
                            className="h-5 w-5 shrink-0 rounded-full border border-wc-border"
                            style={{ backgroundColor: color?.hex }}
                          />
                          <span className="text-xs text-wc-text">
                            Posición {index + 1} ({posicion?.gramos ?? '?'} g): <strong>{color?.nombre ?? 'Color desconocido'}</strong>
                          </span>
                        </div>
                      );
                    })}
                    {esCampera && (
                      <div className="flex items-center gap-2 rounded-lg border border-wc-border p-1.5">
                        <span
                          className="h-5 w-5 shrink-0 rounded-full border border-wc-border"
                          style={{ backgroundColor: coloresCierre.find((c) => c.id === cierreDraft)?.hex }}
                        />
                        <span className="text-xs text-wc-text">
                          Cierre: <strong>{coloresCierre.find((c) => c.id === cierreDraft)?.nombre ?? 'Sin definir'}</strong>
                        </span>
                      </div>
                    )}
                  </div>

                  {errorGuardado && <p className="text-xs font-medium text-red-600">{errorGuardado}</p>}

                  <button
                    type="button"
                    onClick={handleConfirmarTodo}
                    disabled={guardandoFinal}
                    className="self-end rounded-md bg-wc-green px-5 py-2 text-sm font-semibold text-white transition hover:bg-wc-green-dark disabled:cursor-wait disabled:opacity-60"
                  >
                    {guardandoFinal ? 'Guardando…' : 'Confirmar colores'}
                  </button>
                </>
              )}
            </div>

            <div className="flex flex-col gap-3 rounded-lg border border-wc-border p-3">
              <span className="text-xs font-semibold text-wc-text">Insumos secundarios</span>

                {mostrarCapuchaYPunos && (
                  <div className="flex flex-col gap-1">
                    <span className="text-[11px] font-medium text-wc-text-muted">Capucha</span>
                    {capuchaFilas.map((fila) => (
                      <FilaInsumoEditor
                        key={fila.id}
                        fila={fila}
                        tiposTela={tiposTela}
                        colores={coloresParaTipoTela(fila.idTipoTela)}
                        onCambiarTipo={(valor) => actualizarFila(setCapuchaFilas, fila.id, filaConTipoTela(valor, tiposTela))}
                        onCambiarColor={(valor) => actualizarFila(setCapuchaFilas, fila.id, { idPaletaColor: valor === '' ? '' : Number(valor) })}
                        onCambiarCantidad={(valor) => actualizarFila(setCapuchaFilas, fila.id, { cantidad: valor })}
                        onEliminar={() => eliminarFila(setCapuchaFilas, fila.id)}
                      />
                    ))}
                    <button
                      type="button"
                      onClick={() => agregarFila(setCapuchaFilas, DESCRIPCION_CAPUCHA, CODIGO_TIPO_TELA_JERSEY)}
                      className="self-start text-[11px] font-semibold text-wc-green underline"
                    >
                      + Agregar color a Capucha
                    </button>
                  </div>
                )}

                {mostrarCapuchaYPunos && (
                  <div className="flex flex-col gap-1">
                    <span className="text-[11px] font-medium text-wc-text-muted">Puños y cintura</span>
                    {punosFilas.map((fila) => (
                      <FilaInsumoEditor
                        key={fila.id}
                        fila={fila}
                        tiposTela={tiposTela}
                        colores={coloresParaTipoTela(fila.idTipoTela)}
                        onCambiarTipo={(valor) => actualizarFila(setPunosFilas, fila.id, filaConTipoTela(valor, tiposTela))}
                        onCambiarColor={(valor) => actualizarFila(setPunosFilas, fila.id, { idPaletaColor: valor === '' ? '' : Number(valor) })}
                        onCambiarCantidad={(valor) => actualizarFila(setPunosFilas, fila.id, { cantidad: valor })}
                        onEliminar={() => eliminarFila(setPunosFilas, fila.id)}
                      />
                    ))}
                    <button
                      type="button"
                      onClick={() => agregarFila(setPunosFilas, DESCRIPCION_PUNOS, CODIGO_TIPO_TELA_RIBB)}
                      className="self-start text-[11px] font-semibold text-wc-green underline"
                    >
                      + Agregar color a Puños y cintura
                    </button>
                  </div>
                )}

                {filasLibres.length > 0 && (
                  <div className="flex flex-col gap-1">
                    {mostrarCapuchaYPunos && <span className="text-[11px] font-medium text-wc-text-muted">Otros insumos</span>}
                    {filasLibres.map((fila) => (
                      <FilaInsumoEditor
                        key={fila.id}
                        fila={fila}
                        tiposTela={tiposTela}
                        colores={coloresParaTipoTela(fila.idTipoTela)}
                        descripcion={{ valor: fila.descripcion, onCambiar: (valor) => actualizarFila(setFilasLibres, fila.id, { descripcion: valor }) }}
                        onCambiarTipo={(valor) => actualizarFila(setFilasLibres, fila.id, filaConTipoTela(valor, tiposTela))}
                        onCambiarColor={(valor) => actualizarFila(setFilasLibres, fila.id, { idPaletaColor: valor === '' ? '' : Number(valor) })}
                        onCambiarCantidad={(valor) => actualizarFila(setFilasLibres, fila.id, { cantidad: valor })}
                        onEliminar={() => eliminarFila(setFilasLibres, fila.id)}
                      />
                    ))}
                  </div>
                )}

                <button
                  type="button"
                  onClick={() => agregarFila(setFilasLibres, '')}
                  className="self-start text-xs font-semibold text-wc-green underline"
                >
                  + Agregar insumo
                </button>
              </div>
            </div>
          </div>

        <div className="flex items-center justify-end gap-2 border-t border-wc-border pt-4">
          {errorGuardarCambios && <p className="mr-auto text-xs font-medium text-red-600">{errorGuardarCambios}</p>}
          <button
            type="button"
            onClick={onCerrar}
            disabled={guardandoCambios}
            className="rounded-md border border-wc-border bg-white px-4 py-2 text-sm font-semibold text-wc-text-muted transition hover:bg-wc-bg disabled:cursor-not-allowed disabled:opacity-60"
          >
            Cancelar
          </button>
          <button
            type="button"
            onClick={handleGuardarCambios}
            disabled={guardandoCambios}
            className="rounded-md bg-wc-green px-5 py-2 text-sm font-semibold text-white transition hover:bg-wc-green-dark disabled:cursor-wait disabled:opacity-60"
          >
            {guardandoCambios ? 'Guardando…' : 'Guardar'}
          </button>
        </div>
      </div>
    </div>
  );
}
