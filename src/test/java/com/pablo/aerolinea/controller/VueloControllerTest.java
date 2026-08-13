package com.pablo.aerolinea.controller;

import com.pablo.aerolinea.config.SecurityConfig;
import com.pablo.aerolinea.dto.VueloRequestDto;
import com.pablo.aerolinea.exception.RecursoNoEncontradoException;
import com.pablo.aerolinea.exception.ReglaDeNegocioException;
import com.pablo.aerolinea.mapper.VueloMapper;
import com.pablo.aerolinea.model.Avion;
import com.pablo.aerolinea.model.EstadoVuelo;
import com.pablo.aerolinea.model.Vuelo;
import com.pablo.aerolinea.security.JwtUtil;
import com.pablo.aerolinea.security.UsuarioDetailsService;
import com.pablo.aerolinea.service.VueloService;
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

import static org.mockito.ArgumentMatchers.*;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

@WebMvcTest(VueloController.class)
@Import(SecurityConfig.class)
public class VueloControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JsonMapper objectMapper;

    @MockitoBean
    private VueloService vueloService;

    @MockitoBean
    private JwtUtil jwtUtil;

    @MockitoBean
    private UsuarioDetailsService usuarioDetailsService;

    private VueloRequestDto requestValido() {
        VueloRequestDto dto = new VueloRequestDto();
        dto.setOrigen("Buenos Aires");
        dto.setDestino("Madrid");
        dto.setFechaSalida(LocalDateTime.now().plusDays(10));
        dto.setFechaLlegada(LocalDateTime.now().plusDays(10).plusHours(12));
        dto.setPrecio(new BigDecimal("500.00"));
        dto.setAsientosDisponibles(150);
        dto.setAvionId(1L);
        return dto;
    }

    private Avion avionValido() {
        return Avion.builder()
                .id(1L)
                .modelo("Boeing 737")
                .matricula("ABC123")
                .capacidad(180)
                .aerolinea("Aerolineas Argentinas")
                .build();
    }

    private Vuelo vueloCreado() {
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



    @Test
    @WithMockUser(roles = "ADMIN")
    void crear_conDatosValidos_deberiaDevolver201() throws Exception {
        when(vueloService.crearVuelo(any(Vuelo.class), any(Long.class))).thenReturn(vueloCreado());

        mockMvc.perform(post("/api/vuelos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(requestValido())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.origen").value("Buenos Aires"))
                .andExpect(jsonPath("$.destino").value("Madrid"));
    }

    @Test
    @WithAnonymousUser
    void crear_sinAutenticacion_deberiaDevolver403() throws Exception {
        mockMvc.perform(post("/api/vuelos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(requestValido())))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void crear_conAvionInexistente_deberiaDevolver404() throws Exception {
        when(vueloService.crearVuelo(any(Vuelo.class), any(Long.class)))
                .thenThrow(new RecursoNoEncontradoException("No existe un avion con id: 99."));

        mockMvc.perform(post("/api/vuelos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(requestValido())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.mensaje").value("No existe un avion con id: 99."));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void crear_conReglaDeNegocioViolada_deberiaDevolver400() throws Exception {
        when(vueloService.crearVuelo(any(Vuelo.class), any(Long.class)))
                .thenThrow(new ReglaDeNegocioException("El precio debe ser mayor a 0."));

        mockMvc.perform(post("/api/vuelos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(requestValido())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").value("El precio debe ser mayor a 0."));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void crear_conOrigenFaltante_deberiaDevolver400() throws Exception {
        VueloRequestDto request = requestValido();
        request.setOrigen(null);

        mockMvc.perform(post("/api/vuelos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithAnonymousUser
    void listarVuelos_deberiaDevolver200ConPaginaDeVuelos() throws Exception {
        Pageable pageable = PageRequest.of(0, 10, Sort.by("fechaSalida"));
        Page<Vuelo> pagina = new PageImpl<>(List.of(vueloCreado()), pageable, 1);
        when(vueloService.listarTodos(isNull(), isNull(), isNull(), any(Pageable.class)))
                .thenReturn(pagina);

        mockMvc.perform(get("/api/vuelos"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contenido").isArray())
                .andExpect(jsonPath("$.contenido.length()").value(1))
                .andExpect(jsonPath("$.totalElementos").value(1));
    }

    @Test
    @WithAnonymousUser
    void listarVuelos_conFiltros_deberiaPasarlosAlService() throws Exception {
        Pageable pageable = PageRequest.of(0, 10, Sort.by("fechaSalida"));
        Page<Vuelo> pagina = new PageImpl<>(List.of(vueloCreado()), pageable, 1);
        when(vueloService.listarTodos(eq("Cordoba"), eq("Mendoza"), eq(EstadoVuelo.PROGRAMADO), any(Pageable.class)))
                .thenReturn(pagina);

        mockMvc.perform(get("/api/vuelos")
                        .param("origen", "Cordoba")
                        .param("destino", "Mendoza")
                        .param("estado", "PROGRAMADO"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contenido.length()").value(1));

        verify(vueloService).listarTodos(eq("Cordoba"), eq("Mendoza"), eq(EstadoVuelo.PROGRAMADO), any(Pageable.class));
    }

    @Test
    @WithAnonymousUser
    void buscarPorId_conIdExistente_deberiaDevolver200() throws Exception {
        when(vueloService.buscarPorIdCacheado(1L)).thenReturn(VueloMapper.toResponseDto(vueloCreado()));

        mockMvc.perform(get("/api/vuelos/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.destino").value("Madrid"));
    }

    @Test
    @WithAnonymousUser
    void buscarPorId_conIdInexistente_deberiaDevolver404() throws Exception {
        when(vueloService.buscarPorIdCacheado(99L)).thenReturn(null);

        mockMvc.perform(get("/api/vuelos/99"))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithAnonymousUser
    void buscarPorRuta_deberiaDevolverVuelosFiltrados() throws Exception {
        when(vueloService.buscarPorOrigenYDestino("Buenos Aires", "Madrid"))
                .thenReturn(List.of(vueloCreado()));

        mockMvc.perform(get("/api/vuelos/buscar")
                        .param("origen", "Buenos Aires")
                        .param("destino", "Madrid"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].origen").value("Buenos Aires"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void editar_conDatosValidos_deberiaDevolver200() throws Exception {
        when(vueloService.editarVuelo(any(Long.class), any(Vuelo.class), any(Long.class)))
                .thenReturn(vueloCreado());

        mockMvc.perform(put("/api/vuelos/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(requestValido())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.origen").value("Buenos Aires"));
    }

    @Test
    @WithAnonymousUser
    void editar_sinAutenticacion_deberiaDevolver403() throws Exception {
        mockMvc.perform(put("/api/vuelos/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(requestValido())))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void editar_conVueloInexistente_deberiaDevolver404() throws Exception {
        when(vueloService.editarVuelo(any(Long.class), any(Vuelo.class), any(Long.class)))
                .thenThrow(new RecursoNoEncontradoException("No existe un vuelo con ese id: 99"));

            mockMvc.perform(put("/api/vuelos/99")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(requestValido())))
                    .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void editar_conReglaDeNegocioViolada_deberiaDevolver400() throws Exception {
        when(vueloService.editarVuelo(any(Long.class), any(Vuelo.class), any(Long.class)))
                .thenThrow(new ReglaDeNegocioException("La fecha de llegada no puede ser anterior a la de salida."));

            mockMvc.perform(put("/api/vuelos/1")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(requestValido())))
                    .andExpect(status().isBadRequest());

    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void eliminar_conIdExistente_deberiaDevolver204() throws Exception {
        mockMvc.perform(delete("/api/vuelos/1"))
                        .andExpect(status().isNoContent());
    }

    @Test
    @WithAnonymousUser
    void eliminar_sinAutenticacion_deberiaDevolver403() throws Exception {
        mockMvc.perform(delete("/api/vuelos/1"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void eliminar_conVueloInexistente_deberiaDevolver404() throws Exception {
        doThrow(new RecursoNoEncontradoException("No existe un vuelo con ese id: 99"))
                .when(vueloService).eliminarVuelo(99L);

        mockMvc.perform(delete("/api/vuelos/99"))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void eliminar_conReservasAsociadas_deberiaDevolver400() throws Exception {
        doThrow(new ReglaDeNegocioException("No se puede eliminar el vuelo: tiene reservas asociadas."))
                .when(vueloService).eliminarVuelo(1L);

        mockMvc.perform(delete("/api/vuelos/1"))
                .andExpect(status().isBadRequest());
    }
 }
