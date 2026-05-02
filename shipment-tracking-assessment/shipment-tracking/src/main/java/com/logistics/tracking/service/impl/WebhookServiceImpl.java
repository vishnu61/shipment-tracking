package com.logistics.tracking.service.impl;

import com.logistics.tracking.dto.request.CreateWebhookRequest;
import com.logistics.tracking.dto.response.WebhookResponse;
import com.logistics.tracking.exception.ResourceNotFoundException;
import com.logistics.tracking.model.Webhook;
import com.logistics.tracking.repository.WebhookRepository;
import com.logistics.tracking.service.WebhookService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class WebhookServiceImpl implements WebhookService {

    private final WebhookRepository webhookRepository;

    @Override
    @Transactional
    public WebhookResponse register(CreateWebhookRequest req, UUID companyId) {
        String secret = req.getSecret() != null ? req.getSecret() : UUID.randomUUID().toString();
        Webhook webhook = Webhook.builder()
                .companyId(companyId)
                .url(req.getUrl())
                .secret(secret)
                .eventTypes(req.getEventTypes() != null ? req.getEventTypes() : List.of())
                .active(true)
                .build();
        webhook = webhookRepository.save(webhook);
        return toResponse(webhook);
    }

    @Override
    @Transactional
    public void unregister(UUID webhookId, UUID companyId) {
        Webhook webhook = webhookRepository.findByIdAndCompanyId(webhookId, companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Webhook not found: " + webhookId));
        webhook.setActive(false);
        webhookRepository.save(webhook);
    }

    @Override
    @Transactional(readOnly = true)
    public List<WebhookResponse> listWebhooks(UUID companyId) {
        return webhookRepository.findByCompanyIdAndActiveTrue(companyId)
                .stream().map(this::toResponse).collect(Collectors.toList());
    }

    private WebhookResponse toResponse(Webhook w) {
        return WebhookResponse.builder()
                .id(w.getId())
                .url(w.getUrl())
                .eventTypes(w.getEventTypes())
                .active(w.isActive())
                .createdAt(w.getCreatedAt())
                .build();
    }
}
