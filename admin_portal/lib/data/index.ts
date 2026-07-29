import {
  generatePatients,
  generateDoctors,
  generateAppointments,
  generateConsultations,
  generateDepartments,
  generateHospitalMetrics,
} from "./generators"

// Initialize data
const patients = generatePatients(50)
const doctors = generateDoctors(30)
const appointments = generateAppointments(patients, doctors, 100)
const consultations = generateConsultations(patients, doctors, 50)
const departments = generateDepartments()
const metrics = generateHospitalMetrics(patients, doctors, departments)

// Export data
export { patients, doctors, appointments, consultations, departments, metrics }

// Export generators for future database integration
export {
  generatePatients,
  generateDoctors,
  generateAppointments,
  generateConsultations,
  generateDepartments,
  generateHospitalMetrics,
}
