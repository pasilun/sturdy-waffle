package com.sturdywaffle.domain.port;

import com.sturdywaffle.domain.model.ExtractedInvoice;

public interface Extractor {
    ExtractedInvoice extract(byte[] pdf);
}
