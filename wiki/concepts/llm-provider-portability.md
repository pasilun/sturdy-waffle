---
title: LLM provider portability — what's cheap, what's expensive
type: concept
created: 2026-04-27
updated: 2026-04-27
tags: [llm, architecture, anthropic, openai]
---

# LLM provider portability

A swap between major LLM providers (Anthropic ↔ OpenAI ↔ Google) is rarely uniform. Some surfaces port nearly free; others are real work. Knowing which is which up front lets you put the seams in the right place — and gives an honest cost estimate when someone asks "how hard is it to swap?"

## Cheap (ports almost as-is)

- **Prompts.** System / user message shapes are functionally the same across providers. Wording sometimes wants slight tuning but the file moves untouched.
- **Structured-output schemas.** Anthropic tool-use, OpenAI `response_format: json_schema`, Google `responseSchema` — all consume JSON Schema. The schema is the same; only the call wrapper changes.
- **Plain text in / text out.** No provider-specific surface area at all.

## Expensive (real translation work)

- **Multimodal inputs.** This is the asymmetry that bites.
  - Anthropic accepts PDFs as native `document` content blocks — multi-page, vision-aware, no preprocessing.
  - OpenAI does not (as of early 2026). Two paths: rasterize each page with a PDF library (e.g. PDFBox) and send as image content blocks, or upload via the Files API and reference. Neither is a one-liner.
  - The same shape applies to images, audio, video — provider-specific input plumbing.
- **Native tool execution / agents.** If you use Anthropic's computer-use or OpenAI's Assistants beyond raw function-calling, the abstractions are not interchangeable.

## Smaller deltas

- **Prompt caching.** Anthropic uses explicit `cache_control` markers on stable content. OpenAI caches automatically above ~1024 tokens with no markers. Direction of the irritation depends on the swap: explicit → automatic loses control; automatic → explicit loses set-and-forget.
- **Auth.** One env var per provider. Trivial.
- **Token budgets / pricing** are an operational concern, not a portability one.

## The architectural defense

If you anticipate a swap (or just want to be honest about coupling), put provider work behind a **project-shaped interface** — not a provider-shaped one. The pipeline calls `Extractor`, not `AnthropicClient`. The mapper chain consumes `Mapper`, not `OpenAIClient`. Each implementation imports its own SDK; the rest of the codebase has zero SDK imports.

The interface is overhead with one implementation. It pays back the moment you add a second, and crucially: the cost of the multimodal asymmetry above stays *inside* the new implementation. The pipeline does not move.

This is what [[invoice-to-journal]] does — see [[extractor-as-provider-seam]] for the concrete project decision.

## Honest unknowns

The OpenAI multimodal-input story has been moving (Files API, Responses API, image inputs on chat completions). Before committing to a port, verify which path is currently cleanest — the rasterize-and-send vs. upload-and-reference choice changes the LOC estimate inside the new `Extractor` impl. The architectural defense is unchanged either way.

## See also

- [[extractor-as-provider-seam]] — concrete application in [[invoice-to-journal]]
- [[plan-invoice-to-journal]] §6 — the two interfaces side by side
- [[llm-no-arithmetic]] — separate principle but kindred spirit (put the LLM where its strengths are; put deterministic work elsewhere)
