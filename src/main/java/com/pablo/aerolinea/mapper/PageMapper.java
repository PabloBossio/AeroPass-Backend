package com.pablo.aerolinea.mapper;

import com.pablo.aerolinea.dto.PageResponseDTO;
import org.springframework.data.domain.Page;

public class PageMapper {

    public static <T> PageResponseDTO<T> tPageResponseDTO(Page<T> pagina) {
        return PageResponseDTO.<T>builder()
                .contenido(pagina.getContent())
                .paginaActual(pagina.getNumber())
                .tamanoPagina(pagina.getSize())
                .totalElementos(pagina.getTotalElements())
                .totalPaginas(pagina.getTotalPages())
                .esUltima(pagina.isLast())
                .build();
    }
}
