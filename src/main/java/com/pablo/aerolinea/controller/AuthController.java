package com.pablo.aerolinea.controller;

import com.pablo.aerolinea.dto.LoginRequestDTO;
import com.pablo.aerolinea.dto.LoginResponseDTO;
import com.pablo.aerolinea.model.Usuario;
import com.pablo.aerolinea.repository.UsuarioRepository;
import com.pablo.aerolinea.security.JwtUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Autenticación", description = "Login y generacion de JWT")
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final UsuarioRepository usuarioRepository;
    private final JwtUtil jwtUtil;

    public AuthController(AuthenticationManager authenticationManager, UsuarioRepository usuarioRepository, JwtUtil jwtUtil) {
        this.authenticationManager = authenticationManager;
        this.usuarioRepository = usuarioRepository;
        this.jwtUtil = jwtUtil;
    }

    @Operation(summary = "Iniciar sesión", description = "Autentica al usuario y devuelve un token JWT junto con sus datos si las credenciales son correctas.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Login exitoso, devuelve el token JWT junto con el email y el rol del usuario"),
            @ApiResponse(responseCode = "400", description = "Datos inválidos (email o password faltantes/incorrectos)"),
            @ApiResponse(responseCode = "401", description = "Credenciales incorrectas")
    })

    @PostMapping("/login")
    public LoginResponseDTO login(@Valid @RequestBody LoginRequestDTO request) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getEmail(), request.getPassword())
        );

        Usuario usuario = usuarioRepository.findByEmail(request.getEmail())
                .orElseThrow();

        String token = jwtUtil.generarToken(usuario.getEmail(), usuario.getRol().name());

        return new LoginResponseDTO(token, usuario.getEmail(), usuario.getRol().name(), usuario.getId());
    }
}
