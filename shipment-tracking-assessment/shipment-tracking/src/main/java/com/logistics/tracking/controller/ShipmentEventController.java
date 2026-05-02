package com.logistics.tracking.controller;

import com.logistics.tracking.dto.request.CreateEventRequest;
import com.logistics.tracking.dto.response.ShipmentEventResponse;
import com.logistics.tracking.dto.response.ShipmentStatusResponse;
import com.logistics.tracking.exception.RateLimitExceededException;
import com.logistics.tracking.model.User;
import com.logistics.tracking.service.RateLimitService;
import com.logistics.tracking.service.ShipmentEventService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/shipments")
@RequiredArgsConstructor
@Tag(name = "Shipment Events", description = "Track and retrieve shipment events")
@SecurityRequirement(name = "bearerAuth")
public class ShipmentEventController {

    private final ShipmentEventService shipmentEventService;
    private final RateLimitService rateLimitService;

    @PostMapping("/{shipmentId}/events")
    @Operation(summary = "Record a new shipment event")
    public ResponseEntity<ShipmentEventResponse> createEvent(
            @PathVariable String shipmentId,
            @Valid @RequestBody CreateEventRequest request,
            @AuthenticationPrincipal User currentUser) {

        UUID companyId = currentUser.getCompany().getId();
        if (!rateLimitService.isAllowed(companyId)) {
            throw new RateLimitExceededException("Rate limit exceeded. Max 1000 requests/minute.");
        }

        ShipmentEventResponse response = shipmentEventService.createEvent(shipmentId, request, companyId);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{shipmentId}/events")
    @Operation(summary = "Retrieve all events for a shipment (paginated)")
    public ResponseEntity<Page<ShipmentEventResponse>> getEvents(
            @PathVariable String shipmentId,
            @PageableDefault(size = 20, sort = "eventTimestamp") Pageable pageable,
            @AuthenticationPrincipal User currentUser) {

        UUID companyId = currentUser.getCompany().getId();
        return ResponseEntity.ok(shipmentEventService.getEvents(shipmentId, companyId, pageable));
    }

    @GetMapping("/{shipmentId}/status")
    @Operation(summary = "Get current status of a shipment")
    public ResponseEntity<ShipmentStatusResponse> getStatus(
            @PathVariable String shipmentId,
            @AuthenticationPrincipal User currentUser) {

        UUID companyId = currentUser.getCompany().getId();
        return ResponseEntity.ok(shipmentEventService.getStatus(shipmentId, companyId));
    }
}
