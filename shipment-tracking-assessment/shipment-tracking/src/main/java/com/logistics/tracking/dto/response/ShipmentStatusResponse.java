package com.logistics.tracking.dto.response;

import com.logistics.tracking.model.EventType;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.Map;

@Data @Builder
public class ShipmentStatusResponse {
    private String shipmentId;
    private EventType currentStatus;
    private Map<String, Object> currentLocation;
    private Instant eta;
    private String carrier;
    private Instant lastUpdated;
    private Map<String, Object> origin;
    private Map<String, Object> destination;
}
