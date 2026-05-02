package com.logistics.tracking.service;

import com.logistics.tracking.dto.request.CreateWebhookRequest;
import com.logistics.tracking.dto.response.WebhookResponse;
import com.logistics.tracking.model.ShipmentEvent;
import com.logistics.tracking.model.Webhook;

import java.util.List;
import java.util.UUID;

public interface WebhookService {
    WebhookResponse register(CreateWebhookRequest request, UUID companyId);
    void unregister(UUID webhookId, UUID companyId);
    List<WebhookResponse> listWebhooks(UUID companyId);
}
