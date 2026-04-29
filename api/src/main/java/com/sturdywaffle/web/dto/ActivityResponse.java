package com.sturdywaffle.web.dto;

import java.time.Instant;
import java.util.UUID;

public record ActivityResponse(
        UUID id,
        String event,
        UUID entityId,
        String payload,
        Instant createdAt) {
}
