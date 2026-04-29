import { test, expect } from '@playwright/test'
import { mockApi, SUGGESTION_ID_PENDING } from './fixtures/api-stubs'

test.beforeEach(async ({ page }) => {
  await mockApi(page)
})

test('upload page shows drop zone', async ({ page }) => {
  await page.goto('/upload')
  await expect(page.getByText('Drop a PDF invoice here')).toBeVisible()
  await expect(page.getByText('or click to select a file')).toBeVisible()
})

test('valid PDF triggers upload and navigates to review', async ({ page }) => {
  await page.goto('/upload')

  const fileChooserPromise = page.waitForEvent('filechooser')
  // The dropzone is a <label> wrapping an <input type=file> — click it to open the chooser.
  await page.locator('label').first().click()
  const chooser = await fileChooserPromise
  await chooser.setFiles({
    name: 'invoice.pdf',
    mimeType: 'application/pdf',
    buffer: Buffer.from('%PDF-1.4\n%mock\n'),
  })

  await expect(page).toHaveURL(new RegExp(`/invoices/${SUGGESTION_ID_PENDING}$`))
})

test('non-PDF file shows a validation error', async ({ page }) => {
  await page.goto('/upload')

  const fileChooserPromise = page.waitForEvent('filechooser')
  await page.locator('label').first().click()
  const chooser = await fileChooserPromise
  await chooser.setFiles({
    name: 'notes.txt',
    mimeType: 'text/plain',
    buffer: Buffer.from('not a pdf'),
  })

  await expect(page.getByText('Only PDF files are supported')).toBeVisible()
  await expect(page).toHaveURL(/\/upload$/)
})
