package com.example

import org.junit.Assert.*
import org.junit.Test
import com.example.model.MedicalReport
import com.example.model.deduplicateMedicalReports
import com.example.data.VeritySeedData
import com.example.ui.screens.availableBookingSlots
import com.example.ui.screens.generateNext15Days
import java.time.LocalDate

/**
 * Example local unit test, which will execute on the development machine (host).
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 */
class ExampleUnitTest {
  @Test
  fun addition_isCorrect() {
    assertEquals(4, 2 + 2)
  }

  @Test
  fun bookingDateOptionsIncludeAnUnambiguousIsoDate() {
    val options = generateNext15Days()
    assertEquals(15, options.size)
    assertTrue(options.first().isToday)
    assertTrue(options.first().isoDate.matches(Regex("\\d{4}-\\d{2}-\\d{2}")))
    val firstDate = LocalDate.parse(options.first().isoDate)
    assertEquals(firstDate.plusDays(1).toString(), options[1].isoDate)
    assertFalse(options[1].isToday)
  }

  @Test
  fun bookingSlotsForTodayExcludeSlotsThatHavePassed() {
    val slots = VeritySeedData.bookingTimeSlots
    assertEquals(
      listOf("07:00 PM", "07:30 PM", "08:00 PM", "08:30 PM", "09:00 PM"),
      availableBookingSlots(slots, isToday = true, currentHour = 18, currentMinute = 48)
    )
    assertEquals(slots, availableBookingSlots(slots, isToday = false, currentHour = 18, currentMinute = 48))
    assertTrue(availableBookingSlots(slots, isToday = true, currentHour = 21, currentMinute = 1).isEmpty())
    assertEquals("09:00 PM", slots.last())
  }

  @Test
  fun duplicateReportsCollapseByFileFingerprintOrIdenticalContent() {
    fun report(id: String, fingerprint: String? = null, note: String = "") = MedicalReport(
      id = id,
      title = "Lab report",
      labName = "",
      orderedBy = "",
      dateLabel = "24 Nov 2026",
      category = "blood report",
      status = "Available",
      summaryNote = note,
      metrics = emptyList(),
      duplicateFingerprint = fingerprint
    )

    val results = deduplicateMedicalReports(
      listOf(
        report("one", fingerprint = "sha256:abc"),
        report("two", fingerprint = "sha256:abc"),
        report("three", note = "Same extracted report text"),
        report("four", note = "Same   extracted report text")
      )
    )
    assertEquals(listOf("one", "three"), results.map { it.id })
  }
}
