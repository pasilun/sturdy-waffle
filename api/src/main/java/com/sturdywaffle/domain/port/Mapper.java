package com.sturdywaffle.domain.port;

import com.sturdywaffle.domain.model.InvoiceLine;
import com.sturdywaffle.domain.model.MappingProposal;

import java.util.Optional;

public interface Mapper {
    Optional<MappingProposal> map(String supplierName, InvoiceLine line);
}
