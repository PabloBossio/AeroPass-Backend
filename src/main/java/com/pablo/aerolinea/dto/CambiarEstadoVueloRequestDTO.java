package com.pablo.aerolinea.dto;

import com.pablo.aerolinea.model.EstadoVuelo;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class CambiarEstadoVueloRequestDTO {

    @NotNull(message = "El estado es obligatorio")
    private EstadoVuelo estado;
}
