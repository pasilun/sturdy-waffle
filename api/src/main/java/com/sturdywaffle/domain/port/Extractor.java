package com.sturdywaffle.domain.port;

import com.sturdywaffle.domain.model.ExtractedInvoice;

public interface Extractor {
    ExtractedInvoice extract(byte[] pdf);
    default String modelId() { return "unknown"; }
    default String promptVersion() { return "v1"; }
}
