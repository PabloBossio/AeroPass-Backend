package com.pablo.aerolinea.controller;

import com.pablo.aerolinea.dto.ReservaRequestDTO;
import com.pablo.aerolinea.dto.ReservaResponseDTO;
import com.pablo.aerolinea.mapper.ReservarMapper;
import com.pablo.aerolinea.model.Reserva;
import com.pablo.aerolinea.service.PagoService;
import com.pablo.aerolinea.service.ReservaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Tag(name = "Reservas", description = "Creacion, cancelacion y consulta de reservas - requiere estar autenticado")
@RestController
@RequestMapping("/api/reservas")
public class ReservaController {

    private final ReservaService reservaService;
    private final PagoService pagoService;

    public ReservaController(ReservaService reservaService, PagoService pagoService) {
        this.reservaService = reservaService;
        this.pagoService = pagoService;
    }

    @Operation(summary = "Crear reserva", description = "registra una nueva reserva")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Reserva creada correctamente"),
            @ApiResponse(responseCode = "400", description = "Datos inválidos (Usuario o vuelo faltantes/incorrectos)"),
            @ApiResponse(responseCode = "403", description = "No autenticado"),
            @ApiResponse(responseCode = "404", description = "No existen usuarios o vuelos con ese id.")
    })

    @PostMapping
    public ResponseEntity<ReservaResponseDTO> crear(@Valid @RequestBody ReservaRequestDTO request) {
        Reserva creada = reservaService.crearReserva(request.getUsuarioId(), request.getVueloId());
        return  ResponseEntity.status(HttpStatus.CREATED).body(ReservarMapper.toResponseDTO(creada));
    }

    @Operation(summary = "Cancelar reservas", description = "Cancela una reserva")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Reserva cancelada correctamente"),
            @ApiResponse(responseCode = "400", description = "La reserva ya estaba cancelada"),
            @ApiResponse(responseCode = "403", description = "No autenticado"),
            @ApiResponse(responseCode = "404", description = "No existe una reserva con ese id")
    })

    @PutMapping("/{id}/cancelar")
    public ResponseEntity<ReservaResponseDTO> cancelar(@PathVariable Long id) {
        Reserva cancelada = reservaService.cancelarReserva(id);
        return ResponseEntity.ok(ReservarMapper.toResponseDTO(cancelada));
    }

    @Operation(summary = "Listar todas las reservas", description = "Devuelve todas las reservas del sistema. Solo ADMIN.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Listado devuelto con exito"),
            @ApiResponse(responseCode = "403", description = "No autenticado o sin rol ADMIN")
    })

    @GetMapping
    public List<ReservaResponseDTO> listarTodas() {
        return reservaService.listarTodas().stream()
                .map(ReservarMapper::toResponseDTO)
                .toList();
    }

    @Operation(summary = "Buscar reservas por usuarios", description = "Busca las reservas realizadas por un usuario puntual")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Listado devuelto correctamente"),
            @ApiResponse(responseCode = "403", description = "No autenticado"),
    })

    @GetMapping("/usuario/{usuarioId}")
    public List<ReservaResponseDTO> listarPorUsuario(@PathVariable Long usuarioId) {
        return reservaService.listarPorUsuario(usuarioId).stream()
                .map(ReservarMapper::toResponseDTO)
                .toList();
    }

    @Operation(summary = "Buscar reservas por id", description = "Devuelve una reserva puntual por id")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Reserva encontrada."),
            @ApiResponse(responseCode = "403", description = "No autenticado"),
            @ApiResponse(responseCode = "404", description = "No existe un reserva con ese id")
    })

    @GetMapping("/{id}")
    public ResponseEntity<ReservaResponseDTO> buscarPorId(@PathVariable Long id) {
        return reservaService.buscarPorId(id)
                .map(ReservarMapper::toResponseDTO)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/{id}/pago")
    public ResponseEntity<Map<String, String>> crearPago(@PathVariable Long id) {
        String url = pagoService.crearSesionDePago(id);
        return ResponseEntity.ok(Map.of("url", url));
    }

}
