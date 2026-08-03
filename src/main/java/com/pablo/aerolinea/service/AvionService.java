package com.pablo.aerolinea.service;

import com.pablo.aerolinea.exception.RecursoNoEncontradoException;
import com.pablo.aerolinea.exception.ReglaDeNegocioException;
import com.pablo.aerolinea.model.Avion;
import com.pablo.aerolinea.repository.AvionRepository;
import com.pablo.aerolinea.repository.VueloRepository;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class AvionService {

    private final AvionRepository avionRepository;
    private final VueloRepository vueloRepository;

    public AvionService(AvionRepository avionRepository, VueloRepository vueloRepository) {
        this.avionRepository = avionRepository;
        this.vueloRepository = vueloRepository;
    }

    public List<Avion> listarTodos() {
        return avionRepository.findAll();
    }

    public Optional<Avion> buscarPorId(Long id) {
        return avionRepository.findById(id);
    }

    @Transactional
    public Avion crearAvion(Avion avion) {
        if (avionRepository.existsByMatricula(avion.getMatricula())) {
            throw new ReglaDeNegocioException("Ya existe un avión registrado con la matricula: " + avion.getMatricula());
        }
        return avionRepository.save(avion);
    }

    @Transactional
    public Avion editarAvion(Long id, Avion datosNuevos) {
        Avion avionExistente = avionRepository.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("No existe un avion con ese id: " + id));

        if (avionRepository.existsByMatriculaAndIdNot(datosNuevos.getMatricula(), id)) {
            throw new ReglaDeNegocioException("Ya existe otro avion registrado con la matricula: " + datosNuevos.getMatricula());
        }

        boolean capacidadInsuficiente = vueloRepository.findByAvionId(id).stream()
                .anyMatch(vuelo -> datosNuevos.getCapacidad() < vuelo.getAsientosDisponibles());
        if (capacidadInsuficiente) {
            throw new ReglaDeNegocioException("No se puede reducir la capacidad por debajo de los asientos ya comprometidos en vuelos existentes");
        }

        avionExistente.setModelo(datosNuevos.getModelo());
        avionExistente.setMatricula(datosNuevos.getMatricula());
        avionExistente.setCapacidad(datosNuevos.getCapacidad());
        avionExistente.setAerolinea(datosNuevos.getAerolinea());

        return avionRepository.save(avionExistente);
    }
}
