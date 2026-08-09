'use client'

import { ResourcePage } from '@/components/resource-page'

export default function InventoryPage() {
  return <ResourcePage
    resource="inventory"
    title="Medicine Inventory"
    description="Hospital-scoped medicine stock, batches, and expiry monitoring"
    columns={[
      { key: 'medicine.name', label: 'Medicine', sortable: true, render: (_, row) => String((row.medicine as { name?: string } | undefined)?.name ?? '—') },
      { key: 'quantity', label: 'Quantity', sortable: true },
      { key: 'minimum_threshold', label: 'Minimum threshold', sortable: true },
      { key: 'batch_number', label: 'Batch' },
      { key: 'expiry_date', label: 'Expiry', sortable: true },
      { key: 'unit_cost_inr', label: 'Unit cost', sortable: true },
      { key: 'is_out_of_stock', label: 'Out of stock', render: (value) => value ? 'OUT OF STOCK' : '—' },
      { key: 'is_low_stock', label: 'Low stock', render: (value) => value ? 'LOW STOCK' : 'AVAILABLE' },
    ]}
  />
}
