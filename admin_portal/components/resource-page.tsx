'use client'

import { AdvancedTable } from '@/components/advanced-table'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { useResource } from '@/lib/api/use-resource'
import { ProtectedRoute } from '@/components/protected-route'

export function ResourcePage({ resource, title, description, columns }: { resource: string; title: string; description: string; columns: { key: string; label: string; sortable?: boolean; render?: (value: unknown, row: Record<string, unknown>) => React.ReactNode }[] }) {
  const { data, total, loading, error, reload, remove } = useResource<Record<string, unknown> & { id: string }>(resource)
  return <ProtectedRoute>
    <div className="min-h-screen bg-gradient-to-br from-black to-slate-900 text-slate-100">
      <div className="container max-w-7xl mx-auto px-4 py-8">
        <div className="flex items-center justify-between mb-8"><div><h1 className="text-4xl font-bold">{title}</h1><p className="text-slate-400 mt-2">{description}</p></div><Button variant="outline" onClick={() => void reload()}>Refresh</Button></div>
        <Card className="bg-slate-900/50 border-slate-700/50 backdrop-blur-sm"><CardHeader><CardTitle>{title}</CardTitle><CardDescription>{total} total records</CardDescription></CardHeader><CardContent>
          {loading ? <div className="text-center py-8">Loading...</div> : error ? <div className="text-center py-8 space-y-3"><p className="text-red-300">{error}</p><Button onClick={() => void reload()}>Retry</Button></div> : data.length === 0 ? <div className="text-center py-8 text-slate-400">No {resource} records are available for this account.</div> : <AdvancedTable columns={columns} data={data} title={title} onDelete={(row) => void remove(row.id)} />}
        </CardContent></Card>
      </div>
    </div>
  </ProtectedRoute>
}
