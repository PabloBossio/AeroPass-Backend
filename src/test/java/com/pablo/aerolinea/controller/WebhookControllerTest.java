package com.pablo.aerolinea.controller;

import com.pablo.aerolinea.config.SecurityConfig;
import com.pablo.aerolinea.security.JwtUtil;
import com.pablo.aerolinea.security.UsuarioDetailsService;
import com.pablo.aerolinea.service.PagoService;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.model.Event;
import com.stripe.net.Webhook;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(WebhookController.class)
@Import(SecurityConfig.class)
class WebhookControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PagoService pagoService;

    @MockitoBean
    private JwtUtil jwtUtil;

    @MockitoBean
    private UsuarioDetailsService usuarioDetailsService;

    @Test
    void recibirEventoStripe_firmaValida_devuelve200() throws Exception {
        Event eventMock = mock(Event.class);
        when(eventMock.getType()).thenReturn("checkout.session.completed");

        try (MockedStatic<Webhook> webhookEstatico = mockStatic(Webhook.class)) {
            webhookEstatico.when(() -> Webhook.constructEvent(anyString(), anyString(), anyString()))
                    .thenReturn(eventMock);

            mockMvc.perform(post("/api/webhooks/stripe")
                            .contentType(MediaType.APPLICATION_JSON)
                            .header("Stripe-Signature", "t=123,v1=abc")
                            .content("{\"id\":\"evt_test\"}"))
                    .andExpect(status().isOk())
                    .andExpect(content().string("OK"));
        }

        verify(pagoService, times(1)).confirmarPago(eventMock);
    }

    @Test
    void recibirEventoStripe_firmaInvalida_devuelve400() throws Exception {
        try (MockedStatic<Webhook> webhookEstatico = mockStatic(Webhook.class)) {
            webhookEstatico.when(() -> Webhook.constructEvent(anyString(), anyString(), anyString()))
                    .thenThrow(new SignatureVerificationException("Firma inválida", "t=123,v1=abc"));

            mockMvc.perform(post("/api/webhooks/stripe")
                            .contentType(MediaType.APPLICATION_JSON)
                            .header("Stripe-Signature", "t=123,v1=abc")
                            .content("{\"id\":\"evt_test\"}"))
                    .andExpect(status().isBadRequest());
        }

        verifyNoInteractions(pagoService);
    }

    @Test
    void recibirEventoStripe_eventoNoRelevante_noLlamaAlServicio() throws Exception {
        Event eventMock = mock(Event.class);
        when(eventMock.getType()).thenReturn("payment_intent.created");

        try (MockedStatic<Webhook> webhookEstatico = mockStatic(Webhook.class)) {
            webhookEstatico.when(() -> Webhook.constructEvent(anyString(), anyString(), anyString()))
                    .thenReturn(eventMock);

            mockMvc.perform(post("/api/webhooks/stripe")
                            .contentType(MediaType.APPLICATION_JSON)
                            .header("Stripe-Signature", "t=123,v1=abc")
                            .content("{\"id\":\"evt_test\"}"))
                    .andExpect(status().isOk());
        }

        verifyNoInteractions(pagoService);
    }
}