import type { EstadoPedido } from './pedido';

/** Una importación real desde Kommo (GET /api/kommo/sincronizaciones/ultima). */
export interface SincronizacionKommo {
  id: number;
  /** Fecha y hora local del servidor, sin zona (ej. "2026-10-21T10:30:12.5"). */
  fechaHora: string;
  idUsuario: number;
  nombreUsuario: string;
  desde: string | null;
  hasta: string | null;
  idsLeads: number[];
  importados: number;
  salteados: number;
  sinFicha: number;
}

export interface CantidadesPrendaKommo {
  buzos: number | null;
  camperas: number | null;
  remeras: number | null;
  chombas: number | null;
  bandera: boolean;
}

export interface LeadKommoImportacion {
  idLead: number;
  colegio: string | null;
  vendedorKommo: string | null;
  vendedorAsignado: string | null;
  estado: EstadoPedido | null;
  cantidades: CantidadesPrendaKommo;
  fichaKommo: string | null;
  codigoInternoPropuesto: string | null;
  /** Pedido creado; null en la simulación o si se salteó. */
  idPedido: number | null;
  /** null = se importa. */
  motivoSalteo: string | null;
  /** Se salteó porque ya se había importado antes: no hay nada que corregir. */
  yaImportado: boolean;
}

/** Respuesta de POST /api/pedidos/importar-kommo (simulación o importación real). */
export interface ImportacionKommoResponse {
  simulacion: boolean;
  leadsTraidos: number;
  sinFicha: number;
  /** true = el rango trae más leads que maximoLeads: la importación real se rechaza. */
  hayMasLeads: boolean;
  maximoLeads: number;
  importados: number;
  /** Incluye los ya importados. */
  salteados: number;
  yaImportados: number;
  leads: LeadKommoImportacion[];
}
