package com.pablo.aerolinea.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PageResponseDTO<T> {
    private List<T> contenido;
    private int paginaActual;
    private int tamanoPagina;
    private Long totalElementos;
    private int totalPaginas;
    private boolean esUltima;
}
