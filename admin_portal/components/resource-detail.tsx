'use client'
import { useEffect, useMemo, useState } from 'react'
import { useParams } from 'next/navigation'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Button } from '@/components/ui/button'
import { ProtectedRoute } from '@/components/protected-route'
import { useAuth } from '@/app/auth-context'
import { ApiError, FastApiClient } from '@/lib/api/client'

export function ResourceDetail({ resource, title, fields }: { resource: string; title: string; fields: { key: string; label: string }[] }) {
  const { id } = useParams<{ id: string }>(); const { session } = useAuth(); const client = useMemo(() => new FastApiClient(async () => session), [session]); const [record, setRecord] = useState<Record<string, unknown> | null>(null); const [error, setError] = useState<string | null>(null); const [loading, setLoading] = useState(true)
  const load = async () => { setLoading(true); setError(null); try { setRecord((await client.get<Record<string, unknown>>(resource, id)).data) } catch (cause) { setError(cause instanceof ApiError ? cause.message : 'Unable to load this record.') } finally { setLoading(false) } }
  useEffect(() => { if (session && id) void load() }, [session, id])
  return <ProtectedRoute><div className="min-h-screen bg-gradient-to-br from-black to-slate-900 text-slate-100"><div className="container max-w-5xl mx-auto px-4 py-8"><Card className="bg-slate-900/50 border-slate-700/50"><CardHeader><CardTitle>{title}</CardTitle></CardHeader><CardContent>{loading ? <div className="py-10 text-center">Loading...</div> : error ? <div className="space-y-3 py-10 text-center"><p className="text-red-300">{error}</p><Button onClick={() => void load()}>Retry</Button></div> : record ? <div className="grid gap-4 sm:grid-cols-2">{fields.map((field) => <div key={field.key} className="rounded-lg border border-slate-700/50 p-4"><p className="text-xs uppercase text-slate-400">{field.label}</p><p className="mt-1 break-words">{String(record[field.key] ?? '—')}</p></div>)}</div> : <p>No record found.</p>}</CardContent></Card></div></div></ProtectedRoute>
}
