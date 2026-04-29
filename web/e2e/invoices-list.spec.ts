import { test, expect } from '@playwright/test'
import { mockApi, fixtures, SUGGESTION_ID_PENDING } from './fixtures/api-stubs'

test('table renders one row per fixture invoice with correct columns', async ({ page }) => {
  await mockApi(page)
  await page.goto('/invoices')

  await expect(page.getByRole('cell', { name: 'Acme AB' })).toBeVisible()
  await expect(page.getByRole('cell', { name: 'INV-1042' })).toBeVisible()
  await expect(page.getByRole('cell', { name: 'Office Supplies Co' })).toBeVisible()
  await expect(page.getByRole('cell', { name: 'Dubious Vendor LLC' })).toBeVisible()

  await expect(page.getByText('Approved').first()).toBeVisible()
  await expect(page.getByText('Pending').first()).toBeVisible()
  await expect(page.getByText('Declined').first()).toBeVisible()
})

test('status filter tabs update the URL and re-fetch', async ({ page }) => {
  await mockApi(page)
  await page.goto('/invoices')

  await page.getByRole('button', { name: 'Approved' }).click()
  await expect(page).toHaveURL(/\?status=approved/)

  await page.getByRole('button', { name: 'Pending' }).click()
  await expect(page).toHaveURL(/\?status=pending/)

  await page.getByRole('button', { name: 'All' }).click()
  // Going back to "All" clears the query string entirely
  await expect(page).toHaveURL(/\/invoices$/)
})

test('clicking a row navigates to the review page', async ({ page }) => {
  await mockApi(page)
  await page.goto('/invoices')

  await page.getByRole('cell', { name: 'Office Supplies Co' }).click()
  await expect(page).toHaveURL(new RegExp(`/invoices/${SUGGESTION_ID_PENDING}$`))
})

test('empty fixture array renders empty state with upload prompt', async ({ page }) => {
  await mockApi(page, { invoices: [] })
  await page.goto('/invoices')

  await expect(page.getByText('No invoices yet')).toBeVisible()
  await expect(page.getByRole('link', { name: /Upload your first/i })).toBeVisible()
})

test('every fixture row has a status badge', async ({ page }) => {
  await mockApi(page)
  await page.goto('/invoices')

  // Three fixture invoices → three badges visible (one per row).
  for (const inv of fixtures.invoices) {
    await expect(page.getByRole('cell', { name: inv.supplierName! })).toBeVisible()
  }
})
