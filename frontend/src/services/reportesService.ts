import api from './api';
import type { ReporteFiltros, ReporteProduccionResponse, ReporteVentasResponse } from '../types/reportes';

/** Mismos query params para todos los reportes: desde, hasta, tipoPrenda (id; omitido = todas). */
function aParams(filtros: ReporteFiltros) {
  return {
    desde: filtros.desde,
    hasta: filtros.hasta,
    tipoPrenda: filtros.idTipoPrenda ?? undefined,
  };
}

export async function obtenerReporteVentas(filtros: ReporteFiltros, signal?: AbortSignal): Promise<ReporteVentasResponse> {
  const { data } = await api.get<ReporteVentasResponse>('/reportes/ventas', { params: aParams(filtros), signal });
  return data;
}

export async function obtenerReporteProduccion(filtros: ReporteFiltros, signal?: AbortSignal): Promise<ReporteProduccionResponse> {
  const { data } = await api.get<ReporteProduccionResponse>('/reportes/produccion', { params: aParams(filtros), signal });
  return data;
}
