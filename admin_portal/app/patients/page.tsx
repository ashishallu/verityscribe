'use client'

import { FormEvent, useCallback, useMemo, useState } from 'react'
import { useRouter } from 'next/navigation'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Button } from '@/components/ui/button'
import { Badge } from '@/components/ui/badge'
import { Users, Plus } from 'lucide-react'
import { AdvancedTable } from '@/components/advanced-table'
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/dialog'
import { useResource } from '@/lib/api/use-resource'
import { ProtectedRoute } from '@/components/protected-route'
import { useAuth } from '@/app/auth-context'
import { ApiError, FastApiClient } from '@/lib/api/client'

type PatientForm = {
  mrn: string
  date_of_birth: string
  gender: 'male' | 'female' | 'other'
  marital_status: 'single' | 'married' | 'widowed' | 'divorced' | 'prefer_not_to_say'
  blood_group: 'A+' | 'A-' | 'B+' | 'B-' | 'AB+' | 'AB-' | 'O+' | 'O-'
  height_cm: string
  weight_kg: string
  occupation: string
}

const initialPatientForm: PatientForm = {
  mrn: '',
  date_of_birth: '',
  gender: 'male',
  marital_status: 'single',
  blood_group: 'O+',
  height_cm: '',
  weight_kg: '',
  occupation: '',
}

export default function PatientsPage() {
  const router = useRouter()
  const { data: patients, total, loading, error, reload, remove } = useResource<any>('patients')
  const { session } = useAuth()
  const apiClient = useMemo(() => new FastApiClient(async () => session), [session])
  const [isAddPatientOpen, setIsAddPatientOpen] = useState(false)
  const [form, setForm] = useState<PatientForm>(initialPatientForm)
  const [formError, setFormError] = useState<string | null>(null)
  const [successMessage, setSuccessMessage] = useState<string | null>(null)
  const [isSubmitting, setIsSubmitting] = useState(false)

  const handleView = (patient: any) => router.push(`/patients/${patient.id}`)

  const handleDelete = useCallback(async (patient: any) => {
    if (window.confirm('Delete this patient record?')) await remove(patient.id)
  }, [remove])

  const handleAddPatient = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    setFormError(null)
    setSuccessMessage(null)

    const mrn = form.mrn.trim()
    const occupation = form.occupation.trim()
    const height = form.height_cm ? Number(form.height_cm) : undefined
    const weight = form.weight_kg ? Number(form.weight_kg) : undefined

    if (!mrn || !form.date_of_birth) {
      setFormError('MRN and date of birth are required.')
      return
    }
    if (form.date_of_birth > new Date().toISOString().slice(0, 10)) {
      setFormError('Date of birth cannot be in the future.')
      return
    }
    if (height !== undefined && (!Number.isFinite(height) || height <= 0 || height > 300)) {
      setFormError('Enter a height between 1 and 300 cm.')
      return
    }
    if (weight !== undefined && (!Number.isFinite(weight) || weight <= 0 || weight > 1000)) {
      setFormError('Enter a weight between 1 and 1000 kg.')
      return
    }

    setIsSubmitting(true)
    try {
      await apiClient.create('patients', {
        mrn,
        date_of_birth: form.date_of_birth,
        gender: form.gender,
        marital_status: form.marital_status,
        blood_group: form.blood_group,
        ...(height !== undefined ? { height_cm: height } : {}),
        ...(weight !== undefined ? { weight_kg: weight } : {}),
        ...(occupation ? { occupation } : {}),
      })
      setForm(initialPatientForm)
      setIsAddPatientOpen(false)
      setSuccessMessage('Patient created successfully.')
      await reload()
    } catch (cause) {
      setFormError(cause instanceof ApiError ? cause.message : 'Unable to create the patient. Please try again.')
    } finally {
      setIsSubmitting(false)
    }
  }

  const columns = [
    { key: 'id', label: 'Patient ID', sortable: true, render: (value: string) => value.slice(0, 8) },
    { key: 'mrn', label: 'MRN', sortable: true },
    { key: 'date_of_birth', label: 'Date of Birth', sortable: true },
    { key: 'gender', label: 'Gender', sortable: true },
    { key: 'blood_group', label: 'Blood Group', sortable: true },
    {
      key: 'status',
      label: 'Status',
      sortable: true,
      render: (status: string) => (
        <Badge variant={status === 'active' ? 'default' : 'secondary'}>
          {status}
        </Badge>
      ),
    },
  ]

  return <ProtectedRoute>
    <div className="min-h-screen bg-gradient-to-br from-black to-slate-900 text-slate-100">
      <div className="container max-w-7xl mx-auto px-4 py-8">
        <div className="flex items-center justify-between mb-8">
          <div>
            <div className="flex items-center gap-3 mb-2">
              <Users className="h-8 w-8 text-cyan-500" />
              <h1 className="text-4xl font-bold">Patient Management</h1>
            </div>
            <p className="text-slate-400">Comprehensive patient database and management system</p>
          </div>
          <Button className="gap-2 bg-cyan-600 hover:bg-cyan-700" onClick={() => setIsAddPatientOpen(true)}>
            <Plus className="h-4 w-4" />
            Add Patient
          </Button>
        </div>

        <div className="grid grid-cols-4 gap-4 mb-8">
          <Card className="bg-slate-900/50 border-slate-700/50 backdrop-blur-sm">
            <CardContent className="pt-6">
              <div className="text-3xl font-bold text-cyan-400">{total}</div>
              <p className="text-slate-400 text-sm">Total Patients</p>
            </CardContent>
          </Card>
          <Card className="bg-slate-900/50 border-slate-700/50 backdrop-blur-sm">
            <CardContent className="pt-6">
              <div className="text-3xl font-bold text-green-400">
                {patients.filter((p) => p.status === 'active').length}
              </div>
              <p className="text-slate-400 text-sm">Active Patients</p>
            </CardContent>
          </Card>
          <Card className="bg-slate-900/50 border-slate-700/50 backdrop-blur-sm">
            <CardContent className="pt-6">
              <div className="text-3xl font-bold text-orange-400">
                {patients.filter((p) => p.status === 'inactive').length}
              </div>
              <p className="text-slate-400 text-sm">Inactive Patients</p>
            </CardContent>
          </Card>
          <Card className="bg-slate-900/50 border-slate-700/50 backdrop-blur-sm">
            <CardContent className="pt-6">
              <div className="text-3xl font-bold text-blue-400">
                {patients.filter((p) => p.updated_at).length}
              </div>
              <p className="text-slate-400 text-sm">Recent Visits</p>
            </CardContent>
          </Card>
        </div>

        <Card className="bg-slate-900/50 border-slate-700/50 backdrop-blur-sm">
          <CardHeader>
            <CardTitle>Patient Directory</CardTitle>
            <CardDescription>All patients with search, filter, sort, and export capabilities</CardDescription>
          </CardHeader>
          <CardContent>
            {loading ? (
              <div className="text-center py-8">Loading...</div>
            ) : error ? (
              <div className="text-center py-8 space-y-3"><p className="text-red-300">{error}</p><Button onClick={() => void reload()}>Retry</Button></div>
            ) : patients.length === 0 ? (
              <div className="text-center py-8 text-slate-400">No patient records are available for this account.</div>
            ) : (
              <AdvancedTable
                columns={columns}
                data={patients}
                onView={handleView}
                onDelete={handleDelete}
                title="Patients"
                searchable={true}
              />
            )}
          </CardContent>
        </Card>

        {successMessage && (
          <p className="mt-4 rounded-md border border-emerald-500/40 bg-emerald-500/10 px-4 py-3 text-sm text-emerald-300" role="status">
            {successMessage}
          </p>
        )}

        <Dialog open={isAddPatientOpen} onOpenChange={setIsAddPatientOpen}>
          <DialogContent className="border-slate-700 bg-slate-950 text-slate-100">
            <DialogHeader>
              <DialogTitle>Add Patient</DialogTitle>
              <DialogDescription className="text-slate-400">
                Enter the patient’s clinical details to create their record.
              </DialogDescription>
            </DialogHeader>
            <form className="space-y-4" onSubmit={handleAddPatient}>
              <div className="grid gap-4 sm:grid-cols-2">
                <label className="grid gap-1.5 text-sm">MRN <input required value={form.mrn} onChange={(event) => setForm({ ...form, mrn: event.target.value })} className="rounded-md border border-slate-700 bg-slate-900 px-3 py-2" /></label>
                <label className="grid gap-1.5 text-sm">Date of birth <input required type="date" max={new Date().toISOString().slice(0, 10)} value={form.date_of_birth} onChange={(event) => setForm({ ...form, date_of_birth: event.target.value })} className="rounded-md border border-slate-700 bg-slate-900 px-3 py-2" /></label>
                <label className="grid gap-1.5 text-sm">Gender <select value={form.gender} onChange={(event) => setForm({ ...form, gender: event.target.value as PatientForm['gender'] })} className="rounded-md border border-slate-700 bg-slate-900 px-3 py-2"><option value="male">Male</option><option value="female">Female</option><option value="other">Other</option></select></label>
                <label className="grid gap-1.5 text-sm">Marital status <select value={form.marital_status} onChange={(event) => setForm({ ...form, marital_status: event.target.value as PatientForm['marital_status'] })} className="rounded-md border border-slate-700 bg-slate-900 px-3 py-2"><option value="single">Single</option><option value="married">Married</option><option value="widowed">Widowed</option><option value="divorced">Divorced</option><option value="prefer_not_to_say">Prefer not to say</option></select></label>
                <label className="grid gap-1.5 text-sm">Blood group <select value={form.blood_group} onChange={(event) => setForm({ ...form, blood_group: event.target.value as PatientForm['blood_group'] })} className="rounded-md border border-slate-700 bg-slate-900 px-3 py-2"><option value="A+">A+</option><option value="A-">A-</option><option value="B+">B+</option><option value="B-">B-</option><option value="AB+">AB+</option><option value="AB-">AB-</option><option value="O+">O+</option><option value="O-">O-</option></select></label>
                <label className="grid gap-1.5 text-sm">Occupation <input value={form.occupation} onChange={(event) => setForm({ ...form, occupation: event.target.value })} className="rounded-md border border-slate-700 bg-slate-900 px-3 py-2" /></label>
                <label className="grid gap-1.5 text-sm">Height (cm) <input type="number" min="1" max="300" step="0.1" value={form.height_cm} onChange={(event) => setForm({ ...form, height_cm: event.target.value })} className="rounded-md border border-slate-700 bg-slate-900 px-3 py-2" /></label>
                <label className="grid gap-1.5 text-sm">Weight (kg) <input type="number" min="1" max="1000" step="0.1" value={form.weight_kg} onChange={(event) => setForm({ ...form, weight_kg: event.target.value })} className="rounded-md border border-slate-700 bg-slate-900 px-3 py-2" /></label>
              </div>
              {formError && <p className="text-sm text-red-300" role="alert">{formError}</p>}
              <DialogFooter>
                <Button type="button" variant="outline" disabled={isSubmitting} onClick={() => setIsAddPatientOpen(false)}>Cancel</Button>
                <Button type="submit" className="bg-cyan-600 hover:bg-cyan-700" disabled={isSubmitting}>{isSubmitting ? 'Creating…' : 'Create Patient'}</Button>
              </DialogFooter>
            </form>
          </DialogContent>
        </Dialog>
      </div>
    </div>
  </ProtectedRoute>
}
