package com.pablo.aerolinea.controller;

import com.pablo.aerolinea.dto.AvionRequestDTO;
import com.pablo.aerolinea.dto.AvionResponseDTO;
import com.pablo.aerolinea.mapper.AvionMapper;
import com.pablo.aerolinea.model.Avion;
import com.pablo.aerolinea.service.AvionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "Aviones", description = "Gestión de la flota de aviones - requiere rol ADMIN")
@RestController
@RequestMapping("/api/aviones")
public class AvionController {

    private final AvionService avionService;

    public AvionController(AvionService avionService) {
        this.avionService = avionService;
    }

    @Operation(summary = "Crear un avión", description = "Registra un nuevo avión en la flota")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Avión creado con exito"),
            @ApiResponse(responseCode = "400", description = "Datos inválidos (matrícula, modelo, capacidad o aerolinea faltantes/incorrectos)"),
            @ApiResponse(responseCode = "403", description = "No autenticado o sin rol ADMIN")
    })

    @PostMapping
    public ResponseEntity<AvionResponseDTO> crear(@Valid @RequestBody AvionRequestDTO request) {
        Avion creado = avionService.crearAvion(AvionMapper.toEntity(request));
        return ResponseEntity.status(HttpStatus.CREATED).body(AvionMapper.toResponseDTO(creado));
    }

    @Operation(summary = "Listar aviones", description = "Devuleve todos los aviones registrados en la flota")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Listado devuelto correctamente"),
            @ApiResponse(responseCode = "403", description = "No autenticado o sin rol ADMIN")
    })

    @GetMapping
    public List<AvionResponseDTO> listar() {
        return avionService.listarTodos().stream()
                .map(AvionMapper::toResponseDTO)
                .toList();
    }

    @Operation(summary = "Buscar avión por id", description = "Devuelve un avión puntual por su id.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Avión devuleto correctamente"),
            @ApiResponse(responseCode = "403", description = "No autenticado o sin rol ADMIN"),
            @ApiResponse(responseCode = "404", description = "No existe un avión con ese id.")
    })


    @GetMapping("/{id}")
    public ResponseEntity<AvionResponseDTO> buscarPorId(@PathVariable Long id) {
        return avionService.buscarPorId(id)
                .map(AvionMapper::toResponseDTO)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @Operation(summary = "Editar un avion", description = "Actualiza los datos de un avion existente.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Avion actualizado con exito"),
            @ApiResponse(responseCode = "400", description = "Datos inválidos o matrícula duplicada"),
            @ApiResponse(responseCode = "403", description = "No autenticado o sin rol ADMIN"),
            @ApiResponse(responseCode = "404", description = "No existe un avion con ese id")
    })

    @PutMapping("/{id}")
    public ResponseEntity<AvionResponseDTO> editar(@PathVariable Long id, @Valid @RequestBody AvionRequestDTO request) {
        Avion actualizado = avionService.editarAvion(id, AvionMapper.toEntity(request));
        return ResponseEntity.ok(AvionMapper.toResponseDTO(actualizado));
    }


}
