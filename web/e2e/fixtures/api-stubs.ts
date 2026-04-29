import type { Page } from '@playwright/test'

// Fixture data shaped like the real /invoices, /accounts, /activity, and
// /invoices/:id responses. Kept verbose enough to exercise every column /
// state branch in the UI (Pending, Approved, Declined; with and without
// reasoning; varying confidence buckets).

const SUGGESTION_ID_APPROVED = '11111111-1111-1111-1111-111111111111'
const SUGGESTION_ID_PENDING = '22222222-2222-2222-2222-222222222222'
const SUGGESTION_ID_DECLINED = '33333333-3333-3333-3333-333333333333'

export const fixtures = {
  invoices: [
    {
      suggestionId: SUGGESTION_ID_APPROVED,
      invoiceId: 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa',
      supplierName: 'Acme AB',
      invoiceNumber: 'INV-1042',
      invoiceDate: '2026-04-01',
      currency: 'SEK',
      gross: '12500.00',
      status: 'APPROVED',
      decidedAt: '2026-04-28T12:00:00Z',
      createdAt: '2026-04-28T11:55:00Z',
    },
    {
      suggestionId: SUGGESTION_ID_PENDING,
      invoiceId: 'bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb',
      supplierName: 'Office Supplies Co',
      invoiceNumber: 'OS-2026-04-12',
      invoiceDate: '2026-04-12',
      currency: 'SEK',
      gross: '850.00',
      status: 'PENDING',
      decidedAt: null,
      createdAt: '2026-04-29T08:30:00Z',
    },
    {
      suggestionId: SUGGESTION_ID_DECLINED,
      invoiceId: 'cccccccc-cccc-cccc-cccc-cccccccccccc',
      supplierName: 'Dubious Vendor LLC',
      invoiceNumber: 'X-9000',
      invoiceDate: '2026-04-15',
      currency: 'SEK',
      gross: '99999.00',
      status: 'DECLINED',
      decidedAt: '2026-04-29T07:00:00Z',
      createdAt: '2026-04-29T06:55:00Z',
    },
  ],

  accounts: Array.from({ length: 20 }, (_, i) => {
    const code = String(1920 + i * 100).slice(0, 4)
    return {
      code,
      name: `Account ${code}`,
      type: i < 2 ? 'ASSET' : i === 2 ? 'LIABILITY' : 'EXPENSE',
      normalSide: i === 1 || i === 2 ? 'CREDIT' : 'DEBIT',
    }
  }),

  activity: [
    {
      id: 'eeeeeeee-eeee-eeee-eeee-eeeeeeeeeee1',
      event: 'decision.approved',
      entityId: SUGGESTION_ID_APPROVED,
      payload: '{}',
      createdAt: '2026-04-28T12:00:00Z',
    },
    {
      id: 'eeeeeeee-eeee-eeee-eeee-eeeeeeeeeee2',
      event: 'suggestion.created',
      entityId: SUGGESTION_ID_PENDING,
      payload: '{"invoiceId":"bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb","lineCount":3}',
      createdAt: '2026-04-29T08:30:00Z',
    },
    {
      id: 'eeeeeeee-eeee-eeee-eeee-eeeeeeeeeee3',
      event: 'decision.declined',
      entityId: SUGGESTION_ID_DECLINED,
      payload: '{"note":"suspicious amount"}',
      createdAt: '2026-04-29T07:00:00Z',
    },
  ],

  suggestion: (status: 'APPROVED' | 'PENDING' | 'DECLINED' = 'PENDING') => ({
    id: status === 'APPROVED' ? SUGGESTION_ID_APPROVED : status === 'DECLINED' ? SUGGESTION_ID_DECLINED : SUGGESTION_ID_PENDING,
    invoiceId: 'bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb',
    supplierName: 'Office Supplies Co',
    invoiceNumber: 'OS-2026-04-12',
    invoiceDate: '2026-04-12',
    currency: 'SEK',
    net: '680.00',
    vat: '170.00',
    gross: '850.00',
    postings: [
      {
        lineIndex: 0,
        accountCode: '6110',
        accountName: 'Kontorsmaterial',
        debit: '680.00',
        credit: '0.00',
        description: 'Pens and notebooks',
        reasoning: 'Office supplies are mapped to Kontorsmaterial.',
        confidence: 0.95,
      },
      {
        lineIndex: 1,
        accountCode: '2640',
        accountName: 'Ingående moms',
        debit: '170.00',
        credit: '0.00',
        description: 'VAT 25%',
        reasoning: null,
        confidence: null,
      },
      {
        lineIndex: 2,
        accountCode: '2440',
        accountName: 'Leverantörsskulder',
        debit: '0.00',
        credit: '850.00',
        description: 'Accounts payable',
        reasoning: null,
        confidence: null,
      },
    ],
    decision:
      status === 'PENDING'
        ? null
        : {
            status,
            decidedAt: '2026-04-28T12:00:00Z',
            note: status === 'DECLINED' ? 'suspicious amount' : null,
          },
  }),
}

const UUID = /^\/invoices\/([0-9a-f-]{36})$/
const DECISION = /^\/invoices\/[0-9a-f-]{36}\/decision$/
const PDF = /^\/invoices\/[0-9a-f-]{36}\/pdf$/

/**
 * Wire JSON stubs onto every API endpoint the frontend touches. A single
 * `page.route('**\/*', ...)` matcher dispatches by pathname + method so that
 * Vite's own dev-server URLs (e.g. `/src/ActivityPage.tsx`) are NOT intercepted
 * — they fall through to `route.continue()`.
 */
export async function mockApi(
  page: Page,
  overrides: {
    invoices?: unknown
    accounts?: unknown
    activity?: unknown
    suggestion?: unknown
    uploadResponse?: { id: string }
  } = {},
) {
  await page.route('**/*', route => {
    // Only intercept actual API fetches — let Vite serve documents (the SPA's
    // own HTML), scripts, modules, etc. Otherwise navigating to /invoices in
    // the browser returns the mocked JSON instead of index.html.
    const isApiFetch = route.request().resourceType() === 'fetch'
    if (!isApiFetch) return route.continue()

    const url = new URL(route.request().url())
    const path = url.pathname
    const method = route.request().method()

    if (path === '/invoices' && method === 'GET') {
      return route.fulfill({ json: overrides.invoices ?? fixtures.invoices })
    }
    if (path === '/invoices' && method === 'POST') {
      return route.fulfill({ json: overrides.uploadResponse ?? { id: SUGGESTION_ID_PENDING } })
    }
    if (path === '/accounts') {
      return route.fulfill({ json: overrides.accounts ?? fixtures.accounts })
    }
    if (path === '/activity') {
      return route.fulfill({ json: overrides.activity ?? fixtures.activity })
    }
    if (DECISION.test(path)) {
      return route.fulfill({
        json: { status: 'APPROVED', decidedAt: new Date().toISOString(), note: null },
      })
    }
    if (PDF.test(path)) {
      return route.fulfill({
        contentType: 'application/pdf',
        body: Buffer.from('%PDF-1.4\n%mock\n'),
      })
    }
    const idMatch = path.match(UUID)
    if (idMatch) {
      const id = idMatch[1]
      let body = overrides.suggestion ?? fixtures.suggestion('PENDING')
      if (!overrides.suggestion && id === SUGGESTION_ID_APPROVED) body = fixtures.suggestion('APPROVED')
      if (!overrides.suggestion && id === SUGGESTION_ID_DECLINED) body = fixtures.suggestion('DECLINED')
      return route.fulfill({ json: body })
    }

    return route.continue()
  })
}

export { SUGGESTION_ID_APPROVED, SUGGESTION_ID_PENDING, SUGGESTION_ID_DECLINED }
