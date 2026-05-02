package com.logistics.tracking.service.impl;

import com.logistics.tracking.dto.request.CreateEventRequest;
import com.logistics.tracking.dto.response.ShipmentEventResponse;
import com.logistics.tracking.dto.response.ShipmentStatusResponse;
import com.logistics.tracking.exception.ResourceNotFoundException;
import com.logistics.tracking.model.Shipment;
import com.logistics.tracking.model.ShipmentEvent;
import com.logistics.tracking.repository.ShipmentEventRepository;
import com.logistics.tracking.repository.ShipmentRepository;
import com.logistics.tracking.service.ShipmentEventService;
import com.logistics.tracking.service.WebhookDispatchService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class ShipmentEventServiceImpl implements ShipmentEventService {

    private final ShipmentEventRepository eventRepository;
    private final ShipmentRepository shipmentRepository;
    private final WebhookDispatchService webhookDispatchService;

    @Override
    @Transactional
    public ShipmentEventResponse createEvent(String shipmentId, CreateEventRequest req, UUID companyId) {
        // Upsert shipment (create if first event for this shipment)
        Shipment shipment = shipmentRepository.findByShipmentIdAndCompanyId(shipmentId, companyId)
                .orElseGet(() -> createShipment(shipmentId, companyId, req));

        // Build location map from request
        Map<String, Object> locationMap = null;
        if (req.getLocation() != null) {
            locationMap = new HashMap<>();
            locationMap.put("latitude",  req.getLocation().getLatitude());
            locationMap.put("longitude", req.getLocation().getLongitude());
            locationMap.put("address",   req.getLocation().getAddress());
        }

        // Persist event (immutable)
        ShipmentEvent event = ShipmentEvent.builder()
                .shipmentId(shipmentId)
                .companyId(companyId)
                .eventType(req.getEventType())
                .eventTimestamp(req.getTimestamp())
                .location(locationMap)
                .metadata(req.getMetadata())
                .build();
        event = eventRepository.save(event);

        // Update shipment current status
        shipment.setCurrentStatus(req.getEventType());
        shipment.setCurrentLocation(locationMap);
        shipmentRepository.save(shipment);

        ShipmentEventResponse response = toResponse(event);

        // Async webhook dispatch — does not block the HTTP response
        webhookDispatchService.dispatch(companyId, event);

        log.info("Created event {} for shipment {} (company {})", event.getEventId(), shipmentId, companyId);
        return response;
    }

    @Override
    @Transactional(readOnly = true)
    public Page<ShipmentEventResponse> getEvents(String shipmentId, UUID companyId, Pageable pageable) {
        if (!eventRepository.existsByShipmentIdAndCompanyId(shipmentId, companyId)) {
            throw new ResourceNotFoundException("Shipment not found: " + shipmentId);
        }
        return eventRepository
                .findByShipmentIdAndCompanyIdOrderByEventTimestampDesc(shipmentId, companyId, pageable)
                .map(this::toResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public ShipmentStatusResponse getStatus(String shipmentId, UUID companyId) {
        Shipment shipment = shipmentRepository.findByShipmentIdAndCompanyId(shipmentId, companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Shipment not found: " + shipmentId));

        return ShipmentStatusResponse.builder()
                .shipmentId(shipment.getShipmentId())
                .currentStatus(shipment.getCurrentStatus())
                .currentLocation(shipment.getCurrentLocation())
                .eta(shipment.getEta())
                .carrier(shipment.getCarrier())
                .lastUpdated(shipment.getUpdatedAt())
                .origin(shipment.getOrigin())
                .destination(shipment.getDestination())
                .build();
    }

    // ---------- helpers ----------

    private Shipment createShipment(String shipmentId, UUID companyId, CreateEventRequest req) {
        String carrier = req.getMetadata() != null
                ? (String) req.getMetadata().get("carrier") : null;

        Map<String, Object> empty = new HashMap<>();
        return shipmentRepository.save(Shipment.builder()
                .shipmentId(shipmentId)
                .companyId(companyId)
                .carrier(carrier)
                .origin(empty)
                .destination(empty)
                .build());
    }

    private ShipmentEventResponse toResponse(ShipmentEvent e) {
        return ShipmentEventResponse.builder()
                .eventId(e.getEventId())
                .shipmentId(e.getShipmentId())
                .eventType(e.getEventType())
                .timestamp(e.getEventTimestamp())
                .location(e.getLocation())
                .metadata(e.getMetadata())
                .createdAt(e.getCreatedAt())
                .build();
    }
}
