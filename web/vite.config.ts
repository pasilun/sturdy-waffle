import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import tailwindcss from '@tailwindcss/vite'

// `/invoices`, `/accounts`, `/activity` are both SPA routes (the React app)
// and proxied API paths. Without this bypass, refreshing on /invoices in the
// browser triggers a document GET → Vite proxies to the API → the user sees
// raw JSON. `Sec-Fetch-Dest: document` is sent only for top-level navigation;
// fetch requests send `empty`, iframes send `iframe`, so PDF iframes still
// proxy through correctly.
const bypassForSpaNavigation = (req: { headers: Record<string, string | string[] | undefined>, url?: string }) => {
  if (req.headers['sec-fetch-dest'] === 'document') return req.url
}

const apiTarget = {
  target: 'http://localhost:8080',
  bypass: bypassForSpaNavigation,
}

export default defineConfig({
  plugins: [react(), tailwindcss()],
  server: {
    proxy: {
      '/invoices': apiTarget,
      '/accounts': apiTarget,
      '/activity': apiTarget,
      '/health':   'http://localhost:8080',
    },
  },
})
