'use client'

import { FormEvent, useMemo, useState } from 'react'
import { ResourcePage } from '@/components/resource-page'
import { Button } from '@/components/ui/button'
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/dialog'
import { useAuth } from '@/app/auth-context'
import { ApiError, FastApiClient } from '@/lib/api/client'
import { useResource } from '@/lib/api/use-resource'

type Entity = { id: string; first_name?: string | null; last_name?: string | null; full_name?: string | null; specialization?: string | null; department?: { name?: string } | null }
const nameOf = (item: Entity) => item.full_name || [item.first_name, item.last_name].filter(Boolean).join(' ') || item.id.slice(0, 8)

export default function AppointmentsPage() {
  const { session } = useAuth()
  const client = useMemo(() => new FastApiClient(async () => session), [session])
  const appointments = useResource<any>('appointments')
  const patients = useResource<Entity>('patients')
  const doctors = useResource<Entity>('doctors')
  const [open, setOpen] = useState(false)
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [form, setForm] = useState({ patient_id: '', doctor_id: '', appointment_date: '', appointment_time: '', consultation_type: 'in_person', reason_for_visit: '', duration_minutes: '30', appointment_fee: '', notes: '' })

  const submit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault(); setError(null)
    if (!form.patient_id || !form.doctor_id || !form.appointment_date || !form.appointment_time) return setError('Patient, doctor, date, and time are required.')
    const duration = Number(form.duration_minutes); const fee = form.appointment_fee ? Number(form.appointment_fee) : undefined
    if (!Number.isInteger(duration) || duration < 5 || duration > 480 || (fee !== undefined && (!Number.isFinite(fee) || fee < 0))) return setError('Enter valid duration and fee values.')
    setSaving(true)
    try {
      await client.create('appointments', { ...form, duration_minutes: duration, appointment_fee: fee })
      setForm({ patient_id: '', doctor_id: '', appointment_date: '', appointment_time: '', consultation_type: 'in_person', reason_for_visit: '', duration_minutes: '30', appointment_fee: '', notes: '' })
      setOpen(false); await appointments.reload()
    } catch (cause) { setError(cause instanceof ApiError ? cause.message : 'Unable to create appointment.') }
    finally { setSaving(false) }
  }

  return <>
    <div className="mb-6 flex justify-end"><Button onClick={() => { setError(null); setOpen(true) }} className="bg-cyan-600 hover:bg-cyan-700">Create Appointment</Button></div>
    <ResourcePage resource="appointments" title="Appointments" description="Live hospital-scoped appointment scheduling" columns={[{ key: 'patient_id', label: 'Patient', render: (value, raw) => { const row = raw as any; return row.patient?.first_name ? `${row.patient.first_name} ${row.patient.last_name ?? ''}` : String(value).slice(0, 8) } }, { key: 'doctor_id', label: 'Doctor', render: (value, raw) => { const row = raw as any; return row.doctor?.profiles ? nameOf(row.doctor.profiles) : String(value).slice(0, 8) } }, { key: 'appointment_date', label: 'Date', sortable: true }, { key: 'appointment_time', label: 'Time', sortable: true }, { key: 'consultation_type', label: 'Type' }, { key: 'status', label: 'Status', sortable: true }, { key: 'appointment_fee', label: 'Fee' }]} />
    <Dialog open={open} onOpenChange={setOpen}><DialogContent className="border-slate-700 bg-slate-950 text-slate-100"><DialogHeader><DialogTitle>Create Appointment</DialogTitle><DialogDescription className="text-slate-400">Patients and doctors are loaded from your assigned hospital.</DialogDescription></DialogHeader>
      <form onSubmit={submit} className="grid max-h-[70vh] gap-3 overflow-y-auto sm:grid-cols-2">
        <label className="grid gap-1 text-sm">Patient<select required value={form.patient_id} onChange={e => setForm({ ...form, patient_id: e.target.value })} className="rounded-md border border-slate-700 bg-slate-900 px-3 py-2"><option value="">{patients.loading ? 'Loading patients…' : patients.data.length ? 'Select patient' : 'No patients registered'}</option>{patients.data.map(p => <option key={p.id} value={p.id}>{nameOf(p)}</option>)}</select></label>
        <label className="grid gap-1 text-sm">Doctor<select required value={form.doctor_id} onChange={e => setForm({ ...form, doctor_id: e.target.value })} className="rounded-md border border-slate-700 bg-slate-900 px-3 py-2"><option value="">{doctors.loading ? 'Loading doctors…' : doctors.data.length ? 'Select doctor' : 'No doctors assigned'}</option>{doctors.data.map(d => <option key={d.id} value={d.id}>{nameOf(d)}{d.specialization ? ` — ${d.specialization}` : ''}</option>)}</select></label>
        <label className="grid gap-1 text-sm">Date<input required type="date" min={new Date().toISOString().slice(0, 10)} value={form.appointment_date} onChange={e => setForm({ ...form, appointment_date: e.target.value })} className="rounded-md border border-slate-700 bg-slate-900 px-3 py-2" /></label>
        <label className="grid gap-1 text-sm">Time<input required type="time" value={form.appointment_time} onChange={e => setForm({ ...form, appointment_time: e.target.value })} className="rounded-md border border-slate-700 bg-slate-900 px-3 py-2" /></label>
        <label className="grid gap-1 text-sm">Consultation type<select value={form.consultation_type} onChange={e => setForm({ ...form, consultation_type: e.target.value })} className="rounded-md border border-slate-700 bg-slate-900 px-3 py-2"><option value="in_person">In person</option><option value="video">Video</option><option value="phone">Phone</option><option value="chat">Chat</option></select></label>
        <label className="grid gap-1 text-sm">Duration (minutes)<input required type="number" min="5" max="480" value={form.duration_minutes} onChange={e => setForm({ ...form, duration_minutes: e.target.value })} className="rounded-md border border-slate-700 bg-slate-900 px-3 py-2" /></label>
        <label className="grid gap-1 text-sm">Fee<input type="number" min="0" step="0.01" value={form.appointment_fee} onChange={e => setForm({ ...form, appointment_fee: e.target.value })} className="rounded-md border border-slate-700 bg-slate-900 px-3 py-2" /></label>
        <label className="grid gap-1 text-sm sm:col-span-2">Reason<input value={form.reason_for_visit} onChange={e => setForm({ ...form, reason_for_visit: e.target.value })} className="rounded-md border border-slate-700 bg-slate-900 px-3 py-2" /></label>
        <label className="grid gap-1 text-sm sm:col-span-2">Notes<textarea value={form.notes} onChange={e => setForm({ ...form, notes: e.target.value })} className="rounded-md border border-slate-700 bg-slate-900 px-3 py-2" /></label>
        {error && <p className="text-sm text-red-300 sm:col-span-2" role="alert">{error}</p>}
        <DialogFooter className="sm:col-span-2"><Button type="button" variant="outline" onClick={() => setOpen(false)}>Cancel</Button><Button type="submit" disabled={saving || patients.data.length === 0 || doctors.data.length === 0}>{saving ? 'Creating…' : 'Create Appointment'}</Button></DialogFooter>
      </form>
    </DialogContent></Dialog>
  </>
}
