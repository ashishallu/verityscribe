'use client'
import { ResourcePage } from '@/components/resource-page'
export default function AppointmentsPage() { return <ResourcePage resource="appointments" title="Appointments" description="Live appointment scheduling and status management" columns={[{ key: 'id', label: 'ID', sortable: true, render: (v) => String(v).slice(0, 8) }, { key: 'scheduled_at', label: 'Scheduled', sortable: true }, { key: 'status', label: 'Status', sortable: true }, { key: 'reason', label: 'Reason', sortable: true }, { key: 'patient_id', label: 'Patient' }, { key: 'doctor_id', label: 'Doctor' }]} /> }
