"use client"

import { useState } from "react"
import Link from "next/link"
import { usePathname } from "next/navigation"
import { Button } from "@/components/ui/button"
import { cn } from "@/lib/utils"

interface AppLayoutProps {
  children: React.ReactNode
}

const navigation = [
  { name: "Dashboard", href: "/", emoji: "📊" },
  { name: "Patients", href: "/patients", emoji: "👥" },
  { name: "Doctors", href: "/doctors", emoji: "👨‍⚕️" },
  { name: "Appointments", href: "/appointments", emoji: "📅" },
  { name: "Consultations", href: "/consultations", emoji: "💬" },
  { name: "Reports", href: "/reports", emoji: "📈" },
  { name: "Billing", href: "/billing", emoji: "💳" },
  { name: "Settings", href: "/settings", emoji: "⚙️" },
]

export function AppLayout({ children }: AppLayoutProps) {
  const [sidebarOpen, setSidebarOpen] = useState(true)
  const pathname = usePathname()

  return (
    <div className="flex h-screen bg-background">
      {/* Sidebar */}
      <aside
        className={cn(
          "border-r border-border bg-card transition-all duration-300 flex flex-col",
          sidebarOpen ? "w-64" : "w-20"
        )}
      >
        {/* Logo */}
        <div className="flex items-center justify-between h-16 px-4 border-b border-border">
          {sidebarOpen && (
            <div className="flex items-center gap-2">
              <div className="w-8 h-8 rounded-lg bg-cyan-500/20 flex items-center justify-center text-lg">
                🏥
              </div>
              <span className="text-lg font-bold text-foreground">Hospital</span>
            </div>
          )}
          <Button
            variant="ghost"
            size="icon"
            onClick={() => setSidebarOpen(!sidebarOpen)}
            className="ml-auto text-lg"
          >
            ☰
          </Button>
        </div>

        {/* Navigation */}
        <nav className="flex-1 overflow-y-auto py-4 px-2">
          {navigation.map((item) => {
            const isActive = pathname === item.href || (pathname.startsWith(item.href) && item.href !== "/")
            return (
              <Link key={item.href} href={item.href}>
                <div
                  className={cn(
                    "flex items-center gap-3 px-4 py-3 rounded-lg mb-2 transition-colors",
                    isActive
                      ? "bg-cyan-500/20 text-cyan-400 border border-cyan-500/30"
                      : "text-muted-foreground hover:text-foreground hover:bg-muted"
                  )}
                >
                  <span className="text-lg flex-shrink-0">{item.emoji}</span>
                  {sidebarOpen && <span className="text-sm font-medium">{item.name}</span>}
                </div>
              </Link>
            )
          })}
        </nav>

        {/* Footer */}
        <div className="border-t border-border p-4">
          <div className="flex items-center justify-center h-10 rounded-lg bg-muted/50">
            {sidebarOpen ? (
              <span className="text-xs text-muted-foreground">© Hospital Admin</span>
            ) : (
              <div className="w-5 h-5 rounded-full bg-cyan-500/20"></div>
            )}
          </div>
        </div>
      </aside>

      {/* Main Content */}
      <div className="flex-1 flex flex-col overflow-hidden">
        {/* Header */}
        <header className="h-16 border-b border-border bg-card flex items-center justify-between px-6">
          <div className="flex-1"></div>
          <div className="flex items-center gap-4">
            <Button variant="ghost" size="icon" className="text-muted-foreground">
              🔔
            </Button>
            <div className="w-10 h-10 rounded-full bg-gradient-to-br from-cyan-400 to-blue-500 flex items-center justify-center text-white font-medium text-sm">
              AD
            </div>
          </div>
        </header>

        {/* Content */}
        <main className="flex-1 overflow-auto bg-background">
          <div className="p-6">
            {children}
          </div>
        </main>
      </div>
    </div>
  )
}
