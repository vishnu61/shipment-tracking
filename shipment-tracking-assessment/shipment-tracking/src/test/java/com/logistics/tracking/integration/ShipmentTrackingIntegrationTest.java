package com.logistics.tracking.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.logistics.tracking.dto.request.CreateEventRequest;
import com.logistics.tracking.dto.request.LoginRequest;
import com.logistics.tracking.model.*;
import com.logistics.tracking.repository.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.util.HashMap;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@Testcontainers
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ShipmentTrackingIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine")
            .withDatabaseName("tracking_test")
            .withUsername("test")
            .withPassword("test");

    @DynamicPropertySource
    static void configureDataSource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
        registry.add("spring.flyway.enabled", () -> "false");
    }

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired UserRepository userRepository;
    @Autowired CompanyRepository companyRepository;
    @Autowired PasswordEncoder passwordEncoder;

    private static String accessToken;
    private static String shipmentId = "SHP-INT-001";
    private static UUID companyId;

    @BeforeEach
    void setUp() {
        if (companyId != null) return; // only run once

        Company company = companyRepository.save(Company.builder()
                .name("Integration Test Co")
                .apiKeyHash(UUID.randomUUID().toString())
                .active(true).build());
        companyId = company.getId();

        userRepository.save(User.builder()
                .company(company)
                .email("integration@test.com")
                .passwordHash(passwordEncoder.encode("password123"))
                .role("USER").active(true).build());
    }

    @Test
    @Order(1)
    @DisplayName("Login - obtains JWT token")
    void login_success() throws Exception {
        LoginRequest req = new LoginRequest();
        req.setEmail("integration@test.com");
        req.setPassword("password123");

        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").exists())
                .andReturn();

        String body = result.getResponse().getContentAsString();
        accessToken = objectMapper.readTree(body).get("accessToken").asText();
        assertThat(accessToken).isNotBlank();
    }

    @Test
    @Order(2)
    @DisplayName("POST /events - records first event and auto-creates shipment")
    void createFirstEvent() throws Exception {
        CreateEventRequest req = new CreateEventRequest();
        req.setEventType(EventType.PICKED_UP);
        req.setTimestamp(Instant.now());

        mockMvc.perform(post("/api/v1/shipments/{id}/events", shipmentId)
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.eventType").value("PICKED_UP"))
                .andExpect(jsonPath("$.shipmentId").value(shipmentId))
                .andExpect(jsonPath("$.eventId").exists());
    }

    @Test
    @Order(3)
    @DisplayName("POST /events - records second event IN_TRANSIT")
    void createSecondEvent() throws Exception {
        CreateEventRequest req = new CreateEventRequest();
        req.setEventType(EventType.IN_TRANSIT);
        req.setTimestamp(Instant.now());
        var loc = new CreateEventRequest.LocationRequest();
        loc.setLatitude(40.7128); loc.setLongitude(-74.006); loc.setAddress("New York, NY");
        req.setLocation(loc);

        mockMvc.perform(post("/api/v1/shipments/{id}/events", shipmentId)
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated());
    }

    @Test
    @Order(4)
    @DisplayName("GET /events - returns all events paginated")
    void getEvents() throws Exception {
        mockMvc.perform(get("/api/v1/shipments/{id}/events?page=0&size=10", shipmentId)
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.totalElements").value(2));
    }

    @Test
    @Order(5)
    @DisplayName("GET /status - returns latest status")
    void getStatus() throws Exception {
        mockMvc.perform(get("/api/v1/shipments/{id}/status", shipmentId)
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentStatus").value("IN_TRANSIT"))
                .andExpect(jsonPath("$.currentLocation.address").value("New York, NY"));
    }

    @Test
    @Order(6)
    @DisplayName("Multi-tenant isolation - cannot access another company's shipment")
    void tenantIsolation() throws Exception {
        // Create second company and user
        Company company2 = companyRepository.save(Company.builder()
                .name("Other Co").apiKeyHash(UUID.randomUUID().toString()).active(true).build());
        userRepository.save(User.builder()
                .company(company2).email("other@co.com")
                .passwordHash(passwordEncoder.encode("password123"))
                .role("USER").active(true).build());

        // Login as company 2
        LoginRequest login2 = new LoginRequest();
        login2.setEmail("other@co.com"); login2.setPassword("password123");
        MvcResult r = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(login2)))
                .andExpect(status().isOk()).andReturn();
        String token2 = objectMapper.readTree(r.getResponse().getContentAsString()).get("accessToken").asText();

        // Company 2 should NOT see company 1's shipment
        mockMvc.perform(get("/api/v1/shipments/{id}/status", shipmentId)
                        .header("Authorization", "Bearer " + token2))
                .andExpect(status().isNotFound());
    }

    @Test
    @Order(7)
    @DisplayName("Security - unauthenticated requests are rejected")
    void unauthenticated_rejected() throws Exception {
        mockMvc.perform(get("/api/v1/shipments/{id}/status", shipmentId))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @Order(8)
    @DisplayName("Validation - missing eventType returns 400")
    void validation_missingField() throws Exception {
        mockMvc.perform(post("/api/v1/shipments/{id}/events", shipmentId)
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"timestamp\":\"2026-04-17T14:30:00Z\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.eventType").exists());
    }
}
