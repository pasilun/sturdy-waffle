import type { InvoiceStatus } from './api'

const styles: Record<InvoiceStatus, string> = {
  PENDING: 'bg-gray-100 text-gray-700',
  APPROVED: 'bg-green-50 text-green-700',
  DECLINED: 'bg-red-50 text-red-700',
}

const labels: Record<InvoiceStatus, string> = {
  PENDING: 'Pending',
  APPROVED: 'Approved',
  DECLINED: 'Declined',
}

export function StatusBadge({ status }: { status: InvoiceStatus }) {
  return (
    <span className={`inline-flex items-center px-2 py-0.5 rounded-full text-xs font-medium ${styles[status]}`}>
      {labels[status]}
    </span>
  )
}
