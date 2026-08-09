'use client'

import { useCallback, useState } from 'react'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Button } from '@/components/ui/button'
import { Badge } from '@/components/ui/badge'
import { Stethoscope, Plus, Star } from 'lucide-react'
import { AdvancedTable } from '@/components/advanced-table'
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/dialog'
import { useResource } from '@/lib/api/use-resource'
import { ProtectedRoute } from '@/components/protected-route'

export default function DoctorsPage() {
  const { data: doctors, total, loading, error, reload, remove } = useResource<any>('doctors')
  const [isAddDoctorOpen, setIsAddDoctorOpen] = useState(false)

  const handleView = (doctor: any) => {
    console.log('[v0] View doctor:', doctor)
  }

  const handleEdit = (doctor: any) => {
    console.log('[v0] Edit doctor:', doctor)
  }

  const handleDelete = useCallback(async (doctor: any) => {
    if (window.confirm('Delete this doctor record?')) await remove(doctor.id)
  }, [remove])

  const columns = [
    {
      key: 'id',
      label: 'Name',
      sortable: true,
      render: (val: string, row: any) => row.full_name ?? `Doctor ${val.slice(0, 8)}`
    },
    { key: 'specialization', label: 'Specialization', sortable: true },
    { key: 'years_experience', label: 'Experience', sortable: true },
    {
      key: 'consultationFee',
      label: 'Fee (₹)',
      sortable: true,
    },
    { key: 'department_id', label: 'Department', sortable: true },
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
                {doctors.length ? (doctors.reduce((sum, d) => sum + (d.years_experience ?? 0), 0) / doctors.length).toFixed(0) : '0'}
              </div>
              <p className="text-slate-400 text-sm">Avg. Experience (yrs)</p>
            </CardContent>
          </Card>
          <Card className="bg-slate-900/50 border-slate-700/50 backdrop-blur-sm">
            <CardContent className="pt-6">
              <div className="text-3xl font-bold text-blue-400">
                {doctors.length ? (doctors.reduce((sum, d) => sum + (Number(d.rating) || 0), 0) / doctors.length).toFixed(1) : '0.0'}
              </div>
              <p className="text-slate-400 text-sm">Avg. Rating</p>
            </CardContent>
          </Card>
        </div>

        <Card className="bg-slate-900/50 border-slate-700/50 backdrop-blur-sm">
          <CardHeader>
            <CardTitle>Doctor Directory</CardTitle>
            <CardDescription>All doctors with search, filter, sort, and export capabilities</CardDescription>
          </CardHeader>
          <CardContent>
            {loading ? (
              <div className="text-center py-8">Loading...</div>
            ) : error ? (
              <div className="text-center py-8 space-y-3"><p className="text-red-300">{error}</p><Button onClick={() => void reload()}>Retry</Button></div>
            ) : doctors.length === 0 ? (
              <div className="text-center py-8 text-slate-400">No doctor records are available for this account.</div>
            ) : (
              <AdvancedTable
                columns={columns}
                data={doctors}
                onView={handleView}
                onEdit={handleEdit}
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
                Doctor registration will be available here.
              </DialogDescription>
            </DialogHeader>
            <DialogFooter>
              <Button type="button" variant="outline" onClick={() => setIsAddDoctorOpen(false)}>
                Close
              </Button>
            </DialogFooter>
          </DialogContent>
        </Dialog>
      </div>
    </div>
  </ProtectedRoute>
}
