import { useQuery } from '@tanstack/react-query'
import { fetchAccounts } from './api'

export function AccountsPage() {
  const { data, isLoading, error } = useQuery({
    queryKey: ['accounts'],
    queryFn: fetchAccounts,
    staleTime: Infinity,
  })

  return (
    <div className="p-8 max-w-4xl mx-auto">
      <div className="mb-6">
        <h1 className="text-xl font-semibold text-gray-800">Chart of accounts</h1>
        <p className="text-sm text-gray-500 mt-1">BAS kontoplan subset used by the mapper.</p>
      </div>

      {isLoading && <div className="text-sm text-gray-500 py-12 text-center">Loading…</div>}

      {error && (
        <div className="text-sm text-red-600 py-12 text-center">
          {error instanceof Error ? error.message : 'Failed to load accounts'}
        </div>
      )}

      {data && (
        <table className="w-full text-sm">
          <thead>
            <tr className="text-left text-xs text-gray-500 border-b border-gray-200">
              <th className="py-2 pr-4 font-medium">Code</th>
              <th className="py-2 pr-4 font-medium">Name</th>
              <th className="py-2 pr-4 font-medium">Type</th>
              <th className="py-2 font-medium">Normal side</th>
            </tr>
          </thead>
          <tbody>
            {data.map(account => (
              <tr key={account.code} className="border-b border-gray-100 last:border-0">
                <td className="py-2.5 pr-4 font-mono text-gray-700">{account.code}</td>
                <td className="py-2.5 pr-4 text-gray-800">{account.name}</td>
                <td className="py-2.5 pr-4 text-xs text-gray-500">{account.type}</td>
                <td className="py-2.5 text-xs text-gray-500">{account.normalSide}</td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
    </div>
  )
}
