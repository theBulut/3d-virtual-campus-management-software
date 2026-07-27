package de.tudarmstadt.campus.admin.system.web.dto;

import java.time.Instant;

public record HealthResponse(String status, Instant timestamp) {

    public static HealthResponse up() {
        return new HealthResponse("UP", Instant.now());
    }
}
