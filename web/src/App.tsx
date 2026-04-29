import { Routes, Route, Navigate } from 'react-router-dom'
import { Layout } from './Layout'
import { UploadPage } from './UploadPage'
import { ReviewPage } from './ReviewPage'
import { InvoicesPage } from './InvoicesPage'
import { AccountsPage } from './AccountsPage'
import { ActivityPage } from './ActivityPage'

export default function App() {
  return (
    <Routes>
      <Route element={<Layout />}>
        <Route path="/" element={<Navigate to="/invoices" replace />} />
        <Route path="/invoices" element={<InvoicesPage />} />
        <Route path="/invoices/:id" element={<ReviewPage />} />
        <Route path="/upload" element={<UploadPage />} />
        <Route path="/accounts" element={<AccountsPage />} />
        <Route path="/activity" element={<ActivityPage />} />
      </Route>
    </Routes>
  )
}
