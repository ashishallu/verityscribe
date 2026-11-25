package com.example.data

import com.example.model.AppointmentStatus
import com.example.model.PrescriptionItem
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DoctorHistoryGroupingTest {
    @Test
    fun repeatAppointmentsWithinThirtyDaysProduceOnePatientUsingLatestVisit() {
        val today = LocalDate.of(2026, 11, 24)
        val completed = MockClinicalRepository.initialAppointments.first { it.id == "apt-8" }
            .copy(
                patientId = "patient-1",
                appointmentDate = today.minusDays(20).toString(),
                diagnoses = listOf("Atrial fibrillation"),
                prescriptions = listOf(PrescriptionItem("rx-old", "Apixaban", "5 mg", "Daily", "30 days", ""))
            )
        val repeat = completed.copy(
            id = "apt-8-repeat",
            appointmentDate = today.minusDays(2).toString(),
            clinicalNote = "Most recent completed visit",
            diagnoses = listOf("Atrial fibrillation", "Hypertension"),
            prescriptions = listOf(
                PrescriptionItem("rx-new", "Apixaban", "5 mg", "Daily", "30 days", ""),
                PrescriptionItem("rx-new-2", "Diltiazem", "120 mg", "Daily", "30 days", "")
            )
        )
        val anotherPatient = completed.copy(
            id = "apt-other-patient",
            patientId = "patient-2",
            mrn = "VS-OTHER",
            appointmentDate = today.minusDays(1).toString()
        )
        val outsideWindow = completed.copy(
            id = "apt-old",
            patientId = "patient-old",
            appointmentDate = today.minusDays(31).toString()
        )
        val notCompleted = completed.copy(
            id = "apt-scheduled",
            patientId = "patient-scheduled",
            status = AppointmentStatus.SCHEDULED,
            appointmentDate = today.toString()
        )
        val futureVisit = completed.copy(
            id = "apt-future",
            patientId = "patient-future",
            appointmentDate = today.plusDays(1).toString()
        )

        val history = buildDoctorHistory(
            listOf(completed, repeat, anotherPatient, outsideWindow, notCompleted, futureVisit),
            today
        )

        assertEquals(2, history.size)
        assertEquals(1, history.count { it.mrn == completed.mrn })
        val patientHistory = history.single { it.mrn == completed.mrn }
        assertEquals("apt-8-repeat", patientHistory.linkedAppointmentId)
        assertEquals(2, patientHistory.daysAgo)
        assertEquals("Atrial fibrillation • Hypertension", patientHistory.diagnosisSummary)
        assertEquals("Most recent completed visit", patientHistory.clinicalOutcome)
        assertEquals(2, patientHistory.medicationsPrescribed.size)
        assertTrue(history.none { it.id == "history-patient:patient-old" })
        assertFalse(history.any {
            it.id == "history-patient:patient-scheduled" || it.id == "history-patient:patient-future"
        })
    }

    @Test
    fun missingOrInvalidAppointmentDatesAreNotPresentedAsToday() {
        val today = LocalDate.of(2026, 11, 24)
        val appointment = MockClinicalRepository.initialAppointments.first { it.id == "apt-8" }
            .copy(patientId = "patient-1", appointmentDate = null)

        assertTrue(buildDoctorHistory(listOf(appointment), today).isEmpty())
    }
}
