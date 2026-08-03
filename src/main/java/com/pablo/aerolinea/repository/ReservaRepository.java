package com.pablo.aerolinea.repository;

import com.pablo.aerolinea.model.Reserva;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ReservaRepository extends JpaRepository<Reserva, Long> {
    List<Reserva> findByUsuarioId(Long usuarioId);
    boolean existsByVueloId(Long vueloId);
}
