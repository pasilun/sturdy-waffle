package com.sturdywaffle.web.dto;

public record AccountResponse(
        String code,
        String name,
        String type,
        String normalSide) {
}
