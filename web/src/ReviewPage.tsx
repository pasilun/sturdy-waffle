import { useParams } from 'react-router-dom'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { fetchSuggestion, recordDecision, type SuggestionResponse, type PostingResponse, type DecisionResponse, type InvoiceStatus } from './api'
import { StatusBadge } from './StatusBadge'
import { formatMoney } from './format'

function ConfidenceBar({ value }: { value: number | null }) {
  // Loose equality — backend may send `null` or omit the field entirely (→ undefined).
  // === null misses undefined and would render NaN%.
  if (value == null || !Number.isFinite(value)) return null
  const pct = Math.round(value * 100)
  const color = value >= 0.8 ? 'bg-green-500' : value >= 0.5 ? 'bg-amber-400' : 'bg-red-400'
  return (
    <div className="flex items-center gap-2 mt-1">
      <div className="flex-1 h-1.5 bg-gray-100 rounded-full overflow-hidden">
        <div className={`h-full ${color} rounded-full`} style={{ width: `${pct}%` }} />
      </div>
      <span className="text-xs text-gray-400 w-8 text-right">{pct}%</span>
    </div>
  )
}

function PostingsTable({ postings }: { postings: PostingResponse[] }) {
  return (
    <table className="w-full text-sm">
      <thead>
        <tr className="text-left text-xs text-gray-500 border-b border-gray-100">
          <th className="pb-2 pr-3 font-medium">Account</th>
          <th className="pb-2 pr-3 font-medium text-right">Debit</th>
          <th className="pb-2 font-medium text-right">Credit</th>
        </tr>
      </thead>
      <tbody>
        {postings.map((p) => (
          <tr key={p.lineIndex} className="border-b border-gray-50 last:border-0">
            <td className="py-3 pr-3">
              <div className="font-medium text-gray-800">
                {p.accountCode} — {p.accountName}
              </div>
              {p.description && (
                <div className="text-xs text-gray-500 mt-0.5">{p.description}</div>
              )}
              {p.reasoning && (
                <div className="text-xs text-gray-400 italic mt-0.5">{p.reasoning}</div>
              )}
              <ConfidenceBar value={p.confidence} />
            </td>
            <td className="py-3 pr-3 text-right font-mono text-gray-700 align-top">
              {p.debit !== '0.00' ? formatMoney(p.debit) : ''}
            </td>
            <td className="py-3 text-right font-mono text-gray-700 align-top">
              {p.credit !== '0.00' ? formatMoney(p.credit) : ''}
            </td>
          </tr>
        ))}
      </tbody>
    </table>
  )
}

function DecisionPanel({
  decision,
  onDecide,
  deciding,
}: {
  decision: DecisionResponse | null
  onDecide: (status: 'APPROVED' | 'DECLINED') => void
  deciding: boolean
}) {
  if (decision) {
    const isApproved = decision.status === 'APPROVED'
    return (
      <div
        className={`rounded-lg p-4 text-sm font-medium ${
          isApproved ? 'bg-green-50 text-green-700 border border-green-200' : 'bg-red-50 text-red-700 border border-red-200'
        }`}
      >
        {isApproved ? 'Approved' : 'Declined'} on{' '}
        {new Date(decision.decidedAt).toLocaleString()}
        {decision.note && <div className="font-normal mt-1 text-xs">{decision.note}</div>}
      </div>
    )
  }

  const btn = 'flex-1 py-2.5 px-4 rounded-lg text-white font-medium text-sm disabled:opacity-50 disabled:cursor-not-allowed transition-colors'
  return (
    <div className="flex gap-3">
      <button onClick={() => onDecide('APPROVED')} disabled={deciding} className={`${btn} bg-green-600 hover:bg-green-700`}>
        Approve
      </button>
      <button onClick={() => onDecide('DECLINED')} disabled={deciding} className={`${btn} bg-red-600 hover:bg-red-700`}>
        Decline
      </button>
    </div>
  )
}

function InvoiceHeader({ s }: { s: SuggestionResponse }) {
  const status: InvoiceStatus = (s.decision?.status ?? 'PENDING') as InvoiceStatus
  return (
    <div className="mb-6">
      <div className="flex items-center justify-between gap-3">
        <h2 className="text-lg font-semibold text-gray-800">{s.supplierName}</h2>
        <StatusBadge status={status} />
      </div>
      <div className="text-sm text-gray-500 mt-1">
        #{s.invoiceNumber} &middot; {s.invoiceDate}
      </div>
      <div className="flex gap-6 mt-3 text-sm">
        {([['Net', s.net], ['VAT', s.vat], ['Gross', s.gross]] as const).map(([label, value]) => (
          <div key={label}>
            <span className="text-gray-400">{label}</span>
            <div className="font-mono font-medium text-gray-700">{formatMoney(value)} {s.currency}</div>
          </div>
        ))}
      </div>
    </div>
  )
}

export function ReviewPage() {
  const { id } = useParams<{ id: string }>()
  const queryClient = useQueryClient()

  const { data, isLoading, error } = useQuery({
    queryKey: ['suggestion', id],
    queryFn: () => fetchSuggestion(id!),
    enabled: !!id,
    staleTime: Infinity,
  })

  const mutation = useMutation({
    mutationFn: (status: 'APPROVED' | 'DECLINED') => recordDecision(id!, status),
    onSuccess: decision => {
      queryClient.setQueryData(['suggestion', id], (old: SuggestionResponse) => ({
        ...old,
        decision,
      }))
    },
  })

  if (isLoading) {
    return (
      <div className="h-full flex items-center justify-center">
        <div className="text-gray-500">Loading…</div>
      </div>
    )
  }

  if (error || !data) {
    return (
      <div className="h-full flex items-center justify-center">
        <div className="text-red-600">
          {error instanceof Error ? error.message : 'Failed to load suggestion'}
        </div>
      </div>
    )
  }

  return (
    <div className="flex h-full bg-white">
      {/* Left: PDF viewer */}
      <div className="w-1/2 border-r border-gray-200">
        <iframe
          src={`/invoices/${id}/pdf`}
          className="w-full h-full"
          title="Invoice PDF"
        />
      </div>

      {/* Right: review panel */}
      <div className="w-1/2 flex flex-col overflow-hidden">
        <div className="flex-1 overflow-y-auto p-8">
          <InvoiceHeader s={data} />

          <div className="mb-6">
            <h3 className="text-xs font-semibold uppercase tracking-wide text-gray-400 mb-3">
              Proposed Journal Entry
            </h3>
            <PostingsTable postings={data.postings} />
          </div>
        </div>

        <div className="border-t border-gray-100 p-6">
          {mutation.error && (
            <div className="mb-3 text-sm text-red-600">
              {mutation.error instanceof Error ? mutation.error.message : 'Decision failed'}
            </div>
          )}
          <DecisionPanel
            decision={data.decision}
            onDecide={status => mutation.mutate(status)}
            deciding={mutation.isPending}
          />
        </div>
      </div>
    </div>
  )
}
