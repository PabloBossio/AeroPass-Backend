package com.pablo.aerolinea.controller;

import com.pablo.aerolinea.dto.VueloRequestDto;
import com.pablo.aerolinea.dto.VueloResponseDto;
import com.pablo.aerolinea.mapper.VueloMapper;
import com.pablo.aerolinea.model.Vuelo;
import com.pablo.aerolinea.service.VueloService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "Vuelos", description = "Consulta pública de vuelos; alta y modificacion restringidas a ADMIN")
@RestController
@RequestMapping("/api/vuelos")
public class VueloController {

    private final VueloService vueloService;

    public VueloController(VueloService vueloService) {
        this.vueloService = vueloService;
    }

    @Operation(summary = "Crear vuelo", description = "Registra un nuevo vuelo")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Registrado con exito"),
            @ApiResponse(responseCode = "400", description = "Datos invalidos (Origen, destino, fechas, precio, asientos o estado) faltantes/invalidos"),
            @ApiResponse(responseCode = "403", description = "No autenticado o sin rol ADMIN"),
            @ApiResponse(responseCode = "404", description = "No existe un avión con ese id")
    })

    @PostMapping
    public ResponseEntity<VueloResponseDto> crear(@Valid @RequestBody VueloRequestDto request) {
        Vuelo creado = vueloService.crearVuelo(VueloMapper.toEntity(request), request.getAvionId());
        return ResponseEntity.status(HttpStatus.CREATED).body(VueloMapper.toResponseDto(creado));
    }

    @Operation(summary = "Listar vuelos", description = "Devuelve todos los vuelos registrasdos")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Listado devuelto correctamente"),

    })

    @GetMapping
    public List<VueloResponseDto> listar() {
        return vueloService.listarTodos().stream()
                .map(VueloMapper::toResponseDto)
                .toList();
    }

    @Operation(summary = "Buscar vuelo por id", description = "Devuelve un vuelo puntual por id.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "vuelo devuelto correctamente"),
            @ApiResponse(responseCode = "404", description = "No existe un vuelo con ese id.")
    })

    @GetMapping("/{id}")
    public ResponseEntity<VueloResponseDto> buscarPorId(@PathVariable Long id) {
        return vueloService.buscarPorId(id)
                .map(VueloMapper::toResponseDto)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @Operation(summary = "Buscar vuelos por ruta", description = "devuelve los vuelos que coincidan con el origen y destino indicados")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Listado devuelto correctamente")
    })
    @GetMapping("/buscar")
    public List<VueloResponseDto> buscarPorRuta(@RequestParam String origen, @RequestParam String destino) {
        return vueloService.buscarPorOrigenYDestino(origen, destino).stream()
                .map(VueloMapper::toResponseDto)
                .toList();
    }

}
