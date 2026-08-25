package com.pablo.aerolinea.service;

import com.pablo.aerolinea.exception.RecursoNoEncontradoException;
import com.pablo.aerolinea.exception.ReglaDeNegocioException;
import com.pablo.aerolinea.model.EstadoPago;
import com.pablo.aerolinea.model.EstadoReserva;
import com.pablo.aerolinea.model.Pago;
import com.pablo.aerolinea.model.Reserva;
import com.pablo.aerolinea.repository.PagoRepository;
import com.pablo.aerolinea.repository.ReservaRepository;
import com.stripe.exception.StripeException;
import com.stripe.model.Event;
import com.stripe.model.checkout.Session;
import com.stripe.param.checkout.SessionCreateParams;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Service
public class PagoService {

    private final ReservaRepository reservaRepository;
    private final PagoRepository pagoRepository;
    private final EmailService emailService;
    private final ReservaService reservaService;

    @Value("${app.frontend.url}")
    private String frontendUrl;

    public PagoService(ReservaRepository reservaRepository, PagoRepository pagoRepository, EmailService emailService, ReservaService reservaService) {
        this.reservaRepository = reservaRepository;
        this.pagoRepository = pagoRepository;
        this.emailService = emailService;
        this.reservaService = reservaService;
    }

    public String crearSesionDePago(Long reservaId) {
        Reserva reserva = reservaRepository.findById(reservaId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Reserva no encontrada."));

        if (reserva.getEstado() != EstadoReserva.PENDIENTE_PAGO) {
            throw new ReglaDeNegocioException("Esta reserva no está pendiente de pago.");
        }

        long montoEnCentavos = reserva.getPrecioPagado()
                .multiply(BigDecimal.valueOf(100))
                .longValueExact();

        SessionCreateParams params = SessionCreateParams.builder()
                .setMode(SessionCreateParams.Mode.PAYMENT)
                .setSuccessUrl(frontendUrl + "/pago/exito?session_id={CHECKOUT_SESSION_ID}")
                .setCancelUrl(frontendUrl + "/pago/cancelado")
                .putMetadata("reservaId", reservaId.toString())
                .addLineItem(
                        SessionCreateParams.LineItem.builder()
                                .setQuantity(1L)
                                .setPriceData(
                                        SessionCreateParams.LineItem.PriceData.builder()
                                                .setCurrency("usd")
                                                .setUnitAmount(montoEnCentavos)
                                                .setProductData(
                                                        SessionCreateParams.LineItem.PriceData.ProductData.builder()
                                                                .setName("Vuelo " + reserva.getVuelo().getOrigen()
                                                                        + " -> " + reserva.getVuelo().getDestino())
                                                                .build())
                                                .build())
                                .build())
                .build();


        try {
            Session session = Session.create(params);

            Pago pago = Pago.builder()
                    .reserva(reserva)
                    .stripeSessionId(session.getId())
                    .estado(EstadoPago.PENDIENTE)
                    .monto(reserva.getPrecioPagado())
                    .fechaCreacion(LocalDateTime.now())
                    .build();
            pagoRepository.save(pago);

            return session.getUrl();
        } catch (StripeException e) {
            throw new ReglaDeNegocioException("No se pudo iniciar el pago: " + e.getMessage());
        }
    }

    public void confirmarPago(Event event) {
        Session session = (Session) event.getDataObjectDeserializer()
                .getObject()
                .orElseThrow(() -> new ReglaDeNegocioException("No se pudo leer el evento de Stripe"));

        Pago pago = pagoRepository.findByStripeSessionId(session.getId())
                .orElseThrow(() -> new RecursoNoEncontradoException("Pago no encontrado para la sesión: " + session.getId()));

        if (pago.getEstado() == EstadoPago.APROBADO) {
            return;
        }

        pago.setEstado(EstadoPago.APROBADO);
        pago.setFechaActualizacion(LocalDateTime.now());
        pagoRepository.save(pago);

        Reserva reserva = pago.getReserva();
        reserva.setEstado(EstadoReserva.CONFIRMADA);
        reservaRepository.save(reserva);
        reservaService.evitarCacheReserva(reserva.getId());

        emailService.enviarConfirmacionReserva(
                reserva.getUsuario().getNombre(),
                reserva.getUsuario().getEmail(),
                reserva.getVuelo().getOrigen(),
                reserva.getVuelo().getDestino(),
                reserva.getVuelo().getFechaSalida(),
                reserva.getPrecioPagado()
        );
    }
}
