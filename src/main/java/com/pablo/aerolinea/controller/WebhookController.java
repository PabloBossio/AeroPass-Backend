package com.pablo.aerolinea.controller;

import com.pablo.aerolinea.service.PagoService;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.model.Event;
import com.stripe.net.Webhook;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/webhooks")
public class WebhookController {

    private final PagoService pagoService;

    @Value("${stripe.webhook.secret}")
    private String webhookSecret;

    public WebhookController(PagoService pagoService) {
        this.pagoService = pagoService;
    }

    @PostMapping("/stripe")
    public ResponseEntity<String> recibireventoStripe(
            @RequestBody String payload,
            @RequestHeader("Stripe-Signature") String sigHeader) {

        Event event;
        try {
            event = Webhook.constructEvent(payload, sigHeader, webhookSecret);
        } catch (SignatureVerificationException e) {
            return ResponseEntity.badRequest().body("Firma inválida.");
        }

        if ("checkout.session.completed".equals(event.getType())) {
            pagoService.confirmarPago(event);
        }

        return ResponseEntity.ok("OK");
    }
}
