import api from './api';
import type { ProduccionFiltros, ProduccionPedidoResponse } from '../types/produccion';

export async function listarProduccionPedidos(filtros: ProduccionFiltros): Promise<ProduccionPedidoResponse[]> {
  const { data } = await api.get<ProduccionPedidoResponse[]>('/produccion/pedidos', {
    params: {
      estado: filtros.estado && filtros.estado.length > 0 ? filtros.estado : undefined,
      etapaPendiente: filtros.etapaPendiente || undefined,
      pagoMin: filtros.pagoMin,
      pagoMax: filtros.pagoMax,
    },
    // Sin esto, axios serializa un array como estado[]=A&estado[]=B — Spring espera la clave
    // repetida sin corchetes (estado=A&estado=B) para bindear un List<EstadoPedido>.
    paramsSerializer: { indexes: null },
  });
  return data;
}
