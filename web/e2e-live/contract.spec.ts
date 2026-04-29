import { test, expect, request } from '@playwright/test'

// Live read-only contract checks against a running dev.sh on :8080.
// No `page.route` mocking — every fetch hits the real backend through the
// Vite proxy. Asserts shape, not exact values, so existing data in the DB
// drives the test rather than fixed seeds.
//
// Run with `pnpm e2e:live`. Skip cleanly when :8080 isn't reachable so
// pre-push offline doesn't hard-fail.

test.describe('live API contract (read-only)', () => {
  test.beforeAll(async () => {
    const ctx = await request.newContext({ baseURL: 'http://localhost:8080' })
    const res = await ctx.get('/health').catch(() => null)
    await ctx.dispose()
    test.skip(!res || !res.ok(), 'API on :8080 not reachable — skipping live tier')
  })

  test('GET /invoices returns an array of InvoiceListItem-shaped rows', async ({ page }) => {
    const res = await page.request.get('/invoices?status=all')
    expect(res.ok()).toBe(true)
    const body = await res.json()
    expect(Array.isArray(body)).toBe(true)
    if (body.length > 0) {
      expect(body[0]).toMatchObject({
        suggestionId: expect.stringMatching(/^[0-9a-f-]{36}$/),
        invoiceId: expect.stringMatching(/^[0-9a-f-]{36}$/),
        status: expect.stringMatching(/^(PENDING|APPROVED|DECLINED)$/),
        currency: expect.any(String),
        createdAt: expect.any(String),
      })
    }
  })

  test('GET /accounts returns the seeded BAS chart', async ({ page }) => {
    const res = await page.request.get('/accounts')
    expect(res.ok()).toBe(true)
    const body = await res.json()
    expect(Array.isArray(body)).toBe(true)
    expect(body.length).toBeGreaterThanOrEqual(20)
    expect(body[0]).toMatchObject({
      code: expect.any(String),
      name: expect.any(String),
      type: expect.stringMatching(/^(ASSET|LIABILITY|EXPENSE)$/),
      normalSide: expect.stringMatching(/^(DEBIT|CREDIT)$/),
    })
  })

  test('GET /activity returns ActivityResponse-shaped events', async ({ page }) => {
    const res = await page.request.get('/activity')
    expect(res.ok()).toBe(true)
    const body = await res.json()
    expect(Array.isArray(body)).toBe(true)
    if (body.length > 0) {
      expect(body[0]).toMatchObject({
        id: expect.stringMatching(/^[0-9a-f-]{36}$/),
        event: expect.stringMatching(/^(suggestion\.created|decision\.approved|decision\.declined)$/),
        entityId: expect.stringMatching(/^[0-9a-f-]{36}$/),
        createdAt: expect.any(String),
      })
    }
  })

  test('invoices list page renders against real backend', async ({ page }) => {
    await page.goto('/invoices')
    // Either the table renders rows or the empty state shows — both are valid.
    const tableOrEmpty = page.getByRole('table').or(page.getByText('No invoices yet'))
    await expect(tableOrEmpty).toBeVisible()
  })

  test('accounts page renders 20+ rows against real backend', async ({ page }) => {
    await page.goto('/accounts')
    await expect(page.getByRole('heading', { name: 'Chart of accounts' })).toBeVisible()
    // Auto-wait for the TanStack query to populate the table — otherwise the
    // count samples before /accounts resolves and we see 0 on a cold backend.
    // 20 data rows + 1 header row = at least 21.
    await expect.poll(() => page.getByRole('row').count(), { timeout: 5000 })
        .toBeGreaterThanOrEqual(21)
  })

  test('activity page renders against real backend', async ({ page }) => {
    await page.goto('/activity')
    await expect(page.getByRole('heading', { name: 'Activity' })).toBeVisible()
  })
})
