'use client'
import { ResourcePage } from '@/components/resource-page'
export default function PrescriptionsPage() { return <ResourcePage resource="prescriptions" title="Prescriptions" description="Live prescriptions created by authorized clinicians" columns={[{ key: 'id', label: 'ID', sortable: true, render: (v) => String(v).slice(0, 8) }, { key: 'diagnosis', label: 'Diagnosis', sortable: true }, { key: 'status', label: 'Status', sortable: true }, { key: 'patient_id', label: 'Patient' }, { key: 'doctor_id', label: 'Doctor' }, { key: 'created_at', label: 'Created', sortable: true }]} /> }
