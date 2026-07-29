'use client'

import React, { useState, useMemo, useCallback } from 'react'
import {
  ChevronLeft, ChevronRight, ChevronsLeft, ChevronsRight, Search, Download,
  MoreVertical, Trash2, Edit2, Eye, Filter, RotateCcw
} from 'lucide-react'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import {
  Select, SelectContent, SelectItem, SelectTrigger, SelectValue
} from '@/components/ui/select'
import {
  DropdownMenu, DropdownMenuContent, DropdownMenuItem, DropdownMenuTrigger
} from '@/components/ui/dropdown-menu'

interface Column {
  key: string
  label: string
  width?: string
  sortable?: boolean
  filterable?: boolean
  render?: (value: any, row: any) => React.ReactNode
}

interface AdvancedTableProps {
  columns: Column[]
  data: any[]
  onView?: (row: any) => void
  onEdit?: (row: any) => void
  onDelete?: (row: any) => void
  onSearch?: (query: string) => void
  searchable?: boolean
  pageSize?: number
  title?: string
}

export function AdvancedTable({
  columns,
  data,
  onView,
  onEdit,
  onDelete,
  onSearch,
  searchable = true,
  pageSize = 10,
  title
}: AdvancedTableProps) {
  const [search, setSearch] = useState('')
  const [sortKey, setSortKey] = useState<string | null>(null)
  const [sortOrder, setSortOrder] = useState<'asc' | 'desc'>('asc')
  const [currentPage, setCurrentPage] = useState(0)
  const [selectedRows, setSelectedRows] = useState<Set<string>>(new Set())

  // Filter and sort data
  const processedData = useMemo(() => {
    let filtered = data

    // Apply search
    if (search) {
      filtered = filtered.filter(row =>
        columns.some(col => {
          const value = row[col.key]?.toString().toLowerCase() || ''
          return value.includes(search.toLowerCase())
        })
      )
    }

    // Apply sorting
    if (sortKey) {
      filtered = [...filtered].sort((a, b) => {
        const aVal = a[sortKey]
        const bVal = b[sortKey]

        if (aVal < bVal) return sortOrder === 'asc' ? -1 : 1
        if (aVal > bVal) return sortOrder === 'asc' ? 1 : -1
        return 0
      })
    }

    return filtered
  }, [data, search, sortKey, sortOrder, columns])

  // Pagination
  const totalPages = Math.ceil(processedData.length / pageSize)
  const paginatedData = useMemo(() => {
    const start = currentPage * pageSize
    return processedData.slice(start, start + pageSize)
  }, [processedData, currentPage, pageSize])

  const handleSort = useCallback((key: string) => {
    if (sortKey === key) {
      setSortOrder(sortOrder === 'asc' ? 'desc' : 'asc')
    } else {
      setSortKey(key)
      setSortOrder('asc')
    }
  }, [sortKey, sortOrder])

  const handleSearch = useCallback((value: string) => {
    setSearch(value)
    onSearch?.(value)
    setCurrentPage(0)
  }, [onSearch])

  const handleSelectRow = useCallback((id: string) => {
    const newSelected = new Set(selectedRows)
    if (newSelected.has(id)) {
      newSelected.delete(id)
    } else {
      newSelected.add(id)
    }
    setSelectedRows(newSelected)
  }, [selectedRows])

  const handleSelectAll = useCallback((checked: boolean) => {
    if (checked) {
      setSelectedRows(new Set(paginatedData.map((row, i) => `row-${i}`)))
    } else {
      setSelectedRows(new Set())
    }
  }, [paginatedData])

  const exportCSV = useCallback(() => {
    const headers = columns.map(c => c.label).join(',')
    const rows = processedData.map(row =>
      columns.map(col => row[col.key] || '').join(',')
    )
    const csv = [headers, ...rows].join('\n')
    const blob = new Blob([csv], { type: 'text/csv' })
    const url = window.URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = 'export.csv'
    a.click()
  }, [processedData, columns])

  return (
    <div className="space-y-4">
      {/* Header */}
      <div className="flex items-center justify-between">
        {title && <h3 className="text-lg font-semibold">{title}</h3>}
        {searchable && (
          <div className="flex items-center gap-2">
            <div className="relative">
              <Search className="absolute left-2 top-2.5 h-4 w-4 text-muted-foreground" />
              <Input
                placeholder="Search..."
                value={search}
                onChange={(e) => handleSearch(e.target.value)}
                className="pl-8"
              />
            </div>
            <Button
              variant="outline"
              size="icon"
              onClick={exportCSV}
              title="Export as CSV"
            >
              <Download className="h-4 w-4" />
            </Button>
            {search && (
              <Button
                variant="outline"
                size="icon"
                onClick={() => handleSearch('')}
                title="Clear search"
              >
                <RotateCcw className="h-4 w-4" />
              </Button>
            )}
          </div>
        )}
      </div>

      {/* Table */}
      <div className="border border-slate-700/50 rounded-lg overflow-hidden">
        <table className="w-full">
          <thead className="bg-slate-900/50 border-b border-slate-700/50 sticky top-0">
            <tr>
              <th className="w-10 px-4 py-3 text-left">
                <input
                  type="checkbox"
                  onChange={(e) => handleSelectAll(e.target.checked)}
                  className="rounded"
                />
              </th>
              {columns.map((col) => (
                <th
                  key={col.key}
                  className="px-4 py-3 text-left text-sm font-semibold text-slate-200 cursor-pointer hover:bg-slate-800/50"
                  onClick={() => col.sortable !== false && handleSort(col.key)}
                  style={{ width: col.width }}
                >
                  <div className="flex items-center gap-2">
                    {col.label}
                    {sortKey === col.key && (
                      <span className="text-cyan-500">
                        {sortOrder === 'asc' ? '↑' : '↓'}
                      </span>
                    )}
                  </div>
                </th>
              ))}
              <th className="px-4 py-3 text-left text-sm font-semibold text-slate-200">
                Actions
              </th>
            </tr>
          </thead>
          <tbody>
            {paginatedData.length === 0 ? (
              <tr>
                <td colSpan={columns.length + 2} className="px-4 py-8 text-center text-muted-foreground">
                  No data found
                </td>
              </tr>
            ) : (
              paginatedData.map((row, idx) => (
                <tr
                  key={idx}
                  className="border-b border-slate-700/30 hover:bg-slate-800/30 transition-colors"
                >
                  <td className="w-10 px-4 py-3">
                    <input
                      type="checkbox"
                      checked={selectedRows.has(`row-${idx}`)}
                      onChange={() => handleSelectRow(`row-${idx}`)}
                      className="rounded"
                    />
                  </td>
                  {columns.map((col) => (
                    <td key={`${idx}-${col.key}`} className="px-4 py-3 text-sm">
                      {col.render ? col.render(row[col.key], row) : row[col.key]}
                    </td>
                  ))}
                  <td className="px-4 py-3">
                    <DropdownMenu>
                      <DropdownMenuTrigger asChild>
                        <Button variant="ghost" size="icon" className="h-8 w-8">
                          <MoreVertical className="h-4 w-4" />
                        </Button>
                      </DropdownMenuTrigger>
                      <DropdownMenuContent align="end">
                        {onView && (
                          <DropdownMenuItem onClick={() => onView(row)}>
                            <Eye className="mr-2 h-4 w-4" /> View
                          </DropdownMenuItem>
                        )}
                        {onEdit && (
                          <DropdownMenuItem onClick={() => onEdit(row)}>
                            <Edit2 className="mr-2 h-4 w-4" /> Edit
                          </DropdownMenuItem>
                        )}
                        {onDelete && (
                          <DropdownMenuItem
                            onClick={() => onDelete(row)}
                            className="text-red-500"
                          >
                            <Trash2 className="mr-2 h-4 w-4" /> Delete
                          </DropdownMenuItem>
                        )}
                      </DropdownMenuContent>
                    </DropdownMenu>
                  </td>
                </tr>
              ))
            )}
          </tbody>
        </table>
      </div>

      {/* Footer with pagination */}
      <div className="flex items-center justify-between">
        <div className="text-sm text-muted-foreground">
          Showing {Math.min(currentPage * pageSize + 1, processedData.length)} to {Math.min((currentPage + 1) * pageSize, processedData.length)} of {processedData.length}
        </div>
        <div className="flex items-center gap-2">
          <Select value={pageSize.toString()} onValueChange={(val) => {
            // This would need to be lifted to parent state for proper implementation
          }}>
            <SelectTrigger className="w-20">
              <SelectValue />
            </SelectTrigger>
            <SelectContent>
              <SelectItem value="10">10</SelectItem>
              <SelectItem value="25">25</SelectItem>
              <SelectItem value="50">50</SelectItem>
              <SelectItem value="100">100</SelectItem>
            </SelectContent>
          </Select>
          <Button
            variant="outline"
            size="icon"
            onClick={() => setCurrentPage(0)}
            disabled={currentPage === 0}
          >
            <ChevronsLeft className="h-4 w-4" />
          </Button>
          <Button
            variant="outline"
            size="icon"
            onClick={() => setCurrentPage(Math.max(0, currentPage - 1))}
            disabled={currentPage === 0}
          >
            <ChevronLeft className="h-4 w-4" />
          </Button>
          <span className="text-sm">
            Page {currentPage + 1} of {totalPages || 1}
          </span>
          <Button
            variant="outline"
            size="icon"
            onClick={() => setCurrentPage(Math.min(totalPages - 1, currentPage + 1))}
            disabled={currentPage === totalPages - 1}
          >
            <ChevronRight className="h-4 w-4" />
          </Button>
          <Button
            variant="outline"
            size="icon"
            onClick={() => setCurrentPage(totalPages - 1)}
            disabled={currentPage === totalPages - 1}
          >
            <ChevronsRight className="h-4 w-4" />
          </Button>
        </div>
      </div>
    </div>
  )
}
