import { Patient, Doctor, Appointment, Consultation, Department, HospitalMetrics } from "../types"

// Indian names and cities for realistic data
const firstNames = {
  male: ["Rajesh", "Arjun", "Vikram", "Arun", "Suresh", "Ravi", "Pradeep", "Sanjay", "Rohit", "Amit"],
  female: ["Priya", "Anjali", "Deepika", "Sneha", "Kavya", "Pooja", "Aisha", "Neha", "Shreya", "Divya"],
}

const lastNames = ["Sharma", "Singh", "Patel", "Gupta", "Verma", "Reddy", "Kumar", "Nair", "Iyer", "Chakraborty"]

const cities = ["Mumbai", "Delhi", "Bangalore", "Hyderabad", "Chennai", "Kolkata", "Pune", "Ahmedabad", "Jaipur", "Lucknow"]

const specializations = [
  "Cardiology",
  "Orthopedics",
  "Neurology",
  "Pediatrics",
  "General Surgery",
  "Dermatology",
  "Ophthalmology",
  "ENT",
  "Psychiatry",
  "Oncology",
]

const departments = [
  "Cardiology",
  "Orthopedics",
  "Neurology",
  "Pediatrics",
  "General Medicine",
  "Emergency",
  "ICU",
  "Surgery",
]

const medicines = [
  "Aspirin",
  "Metformin",
  "Atorvastatin",
  "Amoxicillin",
  "Ibuprofen",
  "Lisinopril",
  "Omeprazole",
  "Ciprofloxacin",
  "Paracetamol",
  "Clopidogrel",
]

const bloodGroups = ["A+", "A-", "B+", "B-", "AB+", "AB-", "O+", "O-"]

function getRandomItem<T>(array: T[]): T {
  return array[Math.floor(Math.random() * array.length)]
}

function getRandomName(gender: "M" | "F"): { first: string; last: string } {
  return {
    first: getRandomItem(firstNames[gender === "M" ? "male" : "female"]),
    last: getRandomItem(lastNames),
  }
}

function getRandomDate(daysAgo: number = 365): string {
  const date = new Date()
  date.setDate(date.getDate() - Math.floor(Math.random() * daysAgo))
  return date.toISOString().split("T")[0]
}

function getRandomTime(): string {
  const hours = String(Math.floor(Math.random() * 8) + 9).padStart(2, "0")
  const minutes = String(Math.floor(Math.random() * 4) * 15).padStart(2, "0")
  return `${hours}:${minutes}`
}

export function generatePatients(count: number = 50): Patient[] {
  const patients: Patient[] = []
  for (let i = 0; i < count; i++) {
    const gender = Math.random() > 0.5 ? "M" : ("F" as const)
    const name = getRandomName(gender)
    patients.push({
      id: `P${String(i + 1).padStart(5, "0")}`,
      mrn: `MRN${String(i + 1).padStart(6, "0")}`,
      firstName: name.first,
      lastName: name.last,
      dateOfBirth: getRandomDate(10000), // Ages 18-80
      gender,
      email: `${name.first.toLowerCase()}${name.last.toLowerCase()}@email.com`,
      phone: `+91${Math.floor(Math.random() * 9000000000 + 1000000000)}`,
      address: `${Math.floor(Math.random() * 500 + 1)} Main Street`,
      city: getRandomItem(cities),
      state: "State",
      pincode: String(Math.floor(Math.random() * 900000 + 100000)),
      bloodGroup: getRandomItem(bloodGroups),
      emergencyContact: name.first,
      emergencyPhone: `+91${Math.floor(Math.random() * 9000000000 + 1000000000)}`,
      insuranceProvider: getRandomItem(["Apollo Health", "Max Bupa", "ICICI Prudential", "HDFC ERGO"]),
      insurancePolicyNumber: `POL${String(i + 1).padStart(8, "0")}`,
      status: Math.random() > 0.8 ? "inactive" : "active",
      createdAt: getRandomDate(730),
      lastVisit: getRandomDate(30),
    })
  }
  return patients
}

export function generateDoctors(count: number = 30): Doctor[] {
  const doctors: Doctor[] = []
  for (let i = 0; i < count; i++) {
    const gender = Math.random() > 0.5 ? "M" : ("F" as const)
    const name = getRandomName(gender)
    const experience = Math.floor(Math.random() * 35) + 2
    doctors.push({
      id: `D${String(i + 1).padStart(4, "0")}`,
      firstName: name.first,
      lastName: name.last,
      specialization: getRandomItem(specializations),
      qualification: getRandomItem(["MBBS, MD", "MBBS, DNB", "MBBS, MS"]),
      licenseNumber: `LIC${String(i + 1).padStart(6, "0")}`,
      email: `dr.${name.first.toLowerCase()}${name.last.toLowerCase()}@hospital.com`,
      phone: `+91${Math.floor(Math.random() * 9000000000 + 1000000000)}`,
      experience,
      department: getRandomItem(departments),
      availability: Math.random() > 0.7 ? "available" : "busy",
      consultationFee: Math.floor(Math.random() * 500 + 300),
      hospital: "Apollo Hospital Delhi",
      status: "active",
      createdAt: getRandomDate(1095),
    })
  }
  return doctors
}

export function generateAppointments(
  patients: Patient[],
  doctors: Doctor[],
  count: number = 100
): Appointment[] {
  const appointments: Appointment[] = []
  for (let i = 0; i < count; i++) {
    const patient = getRandomItem(patients)
    const doctor = getRandomItem(doctors)
    const appointmentDate = new Date()
    appointmentDate.setDate(appointmentDate.getDate() + Math.floor(Math.random() * 30 - 15))
    
    appointments.push({
      id: `APT${String(i + 1).padStart(6, "0")}`,
      patientId: patient.id,
      patientName: `${patient.firstName} ${patient.lastName}`,
      doctorId: doctor.id,
      doctorName: `Dr. ${doctor.firstName} ${doctor.lastName}`,
      department: doctor.specialization,
      appointmentDate: appointmentDate.toISOString().split("T")[0],
      appointmentTime: getRandomTime(),
      duration: 30,
      reason: getRandomItem([
        "Regular checkup",
        "Follow-up",
        "Symptoms",
        "Emergency",
        "Consultation",
      ]),
      status: Math.random() > 0.7 ? "completed" : "scheduled",
      createdAt: getRandomDate(90),
    })
  }
  return appointments
}

export function generateConsultations(
  patients: Patient[],
  doctors: Doctor[],
  count: number = 50
): Consultation[] {
  const consultations: Consultation[] = []
  for (let i = 0; i < count; i++) {
    const patient = getRandomItem(patients)
    const doctor = getRandomItem(doctors)
    consultations.push({
      id: `CON${String(i + 1).padStart(6, "0")}`,
      patientId: patient.id,
      doctorId: doctor.id,
      appointmentId: `APT${String(Math.random() * 1000000).padStart(6, "0")}`,
      date: getRandomDate(30),
      diagnosis: getRandomItem([
        "Hypertension",
        "Diabetes Type 2",
        "Asthma",
        "Anxiety",
        "Common Cold",
      ]),
      symptoms: getRandomItem([
        ["fever", "cough"],
        ["headache", "fatigue"],
        ["chest pain"],
        ["nausea", "dizziness"],
      ]),
      medications: [getRandomItem(medicines), getRandomItem(medicines)],
      testsPrescribed: [
        getRandomItem(["CBC", "X-Ray", "ECG", "Blood Sugar", "Thyroid Panel"]),
      ],
      notes: "Patient advised to follow up in 2 weeks",
      status: "completed",
      createdAt: getRandomDate(30),
    })
  }
  return consultations
}

export function generateDepartments(): Department[] {
  return [
    {
      id: "DEPT001",
      name: "Cardiology",
      head: "Dr. Rajesh Sharma",
      description: "Heart and cardiovascular diseases",
      totalBeds: 20,
      occupiedBeds: 15,
      doctors: 5,
      status: "operational",
    },
    {
      id: "DEPT002",
      name: "Orthopedics",
      head: "Dr. Amit Singh",
      description: "Bone and joint disorders",
      totalBeds: 25,
      occupiedBeds: 18,
      doctors: 6,
      status: "operational",
    },
    {
      id: "DEPT003",
      name: "Neurology",
      head: "Dr. Priya Verma",
      description: "Brain and nervous system disorders",
      totalBeds: 15,
      occupiedBeds: 10,
      doctors: 4,
      status: "operational",
    },
    {
      id: "DEPT004",
      name: "Pediatrics",
      head: "Dr. Kavya Patel",
      description: "Children's healthcare",
      totalBeds: 30,
      occupiedBeds: 22,
      doctors: 7,
      status: "operational",
    },
    {
      id: "DEPT005",
      name: "General Surgery",
      head: "Dr. Vikram Reddy",
      description: "Surgical procedures",
      totalBeds: 35,
      occupiedBeds: 28,
      doctors: 8,
      status: "operational",
    },
  ]
}

export function generateHospitalMetrics(
  patients: Patient[],
  doctors: Doctor[],
  departments: Department[]
): HospitalMetrics {
  const activePatients = patients.filter((p) => p.status === "active").length
  const occupiedBeds = departments.reduce((sum, dept) => sum + dept.occupiedBeds, 0)
  const totalBeds = departments.reduce((sum, dept) => sum + dept.totalBeds, 0)
  const availableDoctors = doctors.filter((d) => d.availability === "available").length

  return {
    totalPatients: patients.length,
    activePatients,
    totalDoctors: doctors.length,
    availableDoctors,
    totalBeds,
    occupiedBeds,
    appointmentsToday: Math.floor(Math.random() * 15 + 5),
    completedAppointments: Math.floor(Math.random() * 20 + 10),
    revenue: Math.floor(Math.random() * 500000 + 100000),
    pendingPayments: Math.floor(Math.random() * 50000 + 10000),
  }
}
