package com.logistics.tracking.dto.response;

import com.logistics.tracking.model.EventType;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Data @Builder
public class ShipmentEventResponse {
    private String eventId;
    private String shipmentId;
    private EventType eventType;
    private Instant timestamp;
    private Map<String, Object> location;
    private Map<String, Object> metadata;
    private Instant createdAt;
}
