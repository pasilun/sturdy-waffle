package com.sturdywaffle.web.dto;

import com.sturdywaffle.domain.model.DecisionStatus;

public record DecisionRequest(DecisionStatus status, String note) {}
