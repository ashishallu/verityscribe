// User & Authentication
export type UserRole = 'patient' | 'doctor' | 'hospital_admin' | 'receptionist' | 'nurse' | 'medical_staff' | 'super_admin'

export type Profile = {
  id: string
  email: string
  phone?: string
  role: UserRole
  first_name: string
  last_name: string
  date_of_birth?: string
  gender?: 'male' | 'female' | 'other'
  profile_image_url?: string
  is_active: boolean
  created_at: string
  updated_at: string
  deleted_at?: string
}

// Hospital & Infrastructure
export type Hospital = {
  id: string
  name: string
  email?: string
  phone?: string
  address?: string
  city?: string
  state?: string
  pincode?: string
  country: string
  website?: string
  registration_number?: string
  license_number?: string
  total_beds: number
  occupied_beds: number
  is_active: boolean
  created_at: string
  updated_at: string
  deleted_at?: string
}

export type Department = {
  id: string
  hospital_id: string
  name: string
  description?: string
  head_doctor_id?: string
  total_beds: number
  occupied_beds: number
  phone?: string
  is_active: boolean
  created_at: string
  updated_at: string
  deleted_at?: string
}

// Doctors
export type Doctor = {
  id: string
  hospital_id: string
  department_id: string
  license_number: string
  specialization?: string
  experience_years?: number
  qualification?: string
  consultation_fee_inr?: number
  rating: number
  total_consultations: number
  is_available: boolean
  created_at: string
  updated_at: string
  deleted_at?: string
} & Profile

export type DoctorEducation = {
  id: string
  doctor_id: string
  degree: string
  institution: string
  year_of_completion?: number
  created_at: string
}

export type DoctorSpecialization = {
  id: string
  doctor_id: string
  specialization: string
  created_at: string
}

// Patients
export type Patient = {
  id: string
  hospital_id?: string
  mrn?: string
  date_of_birth: string
  gender: 'male' | 'female' | 'other'
  marital_status?: 'single' | 'married' | 'widowed' | 'divorced' | 'prefer_not_to_say'
  blood_group?: 'A+' | 'A-' | 'B+' | 'B-' | 'AB+' | 'AB-' | 'O+' | 'O-'
  height_cm?: number
  weight_kg?: number
  occupation?: string
  created_at: string
  updated_at: string
  deleted_at?: string
} & Profile

// Appointments
export type AppointmentStatus = 'scheduled' | 'in_progress' | 'completed' | 'cancelled' | 'no_show' | 'rescheduled'
export type ConsultationType = 'in_person' | 'video' | 'phone' | 'chat'

export type Appointment = {
  id: string
  patient_id: string
  doctor_id: string
  hospital_id: string
  appointment_date: string
  appointment_time: string
  consultation_type: ConsultationType
  status: AppointmentStatus
  reason_for_visit?: string
  duration_minutes: number
  appointment_fee?: number
  notes?: string
  created_at: string
  updated_at: string
  deleted_at?: string
}

// Consultations
export type Consultation = {
  id: string
  appointment_id: string
  patient_id: string
  doctor_id: string
  consultation_date: string
  consultation_type: ConsultationType
  symptoms?: string
  diagnosis?: string
  treatment_plan?: string
  follow_up_date?: string
  consultation_fee?: number
  created_at: string
  updated_at: string
  deleted_at?: string
}

// Medical Records
export type Medicine = {
  id: string
  name: string
  generic_name?: string
  manufacturer?: string
  medicine_type: 'tablet' | 'capsule' | 'liquid' | 'injection' | 'inhaler' | 'topical' | 'cream' | 'ointment'
  strength?: string
  description?: string
  side_effects?: string
  contraindications?: string
  created_at: string
}

export type Prescription = {
  id: string
  consultation_id: string
  patient_id: string
  doctor_id: string
  prescription_date: string
  expiry_date?: string
  notes?: string
  is_digital: boolean
  created_at: string
  updated_at: string
  deleted_at?: string
}

export type Report = {
  id: string
  patient_id: string
  doctor_id?: string
  hospital_id: string
  report_type: 'blood' | 'ct' | 'mri' | 'ecg' | 'xray' | 'ultrasound' | 'discharge'
  report_date: string
  report_file_url?: string
  findings?: string
  recommendations?: string
  created_at: string
  updated_at: string
  deleted_at?: string
}

// Vital Signs
export type Vital = {
  id: string
  patient_id: string
  recorded_at: string
  temperature_celsius?: number
  blood_pressure_systolic?: number
  blood_pressure_diastolic?: number
  heart_rate_bpm?: number
  respiratory_rate?: number
  oxygen_saturation_percentage?: number
  blood_glucose_mg_dl?: number
  created_at: string
}

// Notifications
export type NotificationType = 'appointment' | 'medicine' | 'test' | 'message' | 'alert' | 'reminder'

export type Notification = {
  id: string
  user_id: string
  notification_type: NotificationType
  title?: string
  message: string
  related_entity_id?: string
  is_read: boolean
  action_url?: string
  created_at: string
  read_at?: string
}

// Chat
export type DoctorChat = {
  id: string
  patient_id: string
  doctor_id: string
  message_text: string
  sender_type: 'patient' | 'doctor'
  is_read: boolean
  created_at: string
  updated_at: string
}

// Insurance
export type InsurancePolicy = {
  id: string
  patient_id: string
  insurance_provider_id: string
  policy_number: string
  policy_type?: string
  sum_insured?: number
  premium_amount?: number
  start_date: string
  end_date: string
  is_active: boolean
  created_at: string
  updated_at: string
}

// Allergies
export type Allergy = {
  id: string
  patient_id: string
  allergen: string
  reaction?: string
  severity?: 'mild' | 'moderate' | 'severe'
  created_at: string
}

// Chronic Conditions
export type ChronicCondition = {
  id: string
  patient_id: string
  condition_name: string
  diagnosed_date?: string
  status: 'ongoing' | 'controlled' | 'remission'
  created_at: string
}

// Emergency Contact
export type EmergencyContact = {
  id: string
  patient_id: string
  name: string
  relationship?: string
  phone_primary?: string
  phone_secondary?: string
  address?: string
  created_at: string
}
