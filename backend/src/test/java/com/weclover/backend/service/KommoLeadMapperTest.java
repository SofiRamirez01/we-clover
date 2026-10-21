package com.weclover.backend.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.weclover.backend.dto.kommo.KommoContact;
import com.weclover.backend.dto.kommo.KommoLead;
import com.weclover.backend.dto.kommo.KommoListado;
import com.weclover.backend.entity.EstadoPedido;

class KommoLeadMapperTest {

    private final KommoLeadMapper mapper = new KommoLeadMapper(KommoDePrueba.properties());

    /** pedidoCompleto.json es una respuesta de listado (_embedded.leads, 3 leads). */
    private static KommoLead andre() {
        return KommoDePrueba.leer("pedidoCompleto.json", KommoListado.class).leads().stream()
            .filter(lead -> lead.id() == 28237082L)
            .findFirst()
            .orElseThrow();
    }

    @Test
    void lead_completo_de_andre_con_su_contacto_produce_todos_los_campos() {
        KommoContact contacto = KommoDePrueba.leer("contactoAndre.json", KommoContact.class);

        FilaExcelPedido fila = mapper.mapear(andre(), contacto);

        assertThat(fila).isEqualTo(new FilaExcelPedido(
            28237082,
            "n5",
            "pinamar",
            "bsas",
            "Primaria",
            "Andre",
            "+5492254458408",
            null,
            "Pia",
            "SENADO",
            "2027",
            "241-27",
            "97000",
            "2328000",
            "0",
            "24",
            "0",
            "0",
            "Si",
            "Si",
            "No",
            "No",
            "30.06.2026",
            "30.12.2026",
            "seña + 5",
            "Adulto",
            List.of(),
            778684L,
            28237082L));
    }

    @Test
    void completa_el_enum_id_del_vendedor_y_el_id_del_lead() {
        FilaExcelPedido fila = mapper.mapear(andre(), null);

        assertThat(fila.vendedorKommoEnumId()).isEqualTo(778684L);
        assertThat(fila.kommoLeadId()).isEqualTo(28237082L);
    }

    @Test
    void el_contacto_principal_es_el_marcado_is_main() {
        assertThat(KommoLeadMapper.idContactoPrincipal(andre())).isEqualTo(32426660L);
    }

    @Test
    void el_estado_se_resuelve_por_status_id() {
        assertThat(mapper.estadoImportable(andre())).isEqualTo(EstadoPedido.SENADO);
    }

    @Test
    void sin_contacto_quedan_en_null_los_datos_del_contacto() {
        FilaExcelPedido fila = mapper.mapear(andre(), null);

        assertThat(fila.contactoPrincipal()).isNull();
        assertThat(fila.telefonoContacto()).isNull();
        assertThat(fila.emailContacto()).isNull();
        assertThat(fila.colegio()).isEqualTo("n5");
    }

    /** leadIncompleto.json es un objeto suelto, como lo devuelve GET /api/v4/leads/{id}. */
    @Test
    void lead_incompleto_de_mayra_se_descarta_por_no_tener_ficha() {
        KommoLead mayra = KommoDePrueba.leer("leadIncompleto.json", KommoLead.class);

        assertThat(mayra.name()).isEqualTo("MAYRA");
        assertThat(mapper.tieneFicha(mayra)).isFalse();
        // Además está en otro pipeline (Pia): en el flujo real ni siquiera vendría.
        assertThat(mapper.estadoImportable(mayra)).isNull();
    }

    @Test
    void los_tres_leads_del_listado_tienen_ficha() {
        List<KommoLead> leads = KommoDePrueba.leer("pedidoCompleto.json", KommoListado.class).leads();

        assertThat(leads).hasSize(3).allMatch(mapper::tieneFicha);
    }
}
