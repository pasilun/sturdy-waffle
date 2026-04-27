---
title: Interview Brief — Invoice-to-Journal
type: source
source_path: ../../interview 2/interview.md
ingested: 2026-04-27
created: 2026-04-27
updated: 2026-04-27
tags: [interview, brief]
---

# Interview Brief — Invoice-to-Journal

The original take-home assignment from the company. Single source of truth for what was actually asked.

## TL;DR

Build a React+TS web app where an accountant uploads a PDF invoice. An LLM proposes a journal entry mapped against the provided BAS chart of accounts (20 accounts). The accountant approves or declines. Stored in a database. Backend stack is free choice. One sample PDF and an Anthropic API key are provided. Deliverable is a GitHub repo or zip with run instructions. Live interview is 1–1.5 hours: walkthrough of the take-home, then a harder PDF and a new feature implemented live.

## What's explicitly stated

- Frontend must be **React with TypeScript**. Backend is candidate's choice.
- Debits and credits must balance.
- Journal-entry shape (which accounts to debit/credit, how to handle VAT) is up to the candidate.
- "We care more about the product experience than accounting perfection." This sentence shapes the priorities — see [[spec-invoice-to-journal]] §8.
- "Use AI tools" — Cursor, Copilot, Claude, etc. — at the candidate's comfort level.

## Chart of Accounts

Twenty BAS kontoplan accounts covering bank, supplier liability, VAT-in, materials, rent, equipment, office supplies, telecom, insurance, IT services, hosting, software licenses, and staff meals. Full table in the source file at `../../interview 2/interview.md`.

Notable for design: includes both "förbrukningsinventarier" (5410) and "förbrukningsmaterial" (5460), and both "personalmat & fika" (7631) and several office-related codes — these are the boundary cases where LLM mapping confidence will matter.

## Live interview format

- 1–1.5 hours.
- Walkthrough first.
- Then a **harder PDF** is introduced — likely multi-page, missing fields, or layout variation.
- Then a **new feature** is implemented live.
- Iteration is expected; breakage is acceptable.
- Project must be running locally with the IDE ready.

## See also

- [[invoice-to-journal]] — the project page
- [[spec-invoice-to-journal]] — Patrik's interpretation of this brief into a spec
- [[plan-invoice-to-journal]] — implementation plan derived from the spec
