'use client'

import { ProtectedRoute } from '@/components/protected-route'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Button } from '@/components/ui/button'
import { useResource } from '@/lib/api/use-resource'

export default function AnalyticsPage() {
  const patients = useResource<{ id: string }>('patients')
  const doctors = useResource<{ id: string }>('doctors')
  const appointments = useResource<{ id: string }>('appointments')
  const consultations = useResource<{ id: string }>('consultations')
  const sources = [patients, doctors, appointments, consultations]
  const loading = sources.some((source) => source.loading)
  const error = sources.find((source) => source.error)?.error
  const retry = () => sources.forEach((source) => void source.reload())
  return <ProtectedRoute><div className="min-h-screen bg-gradient-to-br from-black to-slate-900 text-slate-100"><div className="container max-w-7xl mx-auto px-4 py-8"><div className="flex items-center justify-between mb-8"><div><h1 className="text-4xl font-bold">Analytics</h1><p className="text-slate-400 mt-2">Live operational totals from the FastAPI backend</p></div><Button variant="outline" onClick={retry}>Refresh</Button></div>{loading ? <div className="text-center py-16">Loading analytics…</div> : error ? <div className="text-center py-16 space-y-3"><p className="text-red-300">{error}</p><Button onClick={retry}>Retry</Button></div> : <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-4">{[{ label: 'Patients', total: patients.total }, { label: 'Doctors', total: doctors.total }, { label: 'Appointments', total: appointments.total }, { label: 'Consultations', total: consultations.total }].map((metric) => <Card key={metric.label} className="bg-slate-900/50 border-slate-700/50"><CardHeader><CardTitle className="text-base text-slate-400">{metric.label}</CardTitle></CardHeader><CardContent><p className="text-4xl font-bold text-cyan-400">{metric.total}</p></CardContent></Card>)}</div>}</div></div></ProtectedRoute>
}
