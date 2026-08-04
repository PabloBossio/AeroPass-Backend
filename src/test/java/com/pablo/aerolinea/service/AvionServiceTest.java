package com.pablo.aerolinea.service;

import com.pablo.aerolinea.exception.RecursoNoEncontradoException;
import com.pablo.aerolinea.exception.ReglaDeNegocioException;
import com.pablo.aerolinea.model.Avion;
import com.pablo.aerolinea.model.Vuelo;
import com.pablo.aerolinea.repository.AvionRepository;
import com.pablo.aerolinea.repository.VueloRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class AvionServiceTest {

    @Mock
    private AvionRepository avionRepository;

    @Mock
    private VueloRepository vueloRepository;

    @InjectMocks
    private AvionService avionService;

    @Test
    void crearAvion_conMatriculaNueva_deberiaGuardarCorrectamente() {
        Avion avion = Avion.builder()
                .modelo("Boeing 737")
                .matricula("ABC123")
                .capacidad(180)
                .aerolinea("Aerolineas Argentinas")
                .build();

        when(avionRepository.existsByMatricula("ABC123")).thenReturn(false);
        when(avionRepository.save(avion)).thenReturn(avion);

        Avion resultado = avionService.crearAvion(avion);

        assertNotNull(resultado);
        assertEquals("ABC123", resultado.getMatricula());
        verify(avionRepository, times(1)).save(avion);
    }

    @Test
    void crearAvion_conMatriculaExistente_deberiaLanzarExcepcion() {
        Avion avion = Avion.builder()
                .matricula("ABC123")
                .build();

        when(avionRepository.existsByMatricula("ABC123")).thenReturn(true);

        assertThrows(ReglaDeNegocioException.class, () -> avionService.crearAvion(avion));

        verify(avionRepository, never()).save(any());
    }

    @Test
    void editarAvion_conDatosValidos_deberiaActualizarCorrectamente() {
        Avion avionExistente = Avion.builder()
                .id(1L)
                .modelo("Boeing 737")
                .matricula("ABC123")
                .capacidad(180)
                .aerolinea("Aerolineas Argentinas")
                .build();

        Avion datosNuevos = Avion.builder()
                .modelo("Boeing 737 MAX")
                .matricula("ABC123")
                .capacidad(190)
                .aerolinea("Aerolineas Argentinas")
                .build();

        when(avionRepository.findById(1L)).thenReturn(Optional.of(avionExistente));
        when(avionRepository.existsByMatriculaAndIdNot("ABC123", 1L)).thenReturn(false);
        when(vueloRepository.findByAvionId(1L)).thenReturn(List.of());
        when(avionRepository.save(any(Avion.class))).thenAnswer(inv -> inv.getArgument(0));

        Avion resultado = avionService.editarAvion(1L, datosNuevos);

        assertEquals("Boeing 737 MAX", resultado.getModelo());
        assertEquals(190, resultado.getCapacidad());
        verify(avionRepository, times(1)).save(avionExistente);
    }

    @Test
    void editarAvion_conAvionInexistente_deberiaLanzarRecursoNoEncontrado() {
        Avion datosNuevos = Avion.builder().matricula("ABC123").capacidad(180).build();

        when(avionRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(RecursoNoEncontradoException.class, () -> avionService.editarAvion(99L, datosNuevos));

        verify(avionRepository, never()).save(any());
    }

    @Test
    void editarAvion_conMatriculaDuplicada_deberiaLanzarReglaDeNegocio() {
        Avion avionExistente = Avion.builder().id(1L).matricula("ABC123").capacidad(180).build();
        Avion datosNuevos = Avion.builder().matricula("XYZ987").capacidad(180).build();

        when(avionRepository.findById(1L)).thenReturn(Optional.of(avionExistente));
        when(avionRepository.existsByMatriculaAndIdNot("XYZ987", 1L)).thenReturn(true);

        assertThrows(ReglaDeNegocioException.class, () -> avionService.editarAvion(1L, datosNuevos));

        verify(avionRepository, never()).save(any());
    }

    @Test
    void editarAvion_conCapacidadMenorAAsientosComprometidos_deberiaLanzarReglaDeNegocio() {
        Avion avionExistente = Avion.builder().id(1L).matricula("ABC123").capacidad(180).build();
        Avion datosNuevos = Avion.builder().matricula("ABC123").capacidad(100).build();

        Vuelo vueloConMuchosAsientos = Vuelo.builder().asientosDisponibles(150).build();

        when(avionRepository.findById(1L)).thenReturn(Optional.of(avionExistente));
        when(avionRepository.existsByMatriculaAndIdNot("ABC123", 1L)).thenReturn(false);
        when(vueloRepository.findByAvionId(1L)).thenReturn(List.of(vueloConMuchosAsientos));

        assertThrows(ReglaDeNegocioException.class, () -> avionService.editarAvion(1L, datosNuevos));

        verify(avionRepository, never()).save(any());
    }

}
