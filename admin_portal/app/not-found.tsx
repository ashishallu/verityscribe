export default function NotFound() {
  return (
    <div className="flex items-center justify-center min-h-screen bg-background">
      <div className="text-center">
        <h1 className="text-4xl font-bold text-foreground mb-4">404</h1>
        <p className="text-muted-foreground mb-6">Page not found</p>
        <a href="/" className="px-4 py-2 bg-cyan-600 hover:bg-cyan-700 text-white rounded-lg inline-block">
          Go home
        </a>
      </div>
    </div>
  )
}
