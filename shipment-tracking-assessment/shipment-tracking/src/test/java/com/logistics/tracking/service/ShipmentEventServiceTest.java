package com.logistics.tracking.service;

import com.logistics.tracking.dto.request.CreateEventRequest;
import com.logistics.tracking.dto.response.ShipmentEventResponse;
import com.logistics.tracking.dto.response.ShipmentStatusResponse;
import com.logistics.tracking.exception.ResourceNotFoundException;
import com.logistics.tracking.model.*;
import com.logistics.tracking.repository.ShipmentEventRepository;
import com.logistics.tracking.repository.ShipmentRepository;
import com.logistics.tracking.service.impl.ShipmentEventServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ShipmentEventServiceTest {

    @Mock ShipmentEventRepository eventRepository;
    @Mock ShipmentRepository shipmentRepository;
    @Mock WebhookDispatchService webhookDispatchService;

    @InjectMocks ShipmentEventServiceImpl service;

    private UUID companyId;
    private String shipmentId;

    @BeforeEach
    void setUp() {
        companyId = UUID.randomUUID();
        shipmentId = "SHP-12345";
    }

    @Test
    @DisplayName("createEvent - creates event and updates shipment status")
    void createEvent_success() {
        CreateEventRequest req = new CreateEventRequest();
        req.setEventType(EventType.IN_TRANSIT);
        req.setTimestamp(Instant.now());
        var loc = new CreateEventRequest.LocationRequest();
        loc.setLatitude(40.7128); loc.setLongitude(-74.0060); loc.setAddress("New York, NY");
        req.setLocation(loc);
        req.setMetadata(Map.of("carrier", "FastFreight"));

        Company company = Company.builder().id(companyId).name("Acme").build();
        Shipment shipment = Shipment.builder()
                .id(UUID.randomUUID()).shipmentId(shipmentId).companyId(companyId)
                .origin(new HashMap<>()).destination(new HashMap<>())
                .currentStatus(EventType.CREATED).build();

        ShipmentEvent savedEvent = ShipmentEvent.builder()
                .id(UUID.randomUUID()).eventId("EVT-abc123")
                .shipmentId(shipmentId).companyId(companyId)
                .eventType(EventType.IN_TRANSIT).eventTimestamp(req.getTimestamp())
                .location(Map.of("latitude", 40.7128)).createdAt(Instant.now())
                .build();

        when(shipmentRepository.findByShipmentIdAndCompanyId(shipmentId, companyId))
                .thenReturn(Optional.of(shipment));
        when(eventRepository.save(any())).thenReturn(savedEvent);
        when(shipmentRepository.save(any())).thenReturn(shipment);
        doNothing().when(webhookDispatchService).dispatch(any(), any());

        ShipmentEventResponse result = service.createEvent(shipmentId, req, companyId);

        assertThat(result).isNotNull();
        assertThat(result.getEventId()).isEqualTo("EVT-abc123");
        assertThat(result.getEventType()).isEqualTo(EventType.IN_TRANSIT);
        verify(eventRepository).save(any());
        verify(shipmentRepository).save(shipment);
        verify(webhookDispatchService).dispatch(companyId, savedEvent);
    }

    @Test
    @DisplayName("createEvent - auto-creates shipment when first event received")
    void createEvent_autoCreatesShipment() {
        CreateEventRequest req = new CreateEventRequest();
        req.setEventType(EventType.PICKED_UP);
        req.setTimestamp(Instant.now());

        Shipment newShipment = Shipment.builder()
                .id(UUID.randomUUID()).shipmentId(shipmentId).companyId(companyId)
                .origin(new HashMap<>()).destination(new HashMap<>()).build();
        ShipmentEvent savedEvent = ShipmentEvent.builder()
                .id(UUID.randomUUID()).eventId("EVT-new")
                .shipmentId(shipmentId).companyId(companyId)
                .eventType(EventType.PICKED_UP).eventTimestamp(req.getTimestamp())
                .createdAt(Instant.now()).build();

        when(shipmentRepository.findByShipmentIdAndCompanyId(shipmentId, companyId))
                .thenReturn(Optional.empty());
        when(shipmentRepository.save(any())).thenReturn(newShipment);
        when(eventRepository.save(any())).thenReturn(savedEvent);
        doNothing().when(webhookDispatchService).dispatch(any(), any());

        ShipmentEventResponse result = service.createEvent(shipmentId, req, companyId);

        assertThat(result.getShipmentId()).isEqualTo(shipmentId);
        verify(shipmentRepository, times(2)).save(any()); // create + update
    }

    @Test
    @DisplayName("getEvents - returns paginated events for valid shipment")
    void getEvents_success() {
        ShipmentEvent event = ShipmentEvent.builder()
                .id(UUID.randomUUID()).eventId("EVT-1")
                .shipmentId(shipmentId).companyId(companyId)
                .eventType(EventType.IN_TRANSIT).eventTimestamp(Instant.now())
                .createdAt(Instant.now()).build();

        Pageable pageable = PageRequest.of(0, 20);
        when(eventRepository.existsByShipmentIdAndCompanyId(shipmentId, companyId)).thenReturn(true);
        when(eventRepository.findByShipmentIdAndCompanyIdOrderByEventTimestampDesc(shipmentId, companyId, pageable))
                .thenReturn(new PageImpl<>(List.of(event)));

        Page<ShipmentEventResponse> result = service.getEvents(shipmentId, companyId, pageable);

        assertThat(result.getTotalElements()).isEqualTo(1);
        assertThat(result.getContent().get(0).getEventId()).isEqualTo("EVT-1");
    }

    @Test
    @DisplayName("getEvents - throws 404 when shipment not found")
    void getEvents_notFound() {
        when(eventRepository.existsByShipmentIdAndCompanyId(shipmentId, companyId)).thenReturn(false);

        assertThatThrownBy(() -> service.getEvents(shipmentId, companyId, PageRequest.of(0, 20)))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining(shipmentId);
    }

    @Test
    @DisplayName("getStatus - returns current shipment status")
    void getStatus_success() {
        Shipment shipment = Shipment.builder()
                .id(UUID.randomUUID()).shipmentId(shipmentId).companyId(companyId)
                .currentStatus(EventType.DELIVERED).carrier("FastFreight")
                .origin(Map.of("address", "LA")).destination(Map.of("address", "NY"))
                .updatedAt(Instant.now()).build();

        when(shipmentRepository.findByShipmentIdAndCompanyId(shipmentId, companyId))
                .thenReturn(Optional.of(shipment));

        ShipmentStatusResponse result = service.getStatus(shipmentId, companyId);

        assertThat(result.getCurrentStatus()).isEqualTo(EventType.DELIVERED);
        assertThat(result.getCarrier()).isEqualTo("FastFreight");
    }

    @Test
    @DisplayName("getStatus - throws 404 when shipment not found")
    void getStatus_notFound() {
        when(shipmentRepository.findByShipmentIdAndCompanyId(shipmentId, companyId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getStatus(shipmentId, companyId))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("Multi-tenancy: company A cannot access company B shipments")
    void multiTenancyIsolation() {
        UUID companyB = UUID.randomUUID();
        when(eventRepository.existsByShipmentIdAndCompanyId(shipmentId, companyB)).thenReturn(false);

        assertThatThrownBy(() -> service.getEvents(shipmentId, companyB, PageRequest.of(0, 20)))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(eventRepository, never())
                .findByShipmentIdAndCompanyIdOrderByEventTimestampDesc(any(), eq(companyId), any());
    }
}
