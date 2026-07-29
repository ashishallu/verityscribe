import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Stethoscope, Users, Calendar, FileText, Pill, TrendingUp, CheckCircle2, ArrowRight } from 'lucide-react'
import Link from 'next/link'

export default function LandingPage() {
  return (
    <div className="min-h-screen bg-gradient-to-br from-slate-50 to-slate-100 dark:from-slate-950 dark:to-slate-900">
      {/* Navigation */}
      <nav className="border-b bg-white dark:bg-slate-900 sticky top-0 z-50">
        <div className="container max-w-7xl mx-auto px-4 py-4 flex items-center justify-between">
          <div className="flex items-center gap-2">
            <Stethoscope className="h-6 w-6 text-blue-600" />
            <h1 className="text-2xl font-bold">HealthCare Hub</h1>
          </div>
          <div className="flex gap-4">
            <Button variant="ghost" asChild>
              <Link href="/auth/login">Login</Link>
            </Button>
            <Button asChild>
              <Link href="/auth/signup">Sign Up</Link>
            </Button>
          </div>
        </div>
      </nav>

      {/* Hero Section */}
      <section className="container max-w-7xl mx-auto px-4 py-16 md:py-24">
        <div className="grid grid-cols-1 lg:grid-cols-2 gap-12 items-center">
          <div className="space-y-6">
            <h2 className="text-4xl md:text-5xl font-bold leading-tight">
              Healthcare Made Simple & Accessible
            </h2>
            <p className="text-lg text-muted-foreground">
              Connect with top doctors, manage appointments, access medical records, and receive personalized healthcare services all in one platform.
            </p>
            <div className="flex gap-4">
              <Button size="lg" asChild>
                <Link href="/auth/signup">Get Started <ArrowRight className="ml-2 h-4 w-4" /></Link>
              </Button>
              <Button size="lg" variant="outline" asChild>
                <Link href="#features">Learn More</Link>
              </Button>
            </div>
          </div>
          <div className="bg-gradient-to-br from-blue-500 to-blue-600 rounded-2xl p-8 text-white">
            <div className="space-y-6">
              <div className="flex items-start gap-4">
                <CheckCircle2 className="h-6 w-6 flex-shrink-0 mt-1" />
                <div>
                  <p className="font-semibold">Expert Medical Professionals</p>
                  <p className="text-sm opacity-90">Access verified doctors and specialists</p>
                </div>
              </div>
              <div className="flex items-start gap-4">
                <CheckCircle2 className="h-6 w-6 flex-shrink-0 mt-1" />
                <div>
                  <p className="font-semibold">Secure Digital Records</p>
                  <p className="text-sm opacity-90">HIPAA-compliant health data storage</p>
                </div>
              </div>
              <div className="flex items-start gap-4">
                <CheckCircle2 className="h-6 w-6 flex-shrink-0 mt-1" />
                <div>
                  <p className="font-semibold">24/7 Accessibility</p>
                  <p className="text-sm opacity-90">Book appointments anytime, anywhere</p>
                </div>
              </div>
            </div>
          </div>
        </div>
      </section>

      {/* Features Section */}
      <section id="features" className="py-16 md:py-24 bg-white dark:bg-slate-900">
        <div className="container max-w-7xl mx-auto px-4">
          <div className="text-center mb-12">
            <h3 className="text-3xl md:text-4xl font-bold mb-4">Core Features</h3>
            <p className="text-lg text-muted-foreground">Everything you need for comprehensive healthcare management</p>
          </div>

          <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6">
            {/* Doctor Booking */}
            <Card className="hover:shadow-lg transition-shadow">
              <CardHeader>
                <div className="flex items-center gap-3 mb-2">
                  <div className="bg-blue-100 dark:bg-blue-900 p-2 rounded-lg">
                    <Stethoscope className="h-5 w-5 text-blue-600" />
                  </div>
                  <CardTitle>Find & Book Doctors</CardTitle>
                </div>
              </CardHeader>
              <CardContent>
                <CardDescription>
                  Browse certified doctors by specialization, location, and patient reviews. Book consultations at your convenience.
                </CardDescription>
              </CardContent>
            </Card>

            {/* Appointments */}
            <Card className="hover:shadow-lg transition-shadow">
              <CardHeader>
                <div className="flex items-center gap-3 mb-2">
                  <div className="bg-green-100 dark:bg-green-900 p-2 rounded-lg">
                    <Calendar className="h-5 w-5 text-green-600" />
                  </div>
                  <CardTitle>Appointment Management</CardTitle>
                </div>
              </CardHeader>
              <CardContent>
                <CardDescription>
                  Schedule, reschedule, and manage appointments. Receive reminders and real-time updates about your bookings.
                </CardDescription>
              </CardContent>
            </Card>

            {/* Medical Records */}
            <Card className="hover:shadow-lg transition-shadow">
              <CardHeader>
                <div className="flex items-center gap-3 mb-2">
                  <div className="bg-purple-100 dark:bg-purple-900 p-2 rounded-lg">
                    <FileText className="h-5 w-5 text-purple-600" />
                  </div>
                  <CardTitle>Medical Records</CardTitle>
                </div>
              </CardHeader>
              <CardContent>
                <CardDescription>
                  Maintain complete health history with medical reports, test results, and doctor notes all in one place.
                </CardDescription>
              </CardContent>
            </Card>

            {/* Prescriptions */}
            <Card className="hover:shadow-lg transition-shadow">
              <CardHeader>
                <div className="flex items-center gap-3 mb-2">
                  <div className="bg-orange-100 dark:bg-orange-900 p-2 rounded-lg">
                    <Pill className="h-5 w-5 text-orange-600" />
                  </div>
                  <CardTitle>Digital Prescriptions</CardTitle>
                </div>
              </CardHeader>
              <CardContent>
                <CardDescription>
                  Receive digital prescriptions from doctors, track medication schedules, and manage pharmacy orders.
                </CardDescription>
              </CardContent>
            </Card>

            {/* Consultations */}
            <Card className="hover:shadow-lg transition-shadow">
              <CardHeader>
                <div className="flex items-center gap-3 mb-2">
                  <div className="bg-pink-100 dark:bg-pink-900 p-2 rounded-lg">
                    <Users className="h-5 w-5 text-pink-600" />
                  </div>
                  <CardTitle>Online Consultations</CardTitle>
                </div>
              </CardHeader>
              <CardContent>
                <CardDescription>
                  Connect with doctors via video, phone, or chat. Secure end-to-end encrypted communication.
                </CardDescription>
              </CardContent>
            </Card>

            {/* Analytics */}
            <Card className="hover:shadow-lg transition-shadow">
              <CardHeader>
                <div className="flex items-center gap-3 mb-2">
                  <div className="bg-cyan-100 dark:bg-cyan-900 p-2 rounded-lg">
                    <TrendingUp className="h-5 w-5 text-cyan-600" />
                  </div>
                  <CardTitle>Health Analytics</CardTitle>
                </div>
              </CardHeader>
              <CardContent>
                <CardDescription>
                  Track health metrics, monitor vitals, and get personalized health insights and recommendations.
                </CardDescription>
              </CardContent>
            </Card>
          </div>
        </div>
      </section>

      {/* Stats Section */}
      <section className="py-16 md:py-24">
        <div className="container max-w-7xl mx-auto px-4">
          <div className="grid grid-cols-1 md:grid-cols-4 gap-8">
            <div className="text-center">
              <div className="text-4xl md:text-5xl font-bold text-blue-600 mb-2">500+</div>
              <p className="text-muted-foreground">Hospitals & Clinics</p>
            </div>
            <div className="text-center">
              <div className="text-4xl md:text-5xl font-bold text-green-600 mb-2">10,000+</div>
              <p className="text-muted-foreground">Verified Doctors</p>
            </div>
            <div className="text-center">
              <div className="text-4xl md:text-5xl font-bold text-purple-600 mb-2">1M+</div>
              <p className="text-muted-foreground">Active Patients</p>
            </div>
            <div className="text-center">
              <div className="text-4xl md:text-5xl font-bold text-orange-600 mb-2">50M+</div>
              <p className="text-muted-foreground">Consultations</p>
            </div>
          </div>
        </div>
      </section>

      {/* CTA Section */}
      <section className="py-16 md:py-24 bg-gradient-to-r from-blue-600 to-blue-700 dark:from-blue-900 dark:to-blue-800 text-white">
        <div className="container max-w-7xl mx-auto px-4 text-center">
          <h3 className="text-3xl md:text-4xl font-bold mb-6">Ready to Transform Your Healthcare?</h3>
          <p className="text-lg mb-8 opacity-90 max-w-2xl mx-auto">
            Join millions of patients and healthcare providers who trust HealthCare Hub for seamless healthcare management.
          </p>
          <Button size="lg" variant="secondary" asChild>
            <Link href="/auth/signup">Get Started Now <ArrowRight className="ml-2 h-4 w-4" /></Link>
          </Button>
        </div>
      </section>

      {/* Footer */}
      <footer className="bg-white dark:bg-slate-900 border-t">
        <div className="container max-w-7xl mx-auto px-4 py-8">
          <div className="flex flex-col md:flex-row items-center justify-between">
            <div className="flex items-center gap-2 mb-4 md:mb-0">
              <Stethoscope className="h-5 w-5 text-blue-600" />
              <p className="font-semibold">HealthCare Hub</p>
            </div>
            <p className="text-sm text-muted-foreground">
              © {new Date().getFullYear()} HealthCare Hub. All rights reserved. Your health, our priority.
            </p>
          </div>
        </div>
      </footer>
    </div>
  )
}
