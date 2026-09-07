import { useState } from 'react';
import AppHeader from '../../components/AppHeader';
import PiezasListView from './PiezasListView';
import PiezaForm from './PiezaForm';

type Vista = 'listado' | 'nueva' | 'ver' | 'editar' | 'duplicar';

export default function PiezasView() {
  const [vista, setVista] = useState<Vista>('listado');
  const [piezaSeleccionadaId, setPiezaSeleccionadaId] = useState<number | null>(null);
  const [mensajeExito, setMensajeExito] = useState<string | null>(null);

  function irANueva() {
    setMensajeExito(null);
    setPiezaSeleccionadaId(null);
    setVista('nueva');
  }

  function irAVer(id: number) {
    setMensajeExito(null);
    setPiezaSeleccionadaId(id);
    setVista('ver');
  }

  function irAEditar(id: number) {
    setMensajeExito(null);
    setPiezaSeleccionadaId(id);
    setVista('editar');
  }

  function irADuplicar(id: number) {
    setMensajeExito(null);
    setPiezaSeleccionadaId(id);
    setVista('duplicar');
  }

  function volverAlListado(mensaje: string) {
    setMensajeExito(mensaje);
    setPiezaSeleccionadaId(null);
    setVista('listado');
  }

  function cancelar() {
    setMensajeExito(null);
    setPiezaSeleccionadaId(null);
    setVista('listado');
  }

  return (
    <div className="tw-scope px-8 pt-7 pb-12">
      <AppHeader title="Piezas" onBack={vista !== 'listado' ? cancelar : undefined} />
      {vista === 'listado' && (
        <PiezasListView onNueva={irANueva} onVer={irAVer} onEditar={irAEditar} onDuplicar={irADuplicar} mensajeExito={mensajeExito} />
      )}
      {vista === 'nueva' && <PiezaForm modo="crear" onGuardada={() => volverAlListado('Pieza guardada correctamente.')} />}
      {vista === 'ver' && piezaSeleccionadaId != null && <PiezaForm modo="ver" piezaId={piezaSeleccionadaId} />}
      {vista === 'editar' && piezaSeleccionadaId != null && (
        <PiezaForm modo="editar" piezaId={piezaSeleccionadaId} onGuardada={() => volverAlListado('Cambios guardados correctamente.')} />
      )}
      {vista === 'duplicar' && piezaSeleccionadaId != null && (
        <PiezaForm modo="duplicar" piezaId={piezaSeleccionadaId} onGuardada={() => volverAlListado('Pieza duplicada correctamente.')} />
      )}
    </div>
  );
}
