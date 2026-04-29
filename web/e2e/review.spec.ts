import { test, expect } from '@playwright/test'
import { mockApi, SUGGESTION_ID_PENDING, SUGGESTION_ID_APPROVED, fixtures } from './fixtures/api-stubs'

test('review page renders split view with PDF iframe and postings table', async ({ page }) => {
  await mockApi(page)
  await page.goto(`/invoices/${SUGGESTION_ID_PENDING}`)

  await expect(page.getByRole('heading', { name: 'Office Supplies Co' })).toBeVisible()
  await expect(page.locator('iframe[title="Invoice PDF"]')).toBeVisible()

  // Postings table shows mapped account.
  await expect(page.getByText('6110 — Kontorsmaterial')).toBeVisible()
})

test('pending invoice shows Approve/Decline buttons; approved invoice shows decision', async ({ page }) => {
  await mockApi(page)
  await page.goto(`/invoices/${SUGGESTION_ID_PENDING}`)

  await expect(page.getByRole('button', { name: 'Approve' })).toBeVisible()
  await expect(page.getByRole('button', { name: 'Decline' })).toBeVisible()

  await page.goto(`/invoices/${SUGGESTION_ID_APPROVED}`)
  await expect(page.getByRole('button', { name: 'Approve' })).not.toBeVisible()
  await expect(page.getByText(/Approved on/)).toBeVisible()
})

test('approving a pending invoice posts and updates the UI', async ({ page }) => {
  await mockApi(page)
  await page.goto(`/invoices/${SUGGESTION_ID_PENDING}`)

  const decisionRequest = page.waitForRequest(req =>
    req.url().includes('/decision') && req.method() === 'POST',
  )
  await page.getByRole('button', { name: 'Approve' }).click()

  const req = await decisionRequest
  expect(req.postDataJSON()).toMatchObject({ status: 'APPROVED' })
  await expect(page.getByText(/Approved on/)).toBeVisible()
})

test('confidence bars render in colors mapped from fixture values', async ({ page }) => {
  await mockApi(page)
  await page.goto(`/invoices/${SUGGESTION_ID_PENDING}`)

  // Fixture has confidence 0.95 on the first posting → green-500
  // and null on the others (no bar).
  const greenBar = page.locator('[class*="bg-green-500"]').first()
  await expect(greenBar).toBeVisible()

  // Sanity: the fixture's first posting confidence renders as 95%
  await expect(page.getByText('95%').first()).toBeVisible()
  // Verify the fixture is what we expect.
  expect(fixtures.suggestion('PENDING').postings[0].confidence).toBe(0.95)
})

test('null-confidence postings render no bar (no NaN%)', async ({ page }) => {
  await mockApi(page)
  await page.goto(`/invoices/${SUGGESTION_ID_PENDING}`)

  // Fixture postings 1 and 2 have confidence: null. Make sure the page
  // never shows "NaN%" — that was the bug from the first MCP browser review.
  await expect(page.getByText('NaN%')).toHaveCount(0)
  await expect(page.getByText(/NaN/)).toHaveCount(0)
})

test('null-confidence also caught when JSON omits the field (undefined)', async ({ page }) => {
  // Backend may serialize null Doubles as omitted fields depending on Jackson
  // config; the guard must handle both null and undefined.
  await mockApi(page, {
    suggestion: {
      ...fixtures.suggestion('PENDING'),
      postings: fixtures.suggestion('PENDING').postings.map(p => {
        const { confidence, ...rest } = p
        return rest // strip the field entirely
      }),
    },
  })
  await page.goto(`/invoices/${SUGGESTION_ID_PENDING}`)
  await expect(page.getByText(/NaN/)).toHaveCount(0)
})

test('money amounts render with sv-SE locale formatting', async ({ page }) => {
  await mockApi(page)
  await page.goto(`/invoices/${SUGGESTION_ID_PENDING}`)

  // Fixture gross is "850.00" → sv-SE format is "850,00" (comma decimal,
  // no thousands separator below 10 000). Larger fixture amounts would get
  // a non-breaking space — covered by the invoices-list test.
  await expect(page.getByText('850,00 SEK')).toBeVisible()
})

test('escalate mapping replaces postings with the escalation response', async ({ page }) => {
  // Pending invoice → click Escalate → mock returns a different account
  // for the first posting → UI re-renders with the new mapping.
  // Pins the contract that the response replaces local state, not just
  // a fire-and-forget request.
  await mockApi(page)
  await page.goto(`/invoices/${SUGGESTION_ID_PENDING}`)

  // Sanity: starts on Kontorsmaterial.
  await expect(page.getByText('6110 — Kontorsmaterial')).toBeVisible()

  const escalateRequest = page.waitForRequest(req =>
    req.url().includes('/escalate-mapping') && req.method() === 'POST',
  )
  await page.getByRole('button', { name: 'Escalate mapping' }).click()
  await escalateRequest

  // After the request resolves the postings table re-renders.
  await expect(page.getByText('6540 — IT-tjänster')).toBeVisible()
  await expect(page.getByText('6110 — Kontorsmaterial')).not.toBeVisible()
})

test('escalate button is hidden once a decision exists', async ({ page }) => {
  // The lock invariant: approved/declined suggestions never see the
  // button. Backend returns 409 if called anyway, but the UI shouldn't
  // even show it. Spec invariant — pinned regardless of how the button
  // is rendered (component name, CSS class, etc.).
  await mockApi(page)
  await page.goto(`/invoices/${SUGGESTION_ID_APPROVED}`)

  await expect(page.getByRole('button', { name: 'Escalate mapping' })).toHaveCount(0)
})
