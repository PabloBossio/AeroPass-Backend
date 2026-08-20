package com.pablo.aerolinea.repository;

import com.pablo.aerolinea.model.Pago;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PagoRepository extends JpaRepository<Pago, Long> {
    Optional<Pago> findByStripeSessionId(String stripeSessionId);
}
