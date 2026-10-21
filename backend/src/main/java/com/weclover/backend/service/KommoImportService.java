package com.weclover.backend.service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.weclover.backend.dto.kommo.KommoContact;
import com.weclover.backend.dto.kommo.KommoLead;
import com.weclover.backend.dto.kommo.KommoPipeline;
import com.weclover.backend.dto.pedido.ImportacionKommoResponse;
import com.weclover.backend.dto.pedido.LeadKommoImportacion;
import com.weclover.backend.entity.EstadoPedido;
import com.weclover.backend.entity.Pedido;
import com.weclover.backend.exception.ServicioNoDisponibleException;
import com.weclover.backend.exception.SolicitudInvalidaException;

import lombok.RequiredArgsConstructor;

/**
 * Importación de pedidos desde la API de Kommo: segunda fuente, además del Excel, que produce el
 * mismo {@link FilaExcelPedido} (ver {@link KommoLeadMapper}) y lo valida y guarda con
 * {@link PedidoImportRowService}. Simular y guardar recorren los mismos pasos; solo cambia si al
 * final se llama a validarFila (simular) o a importarFila (guardar, cada lead en su propia
 * transacción), y las dos aplican las mismas reglas.
 */
@Service
@RequiredArgsConstructor
public class KommoImportService {

    static final int LIMITE_POR_DEFECTO = 10;
    /** Tope duro: el endpoint no tiene autenticación todavía (ver doc/pantallas-pendientes.md). */
    static final int LIMITE_MAXIMO = 50;

    private final KommoApiClient kommoApiClient;
    private final KommoLeadMapper kommoLeadMapper;
    private final PedidoImportRowService pedidoImportRowService;

    public ImportacionKommoResponse importar(
            LocalDate desde, LocalDate hasta, List<Long> ids, Integer limite, boolean simular) {
        int limiteEfectivo = validarParametros(desde, hasta, ids, limite);
        kommoApiClient.verificarConfiguracion();

        KommoApiClient.LeadsKommo traidos = kommoApiClient.listarLeads(desde, hasta, ids, limiteEfectivo);

        List<KommoLead> conFicha = traidos.leads().stream().filter(kommoLeadMapper::tieneFicha).toList();
        int sinFicha = traidos.leads().size() - conFicha.size();

        List<KommoLead> importables = conFicha.stream().filter(lead -> kommoLeadMapper.estadoImportable(lead) != null).toList();
        Map<Long, KommoContact> contactos = kommoApiClient.obtenerContactos(importables.stream()
                .map(KommoLeadMapper::idContactoPrincipal)
                .filter(Objects::nonNull)
                .distinct()
                .toList())
            .stream()
            .collect(Collectors.toMap(KommoContact::id, Function.identity(), (a, b) -> a));
        NombresEstados nombres = importables.size() == conFicha.size() ? NombresEstados.VACIO : nombresEstados();

        List<LeadKommoImportacion> leads = new ArrayList<>();
        for (KommoLead lead : conFicha) {
            leads.add(procesarLead(lead, contactos.get(KommoLeadMapper.idContactoPrincipal(lead)), nombres, simular));
        }
        int salteados = (int) leads.stream().filter(lead -> lead.motivoSalteo() != null).count();
        return new ImportacionKommoResponse(
            simular, traidos.leads().size(), sinFicha, traidos.hayMas(), leads.size() - salteados, salteados, leads);
    }

    private int validarParametros(LocalDate desde, LocalDate hasta, List<Long> ids, Integer limite) {
        boolean porIds = ids != null && !ids.isEmpty();
        if (porIds && (desde != null || hasta != null)) {
            throw new SolicitudInvalidaException("Usar ids o un rango de fechas (desde/hasta), no los dos a la vez");
        }
        if (!porIds && desde == null) {
            throw new SolicitudInvalidaException("Falta desde (o ids): sin filtro se traería todo el pipeline");
        }
        if (desde != null && hasta != null && hasta.isBefore(desde)) {
            throw new SolicitudInvalidaException("hasta (" + hasta + ") es anterior a desde (" + desde + ")");
        }
        if (porIds && ids.stream().anyMatch(Objects::isNull)) {
            throw new SolicitudInvalidaException("Hay un id vacío en ids");
        }
        int limiteEfectivo = limite == null ? LIMITE_POR_DEFECTO : limite;
        if (limiteEfectivo < 1 || limiteEfectivo > LIMITE_MAXIMO) {
            throw new SolicitudInvalidaException("limite tiene que estar entre 1 y " + LIMITE_MAXIMO);
        }
        return limiteEfectivo;
    }

    private LeadKommoImportacion procesarLead(KommoLead lead, KommoContact contacto, NombresEstados nombres, boolean simular) {
        FilaExcelPedido fila = kommoLeadMapper.mapear(lead, contacto);
        EstadoPedido estado = kommoLeadMapper.estadoImportable(lead);
        String vendedorAsignado = null;
        String codigoInterno = codigoPropuesto(fila);
        Long idPedido = null;
        String motivo = null;
        if (estado == null) {
            motivo = "estado no importable: " + nombres.describir(lead.pipelineId(), lead.statusId());
        } else {
            try {
                if (simular) {
                    vendedorAsignado = pedidoImportRowService.validarFila(fila).vendedor().getNombre();
                } else {
                    Pedido pedido = pedidoImportRowService.importarFila(fila);
                    vendedorAsignado = pedido.getCreadoPor().getNombre();
                    codigoInterno = pedido.getCodigoInterno();
                    idPedido = pedido.getId();
                }
            } catch (RuntimeException e) {
                motivo = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
            }
        }
        return new LeadKommoImportacion(
            lead.id(),
            fila.colegio(),
            fila.responsableVendedor(),
            vendedorAsignado,
            estado,
            new LeadKommoImportacion.CantidadesPrenda(
                PedidoImportRowService.parsearEnteroONulo(fila.buzos()),
                PedidoImportRowService.parsearEnteroONulo(fila.camperas()),
                PedidoImportRowService.parsearEnteroONulo(fila.remeras()),
                PedidoImportRowService.parsearEnteroONulo(fila.chombas()),
                PedidoImportRowService.esSi(fila.bandera())),
            fila.fichaKommo(),
            codigoInterno,
            idPedido,
            motivo);
    }

    private String codigoPropuesto(FilaExcelPedido fila) {
        try {
            return PedidoImportRowService.armarCodigoInterno(fila.promo(), fila.fichaKommo());
        } catch (RuntimeException e) {
            return null;
        }
    }

    /**
     * Nombres de pipelines y estados para explicar por qué un lead no es importable. Es solo
     * informativo: si Kommo no los devuelve, se muestran los ids en vez de cortar la importación.
     */
    private NombresEstados nombresEstados() {
        try {
            return new NombresEstados(kommoApiClient.listarPipelines());
        } catch (ServicioNoDisponibleException e) {
            return NombresEstados.VACIO;
        }
    }

    private record NombresEstados(List<KommoPipeline> pipelines) {

        static final NombresEstados VACIO = new NombresEstados(List.of());

        String describir(Long pipelineId, Long statusId) {
            KommoPipeline pipeline = pipelines.stream()
                .filter(p -> Objects.equals(p.id(), pipelineId))
                .findFirst()
                .orElse(null);
            String estado = pipeline == null || pipeline.embebidos() == null || pipeline.embebidos().statuses() == null
                ? null
                : pipeline.embebidos().statuses().stream()
                    .filter(s -> Objects.equals(s.id(), statusId))
                    .map(KommoPipeline.Estado::name)
                    .findFirst()
                    .orElse(null);
            return "pipeline " + (pipeline == null ? pipelineId : "'" + pipeline.name() + "'")
                + ", estado " + (estado == null ? statusId : "'" + estado + "'");
        }
    }
}
