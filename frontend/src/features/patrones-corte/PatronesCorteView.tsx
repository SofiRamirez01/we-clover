import { useState } from 'react';
import AppHeader from '../../components/AppHeader';
import CargaPatronCorteForm from './CargaPatronCorteForm';
import MolderiasListView from './MolderiasListView';
import PatronCorteDetalleView from './PatronCorteDetalleView';

type Vista = 'listado' | 'nueva' | 'detalle';

export default function PatronesCorteView() {
  const [vista, setVista] = useState<Vista>('listado');
  const [mensajeExito, setMensajeExito] = useState<string | null>(null);
  const [patronSeleccionadoId, setPatronSeleccionadoId] = useState<number | null>(null);

  function irANueva() {
    setMensajeExito(null);
    setPatronSeleccionadoId(null);
    setVista('nueva');
  }

  function irADetalle(id: number) {
    setMensajeExito(null);
    setPatronSeleccionadoId(id);
    setVista('detalle');
  }

  function volverAlListado() {
    setMensajeExito('Patrón de corte cargado correctamente.');
    setPatronSeleccionadoId(null);
    setVista('listado');
  }

  function cancelar() {
    setMensajeExito(null);
    setPatronSeleccionadoId(null);
    setVista('listado');
  }

  return (
    <div className="tw-scope px-8 pt-7 pb-12">
      <AppHeader
        title="Molderías · Patrones de Corte"
        onBack={vista !== 'listado' ? cancelar : undefined}
      />
      {vista === 'listado' && <MolderiasListView onNueva={irANueva} onVerDetalle={irADetalle} mensajeExito={mensajeExito} />}
      {vista === 'nueva' && <CargaPatronCorteForm onCreado={volverAlListado} />}
      {vista === 'detalle' && patronSeleccionadoId != null && <PatronCorteDetalleView patronCorteId={patronSeleccionadoId} />}
    </div>
  );
}
