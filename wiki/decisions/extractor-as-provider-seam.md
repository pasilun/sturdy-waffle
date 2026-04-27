---
title: Extractor interface as a provider seam
type: decision
status: accepted
decision_date: 2026-04-27
created: 2026-04-27
updated: 2026-04-27
tags: [invoice-to-journal, llm, architecture]
---

# Extractor as an explicit provider seam

Project: [[invoice-to-journal]]

## Decision

The pipeline calls a Spring-injected `Extractor` interface, not the Anthropic SDK directly. v1 has a single implementation, `AnthropicExtractor`, but the pipeline holds no SDK type. The interface is symmetric with `Mapper`, which already had this shape from the start (driven by the supplier-rule live-extension scenario).

```java
public interface Extractor {
    ExtractedInvoice extract(byte[] pdf);
}
```

The two interfaces are documented together in [[plan-invoice-to-journal]] §6 ("Provider seams").

## Rationale

The original PLAN.md described step 2 of the pipeline as "one Anthropic call." Mapping was already behind an interface; extraction was not — it was inline, coupled to the SDK.

When provider portability came up (specifically: how easy is it to swap to OpenAI?), the asymmetry showed: mapping was a 2-class change; extraction was a step-rewrite. Adding `Extractor` as an interface costs ~20 LOC and makes the answer symmetric.

This also localizes the [[llm-provider-portability]] cost asymmetry. Anthropic accepts PDFs as native `document` content blocks; OpenAI does not — its `Extractor` impl has to rasterize pages (PDFBox) or upload via the Files API. That cost is contained inside the new `Extractor` impl; the pipeline does not move.

## Alternatives considered

- **SDK directly in pipeline (the original).** Simpler today, but a provider swap becomes a pipeline rewrite — exactly the kind of in-place edit that [[spec-invoice-to-journal]] §9.1 says should be a new file instead.
- **Only `Mapper` as a seam (the prior interim state).** Asymmetric. Mapping was portable, extraction wasn't. No principled reason for the asymmetry — it was just where the supplier-rule extension drove the seam first.

## Consequences

- Adding `OpenAiExtractor` is a new `@Component` + a `llm.provider` config switch. Same shape as adding a second `Mapper`.
- Provider-swap appears in the [[plan-invoice-to-journal]] §11 live-extension table with an honest cost: two classes, plus the multimodal-input work inside `OpenAiExtractor`.
- v1 wiring is unchanged — `AnthropicExtractor` is the only bean. The interface is overhead until the second impl exists, but the overhead is small (one file).

## See also

- [[invoice-to-journal]]
- [[plan-invoice-to-journal]] §4, §6, §11
- [[llm-provider-portability]] — the concept this decision applies
- [[two-llm-calls-not-one]] — the upstream choice that made two seams natural
