package com.example.model

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeFormatterBuilder
import java.time.format.DateTimeParseException
import java.util.Locale

/** Appointment cards remain visible for 30 minutes after their scheduled start. */
object AppointmentExpiry {
    private val timeFormats = listOf(
        DateTimeFormatter.ISO_LOCAL_TIME,
        DateTimeFormatter.ofPattern("h:mm a", Locale.getDefault()),
        DateTimeFormatter.ofPattern("hh:mm a", Locale.getDefault())
    )

    fun isExpired(appointment: Appointment, now: LocalDateTime = LocalDateTime.now()): Boolean {
        val scheduled = scheduledAt(appointment, now.toLocalDate()) ?: return false
        return !scheduled.plusMinutes(30).isAfter(now)
    }

    private fun scheduledAt(appointment: Appointment, currentDate: LocalDate): LocalDateTime? {
        val date = parseDate(appointment.appointmentDateIso)
            ?: parseDisplayDate(appointment.dateLabel, currentDate.year)
            ?: return null
        val time = parseTime(appointment.appointmentTimeIso)
            ?: parseTime(appointment.timeLabel)
            ?: return null
        return LocalDateTime.of(date, time)
    }

    private fun parseDate(raw: String): LocalDate? = try {
        LocalDate.parse(raw.trim().take(10), DateTimeFormatter.ISO_LOCAL_DATE)
    } catch (_: DateTimeParseException) {
        null
    }

    private fun parseDisplayDate(raw: String, year: Int): LocalDate? {
        val formats = listOf("EEE, dd MMM yyyy", "dd MMM yyyy", "EEE, dd MMM", "dd MMM")
        for (pattern in formats) {
            val formatter = DateTimeFormatterBuilder()
                .parseCaseInsensitive()
                .appendPattern(pattern)
                .parseDefaulting(java.time.temporal.ChronoField.YEAR, year.toLong())
                .toFormatter(Locale.getDefault())
            try {
                return LocalDate.parse(raw.trim(), formatter)
            } catch (_: DateTimeParseException) {
                // Try the next display format.
            }
        }
        return null
    }

    private fun parseTime(raw: String): LocalTime? {
        val value = raw.trim().substringBefore('+').substringBefore('Z')
        for (format in timeFormats) {
            try {
                return LocalTime.parse(value, format)
            } catch (_: DateTimeParseException) {
                // Try the next accepted backend/display format.
            }
        }
        return null
    }
}
