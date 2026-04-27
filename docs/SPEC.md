# Invoice-to-Journal Entry — Specification

> Interpretation of the take-home brief. This document captures **what** the system must do, not **how** it will be built. Implementation plan lives in a separate `PLAN.md`.

---

## 1. Problem statement

An accountant receives PDF invoices from suppliers and must turn each one into a balanced journal entry — deciding which accounts to debit and credit, and how to handle VAT — before posting it to the books. This is repetitive, mechanical work where the accountant's real value is judgment, not data entry.

**The product compresses the data-entry portion to a glance-and-confirm step.** The accountant uploads a PDF, an LLM proposes a journal entry mapped against a fixed chart of accounts, and the accountant approves or rejects it.

## 2. Users

A single role: **the accountant**. No multi-tenancy, no auth, no admin. One user, one workflow.

## 3. Core user journey

1. Accountant opens the app and uploads a PDF invoice from a supplier.
2. The system extracts invoice data and produces a suggested journal entry — one debit/credit posting per relevant account, balanced, with each posting linked to a chart-of-accounts entry.
3. Accountant sees the original invoice and the suggested journal entry side by side.
4. Accountant approves or declines the suggestion.
5. The decision is persisted.

## 4. Functional requirements

### 4.1 Upload
- Frontend accepts a single PDF invoice per upload.
- The PDF is preserved (so the accountant can view it during review).

### 4.2 Extraction & mapping
- An LLM (Anthropic API, key provided) is used to:
  - Extract invoice fields and line items from the PDF.
  - Decide which chart-of-accounts entry each line item maps to.
- The output is a journal entry composed of postings.
- Each suggested account mapping carries a short **reasoning** string and a **confidence** score (0–1), surfaced to the accountant in the UI. Reasoning answers *why this account*; confidence answers *how sure*.
- Confidence is only attached to mapping decisions, not to extracted numeric fields. Numeric extraction either passes the sanity check in §4.3 or it doesn't.

### 4.3 Journal entry
- A journal entry consists of one or more postings.
- Each posting has: an account (from the provided chart of accounts), a debit OR credit amount, and a description.
- **Total debits must equal total credits.** This is a hard invariant, enforced by deterministic code, not by the LLM.
- The VAT amount, net amount, and total amount are read from the invoice (the supplier states them explicitly). They are not computed.
- Before a journal entry is produced, the system validates `net + vat == total` using strict equality on the canonical numeric type. A failure indicates an extraction error and surfaces as a clear error state — no partial or malformed journal entry is ever persisted.

### 4.4 Persistence
- The journal entry must be stored in a database.
- The invoice itself, the suggested mapping, and the accountant's decision must all be retrievable.

### 4.5 Review UI
- Accountant can see the uploaded PDF.
- Accountant can see the suggested journal entry, including reasoning and confidence per account mapping.
- Accountant can approve the suggestion.
- Accountant can decline the suggestion.
- **Out of scope for v1:** editing individual postings before approval. The data model is shaped to support it (account_code is a foreign key, not a hardcoded value), so it can be wired up later as a frontend-only change — including potentially during the live interview as a feature extension.

### 4.6 Stack constraints
- Frontend: **React with TypeScript** (mandatory).
- Backend: any stack the candidate is comfortable with.

## 5. Inputs provided

- **One sample PDF invoice** — clean, single page, a few line items.
- **An Anthropic API key** for LLM calls.
- **A fixed chart of accounts** (BAS kontoplan subset — 20 accounts covering the common categories: bank, supplier liability, VAT in, materials, rent, equipment, office supplies, telecoms, insurance, IT services, hosting, software licenses, staff meals).

## 6. Deliverable

A GitHub repo (or zip) with instructions to run locally.

## 7. Live interview format (post-submission)

- 1–1.5 hours.
- Walkthrough of the take-home.
- A **harder PDF** is introduced live.
- A **new feature** is implemented live.
- Iteration is expected; breakage is acceptable.
- Project must be running locally with the IDE ready.

## 8. What's explicitly NOT graded

> "We care more about the product experience than accounting perfection."

This sentence is the most important one in the brief. It explicitly de-prioritizes:
- Memorizing the BAS chart of accounts in detail
- Getting Swedish VAT rules exactly right
- Conforming to formal Swedish bookkeeping standards (K2/K3, etc.)

What it implicitly elevates:
- The end-to-end product flow feels good to use
- The accountant trusts the suggestion (or knows when not to)
- The system is pleasant to extend

## 9. Implicit requirements (read between the lines)

These are not stated, but are clearly being measured:

### 9.1 The architecture must be extensible
The live interview will introduce a harder PDF and a new feature. A monolithic prompt or hardcoded schema will visibly buckle. The codebase must be structured so that adding a feature mid-conversation is a small, contained change.

### 9.2 The AI use must be deliberate
"Use AI tools" is stated for the build process. It's also implicitly tested in the product itself: where does the LLM belong, where does it not? Putting an LLM inside the debit/credit balancing logic, for instance, would be a red flag — that's deterministic arithmetic, not judgment.

### 9.3 The accountant must be able to trust the output
A bare "here are some accounts, approve?" UI is much weaker than one that surfaces *why* each account was chosen and *how confident* the system is. Trust is the product. This is enforced by §4.2: every suggested mapping carries reasoning and confidence, visible at glance.

### 9.4 The candidate's methodology shows up in the artifact
Plan files, golden-case fixtures, eval harnesses, README design notes — these signal *how* the candidate thinks about building with AI. Their absence signals their absence.

## 10. Acceptance criteria for "done"

The submission is ready when all of the following are true:

- [ ] Cloning the repo and following the README runs the app end-to-end with one or two commands.
- [ ] Uploading the provided sample PDF produces a balanced journal entry within ~15 seconds.
- [ ] Each posting references a valid account from the provided chart of accounts.
- [ ] Reasoning and confidence are visible for each suggested account mapping in the UI.
- [ ] Net + VAT = total is validated on every extraction; failures surface as a clear error state, not a malformed entry.
- [ ] The journal entry is persisted across restarts.
- [ ] The UI lets the accountant view the original PDF, see the suggestion, and approve or decline.
- [ ] An eval harness runs the full pipeline against a small set of golden-case fixtures and reports per-case results.
- [ ] The README explains what was built, what was deliberately left out, and how to extend it.

## 11. Anti-goals

Things that would be a bad use of time:

- A polished landing page or marketing UI
- Authentication, user management, organizations
- A second supplier upload format (CSV, email, etc.)
- OCR for scanned PDFs
- Multi-currency, foreign VAT, reverse charge
- Batch upload
- Actually integrating with Fortnox / Visma / any real bookkeeping system
- A pixel-perfect design system

## 12. Resolved design decisions

The questions raised during spec review have been resolved as follows. These shape `PLAN.md`.

| # | Question | Decision |
|---|---|---|
| 1 | One LLM call or two (extract vs. map)? | Two — extraction first, then mapping. Decoupled, separately cacheable, separately measurable. |
| 2 | Where does VAT logic live? | VAT amount is read from the invoice, not computed. Deterministic code validates `net + vat == total` (strict equality) and builds the balanced entry. The LLM never does arithmetic. |
| 3 | Confidence and reasoning per posting? | Yes, on account mappings only — not on extracted numbers. Surfaced in the UI. |
| 4 | Edit-before-approve? | Data model accommodates it; UI deferred to v2 / live extension. |
| 5 | Eval harness with golden cases? | Yes, three fixtures (provided sample + a rent invoice + a staff lunch). |

## 13. Open for architecture

One question remains open and is best answered against a concrete architecture rather than in the abstract:

- **What live extensions should the architecture be designed to absorb without rewrites?** Likely candidates include a harder PDF (multi-page, missing fields), a supplier-preference register that short-circuits the mapping LLM call, an audit log of edits, and one-line-to-many-accounts splits. The defenses against each will be specified in `PLAN.md` once the layered architecture is in place.
