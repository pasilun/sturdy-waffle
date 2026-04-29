---
title: Mapping escalation plan — re-run mapping with a stronger model
type: session
created: 2026-04-29
updated: 2026-04-29
tags: [invoice-to-journal, planning, llm, mapper, escalation]
---

# Mapping escalation plan — re-run mapping with a stronger model

Planning page for the "Escalate mapping" feature: a button on the review page that re-runs the mapping step against a configured stronger model when the accountant is unhappy with the proposal. See [[invoice-to-journal]] for project context. Builds directly on the model-config lift completed earlier today.

## Why

Two open questions from the project page collapse into one feature:

- *"could we have dynamic model escalation if confidence is too low?"*
- *"could we have the system or model learn when accountant make changes?"*

The minimal, demoable shape: a button. Accountant triggers escalation when they don't like the mapping. System re-maps with a stronger/different model. Same UI, same data shape, audit log records the swap. No automatic confidence-threshold escalation in v1 — just the human-triggered version. (Auto-escalation becomes trivial later: same backend path, fired by a check at upload time.)

## Scope rule (the lock)

> **Approved or declined invoices cannot be re-mapped.**

This collapses the versioning question. While decision is `null`, mappings are mutable; once decided, they freeze. Endpoint returns 409 if called on a decided suggestion.

## Replace, not version

We **replace** the `mappings` (well, `suggestions` row + `postings` rows) on escalate rather than appending a new version. Reasons:

- Accountant's mental model is "I want a better proposal here, now," not "show me history." Replace matches the UI verb.
- The `audit_events` table already preserves history — emitting a `mapping.escalated` event with `{from_model, to_model}` keeps the trail.
- No schema changes needed.
- If side-by-side compare ("Haiku said 6540, Sonnet said 5410") is wanted later, add a `mapping_version` column then. Don't pre-build the demo we don't have time for.

## Backend shape

**1. Config (`application.yml`):** extends the lift from earlier today.
```yaml
llm:
  anthropic:
    escalation:
      model: claude-sonnet-4-6   # stronger than the haiku default
      max-tokens: 1024
```

**2. Bean topology.** Strip `@Component` from `AnthropicMapper`; convert to a plain class taking config in constructor. Add `MapperConfig` `@Configuration` producing two `@Bean AnthropicMapper`s — `primaryMapper` and `escalationMapper`. `PipelineService` injects them by `@Qualifier`. The autowired `List<Mapper>` chain pattern collapses to single-mapper for now (it was degenerate — `mappers.get(0)` was a smell). Supplier-preference mapper from §11 can re-introduce a chain when it arrives.

**3. Service.** New `PipelineService.escalate(SuggestionId)`:
- Load `ExtractedInvoice` from `extractions.raw_json` via `SuggestionQuery.loadExtractedInvoice`.
- Run escalation mapper per line. Run `Assembler`. Same `Validator` (idempotent).
- Call `Persister.replaceMapping(suggestionId, postings, newModelRun)`.

**4. Persister.** New `replaceMapping(SuggestionId, List<Posting>, ModelRun)`:
- 409 if a `decisions` row exists for this suggestion → throw `ConflictException`.
- `DELETE FROM postings WHERE suggestion_id = ?`.
- `UPDATE suggestions SET model = ?, prompt_version = ?, latency_ms = ? WHERE id = ?`.
- Insert new postings.
- Emit `audit_events`: `{ event: "mapping.escalated", payload: { from_model, to_model, lineCount } }`.
- All in one `@Transactional`.

**5. New exception + handler.** `ConflictException` → 409, mirroring `NotFoundException` → 404.

**6. Endpoint.** `POST /invoices/{id}/escalate-mapping` → returns updated `SuggestionResponse` on success, 409 if decided, 404 if missing.

## Frontend shape

`ReviewPage.tsx`:
- New button "Escalate mapping" in the decision panel area, visible only when `decision === null` (PENDING).
- Click → `POST /invoices/:id/escalate-mapping` → invalidate suggestion query → UI re-renders with new postings.
- Loading state while running; error toast on failure.
- Optional polish: show the model id under the postings table ("Mapped by claude-haiku-4-5") so the swap is visible. Trivial (already in `suggestions.model`, just needs to flow through `SuggestionResponse`).

`api.ts`: `escalateMapping(id: string): Promise<SuggestionResponse>`.

## Test impact

- No existing test asserts the chain pattern (grep clean for `mappers.get`, `List<Mapper>` outside PipelineService).
- New gradle test: `PipelineService.escalate` returns 409-equivalent when decision exists; otherwise replaces postings and emits audit row. Mock both mappers.
- New Playwright spec (mocked): pending invoice → click Escalate → assert postings re-render. Decided invoice → button hidden.

## Out of scope (filed for later)

- Auto-escalation when any posting confidence < threshold.
- Per-line escalation (whole-invoice only in v1).
- Side-by-side compare.
- Letting accountant pick the escalation model from a dropdown.

## Demo narrative

> "If the accountant doesn't trust the mapping, one click re-runs it against a stronger model. The audit log records what changed and why. Once they approve, mappings freeze — no accidental re-runs against committed entries. The same backend path is what auto-escalation would call when confidence drops below a threshold; we just haven't wired the threshold trigger yet."
