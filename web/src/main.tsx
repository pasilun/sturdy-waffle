import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { BrowserRouter } from 'react-router-dom'
import { MutationCache, QueryCache, QueryClient, QueryClientProvider } from '@tanstack/react-query'
import './index.css'
import App from './App.tsx'
import { ErrorBoundary } from './ErrorBoundary'

const queryClient = new QueryClient({
  // TanStack v5 surfaces errors only via component-level `error` state by
  // default. Wire global console logging at the cache level so backend
  // failures are visible to anyone tailing the browser console (incl. the
  // Playwright MCP console-messages tool).
  queryCache: new QueryCache({
    onError: (error, query) =>
      console.error('[query] failed', query.queryKey, error instanceof Error ? error.message : error),
  }),
  mutationCache: new MutationCache({
    onError: (error, _vars, _ctx, mutation) =>
      console.error('[mutation] failed', mutation.options.mutationKey, error instanceof Error ? error.message : error),
  }),
})

// Catch errors that escape React's render path (event handlers, async,
// unhandled promise rejections). Without these, those errors die silently.
window.addEventListener('error', e =>
  console.error('[window.error]', e.message, e.error?.stack),
)
window.addEventListener('unhandledrejection', e =>
  console.error('[unhandledrejection]', e.reason instanceof Error ? e.reason.stack : e.reason),
)

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <ErrorBoundary>
      <BrowserRouter>
        <QueryClientProvider client={queryClient}>
          <App />
        </QueryClientProvider>
      </BrowserRouter>
    </ErrorBoundary>
  </StrictMode>,
)
