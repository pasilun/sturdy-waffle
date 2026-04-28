package com.sturdywaffle.application;

import com.sturdywaffle.domain.model.ExtractedInvoice;
import com.sturdywaffle.domain.model.Posting;

import java.util.List;

public record EvalResult(
        ExtractedInvoice extracted,
        List<Posting> postings,
        long latencyMs
) {}
