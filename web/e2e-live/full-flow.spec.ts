import { test, expect, request } from '@playwright/test'
import path from 'node:path'

// Full happy-path against the live backend: upload sample.pdf, wait for the
// pipeline (~15s for two LLM calls), approve, return to /invoices, see the row.
// Burns Anthropic credits — gated behind E2E_FULL=1 so it doesn't run on every
// `pnpm e2e:live`. Run via `pnpm e2e:full`.

test.describe('live full flow (opt-in)', () => {
  test.skip(!process.env.E2E_FULL, 'Set E2E_FULL=1 to run the upload+approve flow')

  test.beforeAll(async () => {
    const ctx = await request.newContext({ baseURL: 'http://localhost:8080' })
    const res = await ctx.get('/health').catch(() => null)
    await ctx.dispose()
    test.skip(!res || !res.ok(), 'API on :8080 not reachable')
  })

  test('upload sample.pdf, approve, see row in list with Approved badge', async ({ page }) => {
    test.setTimeout(60_000) // allow ~30s for the LLM pipeline + approval round-trip

    await page.goto('/upload')

    const samplePdf = path.resolve(__dirname, '../../api/src/test/resources/sample.pdf')

    const fileChooserPromise = page.waitForEvent('filechooser')
    await page.locator('label').first().click()
    const chooser = await fileChooserPromise
    await chooser.setFiles(samplePdf)

    // Wait for the review page — the pipeline takes ~15s for extract + map.
    await expect(page).toHaveURL(/\/invoices\/[0-9a-f-]{36}$/, { timeout: 45_000 })

    // Approve.
    await page.getByRole('button', { name: 'Approve' }).click()
    await expect(page.getByText(/Approved on/)).toBeVisible()

    // Capture the suggestion id from the URL.
    const url = page.url()
    const id = url.match(/\/invoices\/([0-9a-f-]{36})$/)?.[1]
    expect(id).toBeDefined()

    // Back to the list — the new row should be there with an Approved badge.
    await page.goto('/invoices')
    const row = page.getByRole('row').filter({ hasText: 'Approved' }).first()
    await expect(row).toBeVisible()
  })
})
