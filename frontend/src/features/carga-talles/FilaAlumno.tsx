import { useEffect, useState } from 'react';
import ComboCelda from './ComboCelda';
import { actualizarCombo, eliminarAlumno } from '../../services/cargaTallesService';
import { extraerMensajeError } from '../../utils/errores';
import type { AlumnoResponse, ComboResponse, GrupoTallaResponse, ProductoPedidoResumenResponse } from '../../types/cargaTalles';

export interface BorradorCombo {
  ancho: string;
  largo: string;
  observacion: string;
}

interface FilaAlumnoProps {
  token: string;
  alumno: AlumnoResponse;
  productosConTalle: ProductoPedidoResumenResponse[];
  tablasTalle: GrupoTallaResponse[];
  soloLecturaGlobal: boolean;
  onCambio: () => void;
}

function borradorDeCombo(combo: ComboResponse): BorradorCombo {
  return {
    ancho: combo.anchoCm != null ? String(combo.anchoCm) : '',
    largo: combo.largoCm != null ? String(combo.largoCm) : '',
    observacion: combo.observacionPersonalizado ?? '',
  };
}

/** Mezcla el borrador anterior con los combos actuales del servidor: conserva lo que el
 *  usuario ya venía tipeando (por id de combo) y solo agrega/saca entradas cuando cambia el
 *  conjunto de combos — así un refetch disparado por OTRA fila (u otra acción en esta misma
 *  fila) no pisa texto en curso. */
function sincronizarBorrador(previo: Record<number, BorradorCombo>, combos: ComboResponse[]): Record<number, BorradorCombo> {
  const siguiente: Record<number, BorradorCombo> = {};
  for (const combo of combos) {
    siguiente[combo.id] = previo[combo.id] ?? borradorDeCombo(combo);
  }
  return siguiente;
}

/**
 * Una fila de alumno completa — todas sus unidades, en todos los productos con talle, con un
 * solo botón de guardar/editar para toda la fila (no uno por producto/unidad, ver el pedido
 * del usuario). Mientras `editando` es true, los inputs de cada unidad están habilitados y el
 * talle es una vista previa calculada en el cliente; al guardar, se persiste todo lo que se
 * completó (ancho+largo) y la fila queda bloqueada mostrando el talle fijo que confirmó el
 * backend, hasta tocar editar de nuevo.
 *
 * La composición (cuántas unidades de cada producto) se fija al agregar el alumno y ya no se
 * ajusta acá — si se cargó mal, se borra el alumno entero (tacho de esta fila) y se lo vuelve a
 * agregar con la composición correcta.
 */
export default function FilaAlumno({ token, alumno, productosConTalle, tablasTalle, soloLecturaGlobal, onCambio }: FilaAlumnoProps) {
  const [editando, setEditando] = useState(() => alumno.combos.length === 0 || alumno.combos.some((c) => c.anchoCm == null));
  const [borrador, setBorrador] = useState<Record<number, BorradorCombo>>(() => sincronizarBorrador({}, alumno.combos));
  const [guardando, setGuardando] = useState(false);
  const [eliminandoAlumno, setEliminandoAlumno] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    setBorrador((previo) => sincronizarBorrador(previo, alumno.combos));
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [alumno.combos]);

  const tablaPorGrupo = new Map(tablasTalle.map((g) => [g.nombreGrupo, g]));

  function actualizarBorrador(idCombo: number, cambio: Partial<BorradorCombo>) {
    setBorrador((prev) => ({ ...prev, [idCombo]: { ...(prev[idCombo] ?? { ancho: '', largo: '', observacion: '' }), ...cambio } }));
  }

  function iniciarEdicion() {
    setError(null);
    setEditando(true);
  }

  async function handleEliminarAlumno() {
    setEliminandoAlumno(true);
    setError(null);
    try {
      await eliminarAlumno(token, alumno.id);
      onCambio();
    } catch (err) {
      setError(extraerMensajeError(err, 'No se pudo borrar el alumno.'));
      setEliminandoAlumno(false);
    }
  }

  async function guardarFila() {
    setError(null);

    // Para confirmar la fila hacen falta las medidas de TODAS las unidades del alumno — no se
    // puede dejar una sin medir y guardar igual (eso era lo que pasaba antes: con todo vacío,
    // "Guardar" bloqueaba la fila sin haber cargado nada).
    const combosAGuardar: { combo: ComboResponse; ancho: number; largo: number; observacion: string | null }[] = [];
    for (const combo of alumno.combos) {
      const d = borrador[combo.id];
      const anchoTexto = (d?.ancho ?? '').trim();
      const largoTexto = (d?.largo ?? '').trim();
      if (!anchoTexto || !largoTexto) {
        setError('Para confirmar tenés que completar las medidas pedidas.');
        return;
      }
      const ancho = Number(anchoTexto);
      const largo = Number(largoTexto);
      if (Number.isNaN(ancho) || ancho <= 0 || Number.isNaN(largo) || largo <= 0) {
        setError(`Ancho y largo de "${combo.nombreTipoPrenda}" tienen que ser mayores a cero.`);
        return;
      }
      const observacion = (d?.observacion ?? '').trim() || null;
      const sinCambios = ancho === combo.anchoCm && largo === combo.largoCm && observacion === combo.observacionPersonalizado;
      if (sinCambios) continue;
      combosAGuardar.push({ combo, ancho, largo, observacion });
    }

    if (combosAGuardar.length === 0) {
      setEditando(false);
      return;
    }

    setGuardando(true);
    try {
      for (const { combo, ancho, largo, observacion } of combosAGuardar) {
        await actualizarCombo(token, combo.id, { anchoCm: ancho, largoCm: largo, observacionPersonalizado: observacion });
      }
      setEditando(false);
      onCambio();
    } catch (err) {
      setError(extraerMensajeError(err, 'No se pudo guardar la fila.'));
    } finally {
      setGuardando(false);
    }
  }

  return (
    <>
      {productosConTalle.map((p) => (
        <td key={p.idProducto} className="px-3 py-3">
          <ComboCelda
            tabla={p.nombreGrupoTalle ? tablaPorGrupo.get(p.nombreGrupoTalle) : undefined}
            combos={alumno.combos.filter((c) => c.idProducto === p.idProducto)}
            borrador={borrador}
            editando={editando}
            soloLecturaGlobal={soloLecturaGlobal}
            onCambiarAncho={(idCombo, valor) => actualizarBorrador(idCombo, { ancho: valor })}
            onCambiarLargo={(idCombo, valor) => actualizarBorrador(idCombo, { largo: valor })}
            onCambiarObservacion={(idCombo, valor) => actualizarBorrador(idCombo, { observacion: valor })}
          />
        </td>
      ))}
      <td className="px-3 py-3">
        {!soloLecturaGlobal && (
          <div className="flex flex-col items-start gap-1">
            <div className="inline-flex items-center gap-1">
              {editando ? (
                <button
                  type="button"
                  onClick={guardarFila}
                  disabled={guardando || eliminandoAlumno}
                  aria-label="Guardar fila"
                  className="rounded-md p-1.5 text-wc-green transition hover:bg-wc-green/10 disabled:cursor-wait disabled:opacity-60"
                >
                  <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.2" strokeLinecap="round" strokeLinejoin="round">
                    <path d="M4 12.5 9.5 18 20 6" />
                  </svg>
                </button>
              ) : (
                <button
                  type="button"
                  onClick={iniciarEdicion}
                  disabled={eliminandoAlumno}
                  aria-label="Editar fila"
                  className="rounded-md p-1.5 text-wc-text-muted transition hover:bg-wc-bg hover:text-wc-text disabled:cursor-not-allowed disabled:opacity-60"
                >
                  <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round">
                    <path d="M16.5 3.5a2.1 2.1 0 0 1 3 3L7 19 3 20l1-4Z" />
                  </svg>
                </button>
              )}
              <button
                type="button"
                onClick={handleEliminarAlumno}
                disabled={eliminandoAlumno || guardando}
                aria-label="Eliminar alumno"
                className="rounded-md p-1.5 text-wc-text-muted transition hover:bg-red-50 hover:text-red-600 disabled:cursor-wait disabled:opacity-60"
              >
                <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round">
                  <path d="M4 7h16" />
                  <path d="M9 7V4.5A1.5 1.5 0 0 1 10.5 3h3A1.5 1.5 0 0 1 15 4.5V7" />
                  <path d="M6 7l1 13.5A1.5 1.5 0 0 0 8.5 22h7a1.5 1.5 0 0 0 1.5-1.5L18 7" />
                </svg>
              </button>
            </div>
            {error && <p className="max-w-[10rem] text-xs font-medium text-red-600">{error}</p>}
          </div>
        )}
      </td>
    </>
  );
}
