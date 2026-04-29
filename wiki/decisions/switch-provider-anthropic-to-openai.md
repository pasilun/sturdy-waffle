---
title: "Add OpenAI as a second LLM provider (config-switchable)"
type: decision
decision_date: 2026-04-29
status: accepted
created: 2026-04-29
updated: 2026-04-29
tags: [openai, anthropic, multi-provider, llm]
---

# Add OpenAI as a second LLM provider (config-switchable)

## Decision

Add OpenAI implementations of `Extractor` and `Mapper` alongside the existing Anthropic ones. The active provider is controlled by a single `llm.provider` config key. Switching requires no code change and no recompile — flip `llm.provider: openai` in `application.yml` (or `LLM_PROVIDER=openai` env var) and restart.

## Reason

Anthropic billing is unavailable; credits cannot be topped up. Rather than replacing the Anthropic implementation, both implementations coexist so that switching back when billing recovers is a one-line config change.

## What changes

- New classes: `OpenAiConfig`, `OpenAiExtractor`, `OpenAiMapper`, `OpenAiMapperConfig`
- Existing classes annotated with `@ConditionalOnProperty` (Anthropic ones + existing `MapperConfig`)
- `application.yml` gains an `llm.openai.*` section alongside the existing `llm.anthropic.*`
- Both `ANTHROPIC_API_KEY` and `OPENAI_API_KEY` live in `.env`; only the active provider's key is used

## What stays the same

Pipeline, domain, persistence, frontend, API contracts, eval harness, test coverage. The `Extractor` and `Mapper` interfaces are not touched.

## Trade-offs accepted

**PDF handling:** Anthropic accepts raw PDFs natively (vision-aware). OpenAI's `OpenAiExtractor` uses Apache PDFBox to extract the text layer first. Acceptable for all current digitally-generated fixtures; would need rasterizing for scanned PDFs.

**Prompt caching metrics:** Anthropic logs `cacheReadInputTokens`. OpenAI caches automatically with no per-call metric. The OpenAI log lines omit that field.

## Reversibility

Immediate. `LLM_PROVIDER=anthropic ./dev.sh` (or flip the yaml key). The Anthropic implementation is untouched.

## See also

- [[llm-provider-portability]] — general analysis of the cheap vs. expensive surfaces in provider swaps
- [[extractor-as-provider-seam]] — the interface decision that makes this swap a config flip
- [[2026-04-29-openai-migration-plan]] — step-by-step implementation plan
