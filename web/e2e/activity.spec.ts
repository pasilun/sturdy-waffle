import { test, expect } from '@playwright/test'
import { mockApi, SUGGESTION_ID_PENDING } from './fixtures/api-stubs'

test.beforeEach(async ({ page }) => {
  await mockApi(page)
})

test('activity feed renders humanized labels', async ({ page }) => {
  await page.goto('/activity')

  await expect(page.getByText('Suggestion created')).toBeVisible()
  await expect(page.getByText('Approved').first()).toBeVisible()
  await expect(page.getByText('Declined').first()).toBeVisible()
})

test('clicking a feed row deep-links to the review page', async ({ page }) => {
  await page.goto('/activity')

  await page.getByText('Suggestion created').click()
  await expect(page).toHaveURL(new RegExp(`/invoices/${SUGGESTION_ID_PENDING}$`))
})

test('empty activity renders empty state', async ({ page }) => {
  await mockApi(page, { activity: [] })
  await page.goto('/activity')

  await expect(page.getByText('No activity yet')).toBeVisible()
})
