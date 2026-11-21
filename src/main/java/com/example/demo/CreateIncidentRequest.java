package com.example.demo;

import jakarta.validation.constraints.*;

public record CreateIncidentRequest(
        @NotBlank String description,
        @NotNull Long serviceId,
        Long categoryId,
        @Min(1) @Max(5) int impact,
        @Min(1) @Max(5) int urgency,
        @NotNull @Min(1) Integer affectedUsers) {
    public CreateIncidentRequest(String description, Long serviceId, Long categoryId, int impact, int urgency) {
        this(description, serviceId, categoryId, impact, urgency, 1);
    }

    public CreateIncidentRequest {
        affectedUsers = affectedUsers == null ? 1 : Math.max(1, affectedUsers);
    }
}
