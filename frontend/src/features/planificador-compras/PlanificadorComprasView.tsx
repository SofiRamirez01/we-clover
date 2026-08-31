import { useState } from 'react';
import AppHeader from '../../components/AppHeader';
import ModalConfirmacion from '../../components/ModalConfirmacion';
import NuevaPlanificacionView from './NuevaPlanificacionView';
import PlanificacionDetalleView from './PlanificacionDetalleView';
import PlanificacionesListView from './PlanificacionesListView';
import { eliminarPlanificacion } from '../../services/planificacionCompraService';
import type { PlanificacionCompraResponse } from '../../types/planificacionCompra';

type Vista = 'listado' | 'nueva' | 'detalle';

export default function PlanificadorComprasView() {
  const [vista, setVista] = useState<Vista>('listado');
  const [planificacionDetalle, setPlanificacionDetalle] = useState<PlanificacionCompraResponse | null>(null);
  const [mensajeExito, setMensajeExito] = useState<string | null>(null);

  // Id del borrador que se está editando en "nueva" — null para una planificación realmente
  // nueva (sin guardar todavía). Vive acá (no solo dentro de NuevaPlanificacionView) porque la
  // flecha "Volver" del AppHeader, que se renderiza en este componente, necesita saber si hay
  // algo guardado para ofrecer el cartel de guardar/eliminar borrador.
  const [idBorradorActual, setIdBorradorActual] = useState<number | null>(null);
  const [mostrarConfirmVolver, setMostrarConfirmVolver] = useState(false);

  function irANueva() {
    setMensajeExito(null);
    setIdBorradorActual(null);
    setVista('nueva');
  }

  function continuarBorrador(planificacion: PlanificacionCompraResponse) {
    setMensajeExito(null);
    setIdBorradorActual(planificacion.id);
    setVista('nueva');
  }

  function verDetalle(planificacion: PlanificacionCompraResponse) {
    setPlanificacionDetalle(planificacion);
    setVista('detalle');
  }

  function handleConfirmada(planificacion: PlanificacionCompraResponse) {
    setIdBorradorActual(null);
    setMensajeExito(`Planificación "${planificacion.nombre}" creada correctamente.`);
    setVista('listado');
  }

  function volverAlListado() {
    setPlanificacionDetalle(null);
    setVista('listado');
  }

  /** Usado solo por la flecha "Volver" del AppHeader: si se está en "nueva" con un borrador ya
   *  guardado, ofrece guardarlo (dejarlo como está, ya se persiste solo) o eliminarlo antes de
   *  salir. Si todavía no se guardó nada (idBorradorActual null) o se está en "detalle", vuelve
   *  directo, sin preguntar. */
  function handleVolverDesdeHeader() {
    if (vista === 'nueva' && idBorradorActual != null) {
      setMostrarConfirmVolver(true);
      return;
    }
    volverAlListado();
  }

  async function eliminarYVolver() {
    if (idBorradorActual != null) {
      try {
        await eliminarPlanificacion(idBorradorActual);
      } catch {
        // Si falla el borrado igual salimos: el usuario ya decidió eliminar.
      }
    }
    setIdBorradorActual(null);
    setMostrarConfirmVolver(false);
    volverAlListado();
  }

  return (
    <div className="tw-scope px-8 pt-7 pb-12">
      <AppHeader title="Planificador de Compras" onBack={vista !== 'listado' ? handleVolverDesdeHeader : undefined} />
      {vista === 'listado' && (
        <PlanificacionesListView
          onNueva={irANueva}
          onVerDetalle={verDetalle}
          onContinuarBorrador={continuarBorrador}
          mensajeExito={mensajeExito}
        />
      )}
      {vista === 'nueva' && (
        <NuevaPlanificacionView
          idBorrador={idBorradorActual}
          onIdBorradorCreado={setIdBorradorActual}
          onBorradorEliminado={() => setIdBorradorActual(null)}
          onConfirmada={handleConfirmada}
          onCancelar={volverAlListado}
        />
      )}
      {vista === 'detalle' && planificacionDetalle && (
        <PlanificacionDetalleView planificacion={planificacionDetalle} onVolver={volverAlListado} />
      )}

      {mostrarConfirmVolver && (
        <ModalConfirmacion
          titulo="Planificación en curso"
          mensaje="Tenés una planificación sin confirmar. ¿Qué querés hacer antes de salir?"
          onCerrar={() => setMostrarConfirmVolver(false)}
          acciones={[
            {
              label: 'Guardar borrador y salir',
              variante: 'primaria',
              onClick: () => {
                setMostrarConfirmVolver(false);
                volverAlListado();
              },
            },
            {
              label: 'Eliminar borrador y salir',
              variante: 'peligro',
              onClick: eliminarYVolver,
            },
            {
              label: 'Seguir editando',
              variante: 'secundaria',
              onClick: () => setMostrarConfirmVolver(false),
            },
          ]}
        />
      )}
    </div>
  );
}
