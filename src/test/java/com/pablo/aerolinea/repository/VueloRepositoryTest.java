package com.pablo.aerolinea.repository;

import com.pablo.aerolinea.model.Avion;
import com.pablo.aerolinea.model.EstadoVuelo;
import com.pablo.aerolinea.model.Vuelo;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.ANY)
public class VueloRepositoryTest {

    @Autowired
    private VueloRepository vueloRepository;

    @Autowired
    private AvionRepository avionRepository;

    private Avion avionValido() {
        return avionRepository.save(Avion.builder()
                .modelo("Boeing 737")
                .matricula("ABC123")
                .aerolinea("Aerolineas Argentinas")
                .capacidad(180)
                .build());
    }

    private Vuelo vueloConEstado(EstadoVuelo estado, Avion avion) {
        return vueloRepository.save(Vuelo.builder()
                .origen("Cordoba")
                .destino("Mendoza")
                .fechaSalida(LocalDateTime.now().plusDays(1))
                .fechaLlegada(LocalDateTime.now().plusDays(1).plusHours(2))
                .precio(new BigDecimal("500"))
                .asientosDisponibles(100)
                .avion(avion)
                .estado(estado)
                .build());
    }

    @Test
    void buscarConFiltros_soloReservablesTrue_excluyeCanceladoYFinalizado() {
        Avion avion = avionValido();
        Vuelo programado = vueloConEstado(EstadoVuelo.PROGRAMADO, avion);
        Vuelo cancelado = vueloConEstado(EstadoVuelo.CANCELADO, avion);
        Vuelo finalizado = vueloConEstado(EstadoVuelo.FINALIZADO, avion);

        Page<Vuelo> resultado = vueloRepository.buscarConFiltros(null, null, null, true, PageRequest.of(0, 10));
        List<Long> ids = resultado.getContent().stream().map(Vuelo::getId).toList();

        assertTrue(ids.contains(programado.getId()));
        assertFalse(ids.contains(cancelado.getId()));
        assertFalse(ids.contains(finalizado.getId()));
    }

    @Test
    void buscarConFiltros_soloReservablesFalse_incluyeTodos() {
        Avion avion = avionValido();
        Vuelo programado = vueloConEstado(EstadoVuelo.PROGRAMADO, avion);
        Vuelo cancelado = vueloConEstado(EstadoVuelo.CANCELADO, avion);

        Page<Vuelo> resultado = vueloRepository.buscarConFiltros(null, null,null, false, PageRequest.of(0, 10));
        List<Long> ids = resultado.getContent().stream().map(Vuelo::getId).toList();

        assertTrue(ids.contains(programado.getId()));
        assertTrue(ids.contains(cancelado.getId()));

    }
}
