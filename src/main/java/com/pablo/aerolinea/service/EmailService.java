package com.pablo.aerolinea.service;


import com.pablo.aerolinea.model.Reserva;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import org.slf4j.Logger;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Service
public class EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailService.class);

    private static final DateTimeFormatter FORMATO_FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private final JavaMailSender mailSender;

    @Value("${app.frontend.url}")
    private String frontendUrl;

    public EmailService(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    @Async
    public void enviarConfirmacionReserva(String nombreUsuario, String email, String origen, String destino,
                                          LocalDateTime fechasalida, BigDecimal precioPagado) {

        String cuerpo = """
                Hola %s, 
                
                Tu reserva fue confirmada con éxito.
                
                Vuelo: %s -> %s
                Fecha Salida: %s
                Precio pagado: %s
                
                Gracias por volar con AeroPass! 
                """.formatted(nombreUsuario, origen, destino, fechasalida.format(FORMATO_FECHA), precioPagado);
        enviar(email, "Confirmacion de tu reserva en AeroPass", cuerpo);
    }

    @Async
    public void enviarCancelacionReserva(String nombreUsuario, String email, String origen, String destino,
                                         LocalDateTime fechaSalida) {
        String cuerpo = """
                Hola %s, 
                
                Tu reserva fue cancelada.
                
                Vuelo: %s -> %s
                Fecha Salida: %s
                
                Si fue un error, podes volver a reservar desde la app. 
                """.formatted(nombreUsuario, origen, destino, fechaSalida.format(FORMATO_FECHA));
        enviar(email, "Tu reserva en AeroPass fue cancelada", cuerpo);
    }

    private void enviar(String destinatario, String asunto, String cuerpo) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setTo(destinatario);
            message.setSubject(asunto);
            message.setText(cuerpo);
            mailSender.send(message);
        } catch (Exception e) {
            log.warn("No se pudo enviar el email a {}: {}", destinatario, e.getMessage());
        }
    }

    @Async
    public void enviarAvisoVueloDemorado(String nombreUsuario, String email, String origen, String destino,
                                         LocalDateTime fechaSalida) {
        String cuerpo = """
                Hola %s, 
                
                Tu vuelo sufrió una demora.
                
                Vuelo: %s -> %s
                Fecha Salida: %s
                
                Te recomendamos estar atento a nuevas actuñizaciones antes de dirigirte al aeropuerto.
                """.formatted(nombreUsuario, origen, destino, fechaSalida.format(FORMATO_FECHA));
        enviar(email, "Tu vuelo en AeroPass sufrió una demora", cuerpo);
    }

    @Async
    public void enviarAvisoVueloCancelado(String nombreUsuario, String email, String origen, String destino,
                                          LocalDateTime fechaSalida) {
        String cuerpo = """
                Hola %s,
                
                Lamentamos informarte que tu vuelo fue cancelado.
                
                Vuelo: %s -> %s
                Fecha Salida: %s
                
                Podés contactarnos para coordinar un reembolso o una reprogramación.
                """.formatted(nombreUsuario, origen, destino, fechaSalida.format(FORMATO_FECHA));
        enviar(email, "Tu vuelo en AeroPass fue cancelado", cuerpo);
    }

    @Async
    public void enviarAvisoReservaPendientePago(String nombreUsuario, String email, String origen, String destino,
                                                LocalDateTime fechaSalida, BigDecimal precioPagado) {
        String cuerpo = """
                Hola %s, 
                
                Tu reserva fue creada, pero todavía no está confirmada.
                
                Vuelo: %s -> %s
                Fecha Salida: %s
                Monto a Pagar: %s
                
                Para asegurar tu lugar, completá el pago desde la sección "Mis reservas": %s/mis-reservas
                """.formatted(nombreUsuario, origen, destino, fechaSalida.format(FORMATO_FECHA), precioPagado, frontendUrl);
        enviar(email, "Tu reserva en AeroPass está pendiente de pago", cuerpo);
    }
}
