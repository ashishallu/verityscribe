// Patient Types
export interface Patient {
  id: string
  name: string
  email: string
  phone: string
  dateOfBirth: string
  gender: 'male' | 'female' | 'other'
  bloodGroup: string
  address: string
  city: string
  state: string
  zipCode: string
  hospitalId: string
  departmentId: string
  assignedDoctorId: string | null
  status: 'active' | 'inactive' | 'discharged'
  medicalHistory: string[]
  allergies: string[]
  currentMedicines: string[]
  emergencyContactName: string
  emergencyContactPhone: string
  insuranceProvider: string
  insurancePolicyNumber: string
  lastVisit: string
  createdAt: string
  updatedAt: string
}

export interface Doctor {
  id: string
  name: string
  email: string
  phone: string
  specialization: string
  departmentId: string
  hospitalId: string
  licenseNumber: string
  yearsExperience: number
  consultationFee: number
  availability: {
    monday: string[]
    tuesday: string[]
    wednesday: string[]
    thursday: string[]
    friday: string[]
    saturday: string[]
    sunday: string[]
  }
  status: 'active' | 'on_leave' | 'inactive'
  rating: number
  patientsCount: number
  createdAt: string
  updatedAt: string
}

export interface Appointment {
  id: string
  patientId: string
  doctorId: string
  hospitalId: string
  departmentId: string
  date: string
  time: string
  type: 'consultation' | 'checkup' | 'surgery' | 'follow_up'
  status: 'scheduled' | 'completed' | 'cancelled' | 'no_show'
  reason: string
  notes: string
  createdAt: string
  updatedAt: string
}

export interface Hospital {
  id: string
  name: string
  address: string
  city: string
  state: string
  zipCode: string
  phone: string
  email: string
  license: string
  createdAt: string
  updatedAt: string
}

export interface Department {
  id: string
  hospitalId: string
  name: string
  description: string
  headDoctorId: string
  totalBeds: number
  occupiedBeds: number
  createdAt: string
  updatedAt: string
}

export interface TableFilters {
  search: string
  filters: Record<string, any>
  sortBy: string
  sortOrder: 'asc' | 'desc'
  page: number
  pageSize: number
}
