'use client'

import { useCallback, useEffect, useMemo, useState } from 'react'
import { useAuth } from '@/app/auth-context'
import { ApiError, FastApiClient, type ApiPage } from './client'

export function useResource<T extends { id: string }>(resource: string) {
  const { session } = useAuth()
  const client = useMemo(() => new FastApiClient(async () => session), [session])
  const [result, setResult] = useState<ApiPage<T> | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  const load = useCallback(async () => {
    if (!session) { setLoading(false); return }
    setLoading(true); setError(null)
    try { setResult(await client.list<T>(resource)) }
    catch (cause) { setError(cause instanceof ApiError ? cause.message : 'Unable to load data.') }
    finally { setLoading(false) }
  }, [client, resource, session])

  useEffect(() => { void load() }, [load])

  const remove = useCallback(async (id: string) => {
    await client.remove(resource, id)
    setResult((previous) => previous ? { ...previous, data: previous.data.filter((item) => item.id !== id), meta: { ...previous.meta, total: Math.max(0, previous.meta.total - 1) } } : previous)
  }, [client, resource])

  return { data: result?.data ?? [], total: result?.meta.total ?? 0, loading, error, reload: load, remove }
}
