# sturdy-waffle

Invoice-to-journal-entry assistant. Upload a PDF invoice; the app extracts line items via Claude, maps each line to a BAS chart-of-accounts code, and presents a balanced double-entry journal entry for an accountant to approve or decline.

## Prerequisites

- JDK 21 (`java -version` should show `21`). Install via Homebrew: `brew install --cask temurin@21`.
  On macOS the cask does **not** add `java` to your PATH automatically — add this to your shell profile and reload it:
  ```bash
  export JAVA_HOME=$(/usr/libexec/java_home -v 21)
  export PATH="$JAVA_HOME/bin:$PATH"
  ```
- Node 20+ with pnpm (`pnpm --version`). If pnpm is installed via corepack, ensure the corepack shims directory is on your PATH:
  ```bash
  corepack enable pnpm
  ```
- An Anthropic API key

## Running

```bash
# 1. Put your API key in place
echo "ANTHROPIC_API_KEY=sk-ant-..." > api/.env

# 2. Start API (port 8080) + web dev server (port 5173)
./dev.sh
```

Open **http://localhost:5173** in a browser. The Spring Boot boot takes ~20 s on first run; wait for `Started ApiApplication` in the terminal before uploading.

First boot also downloads the embedded Postgres binary (~30 MB, cached under `~/.zonky/` afterwards). Subsequent starts are fast.

`api/data/pg/` holds the database and `api/data/uploads/` holds PDF blobs — both survive restarts.

## Using the app

The app has a persistent left sidebar with four sections — **Invoices** (the home page), **Upload**, **Accounts**, **Activity**. The home page lists every processed invoice with a status filter (All / Pending / Approved / Declined); click a row to review.

1. **Upload** — drag-drop (or click to select) a PDF invoice on the upload page. The pipeline runs in ~15 s (two Claude calls: extract + map). On success you land on the review page for the new suggestion.
2. **Review** — a split view shows the original PDF on the left and the proposed journal entry on the right. Each posting shows the account code, debit/credit amounts, a one-line reasoning, and a confidence bar (green ≥ 80 %, amber 50–80 %, red < 50 %). The current decision state shows as a badge in the header.
3. **Decide** — click **Approve** or **Decline**. The decision is persisted with an audit timestamp.
   - If you don't trust the proposed mapping, click **Escalate mapping** before deciding. This re-runs only the mapping step against a stronger model (configured under `llm.anthropic.escalation`); the postings update in place. The button is hidden once a decision is recorded; calling the endpoint on a decided suggestion returns 409.
4. **Browse** — return to **Invoices** to see the row with its new badge, or open **Activity** for a reverse-chrono feed of `suggestion.created`, `decision.approved|declined`, and `mapping.escalated` events. **Accounts** is a read-only view of the BAS chart the mapper picks from.

## API

| Endpoint | Description |
|---|---|
| `POST /invoices` (multipart `file`) | Runs full pipeline. Returns `{"id": "<uuid>"}`. |
| `GET /invoices?status=all\|pending\|approved\|declined&limit=100` | List of processed invoices for the home page. |
| `GET /invoices/:id` | Suggestion + postings + decision (if any). |
| `GET /invoices/:id/pdf` | Raw PDF bytes (served to the in-browser iframe). |
| `POST /invoices/:id/decision` | Body: `{"status": "APPROVED"\|"DECLINED", "note": null}`. Returns the persisted decision. |
| `POST /invoices/:id/escalate-mapping` | Re-runs mapping against the escalation model and replaces postings. Returns the updated suggestion. 409 if a decision already exists. |
| `GET /accounts` | Chart of accounts (20 BAS rows). |
| `GET /activity?limit=100` | Recent audit events (`suggestion.created`, `decision.approved`, `decision.declined`). |
| `GET /health` | Returns `{"status": "UP"}`. |

## Running the eval harness

```bash
cd api && ./gradlew :api:eval
```

Runs the PDF fixtures in `api/src/eval/fixtures/` through the live pipeline (no DB) and prints a per-case table: extract pass/fail, map accuracy, average confidence, latency. Three are easy single-line cases (`rent.pdf`, `lunch.pdf`, `sample.pdf`); `devops.pdf` is a hard six-line consultancy invoice that spreads across four similar 65xx codes plus 5400 — useful for A/B'ing mapper models. Use this as a canary before editing prompts or swapping models.

## Testing

| Layer | Command | What it covers |
|---|---|---|
| Backend unit | `cd api && ./gradlew test` | `@WebMvcTest` controller tests with mocked queries. |
| Frontend mocked e2e | `cd web && pnpm e2e` | 20 Playwright specs against a Vite-served React app with API responses stubbed via `page.route`. ~7 s, deterministic, no servers needed. |
| Frontend live contract | `cd web && pnpm e2e:live` | Same shape as mocked but hits the real backend on `:8080` via the Vite proxy (read-only — no upload). Catches frontend↔backend contract drift. |
| Frontend live full flow | `cd web && pnpm e2e:full` | Uploads `api/src/test/resources/sample.pdf`, waits ~15 s for the LLM pipeline, approves, asserts persistence. Burns Anthropic credits. |
| Interactive | `cd web && pnpm e2e:ui` | Playwright's UI mode for authoring + debugging specs. |

`pnpm install` at the project root sets up husky:

- **pre-commit** runs the relevant layers based on what's staged: `pnpm e2e` + `tsc` if `web/**` changed, `./gradlew test` if `api/**` changed.
- **pre-push** runs `pnpm e2e:live` if `:8080/health` answers, otherwise skips with a warning.

Bypass in emergencies with `git commit --no-verify` / `git push --no-verify`.

The first-time setup needs the Chromium binary: `cd web && pnpm exec playwright install chromium`.

## Quick cURL round-trip

```bash
ID=$(curl -s -F "file=@invoice.pdf" http://localhost:8080/invoices | jq -r .id)
curl -s http://localhost:8080/invoices/$ID | jq
curl -s -X POST http://localhost:8080/invoices/$ID/decision \
     -H 'Content-Type: application/json' \
     -d '{"status":"APPROVED","note":null}' | jq
```

## Project layout

```
api/    Spring Boot 3.5, Java 21, Gradle — pipeline + REST API
web/    React 19 + TypeScript + Vite + Tailwind — two-page UI
data/   created on first boot — pg/ (Postgres data) + uploads/ (PDFs)
```

## Architecture notes

- **Two LLM calls only**: extract (PDF → structured JSON via tool-use) + map (each line → account code + reasoning + confidence). Arithmetic is always code, never LLM.
- **Provider seams**: both `Extractor` and `Mapper` are project-shaped interfaces. Swapping to OpenAI is two new `@Component`s and a config flag.
- **Embedded Postgres**: `io.zonky.test:embedded-postgres` — real Postgres 14.10 binary, no Docker.
- **Confidence surfaced**: per-posting confidence (0–1) from the mapper is stored and shown in the UI so an accountant knows where to scrutinize.

## What's deferred (by design)

- No Docker.
- No auth, no multi-user, no streaming.
- No retry on LLM failure (surfaces as a 422/500; re-upload to retry).
- Edit-before-approve deferred to v2; data model accommodates it.
- Multi-currency, background jobs, observability stack.
