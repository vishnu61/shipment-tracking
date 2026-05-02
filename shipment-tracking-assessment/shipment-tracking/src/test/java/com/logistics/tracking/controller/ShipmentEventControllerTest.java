package com.logistics.tracking.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.logistics.tracking.dto.request.CreateEventRequest;
import com.logistics.tracking.dto.response.ShipmentEventResponse;
import com.logistics.tracking.dto.response.ShipmentStatusResponse;
import com.logistics.tracking.exception.ResourceNotFoundException;
import com.logistics.tracking.model.*;
import com.logistics.tracking.security.JwtAuthenticationFilter;
import com.logistics.tracking.service.RateLimitService;
import com.logistics.tracking.service.ShipmentEventService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = ShipmentEventController.class,
        excludeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = JwtAuthenticationFilter.class))
class ShipmentEventControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    @MockBean ShipmentEventService shipmentEventService;
    @MockBean RateLimitService rateLimitService;

    private User mockUser;
    private UUID companyId;
    private String shipmentId = "SHP-12345";

    @BeforeEach
    void setUp() {
        companyId = UUID.randomUUID();
        Company company = Company.builder().id(companyId).name("Acme").build();
        mockUser = User.builder()
                .id(UUID.randomUUID()).email("user@acme.com")
                .passwordHash("hash").role("USER").company(company).active(true).build();
    }

    @Test
    @DisplayName("POST /events - 201 on valid request")
    void createEvent_returns201() throws Exception {
        CreateEventRequest req = new CreateEventRequest();
        req.setEventType(EventType.IN_TRANSIT);
        req.setTimestamp(Instant.now());

        ShipmentEventResponse resp = ShipmentEventResponse.builder()
                .eventId("EVT-abc").shipmentId(shipmentId)
                .eventType(EventType.IN_TRANSIT).timestamp(Instant.now()).build();

        when(rateLimitService.isAllowed(companyId)).thenReturn(true);
        when(shipmentEventService.createEvent(eq(shipmentId), any(), eq(companyId))).thenReturn(resp);

        mockMvc.perform(post("/api/v1/shipments/{id}/events", shipmentId)
                        .with(user(mockUser))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.eventId").value("EVT-abc"))
                .andExpect(jsonPath("$.eventType").value("IN_TRANSIT"));
    }

    @Test
    @DisplayName("POST /events - 429 when rate limit exceeded")
    void createEvent_rateLimitExceeded() throws Exception {
        CreateEventRequest req = new CreateEventRequest();
        req.setEventType(EventType.IN_TRANSIT);
        req.setTimestamp(Instant.now());

        when(rateLimitService.isAllowed(companyId)).thenReturn(false);

        mockMvc.perform(post("/api/v1/shipments/{id}/events", shipmentId)
                        .with(user(mockUser))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isTooManyRequests());
    }

    @Test
    @DisplayName("POST /events - 400 when eventType is missing")
    void createEvent_validationFails() throws Exception {
        mockMvc.perform(post("/api/v1/shipments/{id}/events", shipmentId)
                        .with(user(mockUser))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("GET /events - 200 with paginated results")
    void getEvents_success() throws Exception {
        ShipmentEventResponse resp = ShipmentEventResponse.builder()
                .eventId("EVT-1").shipmentId(shipmentId).eventType(EventType.DELIVERED).build();

        when(shipmentEventService.getEvents(eq(shipmentId), eq(companyId), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(resp)));

        mockMvc.perform(get("/api/v1/shipments/{id}/events", shipmentId)
                        .with(user(mockUser)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].eventId").value("EVT-1"));
    }

    @Test
    @DisplayName("GET /events - 404 when shipment not found")
    void getEvents_notFound() throws Exception {
        when(shipmentEventService.getEvents(eq(shipmentId), eq(companyId), any(Pageable.class)))
                .thenThrow(new ResourceNotFoundException("Shipment not found"));

        mockMvc.perform(get("/api/v1/shipments/{id}/events", shipmentId)
                        .with(user(mockUser)))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("GET /status - 200 with current status")
    void getStatus_success() throws Exception {
        ShipmentStatusResponse resp = ShipmentStatusResponse.builder()
                .shipmentId(shipmentId).currentStatus(EventType.DELIVERED)
                .carrier("FastFreight").build();

        when(shipmentEventService.getStatus(eq(shipmentId), eq(companyId))).thenReturn(resp);

        mockMvc.perform(get("/api/v1/shipments/{id}/status", shipmentId)
                        .with(user(mockUser)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentStatus").value("DELIVERED"))
                .andExpect(jsonPath("$.carrier").value("FastFreight"));
    }

    @Test
    @DisplayName("Unauthenticated request - 401")
    void unauthenticated_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/shipments/{id}/status", shipmentId))
                .andExpect(status().isUnauthorized());
    }
}
