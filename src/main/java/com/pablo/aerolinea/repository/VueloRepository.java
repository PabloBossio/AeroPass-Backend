package com.pablo.aerolinea.repository;

import com.pablo.aerolinea.model.EstadoVuelo;
import com.pablo.aerolinea.model.Vuelo;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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

    @Query("SELECT v FROM Vuelo v WHERE " +
            "(:origen IS NULL OR v.origen = :origen) AND " +
            "(:destino IS NULL OR v.destino = :destino) AND " +
            "(:estado IS NULL OR v.estado = :estado) AND" +
            "(:soloReservables = false OR v.estado NOT IN (" +
            "com.pablo.aerolinea.model.EstadoVuelo.CANCELADO, " +
            "com.pablo.aerolinea.model.EstadoVuelo.FINALIZADO))")
    Page<Vuelo> buscarConFiltros(@Param("origen")String origen,
                                 @Param("destino")String destino,
                                 @Param("estado")EstadoVuelo estado,
                                 @Param("soloReservables")Boolean soloReservables,
                                 Pageable pageable);
}
