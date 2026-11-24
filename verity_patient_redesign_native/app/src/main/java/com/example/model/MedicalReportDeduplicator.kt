package com.example.model

import java.util.Locale

internal fun deduplicateMedicalReports(reports: List<MedicalReport>): List<MedicalReport> {
    val seen = HashSet<String>()
    return reports.filter { report ->
        val sourceIdentity = report.duplicateFingerprint?.takeIf(String::isNotBlank)?.let { fingerprint ->
            if (fingerprint.startsWith("url:", ignoreCase = true)) {
                "source:${fingerprint.substringAfter(':').substringBefore('?').substringBefore('#').trim().lowercase(Locale.ROOT)}"
            } else {
                "source:${fingerprint.trim().lowercase(Locale.ROOT)}"
            }
        }
        val normalizedContent = report.summaryNote.trim().lowercase(Locale.ROOT).replace(Regex("\\s+"), " ")
        val identity = sourceIdentity
            ?: normalizedContent.takeIf(String::isNotBlank)
                ?.let { "content:${report.category.trim().lowercase(Locale.ROOT)}:$it" }
            ?: "id:${report.id}"
        seen.add(identity)
    }
}
