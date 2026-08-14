package com.pablo.aerolinea.service;

import com.pablo.aerolinea.exception.RecursoNoEncontradoException;
import com.pablo.aerolinea.exception.ReglaDeNegocioException;
import com.pablo.aerolinea.model.*;
import com.pablo.aerolinea.repository.ReservaRepository;
import com.pablo.aerolinea.repository.UsuarioRepository;
import com.pablo.aerolinea.repository.VueloRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class ReservaServiceTest {

    @Mock
    private ReservaRepository reservaRepository;

    @Mock
    private VueloRepository vueloRepository;

    @Mock
    private UsuarioRepository usuarioRepository;

    @Mock
    private EmailService emailService;

    @InjectMocks
    private ReservaService reservaService;

    private Usuario usuarioValido() {
        return Usuario.builder()
                .id(1L)
                .nombre("Pablo Bossio")
                .email("pablo@gmial.com")
                .password("hashXYZ")
                .rol(Rol.USUARIO)
                .build();
    }

    private Vuelo vueloValido() {
        return Vuelo.builder()
                .id(2L)
                .origen("Cordoba")
                .destino("Miami")
                .fechaSalida(LocalDateTime.now().plusDays(20))
                .fechaLlegada(LocalDateTime.now().plusDays(20).plusHours(8))
                .precio(new BigDecimal("500.00"))
                .asientosDisponibles(10)
                .estado(EstadoVuelo.PROGRAMADO)
                .build();
    }

    @Test
    void crearReserva_ConDatosValidos_deberiaCrearCorrectamente() {
        Usuario usuario = usuarioValido();
        Vuelo vuelo = vueloValido();

        when(usuarioRepository.findById(1L)).thenReturn(Optional.of(usuario));
        when(vueloRepository.buscarPorIdConBloqueo(2L)).thenReturn(Optional.of(vuelo));
        when(reservaRepository.save(any(Reserva.class))).thenAnswer(inv -> inv.getArgument(0));

        Reserva resultado = reservaService.crearReserva(1L, 2L);

        assertNotNull(resultado);
        assertEquals(EstadoReserva.CONFIRMADA, resultado.getEstado());
        assertEquals(new BigDecimal("500.00"), resultado.getPrecioPagado());
        assertEquals(9, vuelo.getAsientosDisponibles());
        verify(vueloRepository, times(1)).save(vuelo);
        verify(reservaRepository, times(1)).save(any(Reserva.class));
        verify(emailService, times(1)).enviarConfirmacionReserva(
                usuario.getNombre(), usuario.getEmail(), vuelo.getOrigen(), vuelo.getDestino(),
                vuelo.getFechaSalida(), resultado.getPrecioPagado());
    }

    @Test
    void crearReserva_conUsuarioInexistente_deberiaLanzarRecursoNoEncontrado() {
        when(usuarioRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(RecursoNoEncontradoException.class, () -> reservaService.crearReserva(99L, 2L));

        verify(reservaRepository, never()).save(any());
    }

    @Test
    void crearReserva_conVueloInexistente_deberiaLanzarRecursoNoEncontrado() {
        Usuario usuario = usuarioValido();

        when(usuarioRepository.findById(1L)).thenReturn(Optional.of(usuario));
        when(vueloRepository.buscarPorIdConBloqueo(99L)).thenReturn(Optional.empty());

        assertThrows(RecursoNoEncontradoException.class, () -> reservaService.crearReserva(1L, 99L));

        verify(reservaRepository, never()).save(any());
    }

    @Test
    void crearReserva_ConVueloNoProgramado_deberiaLanzarReglaDeNegocio() {
        Usuario usuario = usuarioValido();
        Vuelo vuelo = vueloValido();
        vuelo.setEstado(EstadoVuelo.CANCELADO);

        when(usuarioRepository.findById(1L)).thenReturn(Optional.of(usuario));
        when(vueloRepository.buscarPorIdConBloqueo(2L)).thenReturn(Optional.of(vuelo));

        assertThrows(ReglaDeNegocioException.class, () -> reservaService.crearReserva(1L, 2L));

        verify(reservaRepository, never()).save(any());
    }

    @Test
    void crearReserva_sinAsientosDisponibles_deberiaLanzarReglaDeNegocio() {
        Usuario usuario = usuarioValido();
        Vuelo vuelo = vueloValido();
        vuelo.setAsientosDisponibles(0);

        when(usuarioRepository.findById(1L)).thenReturn(Optional.of(usuario));
        when(vueloRepository.buscarPorIdConBloqueo(2L)).thenReturn(Optional.of(vuelo));

        assertThrows(ReglaDeNegocioException.class, () -> reservaService.crearReserva(1L, 2L));

        verify(reservaRepository, never()).save(any());
    }

    @Test
    void cancelarReserva_conDatosValidos_deberiaCancelarCorrectamente() {
        Vuelo vuelo = vueloValido();
        vuelo.setAsientosDisponibles(9);

        Reserva reserva = Reserva.builder()
                .id(5L)
                .usuario(usuarioValido())
                .vuelo(vuelo)
                .estado(EstadoReserva.CONFIRMADA)
                .build();

        when(reservaRepository.findById(5L)).thenReturn(Optional.of(reserva));
        when(reservaRepository.save(reserva)).thenReturn(reserva);

        Reserva resultado = reservaService.cancelarReserva(5L);

        assertEquals(EstadoReserva.CANCELADA, resultado.getEstado());
        assertEquals(10, vuelo.getAsientosDisponibles());
        verify(vueloRepository, times(1)).save(vuelo);
        verify(emailService, times(1)).enviarCancelacionReserva(
                reserva.getUsuario().getNombre(), reserva.getUsuario().getEmail(), vuelo.getOrigen(), vuelo.getDestino(),
                vuelo.getFechaSalida());
    }

    @Test
    void cancelarReserva_ConReservaInexistente_deberiaLanzarRecursoNoEncontrado() {
        when(reservaRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(RecursoNoEncontradoException.class, () -> reservaService.cancelarReserva(99L));

        verify(vueloRepository, never()).save(any());
    }

    @Test
    void cancelarReserva_yaCancelada_deberiaLanzarReglaDeNegocio() {
        Vuelo vuelo = vueloValido();

        Reserva reserva = Reserva.builder()
                .id(5L)
                .vuelo(vuelo)
                .estado(EstadoReserva.CANCELADA)
                .build();

        when(reservaRepository.findById(5L)).thenReturn(Optional.of(reserva));

        assertThrows(ReglaDeNegocioException.class, () -> reservaService.cancelarReserva(5L));

        verify(vueloRepository, never()).save(any());
    }

    @Test
    void listarTodas_deberiaDevolverTodasLasReservas() {
        Reserva reserva1 = Reserva.builder().id(1L).estado(EstadoReserva.CONFIRMADA).build();
        Reserva reserva2 = Reserva.builder().id(2L).estado(EstadoReserva.CANCELADA).build();

        when(reservaRepository.findAll()).thenReturn(List.of(reserva1, reserva2));

        var resultado = reservaService.listarTodas();

        assertEquals(2, resultado.size());
        verify(reservaRepository, times(1)).findAll();
    }
}
