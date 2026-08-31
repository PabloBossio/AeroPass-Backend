package com.pablo.aerolinea.service;

import com.pablo.aerolinea.exception.RecursoNoEncontradoException;
import com.pablo.aerolinea.exception.ReglaDeNegocioException;
import com.pablo.aerolinea.model.*;
import com.pablo.aerolinea.repository.AvionRepository;
import com.pablo.aerolinea.repository.ReservaRepository;
import com.pablo.aerolinea.repository.VueloRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class VueloServiceTest {

    @Mock
    private VueloRepository vueloRepository;

    @Mock
    private AvionRepository avionRepository;

    @Mock
    private ReservaRepository reservaRepository;

    @Mock
    private EmailService emailService;

    @InjectMocks
    private VueloService vueloService;

    private Avion avionValido() {
        return Avion.builder()
                .id(1L)
                .modelo("Boeing 737")
                .matricula("ABC123")
                .capacidad(180)
                .aerolinea("Aerolineas Argentinas")
                .build();
    }

    private Vuelo vueloValido() {
        return Vuelo.builder()
                .origen("Buenos aires")
                .destino("Madrid")
                .fechaSalida(LocalDateTime.now().plusDays(10))
                .fechaLlegada(LocalDateTime.now().plusDays(10).plusHours(12))
                .precio(new BigDecimal("850.00"))
                .asientosDisponibles(150)
                .build();
    }

    private Usuario usuarioValido() {
        Usuario usuario = new Usuario();
        usuario.setNombre("Pablo");
        usuario.setEmail("pablo@test.com");
        return usuario;
    }

    private Reserva reservaConEstado(EstadoReserva estado) {
        Reserva reserva = new Reserva();
        reserva.setEstado(estado);
        reserva.setUsuario(usuarioValido());
        return reserva;
    }

    @Test
    void crearVuelo_conDatosValidos_deberiaCrearCorrectamente() {
        Avion avion = avionValido();
        Vuelo vuelo = vueloValido();

        when(avionRepository.findById(1L)).thenReturn(Optional.of(avion));
        when(vueloRepository.save(vuelo)).thenReturn(vuelo);

        Vuelo resultado = vueloService.crearVuelo(vuelo, 1L);

        assertNotNull(resultado);
        assertEquals(EstadoVuelo.PROGRAMADO, resultado.getEstado());
        assertEquals(avion, resultado.getAvion());
        verify(vueloRepository, times(1)).save(vuelo);

    }

    @Test
    void crearVuelo_conAvionInexistente_deberiaLanzarRecursoNoEncontrado() {
        Vuelo vuelo = vueloValido();

        when(avionRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(RecursoNoEncontradoException.class, () -> vueloService.crearVuelo(vuelo, 99L));

        verify(vueloRepository, never()).save(any());
    }

    @Test
    void crearVuelo_conAsientosSuperandoCapacidad_deberiaLanzarReglaDeNegocio() {
        Avion avion = avionValido();
        Vuelo vuelo = vueloValido();
        vuelo.setAsientosDisponibles(200);

        when(avionRepository.findById(1L)).thenReturn(Optional.of(avion));

        assertThrows(ReglaDeNegocioException.class, () -> vueloService.crearVuelo(vuelo, 1L));

        verify(vueloRepository, never()).save(any());
    }

    @Test
    void crearVuelo_conFechaLlegadaAnteriorFechaSalida_DeberialanzarReglaDeNegocio() {
        Avion avion = avionValido();
        Vuelo vuelo = vueloValido();
        vuelo.setFechaSalida(LocalDateTime.now().plusDays(10));
        vuelo.setFechaLlegada(LocalDateTime.now().plusDays(5));

        when(avionRepository.findById(1L)).thenReturn(Optional.of(avion));

        assertThrows(ReglaDeNegocioException.class, () -> vueloService.crearVuelo(vuelo, 1L));

        verify(vueloRepository, never()).save(any());
    }

    @Test
    void crearVuelo_conPrecioCero_deberiaLanzarReglaDeNegocio() {
        Avion avion = avionValido();
        Vuelo vuelo = vueloValido();
        vuelo.setPrecio(BigDecimal.ZERO);

        when(avionRepository.findById(1L)).thenReturn(Optional.of(avion));

        assertThrows(ReglaDeNegocioException.class, () -> vueloService.crearVuelo(vuelo, 1L));

        verify(vueloRepository, never()).save(any());
    }

    @Test
    void editarVuelo_conDatosValidos_deberiaActualizarYReasignarAvion() {
        Avion avionOriginal = avionValido();
        Vuelo vueloExistente = vueloValido();
        vueloExistente.setAvion(avionOriginal);

        Avion avionNuevo = Avion.builder()
                .id(2L)
                .modelo("Airbus A320")
                .matricula("XYZ987")
                .capacidad(150)
                .aerolinea("Aerolineas Argentinas")
                .build();

        Vuelo datosNuevos = vueloValido();
        datosNuevos.setDestino("Roma");

        when(vueloRepository.findById(1L)).thenReturn(Optional.of(vueloExistente));
        when(avionRepository.findById(2L)).thenReturn(Optional.of(avionNuevo));
        when(vueloRepository.save(any(Vuelo.class))).thenAnswer(inv -> inv.getArgument(0));

        Vuelo resultado = vueloService.editarVuelo(1L, datosNuevos, 2L);

        assertEquals("Roma", resultado.getDestino());
        assertEquals(avionNuevo, resultado.getAvion());
        verify(vueloRepository, times(1)).save(vueloExistente);
    }

    @Test
    void editarVuelo_conVueloInexistente_deberiaLanzarRecursoNoEncontrado() {
        Vuelo datosNuevos = vueloValido();

        when(vueloRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(RecursoNoEncontradoException.class, () -> vueloService.editarVuelo(99L, datosNuevos, 1L));

        verify(vueloRepository, never()).save(any());
    }

    @Test
    void editarVuelo_conAvionInexistente_deberiaLanzarRecursoNoEncontrado() {
        Vuelo vueloExistente = vueloValido();
        Vuelo datosNuevos = vueloValido();

        when(vueloRepository.findById(1L)).thenReturn(Optional.of(vueloExistente));
        when(avionRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(RecursoNoEncontradoException.class, () -> vueloService.editarVuelo(1L, datosNuevos , 99L));

        verify(vueloRepository, never()).save(any());
    }

    @Test
    void eliminarVuelo_sinReservas_deberiaEliminarCorrectamente() {
        Vuelo vuelo = vueloValido();

        when(vueloRepository.findById(1L)).thenReturn(Optional.of(vuelo));
        when(reservaRepository.existsByVueloId(1L)).thenReturn(false);

        vueloService.eliminarVuelo(1L);

        verify(vueloRepository, times(1)).delete(vuelo);
    }

    @Test
    void eliminarVuelo_conReservasAsociadas_deberiaLanzarReglaDeNegocio() {
        Vuelo vuelo = vueloValido();

        when(vueloRepository.findById(1L)).thenReturn(Optional.of(vuelo));
        when(reservaRepository.existsByVueloId(1L)).thenReturn(true);

        assertThrows(ReglaDeNegocioException.class, () -> vueloService.eliminarVuelo(1L));

        verify(vueloRepository, never()).delete(any());
    }

    @Test
    void eliminarVuelo_conVueloInexistente_deberiaLanzarRecursoNoEncontrado() {
        when(vueloRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(RecursoNoEncontradoException.class,() -> vueloService.eliminarVuelo(99L));

        verify(vueloRepository, never()).delete(any());
    }

    @Test
    void listarTodos_sinFiltros_deberiaDevolverUnaPaginaDeVuelos() {
        Pageable pageable = PageRequest.of(0, 10);
        Page<Vuelo> paginaEsperada = new PageImpl<>(List.of(vueloValido()), pageable, 1);
        when(vueloRepository.buscarConFiltros(null, null, null, pageable))
                .thenReturn(paginaEsperada);

        Page<Vuelo> resultado = vueloService.listarTodos(null, null, null, pageable);

        assertEquals(1, resultado.getTotalElements());
        assertEquals(1, resultado.getContent().size());
    }

    @Test
    void listarTodos_conFiltros_deberiaPasarlosAlRepository() {
        Pageable pageable = PageRequest.of(0, 10);
        Page<Vuelo> paginaEsperada = new PageImpl<>(List.of(vueloValido()), pageable, 1);
        when(vueloRepository.buscarConFiltros("Cordoba", "Mendoza", EstadoVuelo.PROGRAMADO, pageable))
                .thenReturn(paginaEsperada);

        Page<Vuelo> resultado = vueloService.listarTodos("Cordoba", "Mendoza", EstadoVuelo.PROGRAMADO, pageable);

        assertEquals(1, resultado.getTotalElements());
        verify(vueloRepository).buscarConFiltros("Cordoba", "Mendoza", EstadoVuelo.PROGRAMADO, pageable);
    }

    @Test
    void cambiarEstado_aDemorado_deberiaNotificarReservasActivas() {
        Vuelo vuelo = vueloValido();
        vuelo.setId(1L);
        vuelo.setEstado(EstadoVuelo.PROGRAMADO);

        Reserva confirmada = reservaConEstado(EstadoReserva.CONFIRMADA);

        when(vueloRepository.findById(1L)).thenReturn(Optional.of(vuelo));
        when(vueloRepository.save(any(Vuelo.class))).thenAnswer(inv -> inv.getArgument(0));
        when(reservaRepository.findByVueloId(1L)).thenReturn(List.of(confirmada));

        Vuelo resultado = vueloService.cambiarEstado(1L, EstadoVuelo.DEMORADO);

        assertEquals(EstadoVuelo.DEMORADO, resultado.getEstado());
        verify(emailService, times(1)).enviarAvisoVueloDemorado(
                eq("Pablo"), eq("pablo@test.com"), any(), any(), any());
        verify(emailService, never()).enviarAvisoVueloCancelado(any(), any(), any(), any(), any());
    }

    @Test
    void cambiarEstado_aCancelado_deberiaUsarElMetodoDeCancelado() {
        Vuelo vuelo = vueloValido();
        vuelo.setId(1L);
        vuelo.setEstado(EstadoVuelo.PROGRAMADO);

        Reserva confirmada = reservaConEstado(EstadoReserva.CONFIRMADA);

        when(vueloRepository.findById(1L)).thenReturn(Optional.of(vuelo));
        when(vueloRepository.save(any(Vuelo.class))).thenAnswer(inv -> inv.getArgument(0));
        when(reservaRepository.findByVueloId(1L)).thenReturn(List.of(confirmada));

        vueloService.cambiarEstado(1L, EstadoVuelo.CANCELADO);

        verify(emailService, times(1)).enviarAvisoVueloCancelado(any(), any(), any(), any(), any());
        verify(emailService, never()).enviarAvisoVueloDemorado(any(), any(), any(), any(), any());
    }

    @Test
    void cambiarEstado_aProgramado_noDeberiaEnviarEmail() {
        Vuelo vuelo = vueloValido();
        vuelo.setId(1L);
        vuelo.setEstado(EstadoVuelo.DEMORADO);

        when(vueloRepository.findById(1L)).thenReturn(Optional.of(vuelo));
        when(vueloRepository.save(any(Vuelo.class))).thenAnswer(inv -> inv.getArgument(0));

        vueloService.cambiarEstado(1L, EstadoVuelo.PROGRAMADO);

        verifyNoInteractions(emailService);
    }

    @Test
    void cambiarEstado_mismoEstado_noDeberiaHacerNada() {
        Vuelo vuelo = vueloValido();
        vuelo.setId(1L);
        vuelo.setEstado(EstadoVuelo.DEMORADO);

        when(vueloRepository.findById(1L)).thenReturn(Optional.of(vuelo));

        Vuelo resultado = vueloService.cambiarEstado(1L, EstadoVuelo.DEMORADO);

        assertEquals(vuelo, resultado);
        verify(vueloRepository, never()).save(any());
        verifyNoInteractions(emailService);
    }

    @Test
    void cambiarEstado_vueloInexistente_deberiaLanzarExcepcion() {
        when(vueloRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(RecursoNoEncontradoException.class, () -> vueloService.cambiarEstado(99L, EstadoVuelo.DEMORADO));

        verify(vueloRepository, never()).save(any());
        verifyNoInteractions(emailService);
    }

    @Test
    void cambiarEstado_reservaCancelada_noDeberiaRecibirEmail() {
        Vuelo vuelo = vueloValido();
        vuelo.setId(1L);
        vuelo.setEstado(EstadoVuelo.PROGRAMADO);

        Reserva cancelada = reservaConEstado(EstadoReserva.CANCELADA);

        when(vueloRepository.findById(1L)).thenReturn(Optional.of(vuelo));
        when(vueloRepository.save(any(Vuelo.class))).thenAnswer(inv -> inv.getArgument(0));
        when(reservaRepository.findByVueloId(1L)).thenReturn(List.of(cancelada));

        vueloService.cambiarEstado(1L, EstadoVuelo.DEMORADO);

        verifyNoInteractions(emailService);
    }
}
