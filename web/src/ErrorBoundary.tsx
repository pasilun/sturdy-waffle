import { Component, type ReactNode } from 'react'

type Props = { children: ReactNode }
type State = { error: Error | null }

// Catches render-phase errors anywhere below it. Without this, a thrown
// error in a child component blanks the whole page with no diagnostic.
// Console output goes to the browser dev tools and to the Playwright MCP
// console-messages tool — both surfaces benefit.
export class ErrorBoundary extends Component<Props, State> {
  state: State = { error: null }

  static getDerivedStateFromError(error: Error): State {
    return { error }
  }

  componentDidCatch(error: Error, info: { componentStack?: string | null }) {
    console.error('[error-boundary] render failed', {
      message: error.message,
      stack: error.stack,
      componentStack: info.componentStack,
    })
  }

  render() {
    if (this.state.error) {
      return (
        <div className="h-full flex items-center justify-center p-8">
          <div className="max-w-lg text-center">
            <h1 className="text-lg font-semibold text-red-700 mb-2">Something went wrong</h1>
            <p className="text-sm text-gray-600 mb-4">
              The page failed to render. Check the browser console for details.
            </p>
            <pre className="text-xs text-left bg-red-50 border border-red-200 rounded p-3 overflow-auto text-red-900">
              {this.state.error.message}
            </pre>
            <button
              onClick={() => this.setState({ error: null })}
              className="mt-4 text-sm px-3 py-1.5 rounded-md border border-gray-200 hover:bg-gray-50"
            >
              Try again
            </button>
          </div>
        </div>
      )
    }
    return this.props.children
  }
}
