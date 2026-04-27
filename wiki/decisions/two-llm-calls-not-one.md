---
title: Extract and map run as two LLM calls, not one
type: decision
status: accepted
decision_date: 2026-04-27
created: 2026-04-27
updated: 2026-04-27
tags: [invoice-to-journal, llm, architecture]
---

# Two LLM calls, not one

Project: [[invoice-to-journal]]

## Decision

The pipeline runs **two separate Anthropic calls**: one for extraction (PDF → structured invoice fields and line items), one for mapping (line items → chart-of-accounts codes with reasoning + confidence). Not a single combined "PDF → finished journal entry" call.

## Rationale

- **Decoupled.** Extraction is mechanical (the supplier states net, VAT, gross — read them). Mapping is judgment (this line is "staff lunch", which goes to 7631). Mixing them in one prompt mixes two failure modes and makes both harder to debug. See also [[llm-no-arithmetic]].
- **Separately cacheable.** The mapping prompt includes the full chart of accounts and the system prompt — both stable across invoices, both candidates for `cache_control: ephemeral`. The extraction prompt is small and per-PDF-unique. Mixing them defeats caching on the stable half.
- **Separately measurable.** The eval harness reports extract pass/fail and map @ 1 separately ([[plan-invoice-to-journal]] §10). A regression in one is unambiguous instead of "something got worse somewhere".
- **Validation can short-circuit.** If extraction fails the `net + vat == gross` check, mapping is skipped entirely. Combined into one call, the LLM has already spent tokens choosing accounts the system will throw away.

## Alternatives considered

- **One combined call.** Rejected: above costs outweigh the latency saving (one round-trip vs two). The 15s SPEC budget is comfortable for two non-streamed tool-use calls.
- **Extract + map + assemble in one call (LLM emits final postings including amounts).** Strongly rejected: would require the LLM to do arithmetic. See [[llm-no-arithmetic]] and [[spec-invoice-to-journal]] §9.2.

## See also

- [[invoice-to-journal]]
- [[plan-invoice-to-journal]] §4, §5
- [[spec-invoice-to-journal]] §12 row 1
- [[llm-no-arithmetic]]
