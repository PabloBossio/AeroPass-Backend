package com.pablo.aerolinea.service;

import com.pablo.aerolinea.exception.RecursoNoEncontradoException;
import com.pablo.aerolinea.exception.ReglaDeNegocioException;
import com.pablo.aerolinea.model.Avion;
import com.pablo.aerolinea.model.EstadoVuelo;
import com.pablo.aerolinea.model.Vuelo;
import com.pablo.aerolinea.repository.AvionRepository;
import com.pablo.aerolinea.repository.ReservaRepository;
import com.pablo.aerolinea.repository.VueloRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class VueloService {

    private final VueloRepository vueloRepository;
    private final AvionRepository avionRepository;
    private final ReservaRepository reservaRepository;


    public VueloService(VueloRepository vueloRepository, AvionRepository avionRepository, ReservaRepository reservaRepository) {
        this.vueloRepository = vueloRepository;
        this.avionRepository = avionRepository;
        this.reservaRepository = reservaRepository;
    }

    public List<Vuelo> listarTodos() {
        return vueloRepository.findAll();
    }

    public Optional<Vuelo> buscarPorId(Long id) {
        return vueloRepository.findById(id);
    }

    public List<Vuelo> buscarPorOrigenYDestino(String origen, String destino) {
        return vueloRepository.findByOrigenAndDestino(origen, destino);
    }

    public Vuelo crearVuelo(Vuelo vuelo, Long avionId) {
        Avion avion = avionRepository.findById(avionId)
                .orElseThrow(()-> new RecursoNoEncontradoException("No existe un avión con id: " + avionId));

        validarDatosVuelo(vuelo, avion);

        vuelo.setAvion(avion);
        vuelo.setEstado(EstadoVuelo.PROGRAMADO);
        return vueloRepository.save(vuelo);
    }

    public Vuelo editarVuelo(Long id, Vuelo datosNuevos, Long avionId) {
        Vuelo vueloExistente =  vueloRepository.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("No existe un vuelo con ese id:" + id));

        Avion avion = avionRepository.findById(avionId)
                .orElseThrow(() -> new RecursoNoEncontradoException("No existe un avion con ese id:" + avionId));

        validarDatosVuelo(datosNuevos, avion);

        vueloExistente.setOrigen(datosNuevos.getOrigen());
        vueloExistente.setDestino(datosNuevos.getDestino());
        vueloExistente.setPrecio(datosNuevos.getPrecio());
        vueloExistente.setFechaSalida(datosNuevos.getFechaSalida());
        vueloExistente.setFechaLlegada(datosNuevos.getFechaLlegada());
        vueloExistente.setAsientosDisponibles(datosNuevos.getAsientosDisponibles());
        vueloExistente.setAvion(datosNuevos.getAvion());

        return vueloRepository.save(vueloExistente);
    }

    public void eliminarVuelo(Long id) {
        Vuelo vuelo = vueloRepository.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("No existe un vuelo con ese id: " + id));

        if (reservaRepository.existsByVueloId(id)) {
            throw new ReglaDeNegocioException("No se puede eliminar el vuelo: tiene reservas asociadas.");
        }

        vueloRepository.delete(vuelo);
    }

    public void validarDatosVuelo(Vuelo vuelo, Avion avion) {
        if (vuelo.getAsientosDisponibles() > avion.getCapacidad()) {
            throw new ReglaDeNegocioException(
                    "Los asientos disponibles (" + vuelo.getAsientosDisponibles() +
                            ") no pueden superar la capacidad del avion (" + avion.getCapacidad() + ")"
            );
        }
        if (vuelo.getFechaLlegada().isBefore(vuelo.getFechaSalida())) {
            throw new ReglaDeNegocioException("La fecha de llegada no puede ser anterior a la de salida.");
        }
        if (vuelo.getPrecio().signum() <= 0) {
            throw new ReglaDeNegocioException("El precio debe ser mayor a 0.");
        }
    }
}
