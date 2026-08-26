import { useState } from 'react';
import AppHeader from '../../components/AppHeader';
import CargaPatronCorteForm from './CargaPatronCorteForm';
import MolderiasListView from './MolderiasListView';

type Vista = 'listado' | 'nueva';

export default function PatronesCorteView() {
  const [vista, setVista] = useState<Vista>('listado');
  const [mensajeExito, setMensajeExito] = useState<string | null>(null);

  function irANueva() {
    setMensajeExito(null);
    setVista('nueva');
  }

  function volverAlListado() {
    setMensajeExito('Patrón de corte cargado correctamente.');
    setVista('listado');
  }

  function cancelarNueva() {
    setMensajeExito(null);
    setVista('listado');
  }

  return (
    <div className="tw-scope px-8 pt-7 pb-12">
      <AppHeader
        title="Molderías · Patrones de Corte"
        onBack={vista === 'nueva' ? cancelarNueva : undefined}
      />
      {vista === 'listado' ? (
        <MolderiasListView onNueva={irANueva} mensajeExito={mensajeExito} />
      ) : (
        <CargaPatronCorteForm onCreado={volverAlListado} />
      )}
    </div>
  );
}
