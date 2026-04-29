---
title: First MCP browser-driven UI review
type: session
created: 2026-04-29
updated: 2026-04-29
tags: [invoice-to-journal, mcp, playwright, ui-review]
---

# First MCP browser-driven UI review

First exploratory session using the Playwright MCP browser tools (installed during Phase 6 plan) to drive the running app, compare against [[spec-invoice-to-journal]], and report findings. Workflow: [[mcp-browser-driven-ui-review]].

## Context

Phase 6 just landed (Playwright + husky). MCP tools `mcp__playwright__browser_*` were available in the session but unused. User asked: "run it and give me feedback on what to improve in UI", then "how does it look compared to spec?" — so the pass was both UX-focused and spec-compliance-focused.

## Setup

- `./dev.sh` started in background (task `bdx7cefv9`); ready loop fired on `Started ApiApplication`
- Viewport `1440x900` — typical laptop size
- Visited every primary route: `/` (redirects to `/invoices`), `/accounts`, `/activity`, `/upload`, `/invoices/9d583f70-...` (review)
- Screenshots saved as `web/.playwright-mcp/01-invoices.png` through `05-review.png`

## Spec compliance (`docs/SPEC.md`)

All §4 functional requirements met. All §10 acceptance criteria met. §9.3 (trust) is the only place where intent is undermined — see Bug #1 below.

| § | Requirement | Status |
|---|---|---|
| 4.1 | Upload single PDF, preserve | ✅ |
| 4.2 | LLM extract + map, reasoning + confidence per mapping | ⚠ rendering bug breaks this |
| 4.3 | Balanced entry; net+vat=gross strict | ✅ |
| 4.4 | Persistence | ✅ |
| 4.5 | View PDF + see suggestion + approve/decline | ✅ |
| 4.6 | React + TS | ✅ |
| 9.3 | Trust surfaced via reasoning + confidence | ⚠ |
| 10  | All 9 acceptance boxes | ✅ |

## Bugs

1. **NaN% on null-confidence postings** — On the live review page, the deterministic VAT (2640) and supplier-payable (2440) postings show a **red confidence bar with "NaN%"**. These postings have `confidence: null` in the API (assembler-built, not LLM-mapped), but `ConfidenceBar`'s `if (value === null) return null` guard misses the `undefined` case. Fix: change to `value == null` (loose equality). One character. Spec §9.3 says trust is the product — a red bar on a deterministic posting reads as "the system is unsure" when in fact it's deterministic. Highest-priority fix.

## Polish (not spec violations, but rough)

1. **Numbers without locale formatting** — `116875.00 SEK` should be `116 875,00 SEK` via `Intl.NumberFormat('sv-SE')`. Hard to scan large amounts.
2. **Sidebar active state inconsistent across pages** — the highlight reads as much stronger on Upload/Activity than on Invoices. CSS specificity drift; should look identical.
3. **Accounts page wastes horizontal space** — `max-w-4xl` content area + sidebar leaves ~600 px empty on the right; Type and Normal Side columns are extra wide for 6-letter values.
4. **Activity feed identity-thin** — only `Approved …ca362461 11h ago` per row. The user explicitly chose "minimal" earlier (no joins), but at 30+ events you can't tell which invoice without clicking. Worth revisiting if usage scales.
5. **List rows can collide** — two `Bright IT Solutions AB · 1047 · 2026-03-10` rows are visually identical (same supplier/number/date/gross/status/decided). Add a created-at time column or hover tooltip with suggestion id.
6. **Decline-with-note is data-modeled but not surfaced** — `DecisionRequest.note` exists in the API; the UI never asks for it. A small text field next to Decline would be a one-line trust upgrade.

## Beyond the spec (Phase 5 + 6 additions)

Spec is silent on a list, navigation, accounts page, activity feed, or a regression suite. §9.1 ("must be extensible") and §9.4 ("methodology shows up in the artifact") justify them — both serve implicit asks. None are spec violations.

## Recommended fix order

1. NaN bug (one character; restores trust signal)
2. Locale-format gross amounts (~5 lines, big readability win)
3. Unify sidebar active state (tweak the `NavLink` callback className)
4. Tighten Accounts table column widths
5. Activity feed: join supplier+invoice number — defers to a backend change

## Concept-page candidates

- **`json-null-vs-undefined`** — JSON deserialization may map missing fields to `undefined` rather than `null` depending on the JSON library / type narrowing. `=== null` checks miss it. Pattern: use `== null` (loose equality) when guarding against either, OR explicitly normalize at the type boundary. Applies anywhere a TS frontend reads optional fields from a Java/Spring backend.
- **`vite-spa-proxy-bypass`** — already noted as a candidate in the Phase 6 ship log. The NaN bug + the proxy bug are both "JS produced something visually wrong because the type contract had a gap."

## Cleanup

- `TaskStop` on `bdx7cefv9` (./dev.sh)
- Verified ports 5173 / 8080 free via `ss -ltn`
