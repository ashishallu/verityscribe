'use client'
import { ResourcePage } from '@/components/resource-page'
export default function NotificationsPage() { return <ResourcePage resource="notifications" title="Notifications" description="Live patient and clinician notifications" columns={[{ key: 'title', label: 'Title', sortable: true }, { key: 'message', label: 'Message' }, { key: 'status', label: 'Status', sortable: true }, { key: 'created_at', label: 'Created', sortable: true }]} /> }
