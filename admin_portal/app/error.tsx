'use client'

export default function Error({
  error,
  reset,
}: {
  error: Error & { digest?: string }
  reset: () => void
}) {
  return (
    <div className="flex items-center justify-center min-h-screen bg-background">
      <div className="text-center">
        <h2 className="text-2xl font-bold text-foreground mb-4">Something went wrong!</h2>
        <p className="text-muted-foreground mb-6">{error.message}</p>
        <button
          onClick={() => reset()}
          className="px-4 py-2 bg-cyan-600 hover:bg-cyan-700 text-white rounded-lg"
        >
          Try again
        </button>
      </div>
    </div>
  )
}
