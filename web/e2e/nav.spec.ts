import { test, expect } from '@playwright/test'
import { mockApi } from './fixtures/api-stubs'

test.beforeEach(async ({ page }) => {
  await mockApi(page)
})

test('/ redirects to /invoices', async ({ page }) => {
  await page.goto('/')
  await expect(page).toHaveURL(/\/invoices(\?|$)/)
})

test('sidebar shows the four nav items', async ({ page }) => {
  await page.goto('/invoices')
  const nav = page.getByRole('navigation')
  await expect(nav.getByRole('link', { name: 'Invoices' })).toBeVisible()
  await expect(nav.getByRole('link', { name: 'Upload' })).toBeVisible()
  await expect(nav.getByRole('link', { name: 'Accounts' })).toBeVisible()
  await expect(nav.getByRole('link', { name: 'Activity' })).toBeVisible()
})

test('clicking each nav item navigates and updates active state', async ({ page }) => {
  await page.goto('/invoices')
  const nav = page.getByRole('navigation')

  await nav.getByRole('link', { name: 'Accounts' }).click()
  await expect(page).toHaveURL(/\/accounts$/)

  await nav.getByRole('link', { name: 'Activity' }).click()
  await expect(page).toHaveURL(/\/activity$/)

  await nav.getByRole('link', { name: 'Upload' }).click()
  await expect(page).toHaveURL(/\/upload$/)

  await nav.getByRole('link', { name: 'Invoices' }).click()
  await expect(page).toHaveURL(/\/invoices(\?|$)/)
})
