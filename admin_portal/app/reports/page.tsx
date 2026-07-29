'use client'
import { ResourcePage } from '@/components/resource-page'
export default function ReportsPage() { return <ResourcePage resource="reports" title="Medical Reports" description="Live reports stored in the VerityScribe ecosystem" columns={[{ key: 'id', label: 'ID', sortable: true, render: (v) => String(v).slice(0, 8) }, { key: 'title', label: 'Title', sortable: true }, { key: 'report_type', label: 'Type', sortable: true }, { key: 'status', label: 'Status', sortable: true }, { key: 'patient_id', label: 'Patient' }, { key: 'created_at', label: 'Created', sortable: true }]} /> }
