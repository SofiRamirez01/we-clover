package com.weclover.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.weclover.backend.dto.pedido.NotaPedidoResponse;
import com.weclover.backend.entity.HistorialUbicacionPedido;
import com.weclover.backend.entity.NotaPedido;
import com.weclover.backend.entity.Pedido;
import com.weclover.backend.entity.Usuario;
import com.weclover.backend.exception.ForbiddenException;
import com.weclover.backend.repository.HistorialUbicacionPedidoRepository;
import com.weclover.backend.repository.NotaPedidoRepository;
import com.weclover.backend.repository.PedidoRepository;
import com.weclover.backend.repository.UsuarioRepository;

/** Notas y ubicación del pedido (paso 4 de Tandas). */
@ExtendWith(MockitoExtension.class)
class PedidoSeguimientoServiceTest {

    private static final Long ID_ACTOR = 7L;
    private static final Long ID_PEDIDO = 1L;

    @Mock private PedidoRepository pedidoRepository;
    @Mock private UsuarioRepository usuarioRepository;
    @Mock private NotaPedidoRepository notaPedidoRepository;
    @Mock private HistorialUbicacionPedidoRepository historialUbicacionPedidoRepository;
    @Mock private AutorizacionService autorizacionService;

    @InjectMocks
    private PedidoSeguimientoService service;

    private final Usuario actor = Usuario.builder().id(ID_ACTOR).nombre("Adrián").build();
    private Pedido pedido;

    @BeforeEach
    void setUp() {
        pedido = Pedido.builder().id(ID_PEDIDO).build();
        lenient().when(pedidoRepository.findById(ID_PEDIDO)).thenReturn(Optional.of(pedido));
        lenient().when(usuarioRepository.findById(ID_ACTOR)).thenReturn(Optional.of(actor));
        lenient().when(notaPedidoRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void agregarNotaGuardaTextoAutorYFecha() {
        NotaPedidoResponse respuesta = service.agregarNota(ID_PEDIDO, "  falta bandera ", ID_ACTOR);

        ArgumentCaptor<NotaPedido> captor = ArgumentCaptor.forClass(NotaPedido.class);
        verify(notaPedidoRepository).save(captor.capture());
        assertThat(captor.getValue().getPedido()).isSameAs(pedido);
        assertThat(captor.getValue().getTexto()).isEqualTo("falta bandera");
        assertThat(captor.getValue().getAutor()).isSameAs(actor);
        assertThat(captor.getValue().getFecha()).isNotNull();
        assertThat(respuesta.nombreAutor()).isEqualTo("Adrián");
        verify(autorizacionService).verificarRolPermitido(ID_ACTOR, Set.of("ROLE_ADMINISTRATIVO", "ROLE_PLANTA"));
    }

    @Test
    void sinRolPermitidoNoSeGuardaLaNota() {
        doThrow(new ForbiddenException("sin permiso")).when(autorizacionService).verificarRolPermitido(any(), any());

        assertThatThrownBy(() -> service.agregarNota(ID_PEDIDO, "nota", ID_ACTOR)).isInstanceOf(ForbiddenException.class);
        assertThatThrownBy(() -> service.listarNotas(ID_PEDIDO, ID_ACTOR)).isInstanceOf(ForbiddenException.class);
        verify(notaPedidoRepository, never()).save(any());
    }

    @Test
    void cambiarUbicacionRegistraAnteriorYNueva() {
        pedido.setUbicacionActual("con Adrián");

        service.cambiarUbicacion(ID_PEDIDO, " en estampado externo ", ID_ACTOR);

        assertThat(pedido.getUbicacionActual()).isEqualTo("en estampado externo");
        ArgumentCaptor<HistorialUbicacionPedido> captor = ArgumentCaptor.forClass(HistorialUbicacionPedido.class);
        verify(historialUbicacionPedidoRepository).save(captor.capture());
        assertThat(captor.getValue().getUbicacionAnterior()).isEqualTo("con Adrián");
        assertThat(captor.getValue().getUbicacionNueva()).isEqualTo("en estampado externo");
        assertThat(captor.getValue().getUsuario()).isSameAs(actor);
        verify(pedidoRepository).save(pedido);
    }

    @Test
    void ubicacionEnBlancoLaBorra() {
        pedido.setUbicacionActual("con Adrián");

        service.cambiarUbicacion(ID_PEDIDO, "   ", ID_ACTOR);

        assertThat(pedido.getUbicacionActual()).isNull();
        verify(historialUbicacionPedidoRepository).save(any());
    }

    @Test
    void siLaUbicacionNoCambiaNoSeRegistraNada() {
        pedido.setUbicacionActual("con Adrián");

        service.cambiarUbicacion(ID_PEDIDO, "con Adrián", ID_ACTOR);

        verify(historialUbicacionPedidoRepository, never()).save(any());
        verify(pedidoRepository, never()).save(any());
    }

    @Test
    void soloElAdministrativoCambiaLaUbicacion() {
        doThrow(new ForbiddenException("sin permiso")).when(autorizacionService).verificarRolAdministrativo(ID_ACTOR);

        assertThatThrownBy(() -> service.cambiarUbicacion(ID_PEDIDO, "con Adrián", ID_ACTOR))
            .isInstanceOf(ForbiddenException.class);

        assertThat(pedido.getUbicacionActual()).isNull();
        verify(historialUbicacionPedidoRepository, never()).save(any());
    }
}
