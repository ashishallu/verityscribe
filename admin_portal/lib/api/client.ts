'use client'

import type { Session } from '@supabase/supabase-js'

export type ApiPage<T> = { data: T[]; meta: { page: number; page_size: number; total: number } }

export class ApiError extends Error {
  constructor(message: string, readonly status?: number) {
    super(message)
    this.name = 'ApiError'
  }
}

/** Centralized browser boundary for all protected FastAPI resource calls. */
export class FastApiClient {
  constructor(private readonly getSession: () => Promise<Session | null>) {}

  private async request<T>(path: string, init: RequestInit = {}): Promise<T> {
    const session = await this.getSession()
    if (!session?.access_token) throw new ApiError('Your session has expired. Please sign in again.', 401)

    const response = await fetch(`${process.env.NEXT_PUBLIC_API_BASE_URL ?? 'http://localhost:8000/api/v1'}${path}`, {
      ...init,
      headers: {
        Authorization: `Bearer ${session.access_token}`,
        ...(init.body ? { 'Content-Type': 'application/json' } : {}),
        ...init.headers,
      },
    })
    if (!response.ok) {
      const body = await response.json().catch(() => null)
      throw new ApiError(body?.detail ?? `Request failed (${response.status})`, response.status)
    }
    return response.json() as Promise<T>
  }

  list<T>(resource: string, options: { page?: number; pageSize?: number; search?: string; sort?: string; descending?: boolean } = {}) {
    const params = new URLSearchParams({
      page: String(options.page ?? 1),
      page_size: String(options.pageSize ?? 25),
      sort: options.sort ?? 'created_at',
      descending: String(options.descending ?? true),
    })
    if (options.search) params.set('search', options.search)
    return this.request<ApiPage<T>>(`/${resource}?${params}`)
  }

  create<T>(resource: string, payload: Partial<T>) {
    return this.request<{ data: T }>(`/${resource}`, { method: 'POST', body: JSON.stringify(payload) })
  }

  update<T>(resource: string, id: string, payload: Partial<T>) {
    return this.request<{ data: T }>(`/${resource}/${id}`, { method: 'PATCH', body: JSON.stringify(payload) })
  }

  remove(resource: string, id: string) {
    return this.request<{ data: { id: string; deleted: boolean } }>(`/${resource}/${id}`, { method: 'DELETE' })
  }
}
