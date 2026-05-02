package com.logistics.tracking.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.logistics.tracking.model.ShipmentEvent;
import com.logistics.tracking.model.Webhook;
import com.logistics.tracking.repository.WebhookRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class WebhookDispatchService {

    private final WebhookRepository webhookRepository;
    private final WebClient.Builder webClientBuilder;
    private final ObjectMapper objectMapper;

    @Async
    public void dispatch(UUID companyId, ShipmentEvent event) {
        List<Webhook> webhooks = webhookRepository.findByCompanyIdAndActiveTrue(companyId);
        if (webhooks.isEmpty()) return;

        Map<String, Object> payload = buildPayload(event);

        for (Webhook webhook : webhooks) {
            if (!shouldDeliver(webhook, event.getEventType().name())) continue;
            deliverWithRetry(webhook, payload, 1);
        }
    }

    private boolean shouldDeliver(Webhook webhook, String eventType) {
        List<String> types = webhook.getEventTypes();
        return types == null || types.isEmpty() || types.contains(eventType);
    }

    private void deliverWithRetry(Webhook webhook, Map<String, Object> payload, int attempt) {
        try {
            String body = objectMapper.writeValueAsString(payload);
            String signature = computeHmacSha256(webhook.getSecret(), body);

            webClientBuilder.build()
                    .post()
                    .uri(webhook.getUrl())
                    .header("Content-Type", "application/json")
                    .header("X-Signature-SHA256", "sha256=" + signature)
                    .header("X-Webhook-Attempt", String.valueOf(attempt))
                    .bodyValue(body)
                    .retrieve()
                    .toBodilessEntity()
                    .timeout(Duration.ofSeconds(10))
                    .doOnSuccess(r -> log.info("Webhook {} delivered (attempt {}), status {}",
                            webhook.getId(), attempt, r.getStatusCodeValue()))
                    .doOnError(e -> {
                        log.warn("Webhook {} delivery failed (attempt {}): {}", webhook.getId(), attempt, e.getMessage());
                        if (attempt < 3) {
                            deliverWithRetry(webhook, payload, attempt + 1);
                        }
                    })
                    .onErrorResume(e -> Mono.empty())
                    .subscribe();
        } catch (Exception e) {
            log.error("Error building webhook payload for {}: {}", webhook.getId(), e.getMessage());
        }
    }

    private Map<String, Object> buildPayload(ShipmentEvent event) {
        Map<String, Object> p = new HashMap<>();
        p.put("eventId", event.getEventId());
        p.put("shipmentId", event.getShipmentId());
        p.put("eventType", event.getEventType().name());
        p.put("timestamp", event.getEventTimestamp().toString());
        p.put("location", event.getLocation());
        p.put("metadata", event.getMetadata());
        return p;
    }

    private String computeHmacSha256(String secret, String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return Base64.getEncoder().encodeToString(mac.doFinal(data.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new RuntimeException("Failed to compute HMAC", e);
        }
    }
}
