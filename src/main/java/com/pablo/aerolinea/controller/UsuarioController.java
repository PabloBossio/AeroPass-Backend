package com.pablo.aerolinea.controller;

import com.pablo.aerolinea.dto.UsuarioRequestDTO;
import com.pablo.aerolinea.dto.UsuarioResponseDTO;
import com.pablo.aerolinea.mapper.UsuarioMapper;
import com.pablo.aerolinea.model.Usuario;
import com.pablo.aerolinea.service.UsuarioService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "Usuarios", description = "Registro público de usuarios; lista y consulta restringidos a ADMIN")
@RestController
@RequestMapping("/api/usuarios")
public class UsuarioController {

    private final UsuarioService usuarioService;

    public UsuarioController(UsuarioService usuarioService) {
        this.usuarioService = usuarioService;
    }

    @Operation(summary = "Registrar un usuario", description = "Registra un usuario nuevo.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Usuario registrado con exito"),
            @ApiResponse(responseCode = "400", description = "Datos inválidos (nombre, email o password faltantes/incorrectos)")
    })

    @PostMapping
    public ResponseEntity<UsuarioResponseDTO> registrar(@Valid @RequestBody UsuarioRequestDTO request) {
        Usuario creado = usuarioService.registrar(UsuarioMapper.toEntity(request));
        return ResponseEntity.status(HttpStatus.CREATED).body(UsuarioMapper.toResponseDTO(creado));
    }

    @Operation(summary = "Listar Usuarios", description = "Devuelve todos los usuarios")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Listado devuelto correctamente"),
            @ApiResponse(responseCode = "403", description = "No autenticado o sin rol ADMIN")
    })

    @GetMapping
    public List<UsuarioResponseDTO> listar() {
        return usuarioService.listarTodos().stream()
                .map(UsuarioMapper::toResponseDTO)
                .toList();
    }

    @Operation(summary = "Buscar usuario por id", description = "Devuelve un usuario puntual por id.")
    @ApiResponses({
             @ApiResponse(responseCode = "200", description = "Usuario devuelto correctamente"),
             @ApiResponse(responseCode = "403", description = "No autenticado o sin rol ADMIN"),
             @ApiResponse(responseCode = "404", description = "No existe un usuario con ese id")
    })

    @GetMapping("/{id}")
    public ResponseEntity<UsuarioResponseDTO> bucarPorId(@PathVariable Long id) {
        return usuarioService.buscarPorId(id)
                .map(UsuarioMapper::toResponseDTO)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
}
