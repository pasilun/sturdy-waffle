package com.sturdywaffle.web.dto;

public record DecisionResponse(
        String status,
        String decidedAt,
        String note
) {}
