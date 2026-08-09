'use client'

import { useCallback, useState } from 'react'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Button } from '@/components/ui/button'
import { Badge } from '@/components/ui/badge'
import { Users, Plus } from 'lucide-react'
import { AdvancedTable } from '@/components/advanced-table'
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/dialog'
import { useResource } from '@/lib/api/use-resource'
import { ProtectedRoute } from '@/components/protected-route'

export default function PatientsPage() {
  const { data: patients, total, loading, error, reload, remove } = useResource<any>('patients')
  const [isAddPatientOpen, setIsAddPatientOpen] = useState(false)

  const handleView = (patient: any) => {
    console.log('[v0] View patient:', patient)
  }

  const handleEdit = (patient: any) => {
    console.log('[v0] Edit patient:', patient)
  }

  const handleDelete = useCallback(async (patient: any) => {
    if (window.confirm('Delete this patient record?')) await remove(patient.id)
  }, [remove])

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
                onEdit={handleEdit}
                onDelete={handleDelete}
                title="Patients"
                searchable={true}
              />
            )}
          </CardContent>
        </Card>

        <Dialog open={isAddPatientOpen} onOpenChange={setIsAddPatientOpen}>
          <DialogContent className="border-slate-700 bg-slate-950 text-slate-100">
            <DialogHeader>
              <DialogTitle>Add Patient</DialogTitle>
              <DialogDescription className="text-slate-400">
                Patient registration will be available here.
              </DialogDescription>
            </DialogHeader>
            <DialogFooter>
              <Button type="button" variant="outline" onClick={() => setIsAddPatientOpen(false)}>
                Close
              </Button>
            </DialogFooter>
          </DialogContent>
        </Dialog>
      </div>
    </div>
  </ProtectedRoute>
}
