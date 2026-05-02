package com.logistics.tracking.controller;

import com.logistics.tracking.dto.request.CreateWebhookRequest;
import com.logistics.tracking.dto.response.WebhookResponse;
import com.logistics.tracking.model.User;
import com.logistics.tracking.service.WebhookService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/webhooks")
@RequiredArgsConstructor
@Tag(name = "Webhooks", description = "Register and manage webhook subscriptions")
@SecurityRequirement(name = "bearerAuth")
public class WebhookController {

    private final WebhookService webhookService;

    @PostMapping
    @Operation(summary = "Register a new webhook")
    public ResponseEntity<WebhookResponse> register(
            @Valid @RequestBody CreateWebhookRequest request,
            @AuthenticationPrincipal User currentUser) {

        WebhookResponse response = webhookService.register(request, currentUser.getCompany().getId());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @DeleteMapping("/{webhookId}")
    @Operation(summary = "Unregister a webhook")
    public ResponseEntity<Void> unregister(
            @PathVariable UUID webhookId,
            @AuthenticationPrincipal User currentUser) {

        webhookService.unregister(webhookId, currentUser.getCompany().getId());
        return ResponseEntity.noContent().build();
    }

    @GetMapping
    @Operation(summary = "List all webhooks for current company")
    public ResponseEntity<List<WebhookResponse>> list(@AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(webhookService.listWebhooks(currentUser.getCompany().getId()));
    }
}
