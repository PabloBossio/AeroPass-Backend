package com.pablo.aerolinea.service;

import com.pablo.aerolinea.dto.ReservaResponseDTO;
import com.pablo.aerolinea.exception.RecursoNoEncontradoException;
import com.pablo.aerolinea.exception.ReglaDeNegocioException;
import com.pablo.aerolinea.mapper.ReservarMapper;
import com.pablo.aerolinea.model.*;
import com.pablo.aerolinea.repository.ReservaRepository;
import com.pablo.aerolinea.repository.UsuarioRepository;
import com.pablo.aerolinea.repository.VueloRepository;
import jakarta.transaction.Transactional;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
public class ReservaService {

    private final ReservaRepository reservaRepository;
    private final VueloRepository vueloRepository;
    private final UsuarioRepository usuarioRepository;
    private final VueloService vueloService;
    private final EmailService emailService;

    public ReservaService(ReservaRepository reservaRepository, VueloRepository vueloRepository, UsuarioRepository usuarioRepository, VueloService vueloService, EmailService emailService) {
        this.reservaRepository = reservaRepository;
        this.vueloRepository = vueloRepository;
        this.usuarioRepository = usuarioRepository;
        this.vueloService = vueloService;
        this.emailService = emailService;
    }

    public Page<Reserva> listarTodas(Pageable pageable) {
        return reservaRepository.findAll(pageable);
    }

    public List<Reserva> listarPorUsuario(Long usuarioId) {
        return reservaRepository.findByUsuarioId(usuarioId);
    }

    public Optional<Reserva> buscarPorId(Long id) {
        return reservaRepository.findById(id);
    }

    @Transactional
    public Reserva crearReserva(Long usuarioId, Long vueloId) {

        Usuario usuario = usuarioRepository.findById(usuarioId)
                .orElseThrow(() -> new RecursoNoEncontradoException("No existe un usuario con ese id: " + usuarioId));

        Vuelo vuelo = vueloRepository.buscarPorIdConBloqueo(vueloId)
                .orElseThrow(() -> new RecursoNoEncontradoException("No existe un vuelo con ese id: " + vueloId));
        
        if (vuelo.getEstado() != EstadoVuelo.PROGRAMADO) {
            throw new ReglaDeNegocioException("Solo se reservar vuelos en estado PROGRAMADO");
        }
        if (vuelo.getAsientosDisponibles() <= 0) {
            throw new ReglaDeNegocioException("No hay asientos disponibles para este vuelo");
        }

        vuelo.setAsientosDisponibles(vuelo.getAsientosDisponibles() -1 );
        vueloRepository.save(vuelo);
        vueloService.evictarCacheVuelo(vuelo.getId());

        Reserva reserva = Reserva.builder()
                .usuario(usuario)
                .vuelo(vuelo)
                .fechaReserva(LocalDateTime.now())
                .precioPagado(vuelo.getPrecio())
                .estado(EstadoReserva.PENDIENTE_PAGO)
                .build();

        Reserva reservaGuardada = reservaRepository.save(reserva);
        return reservaGuardada;
    }

    @Transactional
    @CacheEvict(cacheNames = "reserva", key = "#reservaId")
    public Reserva cancelarReserva(Long reservaId) {
        Reserva reserva = reservaRepository.findById(reservaId)
                .orElseThrow(() -> new RecursoNoEncontradoException("No existe una reserva con ese id: " + reservaId));

        if (reserva.getEstado() == EstadoReserva.CANCELADA) {
            throw new ReglaDeNegocioException("La reserva ya estaba cancelada");
        }

        Vuelo vuelo = reserva.getVuelo();
        vuelo.setAsientosDisponibles(vuelo.getAsientosDisponibles() +1);
        vueloRepository.save(vuelo);
        vueloService.evictarCacheVuelo(vuelo.getId());

        reserva.setEstado(EstadoReserva.CANCELADA);

        Reserva reservaGuardada = reservaRepository.save(reserva);
        emailService.enviarCancelacionReserva(reserva.getUsuario().getNombre(), reserva.getUsuario().getEmail(),
                vuelo.getOrigen(), vuelo.getDestino(), vuelo.getFechaSalida());
        return reservaGuardada;
    }

    @Cacheable(cacheNames = "reserva", key = "#id")
    public ReservaResponseDTO buscarPorIdCacheado(Long id) {
        return buscarPorId(id)
                .map(ReservarMapper::toResponseDTO)
                .orElse(null);
    }

    @CacheEvict(cacheNames = "reserva", key = "#id")
    public void evitarCacheReserva(Long id){}
}
