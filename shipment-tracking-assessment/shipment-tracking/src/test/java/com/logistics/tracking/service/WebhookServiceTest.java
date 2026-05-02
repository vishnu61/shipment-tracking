package com.logistics.tracking.service;

import com.logistics.tracking.dto.request.CreateWebhookRequest;
import com.logistics.tracking.dto.response.WebhookResponse;
import com.logistics.tracking.exception.ResourceNotFoundException;
import com.logistics.tracking.model.Webhook;
import com.logistics.tracking.repository.WebhookRepository;
import com.logistics.tracking.service.impl.WebhookServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WebhookServiceTest {

    @Mock WebhookRepository webhookRepository;
    @InjectMocks WebhookServiceImpl service;

    private UUID companyId;
    private UUID webhookId;

    @BeforeEach
    void setUp() {
        companyId = UUID.randomUUID();
        webhookId = UUID.randomUUID();
    }

    @Test
    @DisplayName("register - creates webhook with generated secret if none provided")
    void register_generatesSecret() {
        CreateWebhookRequest req = new CreateWebhookRequest();
        req.setUrl("https://example.com/webhook");
        req.setEventTypes(List.of("IN_TRANSIT", "DELIVERED"));

        Webhook saved = Webhook.builder().id(webhookId).companyId(companyId)
                .url(req.getUrl()).secret("generated-secret")
                .eventTypes(req.getEventTypes()).active(true).createdAt(Instant.now()).build();

        when(webhookRepository.save(any())).thenReturn(saved);

        WebhookResponse result = service.register(req, companyId);

        assertThat(result.getId()).isEqualTo(webhookId);
        assertThat(result.getUrl()).isEqualTo("https://example.com/webhook");
        assertThat(result.isActive()).isTrue();
        verify(webhookRepository).save(any());
    }

    @Test
    @DisplayName("unregister - deactivates webhook")
    void unregister_success() {
        Webhook webhook = Webhook.builder().id(webhookId).companyId(companyId)
                .url("https://example.com/wh").secret("s").active(true).build();

        when(webhookRepository.findByIdAndCompanyId(webhookId, companyId))
                .thenReturn(Optional.of(webhook));
        when(webhookRepository.save(any())).thenReturn(webhook);

        service.unregister(webhookId, companyId);

        assertThat(webhook.isActive()).isFalse();
        verify(webhookRepository).save(webhook);
    }

    @Test
    @DisplayName("unregister - throws 404 for wrong company")
    void unregister_wrongCompany() {
        when(webhookRepository.findByIdAndCompanyId(webhookId, companyId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.unregister(webhookId, companyId))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("listWebhooks - returns only active webhooks for company")
    void listWebhooks_success() {
        Webhook w1 = Webhook.builder().id(UUID.randomUUID()).companyId(companyId)
                .url("https://a.com").secret("s").active(true)
                .eventTypes(List.of()).createdAt(Instant.now()).build();
        Webhook w2 = Webhook.builder().id(UUID.randomUUID()).companyId(companyId)
                .url("https://b.com").secret("s").active(true)
                .eventTypes(List.of("DELIVERED")).createdAt(Instant.now()).build();

        when(webhookRepository.findByCompanyIdAndActiveTrue(companyId)).thenReturn(List.of(w1, w2));

        List<WebhookResponse> result = service.listWebhooks(companyId);

        assertThat(result).hasSize(2);
        assertThat(result).allMatch(WebhookResponse::isActive);
    }
}
