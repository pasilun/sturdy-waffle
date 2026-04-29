import { Link } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import { fetchActivity, type ActivityEvent, type ActivityResponse } from './api'

const labels: Record<ActivityEvent, string> = {
  'suggestion.created': 'Suggestion created',
  'decision.approved': 'Approved',
  'decision.declined': 'Declined',
}

const dotColors: Record<ActivityEvent, string> = {
  'suggestion.created': 'bg-gray-400',
  'decision.approved': 'bg-green-500',
  'decision.declined': 'bg-red-400',
}

function relativeTime(iso: string): string {
  const ms = Date.now() - new Date(iso).getTime()
  const s = Math.round(ms / 1000)
  if (s < 60) return `${s}s ago`
  const m = Math.round(s / 60)
  if (m < 60) return `${m}m ago`
  const h = Math.round(m / 60)
  if (h < 24) return `${h}h ago`
  const d = Math.round(h / 24)
  return `${d}d ago`
}

export function ActivityPage() {
  const { data, isLoading, error } = useQuery({
    queryKey: ['activity'],
    queryFn: fetchActivity,
  })

  return (
    <div className="p-8 max-w-3xl mx-auto">
      <div className="mb-6">
        <h1 className="text-xl font-semibold text-gray-800">Activity</h1>
        <p className="text-sm text-gray-500 mt-1">Recent events from the audit log.</p>
      </div>

      {isLoading && <div className="text-sm text-gray-500 py-12 text-center">Loading…</div>}

      {error && (
        <div className="text-sm text-red-600 py-12 text-center">
          {error instanceof Error ? error.message : 'Failed to load activity'}
        </div>
      )}

      {data && data.length === 0 && (
        <div className="text-center py-16 border border-dashed border-gray-200 rounded-lg text-sm text-gray-500">
          No activity yet
        </div>
      )}

      {data && data.length > 0 && (
        <ul className="divide-y divide-gray-100 border-t border-b border-gray-100">
          {data.map(event => (
            <Item key={event.id} event={event} />
          ))}
        </ul>
      )}
    </div>
  )
}

function Item({ event }: { event: ActivityResponse }) {
  const idSnippet = event.entityId.slice(-8)
  return (
    <li>
      <Link
        to={`/invoices/${event.entityId}`}
        className="flex items-center gap-3 py-3 px-2 hover:bg-gray-50 transition-colors"
      >
        <span className={`w-2 h-2 rounded-full ${dotColors[event.event]}`} />
        <span className="text-sm text-gray-800 font-medium">{labels[event.event]}</span>
        <span className="text-xs text-gray-400 font-mono">…{idSnippet}</span>
        <span className="ml-auto text-xs text-gray-400">{relativeTime(event.createdAt)}</span>
      </Link>
    </li>
  )
}
