package com.pablo.aerolinea.service;

import com.pablo.aerolinea.exception.RecursoNoEncontradoException;
import com.pablo.aerolinea.exception.ReglaDeNegocioException;
import com.pablo.aerolinea.model.EstadoReserva;
import com.pablo.aerolinea.model.Reserva;
import com.pablo.aerolinea.repository.PagoRepository;
import com.pablo.aerolinea.repository.ReservaRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import com.pablo.aerolinea.model.Vuelo;
import com.pablo.aerolinea.model.Pago;
import com.stripe.model.checkout.Session;
import com.stripe.param.checkout.SessionCreateParams;
import org.mockito.MockedStatic;
import com.pablo.aerolinea.model.EstadoPago;
import com.pablo.aerolinea.model.Usuario;
import com.stripe.model.Event;
import com.stripe.model.EventDataObjectDeserializer;

import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.eq;

import static org.mockito.ArgumentMatchers.any;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
@ExtendWith(MockitoExtension.class)
public class PagoServiceTest {

    @Mock
    private ReservaRepository reservaRepository;

    @Mock
    private PagoRepository pagoRepository;

    @Mock
    private EmailService emailService;

    @InjectMocks
    private PagoService pagoService;

    @Test
    void crearSesionDePago_reservaValida_creaSesionYGuardaPago() throws Exception {
        Vuelo vuelo = Vuelo.builder()
                .id(2L)
                .origen("Mendoza")
                .destino("Cancun")
                .build();

        Reserva reserva = Reserva.builder()
                .id(1L)
                .estado(EstadoReserva.PENDIENTE_PAGO)
                .precioPagado(new BigDecimal("500.00"))
                .vuelo(vuelo)
                .build();

        when(reservaRepository.findById(1L)).thenReturn(Optional.of(reserva));

        Session sessionMock = mock(Session.class);
        when(sessionMock.getId()).thenReturn("cs_test_123");
        when(sessionMock.getUrl()).thenReturn("https://checkout.stripe.com/test");

        try (MockedStatic<Session> sessionEstatico = mockStatic(Session.class)) {
            sessionEstatico.when(() -> Session.create(any(SessionCreateParams.class)))
                    .thenReturn(sessionMock);

            String url = pagoService.crearSesionDePago(1L);

            assertEquals("https://checkout.stripe.com/test", url);
        }

        verify(pagoRepository, times(1)).save(any(Pago.class));
    }

    @Test
    void confirmarPago_PagoPendiente_confirmaReservaYEnviaEmail() {
        Session sessionMock = mock(Session.class);
        when(sessionMock.getId()).thenReturn("cs_test_123");

        EventDataObjectDeserializer deserializerMock = mock(EventDataObjectDeserializer.class);
        when(deserializerMock.getObject()).thenReturn(Optional.of(sessionMock));

        Event eventMock = mock(Event.class);
        when(eventMock.getDataObjectDeserializer()).thenReturn(deserializerMock);

        Usuario usuario = Usuario.builder()
                .nombre("Pablo")
                .email("pablo@test.com")
                .build();

        Vuelo vuelo = Vuelo.builder()
                .origen("Mendoza")
                .destino("Cancun")
                .fechaSalida(LocalDateTime.now().plusDays(5))
                .build();

        Reserva reserva = Reserva.builder()
                .id(1L)
                .estado(EstadoReserva.PENDIENTE_PAGO)
                .precioPagado(new BigDecimal("500.00"))
                .usuario(usuario)
                .vuelo(vuelo)
                .build();

        Pago pago = Pago.builder()
                .id(1L)
                .stripeSessionId("cs_test_123")
                .estado(EstadoPago.PENDIENTE)
                .reserva(reserva)
                .build();

        when(pagoRepository.findByStripeSessionId("cs_test_123")).thenReturn(Optional.of(pago));

        pagoService.confirmarPago(eventMock);

        assertEquals(EstadoPago.APROBADO, pago.getEstado());
        assertEquals(EstadoReserva.CONFIRMADA, reserva.getEstado());
        verify(pagoRepository, times(1)).save(pago);
        verify(reservaRepository, times(1)).save(reserva);
        verify(emailService, times(1)).enviarConfirmacionReserva(
                eq("Pablo"), eq("pablo@test.com"
                ), eq("Mendoza"), eq("Cancun"), any(LocalDateTime.class), eq(new BigDecimal("500.00")));
    }



    @Test
    void crearSesionDePago_reservaNoEncontrada_lanzaExcepcion() {
        when(reservaRepository.findById(1L)).thenReturn(Optional.empty());

        assertThrows(RecursoNoEncontradoException.class, () -> pagoService.crearSesionDePago(1L));

        verifyNoInteractions(pagoRepository);
    }

    @Test
    void crearSesionDePago_reservaNoPendienteDePago_lanzaExcepcion() {
        Reserva reserva = Reserva.builder()
                .id(1L)
                .estado(EstadoReserva.CONFIRMADA)
                .precioPagado(new BigDecimal("500.00"))
                .build();

        when(reservaRepository.findById(1L)).thenReturn(Optional.of(reserva));

        assertThrows(ReglaDeNegocioException.class, () -> pagoService.crearSesionDePago(1L));

        verifyNoInteractions(pagoRepository);
    }

    @Test
    void confirmarPago_pagoNoEncontrado_lanzaExcepcion() {
        Session sessionMock = mock(Session.class);
        when(sessionMock.getId()).thenReturn("cs_test_999");

        EventDataObjectDeserializer deserializerMock = mock(EventDataObjectDeserializer.class);
        when(deserializerMock.getObject()).thenReturn(Optional.of(sessionMock));

        Event eventMock = mock(Event.class);
        when(eventMock.getDataObjectDeserializer()).thenReturn(deserializerMock);

        when(pagoRepository.findByStripeSessionId("cs_test_999")).thenReturn(Optional.empty());

        assertThrows(RecursoNoEncontradoException.class, () -> pagoService.confirmarPago(eventMock));
    }

    @Test
    void confirmarPago_pagoYaAprobado_noVuelveAProcesar() {
        Session sessionMock = mock(Session.class);
        when(sessionMock.getId()).thenReturn("cs_test_123");

        EventDataObjectDeserializer deserializerMock = mock(EventDataObjectDeserializer.class);
        when(deserializerMock.getObject()).thenReturn(Optional.of(sessionMock));

        Event eventMock = mock(Event.class);
        when(eventMock.getDataObjectDeserializer()).thenReturn(deserializerMock);

        Pago pago = Pago.builder()
                .id(1L)
                .stripeSessionId("cs_test_123")
                .estado(EstadoPago.APROBADO)
                .build();

        when(pagoRepository.findByStripeSessionId("cs_test_123")).thenReturn(Optional.of(pago));

        pagoService.confirmarPago(eventMock);

        verify(pagoRepository, never()).save(any(Pago.class));
        verify(reservaRepository, never()).save(any(Reserva.class));
        verifyNoInteractions(emailService);
    }

}
