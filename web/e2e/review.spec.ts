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
