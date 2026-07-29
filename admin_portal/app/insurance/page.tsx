'use client'
import { ResourcePage } from '@/components/resource-page'
export default function InsurancePage() { return <ResourcePage resource="insurance" title="Insurance" description="Live insurance coverage and policy records" columns={[{ key: 'provider', label: 'Provider', sortable: true }, { key: 'policy_number', label: 'Policy number', sortable: true }, { key: 'status', label: 'Status', sortable: true }, { key: 'patient_id', label: 'Patient' }, { key: 'created_at', label: 'Created', sortable: true }]} /> }
