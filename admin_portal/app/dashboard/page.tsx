'use client'

import { useEffect, useState } from 'react'
import { useRouter } from 'next/navigation'
import { useAuth } from '@/app/auth-context'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Badge } from '@/components/ui/badge'
import {
  Activity, AlertCircle, Bell, Calendar, Cpu, Database, Download, Hexagon, LineChart,
  Lock, LogOut, MessageSquare, Moon, Radio, RefreshCw, Search, Settings, Shield,
  Sun, Wifi, Zap, Users, Stethoscope, FileText, Pill, TrendingUp, Heart, Eye
} from 'lucide-react'
import Link from 'next/link'
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs'
import { Avatar, AvatarFallback, AvatarImage } from '@/components/ui/avatar'
import { Progress } from '@/components/ui/progress'
import { Tooltip, TooltipContent, TooltipProvider, TooltipTrigger } from '@/components/ui/tooltip'
import { useResource } from '@/lib/api/use-resource'

export default function DashboardPage() {
  const { profile, user, loading, isAuthenticated, signOut } = useAuth()
  const [theme, setTheme] = useState<'dark' | 'light'>('dark')
  const patients = useResource<{ id: string }>('patients')
  const doctors = useResource<{ id: string }>('doctors')
  const appointments = useResource<{ id: string }>('appointments')
  const consultations = useResource<{ id: string }>('consultations')
  const notifications = useResource<{ id: string }>('notifications')
  const stats = {
    activePatients: patients.total,
    activeDoctors: doctors.total,
    appointments: appointments.total,
    notifications: notifications.total,
    consultations: consultations.total,
  }
  const router = useRouter()
  const profileDisplayName = [profile?.first_name, profile?.last_name].filter(Boolean).join(' ') || user?.email?.split('@')[0] || 'User'

  useEffect(() => {
    if (!loading && !isAuthenticated) {
      router.push('/auth/login')
    }
  }, [loading, isAuthenticated, router])

  const handleLogout = async () => {
    try {
      await signOut()
      router.push('/auth/login')
    } catch (error) {
      console.error('Logout error:', error)
    }
  }

  if (loading) {
    return (
      <div className="min-h-screen bg-gradient-to-br from-black to-slate-900 flex items-center justify-center">
        <div className="text-center">
          <div className="relative w-20 h-20 mx-auto mb-4">
            <div className="absolute inset-0 border-4 border-cyan-500/30 rounded-full animate-ping"></div>
            <div className="absolute inset-2 border-4 border-t-cyan-500 rounded-full animate-spin"></div>
          </div>
          <div className="text-cyan-500 font-mono text-sm">INITIALIZING SYSTEM...</div>
        </div>
      </div>
    )
  }

  return (
    <div className={`${theme} min-h-screen bg-gradient-to-br from-black to-slate-900 text-slate-100 relative overflow-hidden`}>
      <div className="container mx-auto p-4 relative z-10">
        {/* Header */}
        <header className="flex items-center justify-between py-4 border-b border-slate-700/50 mb-6">
          <div className="flex items-center space-x-2">
            <Hexagon className="h-8 w-8 text-cyan-500" />
            <span className="text-xl font-bold bg-gradient-to-r from-cyan-400 to-blue-500 bg-clip-text text-transparent">
              HOSPITAL NEXUS
            </span>
          </div>

          <div className="flex items-center space-x-6">
            <div className="hidden md:flex items-center space-x-1 bg-slate-800/50 rounded-full px-3 py-1.5 border border-slate-700/50 backdrop-blur-sm">
              <Search className="h-4 w-4 text-slate-400" />
              <input
                type="text"
                placeholder="Search patients, doctors..."
                className="bg-transparent border-none focus:outline-none text-sm w-40 placeholder:text-slate-500"
              />
            </div>

            <div className="flex items-center space-x-3">
              <TooltipProvider>
                <Tooltip>
                  <TooltipTrigger asChild>
                    <Link href="/notifications" aria-label="Notifications">
                      <Button variant="ghost" size="icon" className="relative text-slate-400 hover:text-slate-100">
                        <Bell className="h-5 w-5" />
                        {stats.notifications > 0 && <span className="absolute -top-1 -right-1 h-2 w-2 bg-red-500 rounded-full animate-pulse"></span>}
                      </Button>
                    </Link>
                  </TooltipTrigger>
                  <TooltipContent><p>Notifications</p></TooltipContent>
                </Tooltip>

                <Tooltip>
                  <TooltipTrigger asChild>
                    <Button variant="ghost" size="icon" onClick={() => setTheme(theme === 'dark' ? 'light' : 'dark')} className="text-slate-400 hover:text-slate-100">
                      {theme === 'dark' ? <Sun className="h-5 w-5" /> : <Moon className="h-5 w-5" />}
                    </Button>
                  </TooltipTrigger>
                  <TooltipContent><p>Toggle theme</p></TooltipContent>
                </Tooltip>

                <Tooltip>
                  <TooltipTrigger asChild>
                    <Button variant="ghost" size="icon" onClick={handleLogout} className="text-slate-400 hover:text-red-400">
                      <LogOut className="h-5 w-5" />
                    </Button>
                  </TooltipTrigger>
                  <TooltipContent><p>Logout</p></TooltipContent>
                </Tooltip>
              </TooltipProvider>

              <Avatar>
                <AvatarFallback className="bg-slate-700 text-cyan-500">
                  {profileDisplayName.split(' ').map((name) => name[0]).join('').slice(0, 2)}
                </AvatarFallback>
              </Avatar>
            </div>
          </div>
        </header>

        {/* Main Grid */}
        <div className="grid grid-cols-12 gap-6">
          {/* Sidebar */}
          <div className="col-span-12 md:col-span-3 lg:col-span-2">
            <Card className="bg-slate-900/50 border-slate-700/50 backdrop-blur-sm h-full">
              <CardContent className="p-4">
                <nav className="space-y-2">
                  {[
                    { icon: Activity, label: 'Dashboard', href: '/dashboard', active: true },
                    { icon: Users, label: 'Patients', href: '/patients' },
                    { icon: Stethoscope, label: 'Doctors', href: '/doctors' },
                    { icon: Calendar, label: 'Appointments', href: '/appointments' },
                    { icon: FileText, label: 'Reports', href: '/reports' },
                    { icon: MessageSquare, label: 'Consultations', href: '/consultations' },
                    { icon: Pill, label: 'Billing', href: '/billing' },
                    { icon: Settings, label: 'Settings', href: '/settings' },
                  ].map((item) => (
                    <Link key={item.label} href={item.href}>
                      <div className={`flex items-center space-x-2 px-3 py-2 rounded-lg transition-all ${item.active ? 'bg-slate-700/50 text-cyan-400 border border-cyan-500/30' : 'text-slate-400 hover:text-slate-100'}`}>
                        <item.icon className="h-4 w-4" />
                        <span className="text-sm font-medium">{item.label}</span>
                      </div>
                    </Link>
                  ))}
                </nav>
              </CardContent>
            </Card>
          </div>

          {/* Main Content */}
          <div className="col-span-12 md:col-span-9 lg:col-span-10">
            <div className="grid gap-6">
              {/* Hospital Status Card */}
              <Card className="bg-slate-900/50 border-slate-700/50 backdrop-blur-sm overflow-hidden hover:border-cyan-500/30 transition-all">
                <CardHeader className="border-b border-slate-700/50 pb-3">
                  <div className="flex items-center justify-between">
                    <CardTitle className="text-slate-100 flex items-center">
                      <Activity className="mr-2 h-5 w-5 text-cyan-500 animate-pulse" />
                      Hospital Command Center - LIVE
                    </CardTitle>
                    <Badge className="bg-cyan-500/20 text-cyan-400 border-cyan-500/50">OPERATIONAL</Badge>
                  </div>
                </CardHeader>
                <CardContent className="p-6">
                  <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-4">
                    {[
                      { title: 'Total Patients', value: stats.activePatients, icon: Users, color: 'cyan' },
                      { title: 'Available Doctors', value: stats.activeDoctors, icon: Stethoscope, color: 'green' },
                      { title: 'Appointments', value: stats.appointments, icon: Calendar, color: 'blue' },
                      { title: 'Notifications', value: stats.notifications, icon: Bell, color: 'red' },
                    ].map((stat) => (
                      <div key={stat.title} className="bg-slate-800/30 rounded-lg p-4 border border-slate-700/50 hover:border-slate-600/50 hover:shadow-lg hover:shadow-cyan-500/10 transition-all group cursor-pointer">
                        <div className="flex items-start justify-between mb-2">
                          <span className="text-xs font-mono text-slate-400 uppercase">{stat.title}</span>
                          <stat.icon className={`h-4 w-4 text-${stat.color}-500 group-hover:scale-110 transition-transform`} />
                        </div>
                        <div className="text-2xl font-bold text-slate-100 group-hover:text-cyan-400 transition-colors">{stat.value}</div>
                      </div>
                    ))}
                  </div>
                </CardContent>
              </Card>

              {/* Metrics and Status */}
              <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
                <Card className="bg-slate-900/50 border-slate-700/50 backdrop-blur-sm">
                  <CardHeader className="pb-3">
                    <CardTitle className="text-slate-100 flex items-center">
                      <Heart className="mr-2 h-5 w-5 text-red-500" />
                      Hospital Status
                    </CardTitle>
                  </CardHeader>
                  <CardContent className="space-y-4">
                    <p className="text-sm text-slate-400">Live totals are loaded from the authenticated API. Operational percentages are not shown because no authoritative endpoint currently provides them.</p>
                  </CardContent>
                </Card>

                <Card className="bg-slate-900/50 border-slate-700/50 backdrop-blur-sm">
                  <CardHeader className="pb-3">
                    <CardTitle className="text-slate-100 flex items-center">
                      <TrendingUp className="mr-2 h-5 w-5 text-green-500" />
                      Revenue & Operations
                    </CardTitle>
                  </CardHeader>
                  <CardContent className="space-y-4">
                    <div className="bg-slate-800/30 rounded-lg p-3 border border-slate-700/50">
                      <div className="text-xs text-slate-400 font-mono">Consultations</div>
                      <div className="text-xl font-bold text-blue-400 font-mono">{stats.consultations}</div>
                    </div>
                    <div className="grid grid-cols-2 gap-2">
                      <div className="bg-slate-800/30 rounded-lg p-3 border border-slate-700/50">
                        <div className="text-xs text-slate-400">Patients</div>
                        <div className="text-lg font-bold text-cyan-400">{stats.activePatients}</div>
                      </div>
                      <div className="bg-slate-800/30 rounded-lg p-3 border border-slate-700/50">
                        <div className="text-xs text-slate-400">Consultations</div>
                        <div className="text-lg font-bold text-blue-400">{stats.consultations}</div>
                      </div>
                    </div>
                  </CardContent>
                </Card>
              </div>

              {/* Quick Actions */}
              <Card className="bg-slate-900/50 border-slate-700/50 backdrop-blur-sm">
                <CardHeader className="pb-3">
                  <CardTitle className="text-slate-100">Quick Actions</CardTitle>
                </CardHeader>
                <CardContent>
                  <div className="grid grid-cols-2 md:grid-cols-4 gap-3">
                    {[
                      { label: 'New Patient', href: '/patients', icon: Users },
                      { label: 'Book Appointment', href: '/appointments', icon: Calendar },
                      { label: 'View Prescriptions', href: '/prescriptions', icon: Pill },
                      { label: 'Upload Report', href: '/reports', icon: FileText },
                    ].map((action) => (
                      <Link key={action.label} href={action.href}>
                        <Button variant="outline" className="w-full border-slate-700/50 hover:border-cyan-500/50 hover:text-cyan-400 group">
                          <action.icon className="h-4 w-4 mr-2 group-hover:scale-110 transition-transform" />
                          <span className="text-xs">{action.label}</span>
                        </Button>
                      </Link>
                    ))}
                  </div>
                </CardContent>
              </Card>
            </div>
          </div>
        </div>
      </div>
    </div>
  )
}
