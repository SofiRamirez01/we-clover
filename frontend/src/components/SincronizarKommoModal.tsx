import { useState } from 'react';
import './HistorialPedidoModal.css';
import './SincronizarKommoModal.css';
import { importarDesdeKommo } from '../services/kommoService';
import { extraerMensajeError } from '../utils/errores';
import type { CantidadesPrendaKommo, ImportacionKommoResponse, LeadKommoImportacion, SincronizacionKommo } from '../types/kommo';

interface SincronizarKommoModalProps {
  /** Última sincronización real, para proponer el rango por defecto (null = nunca). */
  ultimaSincronizacion: SincronizacionKommo | null;
  onClose: () => void;
  /** Después de una importación real: refrescar el listado y la última sincronización. */
  onImportado: () => void;
}

type Paso = 'rango' | 'previsualizando' | 'previa' | 'importando' | 'listo' | 'error';

const DIAS_POR_DEFECTO = 7;

/** YYYY-MM-DD en hora local (toISOString daría el día UTC, que de noche ya es mañana). */
function fechaLocal(fecha: Date): string {
  const mes = String(fecha.getMonth() + 1).padStart(2, '0');
  const dia = String(fecha.getDate()).padStart(2, '0');
  return `${fecha.getFullYear()}-${mes}-${dia}`;
}

function desdePorDefecto(ultima: SincronizacionKommo | null): string {
  if (ultima) return ultima.fechaHora.slice(0, 10);
  const hace = new Date();
  hace.setDate(hace.getDate() - DIAS_POR_DEFECTO);
  return fechaLocal(hace);
}

function describirCantidades(c: CantidadesPrendaKommo): string {
  const partes = [
    c.buzos ? `${c.buzos} buzos` : null,
    c.camperas ? `${c.camperas} camperas` : null,
    c.remeras ? `${c.remeras} remeras` : null,
    c.chombas ? `${c.chombas} chombas` : null,
    c.bandera ? 'bandera' : null,
  ].filter(Boolean);
  return partes.length > 0 ? partes.join(' · ') : '—';
}

function TablaAImportar({ leads, conPedido }: { leads: LeadKommoImportacion[]; conPedido: boolean }) {
  return (
    <table className="historial-modal-tabla">
      <thead>
        <tr>
          <th>Nº Ficha</th>
          <th>Colegio</th>
          <th>Vendedor</th>
          <th>Prendas</th>
          {conPedido && <th>Pedido</th>}
        </tr>
      </thead>
      <tbody>
        {leads.map((l) => (
          <tr key={l.idLead}>
            <td>{l.codigoInternoPropuesto ?? '—'}</td>
            <td>{l.colegio ?? '—'}</td>
            <td>{l.vendedorAsignado ?? l.vendedorKommo ?? '—'}</td>
            <td>{describirCantidades(l.cantidades)}</td>
            {conPedido && <td>#{l.idPedido}</td>}
          </tr>
        ))}
      </tbody>
    </table>
  );
}

function TablaSalteados({ leads }: { leads: LeadKommoImportacion[] }) {
  return (
    <table className="historial-modal-tabla">
      <thead>
        <tr>
          <th>Lead</th>
          <th>Ficha Kommo</th>
          <th>Colegio</th>
          <th>Motivo</th>
        </tr>
      </thead>
      <tbody>
        {leads.map((l) => (
          <tr key={l.idLead}>
            <td>#{l.idLead}</td>
            <td>{l.fichaKommo ?? '—'}</td>
            <td>{l.colegio ?? '—'}</td>
            <td>{l.motivoSalteo}</td>
          </tr>
        ))}
      </tbody>
    </table>
  );
}

/** Los leads de una respuesta, separados como se muestran en el modal. */
function agrupar(resultado: ImportacionKommoResponse) {
  return {
    aImportar: resultado.leads.filter((l) => l.motivoSalteo == null),
    aCorregir: resultado.leads.filter((l) => l.motivoSalteo != null && !l.yaImportado),
    yaImportados: resultado.leads.filter((l) => l.yaImportado),
  };
}

function Resultado({ resultado }: { resultado: ImportacionKommoResponse }) {
  const { aImportar, aCorregir, yaImportados } = agrupar(resultado);
  const verbo = resultado.simulacion ? 'Se importarían' : 'Se importaron';
  if (resultado.leadsTraidos === 0) {
    return <p className="kommo-resumen">No hay leads actualizados en Kommo dentro de ese rango.</p>;
  }
  return (
    <>
      <p className="kommo-resumen">
        {verbo} <strong>{aImportar.length}</strong> de {resultado.leadsTraidos} leads ·{' '}
        {aCorregir.length} salteados · {yaImportados.length} ya importados · {resultado.sinFicha} sin Ficha
      </p>

      {aImportar.length > 0 && (
        <>
          <h4>{resultado.simulacion ? 'A importar' : 'Importados'} ({aImportar.length})</h4>
          <TablaAImportar leads={aImportar} conPedido={!resultado.simulacion} />
        </>
      )}

      {aCorregir.length > 0 && (
        <>
          <h4>Salteados ({aCorregir.length})</h4>
          <TablaSalteados leads={aCorregir} />
        </>
      )}

      {yaImportados.length > 0 && (
        <details className="kommo-ya-importados">
          <summary>Ya importados ({yaImportados.length})</summary>
          <TablaSalteados leads={yaImportados} />
        </details>
      )}

      {resultado.sinFicha > 0 && (
        <p className="kommo-nota">
          {resultado.sinFicha} {resultado.sinFicha === 1 ? 'lead no tiene' : 'leads no tienen'} Ficha cargada en
          Kommo y no se {resultado.simulacion ? 'importarían' : 'importaron'}.
        </p>
      )}
    </>
  );
}

/**
 * Sincronización con Kommo en dos pasos, sobre el mismo rango de fechas (última actualización del
 * lead): previsualizar (simular=true, no guarda nada) y confirmar (simular=false). Las dos usan
 * las mismas reglas en el backend, así que lo que se previsualiza es lo que se importa, salvo que
 * alguien cambie algo en Kommo entre los dos pasos.
 */
export default function SincronizarKommoModal({ ultimaSincronizacion, onClose, onImportado }: SincronizarKommoModalProps) {
  const [desde, setDesde] = useState(() => desdePorDefecto(ultimaSincronizacion));
  const [hasta, setHasta] = useState(() => fechaLocal(new Date()));
  const [paso, setPaso] = useState<Paso>('rango');
  const [previa, setPrevia] = useState<ImportacionKommoResponse | null>(null);
  const [final, setFinal] = useState<ImportacionKommoResponse | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [errorRango, setErrorRango] = useState<string | null>(null);

  async function previsualizar() {
    if (!desde || !hasta) {
      setErrorRango('Elegí las dos fechas del rango.');
      return;
    }
    if (hasta < desde) {
      setErrorRango('La fecha "hasta" no puede ser anterior a "desde".');
      return;
    }
    setErrorRango(null);
    setPaso('previsualizando');
    try {
      setPrevia(await importarDesdeKommo({ desde, hasta, simular: true }));
      setPaso('previa');
    } catch (err) {
      setError(extraerMensajeError(err, 'No se pudo consultar Kommo. Intentá nuevamente.'));
      setPaso('error');
    }
  }

  async function confirmar() {
    setPaso('importando');
    try {
      setFinal(await importarDesdeKommo({ desde, hasta, simular: false }));
      setPaso('listo');
      onImportado();
    } catch (err) {
      setError(extraerMensajeError(err, 'No se pudo completar la importación. Intentá nuevamente.'));
      setPaso('error');
    }
  }

  const puedeConfirmar = previa != null && !previa.hayMasLeads && agrupar(previa).aImportar.length > 0;

  return (
    <div className="historial-modal-backdrop" onClick={onClose}>
      <div
        className="historial-modal-dialog kommo-modal"
        role="dialog"
        aria-modal="true"
        aria-labelledby="kommo-modal-titulo"
        onClick={(e) => e.stopPropagation()}
      >
        <div className="historial-modal-header">
          <h3 id="kommo-modal-titulo">Sincronizar con Kommo</h3>
          <button type="button" className="historial-modal-cerrar" onClick={onClose} aria-label="Cerrar">
            ×
          </button>
        </div>

        <div className="historial-modal-body">
          {paso === 'rango' && (
            <>
              <p>
                Trae los leads del pipeline Administración que se actualizaron en Kommo dentro del rango. Primero se
                previsualiza qué se importaría; no se guarda nada hasta confirmar.
              </p>
              <div className="kommo-rango">
                <label>
                  Desde
                  <input type="date" value={desde} onChange={(e) => setDesde(e.target.value)} />
                </label>
                <label>
                  Hasta
                  <input type="date" value={hasta} onChange={(e) => setHasta(e.target.value)} />
                </label>
              </div>
              {errorRango && <div className="alerta alerta--error">{errorRango}</div>}
              <p className="kommo-nota">
                Si un lead se salteó en una sincronización anterior y lo corregiste acá (por ejemplo, vinculando su
                vendedor), no vuelve a entrar en el rango si no cambió en Kommo: ampliá el rango para que lo incluya.
              </p>
              <div className="kommo-acciones">
                <button type="button" className="btn-guardar" onClick={previsualizar}>
                  Previsualizar
                </button>
              </div>
            </>
          )}

          {paso === 'previsualizando' && <p className="historial-modal-vacio">Consultando Kommo…</p>}
          {paso === 'importando' && <p className="historial-modal-vacio">Importando pedidos…</p>}

          {paso === 'previa' && previa && (
            <>
              {previa.hayMasLeads && (
                <div className="alerta alerta--error" role="alert">
                  El rango tiene más de {previa.maximoLeads} leads, que es lo máximo que se puede importar de una vez.
                  Achicá el rango de fechas para poder importar.
                </div>
              )}
              <Resultado resultado={previa} />
              <div className="kommo-acciones">
                <button type="button" className="btn-secondary" onClick={() => setPaso('rango')}>
                  Cambiar rango
                </button>
                <button type="button" className="btn-guardar" onClick={confirmar} disabled={!puedeConfirmar}>
                  Confirmar importación
                </button>
              </div>
            </>
          )}

          {paso === 'listo' && final && (
            <>
              <div className="alerta alerta--exito" role="status">
                Importación terminada: {agrupar(final).aImportar.length} pedidos nuevos.
              </div>
              <Resultado resultado={final} />
              <div className="kommo-acciones">
                <button type="button" className="btn-guardar" onClick={onClose}>
                  Cerrar
                </button>
              </div>
            </>
          )}

          {paso === 'error' && (
            <>
              <p className="historial-modal-vacio">{error}</p>
              <button type="button" className="btn-secondary" onClick={() => setPaso(previa ? 'previa' : 'rango')}>
                Volver
              </button>
            </>
          )}
        </div>
      </div>
    </div>
  );
}
