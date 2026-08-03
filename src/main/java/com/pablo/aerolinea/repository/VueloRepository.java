package com.pablo.aerolinea.repository;

import com.pablo.aerolinea.model.EstadoVuelo;
import com.pablo.aerolinea.model.Vuelo;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface VueloRepository extends JpaRepository<Vuelo, Long>{

    List<Vuelo> findByOrigenAndDestino(String origen, String destino);

    List<Vuelo> findByEstado(EstadoVuelo estado);

    List<Vuelo> findByAvionId(Long avionId);

    @Query(value = "SELECT * FROM vuelos  WHERE id = :id FOR UPDATE", nativeQuery = true)
    Optional<Vuelo> buscarPorIdConBloqueo(@Param("id") Long id);
}
