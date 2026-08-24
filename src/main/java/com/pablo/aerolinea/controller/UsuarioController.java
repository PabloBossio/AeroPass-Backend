package com.pablo.aerolinea.controller;

import com.pablo.aerolinea.dto.CambioRolDTO;
import com.pablo.aerolinea.dto.PageResponseDTO;
import com.pablo.aerolinea.dto.UsuarioRequestDTO;
import com.pablo.aerolinea.dto.UsuarioResponseDTO;
import com.pablo.aerolinea.mapper.PageMapper;
import com.pablo.aerolinea.mapper.UsuarioMapper;
import com.pablo.aerolinea.model.Usuario;
import com.pablo.aerolinea.service.UsuarioService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
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
    public ResponseEntity<PageResponseDTO<UsuarioResponseDTO>> listar(
            @PageableDefault(size = 10, sort = "nombre") Pageable pageable) {
        Page<Usuario> pagina = usuarioService.listarTodos(pageable);
        Page<UsuarioResponseDTO> paginaDTO = pagina.map(UsuarioMapper::toResponseDTO);
        return ResponseEntity.ok(PageMapper.tPageResponseDTO(paginaDTO));
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

    @Operation(summary = "Cambiar rol de un usuario", description = "Actualiza el rol de un usuario existente.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Rol actualizado con exito"),
            @ApiResponse(responseCode = "400", description = "Rol inválido o faltante"),
            @ApiResponse(responseCode = "403", description = "No auntenticado o sin rol ADMIN"),
            @ApiResponse(responseCode = "404", description = "No existe un usuario con ese id.")
    })
    @PutMapping("/{id}/rol")
    public ResponseEntity<UsuarioResponseDTO> actualizarRol(@PathVariable Long id, @Valid @RequestBody CambioRolDTO request) {
        Usuario actualizado = usuarioService.actualizarRol(id, request.getRol());
        return ResponseEntity.ok(UsuarioMapper.toResponseDTO(actualizado));
    }

    @Operation(summary = "Obtener mi perfil", description = "Devuelve los datos del usuario autenticado actualmente, identificado por su JWT")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Perfil devuelto con exito"),
            @ApiResponse(responseCode = "400", description = "No autenticado")
    })
    @GetMapping("/me")
    public ResponseEntity<UsuarioResponseDTO> obtenerMiPerfil(Authentication authentication) {
        Usuario usuario = usuarioService.buscarPorEmail(authentication.getName());
        return ResponseEntity.ok(UsuarioMapper.toResponseDTO(usuario));
    }
}
