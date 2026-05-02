package com.logistics.tracking.service;

import com.logistics.tracking.dto.request.CreateEventRequest;
import com.logistics.tracking.dto.response.ShipmentEventResponse;
import com.logistics.tracking.dto.response.ShipmentStatusResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface ShipmentEventService {
    ShipmentEventResponse createEvent(String shipmentId, CreateEventRequest request, UUID companyId);
    Page<ShipmentEventResponse> getEvents(String shipmentId, UUID companyId, Pageable pageable);
    ShipmentStatusResponse getStatus(String shipmentId, UUID companyId);
}
