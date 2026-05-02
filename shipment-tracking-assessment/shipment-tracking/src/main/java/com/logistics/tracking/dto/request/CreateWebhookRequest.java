package com.logistics.tracking.dto.request;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Pattern;
import javax.validation.constraints.Size;
import java.util.List;

@Data
public class CreateWebhookRequest {

    @NotBlank(message = "url is required")
    @Pattern(regexp = "^https?://.*", message = "url must be a valid HTTP/HTTPS URL")
    @Size(max = 2048)
    private String url;

    @Size(max = 255)
    private String secret;

    private List<String> eventTypes;
}
