package com.logistics.tracking.dto.request;

import com.logistics.tracking.model.EventType;
import lombok.Data;

import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import java.time.Instant;
import java.util.Map;

@Data
public class CreateEventRequest {

    @NotNull(message = "eventType is required")
    private EventType eventType;

    @NotNull(message = "timestamp is required")
    private Instant timestamp;

    @Valid
    private LocationRequest location;

    private Map<String, Object> metadata;

    @Data
    public static class LocationRequest {
        private Double latitude;
        private Double longitude;
        private String address;
    }
}
