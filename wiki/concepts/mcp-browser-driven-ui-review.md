---
title: MCP browser-driven UI review
type: concept
created: 2026-04-29
updated: 2026-04-29
tags: [methodology, testing, mcp, playwright, autonomous]
---

# MCP browser-driven UI review

Autonomous workflow for using the Playwright MCP browser to drive the running app, compare it against its spec, and produce an ordered list of bugs / spec gaps / polish opportunities. Designed as a phase-end checkpoint: feature landed, tests green, but nobody has actually looked at the live UI yet. Also useful as a periodic spec-drift audit.

## When to use

- **End of a development phase** — code is in, automated checks pass, but the live UI hasn't been seen
- **Spec-drift check** — the app has grown organically; does it still match the docs?
- **Pre-milestone regression sniff** — quick visual pass before declaring done

## Prerequisites

- Playwright MCP installed in the user's Claude config: `claude mcp add playwright -- npx -y @playwright/mcp@latest` (one-time, restart Claude after)
- A `dev.sh`-equivalent that starts the full stack with one command
- A SPEC document worth comparing against (functional requirements, acceptance criteria, anti-goals)

## Workflow

1. **Start the stack in background.** `Bash(dev.sh, run_in_background=true)`. Then a second background `Bash` with `until grep -q "Started ..." <output>; do sleep 2; done` so a single notification fires when ready.
2. **Resize the viewport** to a representative laptop size — `mcp__playwright__browser_resize` 1440x900 is a good default.
3. **Visit every primary route in turn.** For each: `browser_navigate` → `browser_take_screenshot` (for the human-readable record) AND `browser_snapshot` if you'll be making click decisions afterward (the accessibility snapshot is cheap on tokens and is what the LLM actually reads).
4. **Read the SPEC fresh.** Don't rely on memory. Especially read §-numbered acceptance criteria and anti-goals.
5. **Build a comparison table** — one row per spec §, one column for Status (✅ / ⚠ / ❌ with one-sentence note).
6. **Catalog issues into three buckets**:
   - **Bugs** (correctness: visible NaN, wrong number, broken click, blank page)
   - **Gaps** (spec asks for X but X is missing, or technically present but visually misleading — the most important ones because they violate intent without being obvious)
   - **Polish** (locale formatting, alignment, hover states, empty-state thinness — not spec violations, just rough)
7. **Order fixes by impact-vs-effort.** One-character fixes (e.g. `=== null` → `== null`) go first; they ship the same hour.
8. **Stop the servers cleanly** with `TaskStop` for the background `dev.sh` task. Verify ports free with `ss -ltn | grep -E ':8080|:5173'`.
9. **Do NOT implement during this pass.** Report findings, let the user pick what to land. Fixing while exploring loses the comparative discipline — you start optimizing for "thing I can fix next" rather than "thing the user actually trusts."

## Outputs

- A short ordered improvement list (5–10 items, ranked by impact-vs-effort) — delivered to the user inline
- A session log entry capturing the specific findings + the screenshot file names under `web/.playwright-mcp/`
- If a bug points at a reusable gotcha (e.g. JSON null vs JS undefined, Postgres NULL parameter typing), file a concept-page candidate note in the session log

## Anti-patterns

- **Mixing exploration with implementation.** Run the pass cold. The user picks fixes after seeing the full report.
- **Reporting only positive findings.** Silence is failure. If everything looks fine, you didn't look hard enough.
- **Skipping the spec read.** Without it you start judging by personal taste — that's an opinion piece, not an audit.
- **Forgetting to stop the dev server.** Leaving processes running burns the user's machine and may collide on next session.

## Tools used

- `mcp__playwright__browser_navigate`, `_resize`, `_snapshot`, `_take_screenshot`, `_close`
- `Bash(./dev.sh, run_in_background=true)` + an `until grep -q ...` ready loop
- `TaskStop` for cleanup
- Read the SPEC, README, and the wiki project page

## See also

- [[2026-04-29-phase-6-playwright-plan]] — Phase 6 added the Playwright MCP and the regression suite
- [[invoice-to-journal]] — first project where this workflow ran
