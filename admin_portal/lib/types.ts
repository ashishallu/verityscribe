// Hospital Administration Portal - Data Types

export interface Patient {
  id: string
  mrn: string // Medical Record Number
  firstName: string
  lastName: string
  dateOfBirth: string
  gender: "M" | "F" | "Other"
  email: string
  phone: string
  address: string
  city: string
  state: string
  pincode: string
  bloodGroup: string
  emergencyContact: string
  emergencyPhone: string
  insuranceProvider: string
  insurancePolicyNumber: string
  status: "active" | "inactive" | "discharged"
  createdAt: string
  lastVisit: string
}

export interface Doctor {
  id: string
  firstName: string
  lastName: string
  specialization: string
  qualification: string
  licenseNumber: string
  email: string
  phone: string
  experience: number // years
  department: string
  availability: "available" | "busy" | "off-duty"
  consultationFee: number
  hospital: string
  status: "active" | "inactive"
  createdAt: string
}

export interface Appointment {
  id: string
  patientId: string
  patientName: string
  doctorId: string
  doctorName: string
  department: string
  appointmentDate: string
  appointmentTime: string
  duration: number // minutes
  reason: string
  status: "scheduled" | "completed" | "cancelled" | "no-show"
  notes?: string
  createdAt: string
}

export interface Consultation {
  id: string
  patientId: string
  doctorId: string
  appointmentId: string
  date: string
  diagnosis: string
  symptoms: string[]
  medications: string[]
  testsPrescribed: string[]
  notes: string
  followUpDate?: string
  status: "in-progress" | "completed"
  createdAt: string
}

export interface MedicalRecord {
  id: string
  patientId: string
  recordType: "consultation" | "test" | "imaging" | "vaccination" | "prescription"
  title: string
  description: string
  date: string
  doctor?: string
  attachments?: string[]
  createdAt: string
}

export interface Department {
  id: string
  name: string
  head: string
  description: string
  totalBeds: number
  occupiedBeds: number
  doctors: number
  status: "operational" | "maintenance"
}

export interface HospitalMetrics {
  totalPatients: number
  activePatients: number
  totalDoctors: number
  availableDoctors: number
  totalBeds: number
  occupiedBeds: number
  appointmentsToday: number
  completedAppointments: number
  revenue: number
  pendingPayments: number
}

export interface User {
  id: string
  name: string
  email: string
  role: "admin" | "doctor" | "nurse" | "receptionist" | "patient"
  department?: string
  lastLogin: string
  status: "active" | "inactive"
}

export interface Insurance {
  id: string
  patientId: string
  provider: string
  policyNumber: string
  groupNumber?: string
  effectiveDate: string
  expiryDate: string
  coverageLimit: number
  claimsProcessed: number
  status: "active" | "expired" | "pending"
}

export interface BillingRecord {
  id: string
  patientId: string
  appointmentId?: string
  consultationId?: string
  description: string
  amount: number
  tax: number
  total: number
  paymentMethod: "cash" | "card" | "insurance" | "check"
  status: "pending" | "paid" | "partial" | "refunded"
  date: string
  dueDate: string
}

export interface TableColumn<T> {
  key: keyof T
  label: string
  sortable?: boolean
  filterable?: boolean
  width?: string
}
