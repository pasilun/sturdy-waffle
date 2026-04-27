---
title: SPEC.md — Invoice-to-Journal
type: source
source_path: ../../SPEC.md
ingested: 2026-04-27
created: 2026-04-27
updated: 2026-04-27
tags: [spec, invoice-to-journal]
---

# SPEC.md — Invoice-to-Journal

Patrik's interpretation of [[interview-brief]]. Captures **what** the system must do; the **how** lives in [[plan-invoice-to-journal]].

## TL;DR

Single-user accountant workflow: upload PDF → LLM extracts fields and maps line items to a fixed chart of accounts → deterministic code validates `net + vat == gross` and balances debits/credits → accountant sees PDF and suggested entry side-by-side, approves or declines. Mapping decisions carry reasoning + confidence; numeric extraction either passes the validator or fails loudly.

## Key claims

### Hard invariants (deterministic code, never the LLM)

- Total debits == total credits ([[spec-invoice-to-journal]] §4.3).
- `net + vat == gross` strict equality on canonical numeric type. Failure = clear error, no partial entry persisted.
- `account_code` is a foreign key into the chart, never embedded.

### LLM responsibilities

- Extract invoice fields and line items from PDF (no reasoning, no confidence — extraction is mechanical).
- Decide which chart-of-accounts entry each line item maps to.
- Each mapping carries a short reasoning string and a confidence score (0–1).
- Confidence is attached to **mappings only**, not extracted numbers.

### What's explicitly de-prioritized (§8)

The "we care more about product experience than accounting perfection" line de-prioritizes:
- Memorizing BAS chart in detail
- Swedish VAT exact rules
- K2/K3 bookkeeping standard conformance

And implicitly elevates:
- End-to-end product flow feel
- Whether the accountant trusts the suggestion
- Whether the system is pleasant to extend

### Implicit requirements (§9)

- Architecture must absorb live extensions without rewrites — anticipates harder PDFs, supplier rules, audit logs, splits, edit-before-approve.
- AI use must be deliberate. Putting an LLM inside debit/credit balancing logic would be a red flag — that's deterministic arithmetic. See [[llm-no-arithmetic]].
- Trust is the product. A bare "approve?" UI is much weaker than one surfacing why-and-how-confident.
- Methodology shows up in the artifact: plan files, fixtures, eval harness, README design notes.

### Resolved design decisions (§12)

| # | Question | Decision |
|---|---|---|
| 1 | One LLM call or two? | Two — see [[two-llm-calls-not-one]] |
| 2 | Where does VAT logic live? | Read from invoice, not computed; deterministic validator enforces `net + vat == gross` |
| 3 | Confidence/reasoning per posting? | Yes, on mappings only |
| 4 | Edit-before-approve? | v2 / live extension; data model accommodates |
| 5 | Eval harness with golden cases? | Yes — sample, rent invoice, staff lunch |

### Anti-goals (§11)

Twelve things deliberately out of scope: polished marketing UI, auth/users, second upload format, OCR, multi-currency, batch upload, Fortnox/Visma integration, pixel-perfect design system, etc.

### Acceptance criteria (§10)

Eight checkboxes for "done": cold-clone runs end-to-end with one or two commands, sample PDF produces balanced entry within ~15s, postings reference valid accounts, reasoning + confidence visible per mapping, validator enforces `net + vat == gross`, persistence survives restart, approve/decline works, eval harness runs against golden cases, README covers what was built and how to extend.

## See also

- [[invoice-to-journal]] — project page
- [[interview-brief]] — the brief this spec interprets
- [[plan-invoice-to-journal]] — implementation plan that derives from this
- [[llm-no-arithmetic]] — concept driving §9.2
