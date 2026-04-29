import { Link, useNavigate, useSearchParams } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import { fetchInvoices, type InvoiceListItem } from './api'
import { StatusBadge } from './StatusBadge'

const tabs = [
  { value: 'all', label: 'All' },
  { value: 'pending', label: 'Pending' },
  { value: 'approved', label: 'Approved' },
  { value: 'declined', label: 'Declined' },
] as const

type TabValue = (typeof tabs)[number]['value']

export function InvoicesPage() {
  const [searchParams, setSearchParams] = useSearchParams()
  const status = (searchParams.get('status') as TabValue) ?? 'all'
  const navigate = useNavigate()

  const { data, isLoading, error } = useQuery({
    queryKey: ['invoices', status],
    queryFn: () => fetchInvoices(status),
  })

  return (
    <div className="p-8 max-w-6xl mx-auto">
      <div className="flex items-center justify-between mb-6">
        <h1 className="text-xl font-semibold text-gray-800">Invoices</h1>
        <Link
          to="/upload"
          className="inline-flex items-center gap-2 px-3 py-1.5 rounded-md bg-gray-900 text-white text-sm font-medium hover:bg-gray-800 transition-colors"
        >
          <svg className="w-4 h-4" fill="none" stroke="currentColor" strokeWidth={2} viewBox="0 0 24 24">
            <path strokeLinecap="round" strokeLinejoin="round" d="M12 4v16m8-8H4" />
          </svg>
          Upload new
        </Link>
      </div>

      <div className="flex gap-1 border-b border-gray-200 mb-4">
        {tabs.map(tab => {
          const active = tab.value === status
          return (
            <button
              key={tab.value}
              onClick={() => {
                if (tab.value === 'all') setSearchParams({})
                else setSearchParams({ status: tab.value })
              }}
              className={`px-3 py-2 text-sm font-medium border-b-2 -mb-px transition-colors ${
                active
                  ? 'border-gray-900 text-gray-900'
                  : 'border-transparent text-gray-500 hover:text-gray-700'
              }`}
            >
              {tab.label}
            </button>
          )
        })}
      </div>

      {isLoading && <div className="text-sm text-gray-500 py-12 text-center">Loading…</div>}

      {error && (
        <div className="text-sm text-red-600 py-12 text-center">
          {error instanceof Error ? error.message : 'Failed to load invoices'}
        </div>
      )}

      {data && data.length === 0 && (
        <div className="text-center py-16 border border-dashed border-gray-200 rounded-lg">
          <div className="text-sm text-gray-500 mb-3">
            {status === 'all' ? 'No invoices yet' : `No ${status} invoices`}
          </div>
          {status === 'all' && (
            <Link
              to="/upload"
              className="inline-flex items-center gap-1.5 text-sm font-medium text-gray-900 hover:underline"
            >
              Upload your first
            </Link>
          )}
        </div>
      )}

      {data && data.length > 0 && (
        <table className="w-full text-sm">
          <thead>
            <tr className="text-left text-xs text-gray-500 border-b border-gray-200">
              <th className="py-2 pr-4 font-medium">Supplier</th>
              <th className="py-2 pr-4 font-medium">Invoice #</th>
              <th className="py-2 pr-4 font-medium">Date</th>
              <th className="py-2 pr-4 font-medium text-right">Gross</th>
              <th className="py-2 pr-4 font-medium">Status</th>
              <th className="py-2 font-medium">Decided</th>
            </tr>
          </thead>
          <tbody>
            {data.map(item => (
              <Row key={item.suggestionId} item={item} onClick={() => navigate(`/invoices/${item.suggestionId}`)} />
            ))}
          </tbody>
        </table>
      )}
    </div>
  )
}

function Row({ item, onClick }: { item: InvoiceListItem; onClick: () => void }) {
  return (
    <tr
      onClick={onClick}
      className="border-b border-gray-100 last:border-0 cursor-pointer hover:bg-gray-50 transition-colors"
    >
      <td className="py-3 pr-4 font-medium text-gray-800">{item.supplierName ?? '—'}</td>
      <td className="py-3 pr-4 text-gray-600">{item.invoiceNumber ?? '—'}</td>
      <td className="py-3 pr-4 text-gray-600">{item.invoiceDate ?? '—'}</td>
      <td className="py-3 pr-4 text-right font-mono text-gray-700">
        {item.gross ? `${item.gross} ${item.currency}` : '—'}
      </td>
      <td className="py-3 pr-4">
        <StatusBadge status={item.status} />
      </td>
      <td className="py-3 text-gray-500 text-xs">
        {item.decidedAt ? new Date(item.decidedAt).toLocaleDateString() : '—'}
      </td>
    </tr>
  )
}
