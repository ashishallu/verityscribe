'use client'

import { FormEvent, useCallback, useMemo, useState } from 'react'
import { useRouter } from 'next/navigation'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Button } from '@/components/ui/button'
import { Badge } from '@/components/ui/badge'
import { Stethoscope, Plus, Star } from 'lucide-react'
import { AdvancedTable } from '@/components/advanced-table'
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/dialog'
import { useResource } from '@/lib/api/use-resource'
import { ProtectedRoute } from '@/components/protected-route'
import { useAuth } from '@/app/auth-context'
import { ApiError, FastApiClient } from '@/lib/api/client'

type DoctorForm = { email: string; first_name: string; last_name: string; phone: string; hospital_id: string; department_id: string; license_number: string; specialization: string; experience_years: string; qualification: string; consultation_fee_inr: string; is_available: boolean }
const initialForm: DoctorForm = { email: '', first_name: '', last_name: '', phone: '', hospital_id: '', department_id: '', license_number: '', specialization: '', experience_years: '0', qualification: '', consultation_fee_inr: '0', is_available: true }

export default function DoctorsPage() {
  const router = useRouter()
  const { data: doctors, total, loading, error, reload, remove } = useResource<any>('doctors')
  const [isAddDoctorOpen, setIsAddDoctorOpen] = useState(false)
  const { session } = useAuth()
  const apiClient = useMemo(() => new FastApiClient(async () => session), [session])
  const [form, setForm] = useState<DoctorForm>(initialForm)
  const [formError, setFormError] = useState<string | null>(null)
  const [submitting, setSubmitting] = useState(false)
  const [filters, setFilters] = useState({ specialization: '', department_id: '', availability: '', minExperience: '', maxExperience: '', minRating: '', maxFee: '' })
  const experienceValues = doctors.map((doctor) => Number(doctor.experience_years)).filter(Number.isFinite)
  const ratingValues = doctors.map((doctor) => Number(doctor.rating)).filter((rating) => Number.isFinite(rating) && rating > 0)
  const averageExperience = experienceValues.length ? experienceValues.reduce((sum, value) => sum + value, 0) / experienceValues.length : null
  const averageRating = ratingValues.length ? ratingValues.reduce((sum, value) => sum + value, 0) / ratingValues.length : null
  const specializations = Array.from(new Set(doctors.map((doctor) => doctor.specialization).filter(Boolean)))
  const departments = Array.from(new Map<string, any>(doctors.map((doctor) => [doctor.department?.id, doctor.department] as [string, any]).filter(([id]) => Boolean(id))).values())
  const filteredDoctors = doctors.filter((doctor) => {
    const experience = Number(doctor.experience_years)
    const rating = Number(doctor.rating)
    const fee = Number(doctor.consultation_fee_inr)
    return (!filters.specialization || doctor.specialization === filters.specialization) && (!filters.department_id || doctor.department?.id === filters.department_id) && (!filters.availability || (filters.availability === 'available' ? doctor.is_available : !doctor.is_available)) && (!filters.minExperience || experience >= Number(filters.minExperience)) && (!filters.maxExperience || experience <= Number(filters.maxExperience)) && (!filters.minRating || (Number.isFinite(rating) && rating >= Number(filters.minRating))) && (!filters.maxFee || (Number.isFinite(fee) && fee <= Number(filters.maxFee)))
  })
  const clearFilters = () => setFilters({ specialization: '', department_id: '', availability: '', minExperience: '', maxExperience: '', minRating: '', maxFee: '' })

  const handleProvision = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault(); setFormError(null)
    const experience = Number(form.experience_years); const fee = Number(form.consultation_fee_inr)
    if (!form.hospital_id.trim() || !form.department_id.trim()) return setFormError('Hospital and department IDs are required.')
    if (!Number.isInteger(experience) || experience < 0 || experience > 80 || !Number.isFinite(fee) || fee < 0) return setFormError('Enter valid experience and consultation fee values.')
    setSubmitting(true)
    try { await apiClient.create('doctors/provision', { ...form, hospital_id: form.hospital_id.trim(), department_id: form.department_id.trim(), experience_years: experience, consultation_fee_inr: fee, phone: form.phone.trim() || undefined }); setForm(initialForm); setIsAddDoctorOpen(false); await reload() }
    catch (cause) { setFormError(cause instanceof ApiError ? cause.message : 'Unable to provision doctor. Please try again.') }
    finally { setSubmitting(false) }
  }

  const handleView = (doctor: any) => router.push(`/doctors/${doctor.id}`)

  const handleDelete = useCallback(async (doctor: any) => {
    if (window.confirm('Delete this doctor record?')) await remove(doctor.id)
  }, [remove])

  const columns = [
    {
      key: 'id',
      label: 'Name',
      sortable: true,
      render: (_val: string, row: any) => [row.first_name, row.last_name].filter(Boolean).join(' ') || 'Name unavailable'
    },
    { key: 'specialization', label: 'Specialization', sortable: true },
    { key: 'experience_years', label: 'Experience', sortable: true, render: (value: number | null) => value == null ? 'Not available' : `${value} yrs` },
    {
      key: 'consultation_fee_inr',
      label: 'Fee (₹)',
      sortable: true,
    },
    { key: 'department', label: 'Department', sortable: true, render: (_value: unknown, row: any) => row.department?.name ?? 'Not available' },
    {
      key: 'is_available',
      label: 'Status',
      sortable: true,
      render: (status: boolean) => (
        <Badge variant={status ? 'default' : 'secondary'}>
          {status ? 'available' : 'unavailable'}
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
              <Stethoscope className="h-8 w-8 text-cyan-500" />
              <h1 className="text-4xl font-bold">Doctor Management</h1>
            </div>
            <p className="text-slate-400">Browse and manage healthcare professionals</p>
          </div>
          <Button className="gap-2 bg-cyan-600 hover:bg-cyan-700" onClick={() => setIsAddDoctorOpen(true)}>
            <Plus className="h-4 w-4" />
            Add Doctor
          </Button>
        </div>

        <div className="grid grid-cols-4 gap-4 mb-8">
          <Card className="bg-slate-900/50 border-slate-700/50 backdrop-blur-sm">
            <CardContent className="pt-6">
              <div className="text-3xl font-bold text-cyan-400">{total}</div>
              <p className="text-slate-400 text-sm">Total Doctors</p>
            </CardContent>
          </Card>
          <Card className="bg-slate-900/50 border-slate-700/50 backdrop-blur-sm">
            <CardContent className="pt-6">
              <div className="text-3xl font-bold text-green-400">
                {doctors.filter((d) => d.is_available).length}
              </div>
              <p className="text-slate-400 text-sm">Available Now</p>
            </CardContent>
          </Card>
          <Card className="bg-slate-900/50 border-slate-700/50 backdrop-blur-sm">
            <CardContent className="pt-6">
              <div className="text-3xl font-bold text-orange-400">
                {averageExperience == null ? 'Not available' : averageExperience.toFixed(0)}
              </div>
              <p className="text-slate-400 text-sm">Avg. Experience (yrs)</p>
            </CardContent>
          </Card>
          <Card className="bg-slate-900/50 border-slate-700/50 backdrop-blur-sm">
            <CardContent className="pt-6">
              <div className="text-3xl font-bold text-blue-400">
                {averageRating == null ? 'Not available' : averageRating.toFixed(1)}
              </div>
              <p className="text-slate-400 text-sm">Avg. Rating</p>
            </CardContent>
          </Card>
        </div>

        <Card className="bg-slate-900/50 border-slate-700/50 backdrop-blur-sm">
          <CardHeader>
            <CardTitle>Doctor Directory</CardTitle>
            <CardDescription>All doctors with search, filter, sort, and export capabilities</CardDescription>
            <div className="mt-4 grid gap-2 sm:grid-cols-2 lg:grid-cols-4">
              <select value={filters.specialization} onChange={(e) => setFilters({ ...filters, specialization: e.target.value })} className="rounded border bg-slate-900 px-2 py-1"><option value="">All specializations</option>{specializations.map((value) => <option key={value} value={value}>{value}</option>)}</select>
              <select value={filters.department_id} onChange={(e) => setFilters({ ...filters, department_id: e.target.value })} className="rounded border bg-slate-900 px-2 py-1"><option value="">All departments</option>{departments.map((department: any) => <option key={department.id} value={department.id}>{department.name}</option>)}</select>
              <select value={filters.availability} onChange={(e) => setFilters({ ...filters, availability: e.target.value })} className="rounded border bg-slate-900 px-2 py-1"><option value="">All availability</option><option value="available">Available</option><option value="unavailable">Unavailable</option></select>
              <input type="number" min="0" placeholder="Min experience" value={filters.minExperience} onChange={(e) => setFilters({ ...filters, minExperience: e.target.value })} className="rounded border bg-slate-900 px-2 py-1" />
              <input type="number" min="0" placeholder="Max experience" value={filters.maxExperience} onChange={(e) => setFilters({ ...filters, maxExperience: e.target.value })} className="rounded border bg-slate-900 px-2 py-1" />
              <input type="number" min="0" max="5" step="0.1" placeholder="Min rating" value={filters.minRating} onChange={(e) => setFilters({ ...filters, minRating: e.target.value })} className="rounded border bg-slate-900 px-2 py-1" />
              <input type="number" min="0" placeholder="Max fee" value={filters.maxFee} onChange={(e) => setFilters({ ...filters, maxFee: e.target.value })} className="rounded border bg-slate-900 px-2 py-1" />
              <Button type="button" variant="outline" onClick={clearFilters}>Clear filters</Button>
            </div>
          </CardHeader>
          <CardContent>
            {loading ? (
              <div className="text-center py-8">Loading...</div>
            ) : error ? (
              <div className="text-center py-8 space-y-3"><p className="text-red-300">{error}</p><Button onClick={() => void reload()}>Retry</Button></div>
            ) : doctors.length === 0 ? (
              <div className="text-center py-8 text-slate-400">No doctor records are available for this account.</div>
            ) : filteredDoctors.length === 0 ? (
              <div className="text-center py-8 text-slate-400">No doctors match the selected filters.</div>
            ) : (
              <AdvancedTable
                columns={columns}
                data={filteredDoctors}
                onView={handleView}
                onDelete={handleDelete}
                title="Doctors"
                searchable={true}
              />
            )}
          </CardContent>
        </Card>

        <Dialog open={isAddDoctorOpen} onOpenChange={setIsAddDoctorOpen}>
          <DialogContent className="border-slate-700 bg-slate-950 text-slate-100">
            <DialogHeader>
              <DialogTitle>Add Doctor</DialogTitle>
              <DialogDescription className="text-slate-400">
                An invitation will be sent and the doctor will be linked to the canonical account.
              </DialogDescription>
            </DialogHeader>
            <form className="grid max-h-[70vh] gap-3 overflow-y-auto sm:grid-cols-2" onSubmit={handleProvision}>
              {(['first_name','last_name','email','phone','hospital_id','department_id','license_number','specialization','qualification'] as const).map((field) => <label key={field} className="grid gap-1 text-sm capitalize">{field.replaceAll('_',' ')}<input required={!['phone'].includes(field)} type={field === 'email' ? 'email' : 'text'} value={form[field]} onChange={(e) => setForm({ ...form, [field]: e.target.value })} className="rounded-md border border-slate-700 bg-slate-900 px-3 py-2" /></label>)}
              <label className="grid gap-1 text-sm">Experience (years)<input required type="number" min="0" max="80" value={form.experience_years} onChange={(e) => setForm({ ...form, experience_years: e.target.value })} className="rounded-md border border-slate-700 bg-slate-900 px-3 py-2" /></label>
              <label className="grid gap-1 text-sm">Consultation fee (INR)<input required type="number" min="0" step="0.01" value={form.consultation_fee_inr} onChange={(e) => setForm({ ...form, consultation_fee_inr: e.target.value })} className="rounded-md border border-slate-700 bg-slate-900 px-3 py-2" /></label>
              <label className="flex items-center gap-2 text-sm sm:col-span-2"><input type="checkbox" checked={form.is_available} onChange={(e) => setForm({ ...form, is_available: e.target.checked })} /> Available for consultations</label>
              {formError && <p className="text-sm text-red-300 sm:col-span-2" role="alert">{formError}</p>}
              <DialogFooter className="sm:col-span-2"><Button type="button" variant="outline" onClick={() => setIsAddDoctorOpen(false)}>Cancel</Button><Button type="submit" disabled={submitting}>{submitting ? 'Inviting…' : 'Create Doctor'}</Button></DialogFooter>
            </form>
          </DialogContent>
        </Dialog>
      </div>
    </div>
  </ProtectedRoute>
}
