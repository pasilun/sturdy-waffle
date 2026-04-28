---
title: LLM never does arithmetic
type: concept
created: 2026-04-27
updated: 2026-04-27
tags: [llm, architecture, principle, accounting, validation]
---

# LLM never does arithmetic

**The LLM handles judgment. Deterministic code handles arithmetic.**

Mapping a line item to an account requires judgment: context, supplier name, description ambiguity. Computing whether debits balance credits requires arithmetic: it is exact, repeatable, and has one right answer. These are different tasks and should be handled by different tools.

## The rule

LLM calls in the pipeline are confined to:
1. **Extraction** — reading structured data from a PDF (judgment about where fields are)
2. **Mapping** — choosing which chart-of-accounts entry a line item belongs to (judgment about semantics)

LLM calls are explicitly excluded from:
- Summing line items
- Checking `net + vat == gross`
- Asserting `total_debits == total_credits`
- Any arithmetic on monetary amounts

## Why it matters

LLMs hallucinate. On open-ended judgment tasks (classification, categorization, reasoning), that's a manageable risk — the accountant sees the output and approves or declines. On arithmetic, a hallucination produces a materially wrong journal entry that may appear plausible. Worse, it's invisible: the accountant can't easily verify a balance by looking at it.

Putting arithmetic in code means:
- Failures are deterministic and testable
- A balance error surfaces as a typed exception, not a plausible-looking wrong entry
- The pipeline either produces a balanced entry or rejects with a clear mismatch message (422)

## In the Invoice-to-Journal pipeline

From [[plan-invoice-to-journal]] §12:

> Steps 2 and 4 are the only LLM calls. Steps 3, 5, 6 are pure code.

- Step 3 (validate): `net + vat == gross` — code, not LLM
- Step 5 (assemble): `total_debits == total_credits` — code, not LLM. Defense-in-depth even though it's tautological after step 3.

The VAT-in account and supplier-liability account are also picked deterministically from the chart (always 2640 and 2440), not by the LLM.

## See also

- [[invoice-to-journal]] — project where this principle is applied
- [[plan-invoice-to-journal]] — pipeline steps, explicit LLM/code boundary
- [[spec-invoice-to-journal]] §9.2 — source of this requirement
- [[extractor-as-provider-seam]] — the interface design that enforces the boundary structurally
