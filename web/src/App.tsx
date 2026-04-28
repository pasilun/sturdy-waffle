import { Routes, Route } from 'react-router-dom'

// Pages — Phase 4 will flesh these out
function UploadPage() {
  return (
    <div className="min-h-screen bg-gray-50 flex items-center justify-center">
      <div className="text-center">
        <h1 className="text-2xl font-semibold text-gray-800 mb-2">Invoice Review</h1>
        <p className="text-gray-500">Upload page — Phase 4</p>
      </div>
    </div>
  )
}

function ReviewPage() {
  return (
    <div className="min-h-screen bg-gray-50 flex items-center justify-center">
      <p className="text-gray-500">Review page — Phase 4</p>
    </div>
  )
}

export default function App() {
  return (
    <Routes>
      <Route path="/" element={<UploadPage />} />
      <Route path="/invoices/:id" element={<ReviewPage />} />
    </Routes>
  )
}
