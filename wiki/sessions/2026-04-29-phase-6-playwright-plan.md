---
title: Phase 6 plan — Playwright + browser-driving MCP
type: session
created: 2026-04-29
updated: 2026-04-29
tags: [invoice-to-journal, planning, testing, playwright, mcp]
---

# Phase 6 plan — Playwright + browser-driving MCP

Planning session capturing the design for adding Playwright to the project for two complementary purposes: (1) interactive browser driving by the LLM via the Playwright MCP server, (2) a regression test suite gated behind a husky pre-commit hook with a thin live layer on pre-push. See [[invoice-to-journal]] for project context.

## Why

After Phase 5 the frontend has 5 pages (Upload, Review, Invoices, Accounts, Activity) wrapped in a sidebar layout — zero frontend test coverage. The Postgres null-param bug we caught this morning (commit `a371a64`) is a clean example of the gap: it returned 500 on `/invoices` and would not have been flagged by any existing check (gradle test mocks the query layer; tsc/eslint can't see runtime behaviour). A live-backend smoke test would have caught it instantly.

Secondary motivation: enable Claude to drive the live app interactively — click around, take accessibility snapshots, find UX issues, ground design discussions in the real UI rather than guessing at file contents.

## Three test tiers

| Tier | Command | What | When |
|---|---|---|---|
| Mocked | `pnpm e2e` | Frontend specs against a Playwright-mocked API (`page.route`). Fast (~10s), deterministic, no servers needed. | Pre-commit hook on `web/**` changes |
| Live read-only | `pnpm e2e:live` | Same shape against real `dev.sh` on `:8080` for read-only endpoints (`/invoices`, `/accounts`, `/activity`, `/invoices/:id`). No upload, no LLM cost (~10s). | Pre-push hook (skipped with warning if `:8080` unreachable) |
| Live full | `pnpm e2e:full` | Uploads `api/src/test/resources/sample.pdf`, waits for pipeline, Approves, asserts persistence. Burns Anthropic credits. | Manual, before milestones |

The live read-only tier is the bug-catching layer.

## Repo additions

### Root
- `package.json` (new, minimal — just for husky orchestration; `web/package.json` stays as-is)
- `.husky/pre-commit` — runs `pnpm --filter web e2e` if `web/**` staged, `cd api && ./gradlew test` if `api/**` staged, plus tsc + lint
- `.husky/pre-push` — runs `pnpm --filter web e2e:live` if `curl -sf :8080/health` succeeds; otherwise prints "dev.sh not running, skipping live tests" and exits 0 (skip with warning, not hard fail — working offline is a real case)

### Web project
- `web/package.json` — devDep `@playwright/test`; scripts `e2e`, `e2e:live`, `e2e:full`, `e2e:ui`
- `web/playwright.config.ts` — chromium-only; two Playwright projects: `mocked` (uses `webServer` to autostart Vite, baseURL :5173) and `live` (no webServer, expects dev.sh on :5173+:8080); `e2e:full` is the live project with `E2E_FULL=1`
- `web/e2e/` — mocked specs (default project)
  - `web/e2e/fixtures/api-stubs.ts` — JSON fixtures matching `api.ts` types + a `mockApi(page)` helper wiring `page.route` onto each
  - `web/e2e/nav.spec.ts` — `/` redirects to `/invoices`, sidebar 4 items, active highlight follows route
  - `web/e2e/invoices-list.spec.ts` — table renders fixture rows, status tabs filter, click row → URL becomes `/invoices/:id`, empty state when array empty
  - `web/e2e/accounts.spec.ts` — 20 rows sorted by code
  - `web/e2e/activity.spec.ts` — feed renders events with humanized labels, click row navigates to review
  - `web/e2e/review.spec.ts` — split view, StatusBadge in header reflects fixture decision, Approve POST and badge update
  - `web/e2e/upload.spec.ts` — drop zone visible, non-PDF triggers error, valid PDF posts and navigates
- `web/e2e-live/` — live specs (separate Playwright project, opt-in)
  - `web/e2e-live/contract.spec.ts` — same shape as mocked list/accounts/activity but no `page.route`, hits real `:8080` via Vite proxy. Schema-shape assertions on first row of each.
  - `web/e2e-live/full-flow.spec.ts` — `test.skip(!process.env.E2E_FULL)` guard. Uploads sample.pdf, waits for review, approves, returns to `/invoices`, sees the new row.

### Documentation
- `README.md` — new "Testing" section listing the three tiers + how to bypass hooks in emergencies (`git commit --no-verify`)
- Wiki: this session page; project page Phase 6 row; log entry

## Playwright MCP server

Already installed by the user — `mcp__playwright__browser_*` tools are available in this session. No further config needed. After dev.sh is running I can `browser_navigate http://localhost:5173/invoices`, take accessibility snapshots (cheap on tokens), screenshot specific issues.

## Implementation order

1. Add `@playwright/test` to `web/`; write `playwright.config.ts` with both Playwright projects. `pnpm exec playwright install chromium`.
2. Author `fixtures/api-stubs.ts`. Drives every mocked spec.
3. Write the 6 mocked specs: nav → invoices-list → accounts → activity → upload → review. `pnpm e2e` green.
4. Write `e2e-live/contract.spec.ts`. Start `./dev.sh`, `pnpm e2e:live` green.
5. Write `e2e-live/full-flow.spec.ts` gated by `E2E_FULL=1`. Run once manually.
6. Husky: root `package.json`, install husky, write `.husky/pre-commit` and `.husky/pre-push`. Test by breaking a spec.
7. README "Testing" section + wiki update.
8. **First exploratory MCP session**: Claude drives the running app via `mcp__playwright__browser_*`, reports findings (UX issues, accessibility gaps, dead clicks). Likely a separate session log entry.

## UX decisions (confirmed)

- Pre-commit gating: husky-blocking on every commit
- Live coverage: include from v1 — split into read-only contract layer (pre-push) + opt-in full flow (manual)
- Mocked layer is the default for `pnpm e2e`; pre-commit only runs the mocked tier (fast)
- Pre-push skips with warning if dev.sh isn't up (rather than hard-failing)

## Verification

1. `cd web && pnpm install && pnpm exec playwright install chromium`
2. `pnpm e2e` — 6 mocked specs green in <15s with Vite autostarted
3. `./dev.sh` running, `pnpm e2e:live` — contract spec green against real backend
4. `E2E_FULL=1 pnpm e2e:full` — full-flow uploads sample.pdf and approves
5. Break a fixture-mocked spec, attempt commit — pre-commit blocks
6. `git push` with dev.sh down — pre-push prints skip message and exits clean
7. `git push` with dev.sh up — pre-push runs contract spec
8. `mcp__playwright__browser_navigate http://localhost:5173/invoices` returns an accessibility snapshot of the table
