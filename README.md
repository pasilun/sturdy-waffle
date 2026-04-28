# sturdy-waffle

Invoice-to-journal-entry assistant. Upload a PDF invoice; the app extracts line items via Claude, maps each line to a chart-of-accounts code, and presents a double-entry bookkeeping suggestion for approval.

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

First boot downloads the embedded Postgres binary (~30 MB, cached under `~/.zonky/` afterwards). Subsequent starts are fast.

`data/pg/` holds the database and `data/uploads/` holds PDF blobs — both survive restarts.

## What works now

| Endpoint | Status |
|---|---|
| `POST /invoices` (multipart `file`) | Runs full extract → validate → map → assemble pipeline. Returns `{"id": "<uuid>"}`. |
| `GET /health` | Returns `{"status": "UP"}`. |
| `GET /invoices/:id` | Not yet implemented (step 4). |
| `GET /invoices/:id/pdf` | Not yet implemented (step 4). |
| `POST /invoices/:id/decision` | Not yet implemented (step 4). |

The web UI (`localhost:5173`) starts but shows placeholder pages — the upload and review views are step 5.

Data is **not yet persisted**: the pipeline runs end-to-end and postings are assembled in memory, but nothing is written to the database. The returned UUID is ephemeral.

## Quick cURL test

```bash
curl -s -F "file=@invoice.pdf" http://localhost:8080/invoices | jq
# → {"id":"xxxxxxxx-xxxx-..."}
```

## Project layout

```
api/    Spring Boot 3, Java 21, Gradle
web/    React + TypeScript + Vite
docs/   SPEC.md (requirements), PLAN.md (implementation plan)
data/   created on first boot — pg/ (Postgres data) + uploads/ (PDFs)
```

## What's deferred (by design)

- No Docker — Postgres runs embedded via `io.zonky.test:embedded-postgres`.
- No auth, no multi-user, no streaming.
- No retry on LLM failure (surfaces as a 422/500; re-upload to retry).
- Multi-currency, background jobs, observability stack.
