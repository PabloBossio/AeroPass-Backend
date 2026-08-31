package com.pablo.aerolinea.controller;

import com.pablo.aerolinea.dto.CambiarEstadoVueloRequestDTO;
import com.pablo.aerolinea.dto.PageResponseDTO;
import com.pablo.aerolinea.dto.VueloRequestDto;
import com.pablo.aerolinea.dto.VueloResponseDto;
import com.pablo.aerolinea.mapper.PageMapper;
import com.pablo.aerolinea.mapper.VueloMapper;
import com.pablo.aerolinea.model.EstadoVuelo;
import com.pablo.aerolinea.model.Vuelo;
import com.pablo.aerolinea.service.VueloService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.data.domain.Page;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.data.domain.Pageable;


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

    @Operation(summary = "Editar vuelo", description = "Actualiza los datos de un vuelo existente.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Vuelo actualizado con exito."),
            @ApiResponse(responseCode = "400", description = "Datos invalidos"),
            @ApiResponse(responseCode = "403", description = "No autenticado o sin rol ADMIN"),
            @ApiResponse(responseCode = "404", description = "No existe un vuelo o avion con ese id")
    })

    @CacheEvict(cacheNames = "vuelo", key = "#id")
    @PutMapping("/{id}")
    public ResponseEntity<VueloResponseDto> editar(@PathVariable Long id, @Valid @RequestBody VueloRequestDto request) {
        Vuelo actualizado = vueloService.editarVuelo(id, VueloMapper.toEntity(request), request.getAvionId());
        return ResponseEntity.ok(VueloMapper.toResponseDto(actualizado));
    }

    @Operation(summary = "Listar vuelos", description = "Devuelve todos los vuelos registrasdos")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Listado devuelto correctamente"),

    })

    @GetMapping
    public ResponseEntity<PageResponseDTO<VueloResponseDto>> listarVuelos(
            @RequestParam(required = false) String origen,
            @RequestParam(required = false) String destino,
            @RequestParam(required = false) EstadoVuelo estado,
            @PageableDefault(size = 10, sort = "fechaSalida")Pageable pageable) {
        return ResponseEntity.ok(vueloService.listarTodosCacehado(origen, destino, estado, pageable));
    }

    @Operation(summary = "Buscar vuelo por id", description = "Devuelve un vuelo puntual por id.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "vuelo devuelto correctamente"),
            @ApiResponse(responseCode = "404", description = "No existe un vuelo con ese id.")
    })

    @GetMapping("/{id}")
    public ResponseEntity<VueloResponseDto> buscarPorId(@PathVariable Long id) {
        VueloResponseDto dto = vueloService.buscarPorIdCacheado(id);
        return dto != null ? ResponseEntity.ok(dto) : ResponseEntity.notFound().build();
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

    @Operation(summary = "Eliminar vuelo", description = "Elimina un vuelo existente, siempre que no tenga reservas asociadas.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Vuelo eliminado con exito"),
            @ApiResponse(responseCode = "400", description = "El vuelo tiene reservas asociadas y no se puede eliminar"),
            @ApiResponse(responseCode = "403", description = "No autenticado o sin rol ADMIN"),
            @ApiResponse(responseCode = "404", description = "No existe un vuelo con ese id")
    })

    @CacheEvict(cacheNames = "vuelo", key = "#id")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminar(@PathVariable Long id) {
        vueloService.eliminarVuelo(id);
        return ResponseEntity.noContent().build();
    }


    @Operation(summary = "Cambiar estado de un vuelo",
                description = "Actualiza el estado de un vuelo. si pasa a DEMORADO o CANCELADO, notifica a los usuarios con reservas activas sobre ese vuelo.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Estado actualizado con éxito."),
            @ApiResponse(responseCode = "400", description = "Estado inválido o faltante."),
            @ApiResponse(responseCode = "403", description = "No autenticado o sin rol ADMIN."),
            @ApiResponse(responseCode = "404", description = "No existe un vuelo con ese id.")
    })
    @PutMapping("/{id}/estado")
    public ResponseEntity<VueloResponseDto> cambiarEstado(@PathVariable Long id,
                                                          @Valid @RequestBody CambiarEstadoVueloRequestDTO request) {
        Vuelo actualizado = vueloService.cambiarEstado(id, request.getEstado());
        return ResponseEntity.ok(VueloMapper.toResponseDto(actualizado));
    }
}
