package com.sturdywaffle.web.dto;

import java.time.Instant;

public record DecisionResponse(
        String status,
        Instant decidedAt,
        String note
) {}
