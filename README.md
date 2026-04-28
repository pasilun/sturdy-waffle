# sturdy-waffle

Invoice-to-journal-entry assistant. Upload a PDF invoice; the app extracts line items via Claude, maps each line to a BAS chart-of-accounts code, and presents a balanced double-entry journal entry for an accountant to approve or decline.

## Prerequisites

- JDK 21 (`java -version` should show `21`)
- Node 20+ with pnpm (`pnpm --version`)
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

1. **Upload** — drag-drop (or click to select) a PDF invoice on the home page. The pipeline runs in ~15 s (two Claude calls: extract + map).
2. **Review** — a split view shows the original PDF on the left and the proposed journal entry on the right. Each posting shows the account code, debit/credit amounts, a one-line reasoning, and a confidence bar (green ≥ 80 %, amber 50–80 %, red < 50 %).
3. **Decide** — click **Approve** or **Decline**. The decision is persisted with an audit timestamp.

## API

| Endpoint | Description |
|---|---|
| `POST /invoices` (multipart `file`) | Runs full pipeline. Returns `{"id": "<uuid>"}`. |
| `GET /invoices/:id` | Suggestion + postings + decision (if any). |
| `GET /invoices/:id/pdf` | Raw PDF bytes (served to the in-browser iframe). |
| `POST /invoices/:id/decision` | Body: `{"status": "APPROVED"\|"DECLINED", "note": null}`. Returns the persisted decision. |
| `GET /health` | Returns `{"status": "UP"}`. |

## Running the eval harness

```bash
cd api && ./gradlew :api:eval
```

Runs three PDF fixtures through the live pipeline (no DB) and prints a per-case table: extract pass/fail, map accuracy, average confidence, latency. Use this as a canary before editing prompts or swapping models.

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
