package com.pablo.aerolinea.dto;

import com.pablo.aerolinea.model.Rol;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class CambioRolDTO {

    @NotNull(message = "El rol es obligatorio")
    private Rol rol;

}
