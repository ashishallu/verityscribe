'use client'

import { useEffect, useMemo, useState } from 'react'
import Link from 'next/link'
import { Settings, UserCircle } from 'lucide-react'
import { useAuth } from '@/app/auth-context'
import { ProtectedRoute } from '@/components/protected-route'
import { FastApiClient, ApiError } from '@/lib/api/client'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Button } from '@/components/ui/button'

type Profile = { id: string; email?: string | null; first_name?: string | null; last_name?: string | null; role?: string | null; hospital_id?: string | null; department_id?: string | null }

export default function SettingsPage() {
  const { session, user, profile: authProfile } = useAuth()
  const client = useMemo(() => new FastApiClient(async () => session), [session])
  const [profile, setProfile] = useState<Profile | null>(authProfile)
  const [error, setError] = useState<string | null>(null)
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    let active = true
    void client.getPath<{ data: Profile }>('/me').then(({ data }) => {
      if (active) setProfile(data)
    }).catch((cause) => {
      if (active) setError(cause instanceof ApiError ? cause.message : 'Unable to load your profile.')
    }).finally(() => { if (active) setLoading(false) })
    return () => { active = false }
  }, [client])

  const current = (profile ?? authProfile) as Profile | null
  const name = [current?.first_name, current?.last_name].filter(Boolean).join(' ') || 'Not provided'

  return <ProtectedRoute>
    <div className="min-h-screen bg-gradient-to-br from-slate-50 to-slate-100 dark:from-slate-950 dark:to-slate-900">
      <nav className="border-b bg-white dark:bg-slate-900"><div className="container max-w-7xl mx-auto px-4 py-4"><Button asChild variant="ghost"><Link href="/dashboard">← Back to Dashboard</Link></Button></div></nav>
      <main className="container max-w-3xl mx-auto px-4 py-8">
        <div className="mb-8"><div className="flex items-center gap-2 mb-2"><Settings className="h-6 w-6" /><h1 className="text-3xl font-bold">Settings</h1></div><p className="text-muted-foreground">Your account information and access scope</p></div>
        <Card><CardHeader><CardTitle className="flex items-center gap-2"><UserCircle className="h-5 w-5" />Authenticated profile</CardTitle><CardDescription>This information is read from the canonical FastAPI profile. Identity and role changes are managed by authorized workflows.</CardDescription></CardHeader>
          <CardContent>{loading ? <p>Loading profile…</p> : error ? <p className="text-red-600" role="alert">{error}</p> : <dl className="grid gap-4 sm:grid-cols-2"><div><dt className="text-sm text-muted-foreground">Name</dt><dd>{name}</dd></div><div><dt className="text-sm text-muted-foreground">Email</dt><dd>{current?.email ?? user?.email ?? 'Not provided'}</dd></div><div><dt className="text-sm text-muted-foreground">Role</dt><dd>{current?.role ?? authProfile?.role ?? 'Not provided'}</dd></div><div><dt className="text-sm text-muted-foreground">User ID</dt><dd className="break-all font-mono text-xs">{current?.id ?? user?.id ?? 'Not provided'}</dd></div><div><dt className="text-sm text-muted-foreground">Hospital</dt><dd>{current?.hospital_id ?? 'Not assigned'}</dd></div><div><dt className="text-sm text-muted-foreground">Department</dt><dd>{current?.department_id ?? 'Not assigned'}</dd></div></dl>}</CardContent>
        </Card>
      </main>
    </div>
  </ProtectedRoute>
}
