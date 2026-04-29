import { test, expect } from '@playwright/test'
import { mockApi, fixtures } from './fixtures/api-stubs'

test('accounts page renders one row per fixture account', async ({ page }) => {
  await mockApi(page)
  await page.goto('/accounts')

  await expect(page.getByRole('heading', { name: 'Chart of accounts' })).toBeVisible()

  // 20 fixture accounts → 20 data rows + 1 header row in the table.
  const rows = page.getByRole('row')
  await expect(rows).toHaveCount(fixtures.accounts.length + 1)

  // First fixture account's code is visible.
  await expect(page.getByRole('cell', { name: fixtures.accounts[0].code, exact: true })).toBeVisible()
})

test('accounts table shows code, name, type, normal side columns', async ({ page }) => {
  await mockApi(page)
  await page.goto('/accounts')

  await expect(page.getByRole('columnheader', { name: 'Code' })).toBeVisible()
  await expect(page.getByRole('columnheader', { name: 'Name' })).toBeVisible()
  await expect(page.getByRole('columnheader', { name: 'Type' })).toBeVisible()
  await expect(page.getByRole('columnheader', { name: 'Normal side' })).toBeVisible()
})
