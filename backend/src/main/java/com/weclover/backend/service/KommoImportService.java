package com.weclover.backend.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.weclover.backend.dto.kommo.KommoContact;
import com.weclover.backend.dto.kommo.KommoLead;
import com.weclover.backend.dto.kommo.KommoPipeline;
import com.weclover.backend.dto.kommo.SincronizacionKommoResponse;
import com.weclover.backend.dto.pedido.ImportacionKommoResponse;
import com.weclover.backend.dto.pedido.LeadKommoImportacion;
import com.weclover.backend.entity.EstadoPedido;
import com.weclover.backend.entity.Pedido;
import com.weclover.backend.entity.SincronizacionKommo;
import com.weclover.backend.exception.PedidoYaImportadoException;
import com.weclover.backend.exception.ServicioNoDisponibleException;
import com.weclover.backend.exception.SolicitudInvalidaException;
import com.weclover.backend.repository.SincronizacionKommoRepository;
import com.weclover.backend.repository.UsuarioRepository;

import lombok.RequiredArgsConstructor;

/**
 * Importación de pedidos desde la API de Kommo: segunda fuente, además del Excel, que produce el
 * mismo {@link FilaExcelPedido} (ver {@link KommoLeadMapper}) y lo valida y guarda con
 * {@link PedidoImportRowService}. Simular y guardar recorren los mismos pasos; solo cambia si al
 * final se llama a validarFila (simular) o a importarFila (guardar, cada lead en su propia
 * transacción), y las dos aplican las mismas reglas. Cada importación real queda registrada en
 * SincronizacionKommo.
 *
 * Topes: por ids, 10 leads por defecto y 50 como máximo; por fechas se trae el rango completo
 * hasta 250 leads (una página de Kommo). Si el pedido supera el tope, la simulación lo avisa
 * (hayMasLeads) y la importación real se rechaza sin guardar nada: importar solo una parte de un
 * rango dejaría leads afuera sin que nadie lo note (los siguientes rangos empiezan después).
 */
@Service
@RequiredArgsConstructor
public class KommoImportService {

    static final int LIMITE_POR_DEFECTO = 10;
    /** Tope duro por ids: el endpoint de simulación no tiene autenticación todavía (ver
     *  doc/pantallas-pendientes.md). */
    static final int LIMITE_MAXIMO = 50;
    /** Tope por rango de fechas: una página completa de Kommo. */
    static final int LIMITE_MAXIMO_POR_RANGO = KommoApiClient.LIMITE_PAGINA_KOMMO;

    private final KommoApiClient kommoApiClient;
    private final KommoLeadMapper kommoLeadMapper;
    private final PedidoImportRowService pedidoImportRowService;
    private final SincronizacionKommoRepository sincronizacionKommoRepository;
    private final UsuarioRepository usuarioRepository;
    private final AutorizacionService autorizacionService;

    /**
     * @param idUsuarioActor obligatorio y administrativo para importar de verdad (queda en el
     *                       registro de la sincronización); la simulación no lo exige
     */
    public ImportacionKommoResponse importar(
            LocalDate desde, LocalDate hasta, List<Long> ids, Integer limite, boolean simular, Long idUsuarioActor) {
        int limiteEfectivo = validarParametros(desde, hasta, ids, limite);
        if (!simular) {
            autorizacionService.verificarRolAdministrativo(idUsuarioActor);
        }
        kommoApiClient.verificarConfiguracion();

        KommoApiClient.LeadsKommo traidos = kommoApiClient.listarLeads(desde, hasta, ids, limiteEfectivo);
        if (!simular && traidos.hayMas()) {
            throw new SolicitudInvalidaException(ids != null && !ids.isEmpty()
                ? "Se pidieron más leads (" + ids.size() + ") que el máximo que se puede importar de una vez ("
                    + limiteEfectivo + "): no se importó ninguno"
                : "El rango tiene más leads que el máximo que se puede importar de una vez (" + limiteEfectivo
                    + "): no se importó ninguno. Achicá el rango de fechas y volvé a intentar.");
        }

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
        int yaImportados = (int) leads.stream().filter(LeadKommoImportacion::yaImportado).count();
        int importados = leads.size() - salteados;
        if (!simular) {
            registrarSincronizacion(desde, hasta, ids, importados, salteados, sinFicha, idUsuarioActor);
        }
        return new ImportacionKommoResponse(simular, traidos.leads().size(), sinFicha, traidos.hayMas(),
            limiteEfectivo, importados, salteados, yaImportados, leads);
    }

    /** Última importación real, o vacío si nunca se sincronizó. Solo para el rol administrativo. */
    @Transactional(readOnly = true)
    public Optional<SincronizacionKommoResponse> ultimaSincronizacion(Long idUsuarioActor) {
        autorizacionService.verificarRolAdministrativo(idUsuarioActor);
        return sincronizacionKommoRepository.findFirstByOrderByFechaHoraDescIdDesc()
            .map(s -> new SincronizacionKommoResponse(s.getId(), s.getFechaHora(), s.getUsuario().getId(),
                s.getUsuario().getNombre(), s.getDesde(), s.getHasta(), List.copyOf(s.getIdsLeads()),
                s.getImportados(), s.getSalteados(), s.getSinFicha()));
    }

    private void registrarSincronizacion(LocalDate desde, LocalDate hasta, List<Long> ids,
            int importados, int salteados, int sinFicha, Long idUsuarioActor) {
        sincronizacionKommoRepository.save(SincronizacionKommo.builder()
            .fechaHora(LocalDateTime.now())
            .usuario(usuarioRepository.getReferenceById(idUsuarioActor))
            .desde(desde)
            .hasta(hasta)
            .idsLeads(ids == null ? new ArrayList<>() : new ArrayList<>(ids))
            .importados(importados)
            .salteados(salteados)
            .sinFicha(sinFicha)
            .build());
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
        int maximo = porIds ? LIMITE_MAXIMO : LIMITE_MAXIMO_POR_RANGO;
        int limiteEfectivo = limite != null ? limite : (porIds ? LIMITE_POR_DEFECTO : LIMITE_MAXIMO_POR_RANGO);
        if (limiteEfectivo < 1 || limiteEfectivo > maximo) {
            throw new SolicitudInvalidaException("limite tiene que estar entre 1 y " + maximo
                + (porIds ? " al pedir por ids" : " al pedir por fechas"));
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
        boolean yaImportado = false;
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
            } catch (PedidoYaImportadoException e) {
                motivo = e.getMessage();
                yaImportado = true;
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
            motivo,
            yaImportado);
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
