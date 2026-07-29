"use client"

import { useState, useMemo } from "react"
import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuTrigger,
  DropdownMenuCheckboxItem,
} from "@/components/ui/dropdown-menu"
import { cn } from "@/lib/utils"

interface DataTableProps<T> {
  data: T[]
  columns: {
    key: keyof T
    label: string
    sortable?: boolean
    render?: (value: any) => React.ReactNode
    width?: string
  }[]
  title: string
  onRowClick?: (row: T) => void
  actions?: (row: T) => React.ReactNode
  searchableColumns?: (keyof T)[]
}

export function DataTable<T extends { id: string }>({
  data,
  columns,
  title,
  onRowClick,
  actions,
  searchableColumns = [],
}: DataTableProps<T>) {
  const [searchTerm, setSearchTerm] = useState("")
  const [sortColumn, setSortColumn] = useState<keyof T | null>(null)
  const [sortDirection, setSortDirection] = useState<"asc" | "desc">("asc")
  const [visibleColumns, setVisibleColumns] = useState<Set<keyof T>>(
    new Set(columns.map((col) => col.key))
  )
  const [currentPage, setCurrentPage] = useState(1)
  const itemsPerPage = 10

  // Filter data
  const filteredData = useMemo(() => {
    if (!searchTerm) return data

    return data.filter((row) =>
      searchableColumns.some((col) => {
        const value = row[col]
        return value
          ?.toString()
          .toLowerCase()
          .includes(searchTerm.toLowerCase())
      })
    )
  }, [data, searchTerm, searchableColumns])

  // Sort data
  const sortedData = useMemo(() => {
    if (!sortColumn) return filteredData

    const sorted = [...filteredData].sort((a, b) => {
      const aValue = a[sortColumn]
      const bValue = b[sortColumn]

      if (aValue === null || aValue === undefined) return 1
      if (bValue === null || bValue === undefined) return -1

      if (typeof aValue === "string") {
        return sortDirection === "asc"
          ? aValue.localeCompare(bValue.toString())
          : bValue.toString().localeCompare(aValue)
      }

      if (typeof aValue === "number") {
        return sortDirection === "asc"
          ? (aValue as number) - (bValue as number)
          : (bValue as number) - (aValue as number)
      }

      return 0
    })

    return sorted
  }, [filteredData, sortColumn, sortDirection])

  // Paginate data
  const paginatedData = useMemo(() => {
    const startIndex = (currentPage - 1) * itemsPerPage
    return sortedData.slice(startIndex, startIndex + itemsPerPage)
  }, [sortedData, currentPage])

  const totalPages = Math.ceil(sortedData.length / itemsPerPage)

  const handleSort = (column: keyof T) => {
    if (sortColumn === column) {
      setSortDirection(sortDirection === "asc" ? "desc" : "asc")
    } else {
      setSortColumn(column)
      setSortDirection("asc")
    }
  }

  const toggleColumnVisibility = (column: keyof T) => {
    const newVisible = new Set(visibleColumns)
    if (newVisible.has(column)) {
      newVisible.delete(column)
    } else {
      newVisible.add(column)
    }
    setVisibleColumns(newVisible)
  }

  const visibleColumnsArray = columns.filter((col) => visibleColumns.has(col.key))

  const exportAsCSV = () => {
    const headers = visibleColumnsArray.map((col) => col.label).join(",")
    const rows = sortedData
      .map((row) =>
        visibleColumnsArray
          .map((col) => {
            const value = row[col.key]
            const stringValue = value?.toString() || ""
            return `"${stringValue.replace(/"/g, '""')}"`
          })
          .join(",")
      )
      .join("\n")

    const csv = `${headers}\n${rows}`
    const blob = new Blob([csv], { type: "text/csv" })
    const url = window.URL.createObjectURL(blob)
    const a = document.createElement("a")
    a.href = url
    a.download = `${title.toLowerCase()}-export.csv`
    a.click()
  }

  return (
    <div className="space-y-4">
      {/* Header */}
      <div className="flex items-center justify-between">
        <h2 className="text-xl font-semibold text-foreground">{title}</h2>
        <div className="flex items-center gap-2">
          <DropdownMenu>
            <DropdownMenuTrigger asChild>
              <Button variant="outline" size="sm">
                <span className="mr-2">⚙️</span>
                Columns
              </Button>
            </DropdownMenuTrigger>
            <DropdownMenuContent align="end">
              {columns.map((col) => (
                <DropdownMenuCheckboxItem
                  key={String(col.key)}
                  checked={visibleColumns.has(col.key)}
                  onCheckedChange={() => toggleColumnVisibility(col.key)}
                >
                  {col.label}
                </DropdownMenuCheckboxItem>
              ))}
            </DropdownMenuContent>
          </DropdownMenu>

          <Button variant="outline" size="sm" onClick={exportAsCSV}>
            <span className="mr-2">⬇️</span>
            Export
          </Button>
        </div>
      </div>

      {/* Search */}
      <div className="relative">
        <span className="absolute left-3 top-3 text-muted-foreground">🔍</span>
        <Input
          placeholder="Search..."
          value={searchTerm}
          onChange={(e) => {
            setSearchTerm(e.target.value)
            setCurrentPage(1)
          }}
          className="pl-10"
        />
      </div>

      {/* Table */}
      <div className="rounded-lg border border-border overflow-hidden">
        <div className="overflow-x-auto">
          <table className="w-full">
            <thead>
              <tr className="border-b border-border bg-muted/50">
                {visibleColumnsArray.map((col) => (
                  <th key={String(col.key)} className={cn("px-4 py-3 text-left font-medium text-sm", col.width)}>
                    <div className="flex items-center gap-2">
                      <span>{col.label}</span>
                      {col.sortable && (
                        <button
                          onClick={() => handleSort(col.key)}
                          className="p-1 hover:bg-background rounded transition-colors text-sm"
                        >
                          {sortColumn === col.key ? (
                            sortDirection === "asc" ? (
                              <span>⬆️</span>
                            ) : (
                              <span>⬇️</span>
                            )
                          ) : (
                            <span style={{ opacity: 0.3 }}>↕️</span>
                          )}
                        </button>
                      )}
                    </div>
                  </th>
                ))}
                {actions && <th className="px-4 py-3 text-left font-medium text-sm w-20">Actions</th>}
              </tr>
            </thead>
            <tbody>
              {paginatedData.length > 0 ? (
                paginatedData.map((row) => (
                  <tr
                    key={row.id}
                    onClick={() => onRowClick?.(row)}
                    className={cn(
                      "border-b border-border hover:bg-muted/30 transition-colors",
                      onRowClick && "cursor-pointer"
                    )}
                  >
                    {visibleColumnsArray.map((col) => (
                      <td key={String(col.key)} className="px-4 py-3 text-sm text-foreground">
                        {col.render ? col.render(row[col.key]) : String(row[col.key])}
                      </td>
                    ))}
                    {actions && <td className="px-4 py-3">{actions(row)}</td>}
                  </tr>
                ))
              ) : (
                <tr>
                  <td colSpan={visibleColumnsArray.length + (actions ? 1 : 0)} className="px-4 py-8 text-center text-muted-foreground">
                    No results found
                  </td>
                </tr>
              )}
            </tbody>
          </table>
        </div>
      </div>

      {/* Pagination */}
      {totalPages > 1 && (
        <div className="flex items-center justify-between">
          <p className="text-sm text-muted-foreground">
            Showing {(currentPage - 1) * itemsPerPage + 1} to{" "}
            {Math.min(currentPage * itemsPerPage, sortedData.length)} of {sortedData.length}
          </p>
          <div className="flex gap-2">
            <Button
              variant="outline"
              size="sm"
              onClick={() => setCurrentPage(Math.max(1, currentPage - 1))}
              disabled={currentPage === 1}
            >
              Previous
            </Button>
            {Array.from({ length: totalPages }, (_, i) => i + 1).map((page) => (
              <Button
                key={page}
                variant={page === currentPage ? "default" : "outline"}
                size="sm"
                onClick={() => setCurrentPage(page)}
              >
                {page}
              </Button>
            ))}
            <Button
              variant="outline"
              size="sm"
              onClick={() => setCurrentPage(Math.min(totalPages, currentPage + 1))}
              disabled={currentPage === totalPages}
            >
              Next
            </Button>
          </div>
        </div>
      )}
    </div>
  )
}
