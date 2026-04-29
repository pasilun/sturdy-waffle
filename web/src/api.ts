export type PostingResponse = {
  lineIndex: number
  accountCode: string
  accountName: string
  debit: string
  credit: string
  description: string
  reasoning: string | null
  confidence: number | null
}

export type DecisionResponse = {
  status: 'APPROVED' | 'DECLINED'
  decidedAt: string
  note: string | null
}

export type SuggestionResponse = {
  id: string
  invoiceId: string
  supplierName: string
  invoiceNumber: string
  invoiceDate: string
  currency: string
  net: string
  vat: string
  gross: string
  postings: PostingResponse[]
  decision: DecisionResponse | null
}

export async function uploadInvoice(file: File): Promise<{ id: string }> {
  const form = new FormData()
  form.append('file', file)
  const res = await fetch('/invoices', { method: 'POST', body: form })
  if (!res.ok) {
    const text = await res.text().catch(() => '')
    throw new Error(`Upload failed (${res.status})${text ? ': ' + text : ''}`)
  }
  return res.json()
}

export async function fetchSuggestion(id: string): Promise<SuggestionResponse> {
  const res = await fetch(`/invoices/${id}`)
  if (!res.ok) throw new Error(`Not found (${res.status})`)
  return res.json()
}

export async function recordDecision(
  id: string,
  status: 'APPROVED' | 'DECLINED',
  note?: string,
): Promise<DecisionResponse> {
  const res = await fetch(`/invoices/${id}/decision`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ status, note: note ?? null }),
  })
  if (!res.ok) throw new Error(`Decision failed (${res.status})`)
  return res.json()
}
