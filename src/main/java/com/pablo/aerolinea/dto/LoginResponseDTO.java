package com.pablo.aerolinea.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class LoginResponseDTO {

    private String token;
    private String email;
    private String rol;
    private Long id;
}
