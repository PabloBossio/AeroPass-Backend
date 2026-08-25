package com.pablo.aerolinea.controller;

import com.pablo.aerolinea.config.SecurityConfig;
import com.pablo.aerolinea.dto.CambioRolDTO;
import com.pablo.aerolinea.dto.UsuarioRequestDTO;
import com.pablo.aerolinea.exception.RecursoNoEncontradoException;
import com.pablo.aerolinea.exception.ReglaDeNegocioException;
import com.pablo.aerolinea.mapper.UsuarioMapper;
import com.pablo.aerolinea.model.Rol;
import com.pablo.aerolinea.model.Usuario;
import com.pablo.aerolinea.security.JwtUtil;
import com.pablo.aerolinea.security.UsuarioDetailsService;
import com.pablo.aerolinea.service.UsuarioService;
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

import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(UsuarioController.class)
@Import(SecurityConfig.class)
public class UsuarioControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JsonMapper objectMapper;

    @MockitoBean
    private UsuarioService usuarioService;

    @MockitoBean
    private JwtUtil jwtUtil;

    @MockitoBean
    private UsuarioDetailsService usuarioDetailsService;

    private UsuarioRequestDTO requestValido() {
        UsuarioRequestDTO dto = new UsuarioRequestDTO();
        dto.setNombre("Juan Perez");
        dto.setEmail("juan.perez@gmail.com");
        dto.setPassword("password123");
        return dto;
    }

    private Usuario usuarioCreado() {
        return Usuario.builder()
                .id(1L)
                .nombre("Juan Perez")
                .email("juan.perez@gmail.com")
                .password("hashEncriptado")
                .rol(Rol.USUARIO)
                .build();
    }

    @Test
    @WithAnonymousUser
    void registrar_conDatosValidos_deberiaDevolver201() throws Exception {
        when(usuarioService.registrar(any(Usuario.class))).thenReturn(usuarioCreado());

        mockMvc.perform(post("/api/usuarios")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(requestValido())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value("juan.perez@gmail.com"))
                .andExpect(jsonPath("$.rol").value("USUARIO"));
    }

    @Test
    @WithAnonymousUser
    void registrar_conEmailDuplicado_deberiaDevolver400() throws Exception {
        when(usuarioService.registrar(any(Usuario.class)))
                .thenThrow(new ReglaDeNegocioException("Ya existe un usuario con el email: juan.perez@gmail.com"));

        mockMvc.perform(post("/api/usuarios")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(requestValido())))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithAnonymousUser
    void registrar_conEmailInvalido_deberiaDevolver400() throws Exception {
        UsuarioRequestDTO request = requestValido();
        request.setEmail("no-es-un-email");

        mockMvc.perform(post("/api/usuarios")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithAnonymousUser
    void registrar_conPasswordCorta_deberiaDevolver400() throws Exception {
        UsuarioRequestDTO request = requestValido();
        request.setPassword("1234");

        mockMvc.perform(post("/api/usuarios")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithAnonymousUser
    void listar_sinAutenticacion_deberiaDevolver403() throws Exception {
        mockMvc.perform(get("/api/usuarios"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "USUARIO")
    void listar_conRolUsuario_deveriaDevolver403() throws Exception {
        mockMvc.perform(get("/api/usuarios"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void listar_conRolAdmin_deberiaDevolverListaUsuarios() throws Exception {
        Pageable pageable = PageRequest.of(0,10, Sort.by("nombre"));
        Page<Usuario> pagina = new PageImpl<>(List.of(usuarioCreado()), pageable, 1);
        when(usuarioService.listarTodos(any(Pageable.class))).thenReturn(pagina);

        mockMvc.perform(get("/api/usuarios"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contenido").isArray())
                .andExpect(jsonPath("$.contenido[0].email").value("juan.perez@gmail.com"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void buscarPorId_conIdExistente_deberiaDevolver200() throws Exception {
        when(usuarioService.buscarPorIdCacheado(1L)).thenReturn(UsuarioMapper.toResponseDTO(usuarioCreado()));

        mockMvc.perform(get("/api/usuarios/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nombre").value("Juan Perez"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void buscarPorId_conIdInexistente_deberiaDevolver404() throws Exception {
        when(usuarioService.buscarPorIdCacheado(99L)).thenReturn(null);

        mockMvc.perform(get("/api/usuarios/99"))
                .andExpect(status().isNotFound());
    }

    private CambioRolDTO cambioRolValido() {
        CambioRolDTO dto = new CambioRolDTO();
        dto.setRol(Rol.ADMIN);
        return dto;
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void actualizarRol_conDatosValidos_deberiaDevovler200() throws Exception {
        Usuario usuarioActualizado = usuarioCreado();
        usuarioActualizado.setRol(Rol.ADMIN);

        when(usuarioService.actualizarRol(1L, Rol.ADMIN)).thenReturn(usuarioActualizado);

        mockMvc.perform(put("/api/usuarios/1/rol")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(cambioRolValido())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rol").value("ADMIN"));
    }

    @Test
    @WithMockUser(roles = "USUARIO")
    void actualizarRol_conRolUsuario_deberiaDevolver403() throws Exception {
        mockMvc.perform(put("/api/usuarios/1/rol")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(cambioRolValido())))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void actualizarRol_conUsuarioInexistente_deberiaDevovler404() throws Exception {
        when(usuarioService.actualizarRol(99L, Rol.ADMIN))
                .thenThrow(new RecursoNoEncontradoException("No exite un usuario con ese id: 99"));

        mockMvc.perform(put("/api/usuarios/99/rol")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(cambioRolValido())))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void actualizarRol_conRolFaltante_deberiaDevolver400() throws Exception {
        CambioRolDTO request = new CambioRolDTO();

        mockMvc.perform(put("/api/usuarios/1/rol")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(username = "juan.perez@gmail.com", roles = "USUARIO")
    void obtenerMiPerfil_conUsuarioAutenticado_deberiaDevolver200() throws Exception {
        when(usuarioService.buscarPorEmail("juan.perez@gmail.com")).thenReturn(usuarioCreado());

        mockMvc.perform(get("/api/usuarios/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("juan.perez@gmail.com"));
    }

    @Test
    @WithAnonymousUser
    void obtenerMiPerfil_sinAutenticacion_deberiaDevolver403() throws Exception {
        mockMvc.perform(get("/api/usuarios/me"))
                .andExpect(status().isForbidden());
    }
}
