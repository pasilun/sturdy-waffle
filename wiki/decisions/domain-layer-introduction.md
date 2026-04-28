---
title: Domain layer package structure
type: decision
status: accepted
decision_date: 2026-04-28
created: 2026-04-28
updated: 2026-04-28
tags: [invoice-to-journal, architecture, java]
---

# Domain layer package structure

Project: [[invoice-to-journal]]

## Decision

Introduce an explicit four-layer package structure before Phase 2 business logic is written:

```
com.sturdywaffle/
├── domain/          ← zero Spring / SDK imports
│   ├── model/       ← Money, Account, InvoiceLine, ExtractedInvoice,
│   │                    MappingProposal, Posting, SuggestionId
│   ├── port/        ← Extractor, Mapper (interfaces owned by the domain)
│   ├── service/     ← Validator, Assembler (pure logic, no framework)
│   └── exception/   ← ExtractionException, ValidationException
├── application/     ← PipelineService (orchestration; depends on domain)
├── infrastructure/  ← Spring, JDBC, Anthropic SDK
│   ├── config/      ← PostgresConfig
│   ├── llm/         ← AnthropicExtractor, AnthropicMapper (Phase 2)
│   ├── persistence/ ← repositories (Phase 2)
│   └── seed/        ← ChartSeeder
└── web/             ← InvoiceController, HealthController
```

Dependency rule: `domain` → nothing; `application` → `domain`; `infrastructure` → `domain` + Spring + SDK; `web` → `application` + `domain.model`.

## Rationale

Phase 1 left business logic flat inside `pipeline/PipelineService`. The PLAN describes `Validator`, `Assembler`, `Extractor`, and `Mapper` as separable concerns, but didn't name a home for them. Phase 2 is the first moment real domain logic is written; establishing the package structure before that moment costs ~30 minutes and means every new class lands in the right place from the start rather than needing a second refactor.

Three concrete gains:

1. **`Validator` and `Assembler` are unit-testable without a Spring context.** They live in `domain/service/` with no framework dependency. A test is `new Validator().validate(extracted)` — no `@SpringBootTest`, no embedded Postgres.
2. **The port interfaces flip the dependency arrow.** `Extractor` and `Mapper` previously sat next to their implementations; `infrastructure` would have imported from `pipeline`. Moving them to `domain/port/` means `infrastructure` depends on `domain`, never the reverse.
3. **`Money` has a canonical home.** The plan called for a `Money` value type but didn't place it. It now lives in `domain/model/` with `of(String)` and `of(BigDecimal)` factory methods that enforce scale-2 canonicalization at the boundary.

## Alternatives considered

- **Flatten everything in `pipeline/`.** Simpler initially, but `Validator` and `Assembler` would acquire Spring imports as `PipelineService` grows, making them impossible to unit-test cheaply.
- **Wait until Phase 2 and refactor then.** More churn: every new class in Phase 2 would be written in the wrong place and immediately moved.

## Consequences

- 12 new files created, 4 existing files moved (package declarations updated), 1 import updated in `InvoiceController`. No behavior change; compiles clean.
- Phase 2 starts with `AnthropicExtractor` going directly into `infrastructure/llm/` and `Validator`/`Assembler` implementations going into their domain service classes — no further structural decisions needed.
- `Extractor` and `Mapper` are now in `domain/port/`; see [[extractor-as-provider-seam]] for the original rationale.

## See also

- [[invoice-to-journal]]
- [[extractor-as-provider-seam]] — the upstream decision that defined the two port interfaces
- [[plan-invoice-to-journal]] §1, §4, §6
