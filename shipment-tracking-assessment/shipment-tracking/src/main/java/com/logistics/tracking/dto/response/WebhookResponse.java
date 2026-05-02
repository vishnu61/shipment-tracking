package com.logistics.tracking.dto.response;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Data @Builder
public class WebhookResponse {
    private UUID id;
    private String url;
    private List<String> eventTypes;
    private boolean active;
    private Instant createdAt;
}
