package com.pablo.aerolinea.service;

import com.pablo.aerolinea.exception.RecursoNoEncontradoException;
import com.pablo.aerolinea.exception.ReglaDeNegocioException;
import com.pablo.aerolinea.model.Rol;
import com.pablo.aerolinea.model.Usuario;
import com.pablo.aerolinea.repository.UsuarioRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class UsuarioServiceTest {

    @Mock
    private UsuarioRepository usuarioRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private UsuarioService usuarioService;

    @Test
    void registrarUsuario_ConEmailNuevo_deberiaRegistrarCorrectamente() {
        Usuario usuario = Usuario.builder()
                .nombre("Juan Perez")
                .email("juan.perez@gmail.com")
                .password("password123")
                .build();

        when(usuarioRepository.existsByEmail("juan.perez@gmail.com")).thenReturn(false);
        when(passwordEncoder.encode("password123")).thenReturn("hashSimulado123");
        when(usuarioRepository.save(usuario)).thenReturn(usuario);

        Usuario resultado = usuarioService.registrar(usuario);

        assertNotNull(resultado);
        assertEquals("hashSimulado123", resultado.getPassword());
        assertEquals(Rol.USUARIO, resultado.getRol());
        verify(usuarioRepository, times(1)).save(usuario);
    }

    @Test
    void registrarUsuario_conEmailExistente_deberiaLanzarReglaDeNegocio() {
        Usuario usuario = Usuario.builder()
                .nombre("Juan Perez")
                .email("juan.perez@gmail.com")
                .password("password123")
                .build();

        when(usuarioRepository.existsByEmail("juan.perez@gmail.com")).thenReturn(true);

        assertThrows(ReglaDeNegocioException.class, () -> usuarioService.registrar(usuario));

        verify(usuarioRepository, never()).save(any());
        verify(passwordEncoder, never()).encode(any());
    }

    @Test
    void actualizarRol_conUsuarioExistente_deberiaActualizarCorrectamente() {
        Usuario usuario = Usuario.builder()
                .id(1L)
                .nombre("Juan Perez")
                .email("juan.perez@gmail.com")
                .password("hashXYZ")
                .rol(Rol.USUARIO)
                .build();

        when(usuarioRepository.findById(1L)).thenReturn(Optional.of(usuario));
        when(usuarioRepository.save(usuario)).thenReturn(usuario);

        Usuario resultado = usuarioService.actualizarRol(1L, Rol.ADMIN);

        assertEquals(Rol.ADMIN, resultado.getRol());
        verify(usuarioRepository, times(1)).save(usuario);
    }

    @Test
    void actualizarRol_conUsuarioInexistente_deberiaLanzarRecursoNoEcontrado() {
        when(usuarioRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(RecursoNoEncontradoException.class, () -> usuarioService.actualizarRol(99L, Rol.ADMIN));

        verify(usuarioRepository, never()).save(any());
    }
}
