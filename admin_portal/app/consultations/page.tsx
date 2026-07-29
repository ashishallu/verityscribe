'use client'
import { ResourcePage } from '@/components/resource-page'
export default function ConsultationsPage() { return <ResourcePage resource="consultations" title="Consultations" description="Live patient consultation records" columns={[{ key: 'id', label: 'ID', sortable: true, render: (v) => String(v).slice(0, 8) }, { key: 'diagnosis', label: 'Diagnosis', sortable: true }, { key: 'status', label: 'Status', sortable: true }, { key: 'patient_id', label: 'Patient' }, { key: 'doctor_id', label: 'Doctor' }, { key: 'created_at', label: 'Created', sortable: true }]} /> }
