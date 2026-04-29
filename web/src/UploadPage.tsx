import { useState, useCallback } from 'react'
import { useNavigate } from 'react-router-dom'
import { uploadInvoice } from './api'

export function UploadPage() {
  const navigate = useNavigate()
  const [dragging, setDragging] = useState(false)
  const [uploading, setUploading] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const handleFile = useCallback(
    async (file: File) => {
      if (file.type !== 'application/pdf') {
        setError('Only PDF files are supported')
        return
      }
      setUploading(true)
      setError(null)
      try {
        const { id } = await uploadInvoice(file)
        navigate(`/invoices/${id}`)
      } catch (e) {
        setError(e instanceof Error ? e.message : 'Upload failed')
        setUploading(false)
      }
    },
    [navigate],
  )

  const onDrop = useCallback(
    (e: React.DragEvent) => {
      e.preventDefault()
      setDragging(false)
      const file = e.dataTransfer.files[0]
      if (file) handleFile(file)
    },
    [handleFile],
  )

  return (
    <div className="flex items-center justify-center min-h-full p-8">
      <div className="w-full max-w-md">
        <h1 className="text-xl font-semibold text-gray-800 mb-6 text-center">
          Upload invoice
        </h1>

        <label
          className={`block border-2 border-dashed rounded-xl p-16 text-center cursor-pointer transition-colors ${
            uploading
              ? 'border-gray-200 bg-gray-100 cursor-not-allowed'
              : dragging
                ? 'border-blue-500 bg-blue-50'
                : 'border-gray-300 bg-white hover:border-gray-400'
          }`}
          onDrop={onDrop}
          onDragOver={e => {
            e.preventDefault()
            if (!uploading) setDragging(true)
          }}
          onDragLeave={() => setDragging(false)}
        >
          <input
            type="file"
            accept="application/pdf"
            className="hidden"
            disabled={uploading}
            onChange={e => {
              const f = e.target.files?.[0]
              if (f) handleFile(f)
            }}
          />
          {uploading ? (
            <div>
              <div className="text-gray-600 font-medium mb-1">Analyzing invoice…</div>
              <div className="text-sm text-gray-400">This may take ~15 seconds</div>
            </div>
          ) : (
            <div>
              <svg
                className="mx-auto mb-4 w-10 h-10 text-gray-400"
                fill="none"
                stroke="currentColor"
                viewBox="0 0 24 24"
              >
                <path
                  strokeLinecap="round"
                  strokeLinejoin="round"
                  strokeWidth={1.5}
                  d="M9 13h6m-3-3v6m5 5H7a2 2 0 01-2-2V5a2 2 0 012-2h5.586a1 1 0 01.707.293l5.414 5.414a1 1 0 01.293.707V19a2 2 0 01-2 2z"
                />
              </svg>
              <div className="text-gray-700 font-medium mb-1">Drop a PDF invoice here</div>
              <div className="text-sm text-gray-400">or click to select a file</div>
            </div>
          )}
        </label>

        {error && (
          <div className="mt-4 p-3 bg-red-50 border border-red-200 rounded-lg text-red-700 text-sm">
            {error}
          </div>
        )}
      </div>
    </div>
  )
}
