import { Routes, Route } from 'react-router-dom'
import { UploadPage } from './UploadPage'
import { ReviewPage } from './ReviewPage'

export default function App() {
  return (
    <Routes>
      <Route path="/" element={<UploadPage />} />
      <Route path="/invoices/:id" element={<ReviewPage />} />
    </Routes>
  )
}
