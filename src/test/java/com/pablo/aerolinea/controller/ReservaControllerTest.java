package com.pablo.aerolinea.controller;

import com.pablo.aerolinea.config.SecurityConfig;
import com.pablo.aerolinea.dto.ReservaRequestDTO;
import com.pablo.aerolinea.exception.RecursoNoEncontradoException;
import com.pablo.aerolinea.exception.ReglaDeNegocioException;
import com.pablo.aerolinea.mapper.ReservarMapper;
import com.pablo.aerolinea.model.*;
import com.pablo.aerolinea.security.JwtUtil;
import com.pablo.aerolinea.security.UsuarioDetailsService;
import com.pablo.aerolinea.service.PagoService;
import com.pablo.aerolinea.service.ReservaService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.*;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ReservaController.class)
@Import(SecurityConfig.class)
public class ReservaControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JsonMapper objectMapper;

    @MockitoBean
    private ReservaService reservaService;

    @MockitoBean
    private JwtUtil jwtUtil;

    @MockitoBean
    private UsuarioDetailsService usuarioDetailsService;

    @MockitoBean
    private PagoService pagoService;

    private Avion avionValido() {
        return Avion.builder()
                .id(1L)
                .modelo("Boeing 737")
                .matricula("ABC123")
                .capacidad(180)
                .aerolinea("Aerolineas Argentinas")
                .build();
    }

    private Usuario usuarioValido() {
        return Usuario.builder()
                .id(1L)
                .nombre("Juan Perez")
                .email("juan.perez@gmail.com")
                .password("hashEncriptado")
                .rol(Rol.USUARIO)
                .build();
    }

    private Vuelo vueloValido() {
        return Vuelo.builder()
                .id(1L)
                .origen("Buenos Aires")
                .destino("Madrid")
                .fechaSalida(LocalDateTime.now().plusDays(10))
                .fechaLlegada(LocalDateTime.now().plusDays(10).plusHours(12))
                .precio(new BigDecimal("500.00"))
                .asientosDisponibles(150)
                .estado(EstadoVuelo.PROGRAMADO)
                .avion(avionValido())
                .build();
    }

    private Reserva reservaCreada() {
        return Reserva.builder()
                .id(1L)
                .usuario(usuarioValido())
                .vuelo(vueloValido())
                .fechaReserva(LocalDateTime.now())
                .precioPagado(new BigDecimal("500.00"))
                .estado(EstadoReserva.CONFIRMADA)
                .build();
    }

    private ReservaRequestDTO requestValido() {
        ReservaRequestDTO dto = new ReservaRequestDTO();
        dto.setUsuarioId(1L);
        dto.setVueloId(1L);
        return dto;
    }

    @Test
    @WithMockUser(roles = "USUARIO")
    void crear_conDatosValidos_deberiaDevolver201() throws Exception {
        when(reservaService.crearReserva(1L,1L)).thenReturn(reservaCreada());

        mockMvc.perform(post("/api/reservas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(requestValido())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.estado").value("CONFIRMADA"));
    }

    @Test
    @WithAnonymousUser
    void crear_sinAuntenticacion_deberiaDevolver403() throws Exception {
        mockMvc.perform(post("/api/reservas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(requestValido())))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "USUARIO")
    void crear_conUsuarioInexistente_deberiaDevolver404() throws Exception {
        when(reservaService.crearReserva(1L, 1L))
                .thenThrow(new RecursoNoEncontradoException("No existe un usuario con el id: 1"));

        mockMvc.perform(post("/api/reservas")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(requestValido())))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(roles = "USUARIO")
    void crar_conVueloSinAsientos_deberiaDevolver400() throws Exception {
        when(reservaService.crearReserva(1L, 1L))
                .thenThrow(new ReglaDeNegocioException("No hay asientos disponibles para este vuelo"));

        mockMvc.perform(post("/api/reservas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(requestValido())))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void cancelar_conIdExistente_deberiaDevolver200() throws Exception {
        Reserva cancelada = reservaCreada();
        cancelada.setEstado(EstadoReserva.CANCELADA);
        when(reservaService.cancelarReserva(1L)).thenReturn(cancelada);

        mockMvc.perform(put("/api/reservas/1/cancelar"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("CANCELADA"));
    }

    @Test
    @WithMockUser(roles = "USUARIO")
    void cancelar_conReservaYaCancelada_deberiaDevolver400() throws Exception {
        when(reservaService.cancelarReserva(1L))
                .thenThrow(new ReglaDeNegocioException("La reserva ya estaba cancelada"));

        mockMvc.perform(put("/api/reservas/1/cancelar"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithAnonymousUser
    void cancelar_sinAutenticacion_deberiaDevolver403() throws Exception {
        mockMvc.perform(put("/api/reservas/1/cancelar"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "USUARIO")
    void listarPorUsuario_deberiaDevolverlistaReservas() throws Exception {
        when(reservaService.listarPorUsuario(1L)).thenReturn(List.of(reservaCreada()));

        mockMvc.perform(get("/api/reservas/usuario/1"))
                .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].estado").value("CONFIRMADA"));
    }

    @Test
    @WithMockUser(roles = "USUARIO")
    void buscarPorId_conIdExistente_deberiaDevolver200() throws Exception {
        when(reservaService.buscarPorIdCacheado(1L)).thenReturn(ReservarMapper.toResponseDTO(reservaCreada()));

        mockMvc.perform(get("/api/reservas/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.precioPagado").value(500.00));
    }

    @Test
    @WithMockUser(roles = "USUARIO")
    void buscarPorId_conIdInexistente_deberiaDevolver404() throws Exception {
        when(reservaService.buscarPorIdCacheado(99L)).thenReturn(null);

        mockMvc.perform(get("/api/reservas/99"))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void listarTodas_conRolAdmin_deberiaDevolverListaReservas() throws Exception {
        Pageable pageable = PageRequest.of(0,10, Sort.by("fechaReserva"));
        Page<Reserva> pagina = new PageImpl<>(List.of(reservaCreada()), pageable, 1);
        when(reservaService.listarTodas(any(Pageable.class))).thenReturn(pagina);

        mockMvc.perform(get("/api/reservas"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contenido[0].estado").value("CONFIRMADA"));
    }

    @Test
    @WithAnonymousUser
    void listarTodas_sinAutenticacion_deberiaDevolver403() throws Exception {
        mockMvc.perform(get("/api/reservas"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "USUARIO")
    void listarTodas_conRolUsuario_deberiaDevolver403() throws Exception {
        mockMvc.perform(get("/api/reservas"))
                .andExpect(status().isForbidden());
    }
}
