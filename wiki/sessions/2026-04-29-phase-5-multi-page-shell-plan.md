---
title: Phase 5 plan — multi-page app shell
type: session
created: 2026-04-29
updated: 2026-04-29
tags: [invoice-to-journal, planning, frontend, backend]
---

# Phase 5 plan — multi-page app shell

Planning session capturing the design for turning the current 2-page app (upload → review) into a small but real back-office app you can click around in: persistent left sidebar, list of all processed invoices with status filtering, chart-of-accounts page, activity feed pulled from `audit_events`. See [[invoice-to-journal]] for project context.

## Why

Today the app is a one-way path: `/` (upload) → `/invoices/:id` (review) → done. Most data already exists in the DB (invoices, decisions, accounts seeded, `audit_events` written on every event) — none of it is exposed. Phase 5 makes the data navigable without changing the pipeline or write paths.

## Outcome

- Persistent left sidebar with 4 nav items (Invoices, Upload, Accounts, Activity), visible on every page including review.
- `/` redirects to `/invoices`.
- `/invoices` list view with status filter tabs and click-through to review.
- `/accounts` read-only chart-of-accounts page.
- `/activity` reverse-chrono feed from `audit_events`.

## Routing

```
/                → <Navigate to="/invoices" replace />   (inside Layout)
/invoices        → InvoicesPage                           (inside Layout)
/upload          → UploadPage    (existing, restyled)     (inside Layout)
/accounts        → AccountsPage                           (inside Layout)
/activity        → ActivityPage                           (inside Layout)
/invoices/:id    → ReviewPage    (existing)               (inside Layout)
```

All pages render inside the Layout shell. The review page's split view becomes 50/50 of the content area (sidebar `w-56` ≈ 224 px; on a 1440 px screen the remaining 1216 px split 608/608 is plenty for PDF + postings).

## Sidebar

Fixed `w-56` left rail, white bg, `border-r border-gray-200`, full height. Top: small wordmark "Sturdy Waffle" with a subtitle "Invoice review". Below: vertical nav of 4 `NavLink`s with inline-SVG icons. Active state via `NavLink`'s callback className: `bg-gray-100 text-gray-900 font-medium`; inactive: `text-gray-600 hover:bg-gray-50`. Content: `flex-1 overflow-y-auto bg-gray-50`.

## Backend

### New endpoints

**`GET /invoices?status={pending|approved|declined|all}&limit=100`** → `InvoiceListItem[]`
```
{ suggestionId, invoiceId, supplierName, invoiceNumber, invoiceDate,
  currency, gross, status: "PENDING"|"APPROVED"|"DECLINED",
  decidedAt, createdAt }
```
Default `status=all`, `limit=100` (clamped to 500). Sorted by `created_at DESC`.

**`GET /accounts`** → `AccountResponse[]`
```
{ code, name, type, normalSide }
```
Sorted by `code` ASC.

**`GET /activity?limit=100`** → `ActivityResponse[]`
```
{ id, event, entityId, payload, createdAt }
```
Filtered to `event IN ('suggestion.created','decision.approved','decision.declined')`. No joins — frontend humanizes the event string and uses `entityId` to deep-link to `/invoices/:entityId`.

### New files

- `api/.../web/dto/InvoiceListItem.java`
- `api/.../web/dto/AccountResponse.java`
- `api/.../web/dto/ActivityResponse.java`
- `api/.../infrastructure/persistence/InvoiceListQuery.java`
- `api/.../infrastructure/persistence/AccountQuery.java`
- `api/.../infrastructure/persistence/ActivityQuery.java`
- `api/.../web/AccountController.java`
- `api/.../web/ActivityController.java`

### New SQL

```sql
-- InvoiceListQuery.listInvoices(status, limit)
SELECT s.id AS suggestion_id, i.id AS invoice_id, i.supplier_name,
       i.invoice_number, i.invoice_date, i.currency, i.gross, i.created_at,
       COALESCE(d.status, 'PENDING') AS status, d.decided_at
FROM suggestions s
JOIN invoices i ON i.id = s.invoice_id
LEFT JOIN decisions d ON d.suggestion_id = s.id
WHERE (? IS NULL OR COALESCE(d.status,'PENDING') = ?)
ORDER BY s.created_at DESC
LIMIT ?

-- AccountQuery.listAll()
SELECT code, name, type, normal_side FROM accounts ORDER BY code

-- ActivityQuery.recent(limit)
SELECT id, entity, entity_id, event, payload_json, created_at
FROM audit_events
WHERE event IN ('suggestion.created','decision.approved','decision.declined')
ORDER BY created_at DESC
LIMIT ?
```

### Modify

- `api/.../web/InvoiceController.java` — add `GET /invoices` (no id) handler. Spring matches by exact path so this does not collide with `GET /invoices/{id}`.

## Frontend

### New files

- `web/src/Layout.tsx` — sidebar + `<Outlet/>` shell
- `web/src/InvoicesPage.tsx` — `/invoices` table with status tabs
- `web/src/AccountsPage.tsx` — `/accounts` read-only table
- `web/src/ActivityPage.tsx` — `/activity` reverse-chrono feed
- `web/src/StatusBadge.tsx` — gray (Pending) / green (Approved) / red (Declined) pill

### Modify

- `web/src/App.tsx` — replace flat `Routes` with `<Route element={<Layout/>}>` wrapping all routes including `/invoices/:id`. Add `/` → `<Navigate to="/invoices" replace/>`.
- `web/src/api.ts` — `fetchInvoices(status?)`, `fetchAccounts()`, `fetchActivity()` plus their response types.
- `web/src/UploadPage.tsx` — drop `min-h-screen` outer wrapper; now lives inside Layout.
- `web/src/ReviewPage.tsx` — replace top-level `flex h-screen` with a layout that fits inside Layout's content area; render `<StatusBadge>` next to invoice header.

### Page details

**InvoicesPage**: header with title + "Upload new" button (right-aligned, links to `/upload`). Tab strip: All / Pending / Approved / Declined (URL-driven via `?status=`). Cols: Supplier · Invoice # · Date · Gross · Status · Decided. Empty state: "No invoices yet — upload your first" + button. Row click navigates to `/invoices/:suggestionId`.

**AccountsPage**: title + table. Cols: Code · Name · Type · Normal side. Sorted by code.

**ActivityPage**: title + vertical list. Each row: small icon, one-line label (`Suggestion created` / `Approved` / `Declined`), suggestion id last 8 chars, relative timestamp ("2h ago"). Whole row links to `/invoices/{entityId}`. Minimal — no joins to invoices/suggestions.

## Implementation order

1. Backend: `InvoiceListQuery` + `InvoiceListItem` DTO + `GET /invoices` handler. Test with curl.
2. Frontend: `Layout` + sidebar + route restructure. Placeholders for the three new pages so nav works.
3. Frontend: `InvoicesPage` + `StatusBadge` + `fetchInvoices` in api.ts. End-to-end demo of list → click → review.
4. Backend + frontend: accounts. Query, DTO, controller, page, fetcher.
5. Backend + frontend: activity. Query, DTO, controller, page, fetcher.
6. Polish: trim duplicate `min-h-screen` shells from `UploadPage` and `ReviewPage` so they fit inside Layout cleanly. `StatusBadge` on ReviewPage header.

Reasoning: each step is independently demoable; backend-before-frontend per feature avoids mocking; layout lands early so styling debt doesn't accumulate.

## UX decisions (confirmed)

- `/` → `/invoices` (no dashboard, deferred)
- Sidebar visible on `/invoices/:id` review page (consistent app feel)
- Status filter: tabs (4 options, frequent switching)
- Activity feed: minimal — no joins; show event + suggestion id snippet + relative time
- Sidebar wordmark: "Sturdy Waffle" + "Invoice review" subtitle (placeholder)

## Verification

1. `./dev.sh` — both API and web up
2. `curl -s localhost:8080/invoices | jq` returns array; `curl -s localhost:8080/accounts | jq` returns 20 accounts; `curl -s localhost:8080/activity | jq` returns recent events
3. Browser: `/` redirects to `/invoices`; sidebar shows 4 items with active highlighting
4. Upload sample PDF → auto-navigate to review → sidebar still visible → Approve
5. Back to "Invoices" → row appears with Approved badge → click row → review with prior decision
6. Status tabs update list
7. Accounts page renders 20 BAS accounts
8. Activity page shows `decision.approved` and `suggestion.created` events with click-through
9. `cd web && ./node_modules/.bin/tsc --noEmit -p tsconfig.app.json` clean
10. `cd api && ./gradlew test` passes
