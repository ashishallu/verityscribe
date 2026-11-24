package com.example.data

import com.example.R
import com.example.model.ActivityType
import com.example.model.AiHistoryThread
import com.example.model.Appointment
import com.example.model.ChatMessage
import com.example.model.ConsultationRecord
import com.example.model.ConsultationType
import com.example.model.DoctorDirectoryEntry
import com.example.model.DosePeriod
import com.example.model.EmergencyContact
import com.example.model.HealthSnapshotMetric
import com.example.model.HospitalOption
import com.example.model.InsuranceInfo
import com.example.model.LinkedHospital
import com.example.model.MedicalHistorySection
import com.example.model.MedicalReport
import com.example.model.MedicationScheduleItem
import com.example.model.Medicine
import com.example.model.NearbyStore
import com.example.model.NotificationItem
import com.example.model.PatientProfile
import com.example.model.PharmacyPrice
import com.example.model.PrescriptionLine
import com.example.model.RecentActivityItem
import com.example.model.ReportMetric

object VeritySeedData {

    val patientProfile = PatientProfile(
        name = "Ashish Allu",
        firstName = "Ashish",
        verityId = "VS-2048-731",
        avatarInitial = "A",
        clinicalContext = "Clinical context: mild hypertension • no known allergies • 2 active medicines",
        emergencyContactsCount = 2,
        insurancePlan = "Aster Health Platinum",
        linkedHospitalsSummary = "NovaCare and 2 more",
        connectedDeviceSummary = "Google Fit • Tap to connect"
    )

    val appointments = listOf(
        Appointment(
            id = "appt_1",
            doctorName = "Dr. Meera Kapoor",
            specialty = "Cardiology",
            hospital = "NovaCare Hospital",
            dateLabel = "Today, 23 Nov",
            timeLabel = "10:30 AM",
            consultationType = ConsultationType.VIDEO.label,
            notes = "Blood pressure follow-up & lipid panel review.",
            avatarRes = R.drawable.img_doctor_meera
        ),
        Appointment(
            id = "appt_2",
            doctorName = "Dr. Ananya Rao",
            specialty = "General Medicine",
            hospital = "NovaCare Hospital",
            dateLabel = "Today, 23 Nov",
            timeLabel = "02:00 PM",
            consultationType = ConsultationType.AI_VIRTUAL.label,
            notes = "AI Virtual pre-visit screening & glycemic control review.",
            avatarRes = R.drawable.img_doctor_ananya
        ),
        Appointment(
            id = "appt_3",
            doctorName = "Dr. Kavya Nair",
            specialty = "Endocrinology",
            hospital = "Aster Clinic",
            dateLabel = "Today, 23 Nov",
            timeLabel = "04:30 PM",
            consultationType = ConsultationType.AI_CHAT.label,
            notes = "AI Chat guided Vitamin D3 & dosage follow-up.",
            avatarRes = R.drawable.img_doctor_meera
        ),
        Appointment(
            id = "appt_4",
            doctorName = "Dr. Arjun Menon",
            specialty = "Cardiology",
            hospital = "Apollo Specialty Center",
            dateLabel = "Thu, 26 Nov",
            timeLabel = "11:00 AM",
            consultationType = ConsultationType.IN_PERSON.label,
            notes = "In-person ECG & annual cardiac assessment.",
            avatarRes = R.drawable.img_doctor_meera
        )
    )

    val medicines = listOf(
        Medicine(
            id = "med_metformin",
            name = "Metformin",
            dosage = "500 mg",
            category = "Diabetes management",
            scheduleText = "08:00 AM • After breakfast",
            shortSchedule = "500 mg • after breakfast",
            initialQuantity = 30,
            remainingQuantity = 12,
            dailyUsage = 2,
            finishesInDays = 6,
            reorderLabel = "Now",
            needsReorder = true,
            prescribedBy = "Dr. Ananya Rao",
            purpose = "Diabetes management",
            howToTake = "Take with a glass of water.",
            foodInstructions = "Take after food.",
            sideEffects = "Mild nausea may occur.",
            doctorNotes = "Continue as advised.",
            prices = listOf(
                PharmacyPrice(
                    id = "p_1mg",
                    vendorName = "Tata 1mg",
                    discountInfo = "20% off • delivery ₹25",
                    etaInfo = "Tomorrow • 2 days",
                    priceRupees = 84,
                    isBestValue = true
                ),
                PharmacyPrice(
                    id = "p_apollo",
                    vendorName = "Apollo 247",
                    discountInfo = "15% off • free delivery",
                    etaInfo = "Today • 4 hours",
                    priceRupees = 92,
                    isBestValue = false
                ),
                PharmacyPrice(
                    id = "p_pharmeasy",
                    vendorName = "PharmEasy",
                    discountInfo = "18% off • delivery ₹20",
                    etaInfo = "Tomorrow • 1 day",
                    priceRupees = 89,
                    isBestValue = false
                )
            ),
            nearbyStores = listOf(
                NearbyStore(
                    id = "store_medplus",
                    name = "MedPlus Pharmacy",
                    distanceKm = "0.8 km",
                    stockStatus = "In stock",
                    openUntil = "open until 10 PM",
                    phoneNumber = "+91 98450 11223"
                ),
                NearbyStore(
                    id = "store_apollo",
                    name = "Apollo Pharmacy",
                    distanceKm = "1.2 km",
                    stockStatus = "In stock",
                    openUntil = "open until 11 PM",
                    phoneNumber = "+91 98450 44556"
                )
            )
        ),
        Medicine(
            id = "med_atorvastatin",
            name = "Atorvastatin",
            dosage = "10 mg",
            category = "Heart health",
            scheduleText = "08:00 PM • After dinner",
            shortSchedule = "10 mg • 9:00 PM",
            initialQuantity = 30,
            remainingQuantity = 16,
            dailyUsage = 1,
            finishesInDays = 16,
            reorderLabel = "In 11 days",
            needsReorder = false,
            prescribedBy = "Dr. Ananya Rao",
            purpose = "Cholesterol & heart health",
            howToTake = "Swallow whole with water.",
            foodInstructions = "Take after dinner.",
            sideEffects = "Mild muscle soreness rarely.",
            doctorNotes = "Take consistently at night.",
            prices = listOf(
                PharmacyPrice(
                    id = "p_1mg_a",
                    vendorName = "Tata 1mg",
                    discountInfo = "18% off • delivery ₹25",
                    etaInfo = "Tomorrow • 2 days",
                    priceRupees = 112,
                    isBestValue = true
                ),
                PharmacyPrice(
                    id = "p_apollo_a",
                    vendorName = "Apollo 247",
                    discountInfo = "12% off • free delivery",
                    etaInfo = "Today • 4 hours",
                    priceRupees = 124,
                    isBestValue = false
                ),
                PharmacyPrice(
                    id = "p_pharmeasy_a",
                    vendorName = "PharmEasy",
                    discountInfo = "15% off • delivery ₹20",
                    etaInfo = "Tomorrow • 1 day",
                    priceRupees = 119,
                    isBestValue = false
                )
            ),
            nearbyStores = listOf(
                NearbyStore(
                    id = "store_medplus",
                    name = "MedPlus Pharmacy",
                    distanceKm = "0.8 km",
                    stockStatus = "In stock",
                    openUntil = "open until 10 PM",
                    phoneNumber = "+91 98450 11223"
                ),
                NearbyStore(
                    id = "store_apollo",
                    name = "Apollo Pharmacy",
                    distanceKm = "1.2 km",
                    stockStatus = "In stock",
                    openUntil = "open until 11 PM",
                    phoneNumber = "+91 98450 44556"
                )
            )
        ),
        Medicine(
            id = "med_vitamind3",
            name = "Vitamin D3",
            dosage = "60,000 IU",
            category = "Bone health",
            scheduleText = "Sunday • 08:00 AM",
            shortSchedule = "60,000 IU • Weekly",
            initialQuantity = 4,
            remainingQuantity = 4,
            dailyUsage = 1,
            finishesInDays = 4,
            reorderLabel = "Now",
            needsReorder = true,
            prescribedBy = "Dr. Ananya Rao",
            purpose = "Bone health & immunity",
            howToTake = "Take one capsule weekly.",
            foodInstructions = "Take with a meal containing healthy fats.",
            sideEffects = "Generally well tolerated.",
            doctorNotes = "Complete 4-week course before repeat lab.",
            prices = listOf(
                PharmacyPrice(
                    id = "p_1mg_v",
                    vendorName = "Tata 1mg",
                    discountInfo = "22% off • delivery ₹25",
                    etaInfo = "Tomorrow • 2 days",
                    priceRupees = 98,
                    isBestValue = true
                ),
                PharmacyPrice(
                    id = "p_apollo_v",
                    vendorName = "Apollo 247",
                    discountInfo = "15% off • free delivery",
                    etaInfo = "Today • 4 hours",
                    priceRupees = 110,
                    isBestValue = false
                ),
                PharmacyPrice(
                    id = "p_pharmeasy_v",
                    vendorName = "PharmEasy",
                    discountInfo = "18% off • delivery ₹20",
                    etaInfo = "Tomorrow • 1 day",
                    priceRupees = 104,
                    isBestValue = false
                )
            ),
            nearbyStores = listOf(
                NearbyStore(
                    id = "store_medplus",
                    name = "MedPlus Pharmacy",
                    distanceKm = "0.8 km",
                    stockStatus = "In stock",
                    openUntil = "open until 10 PM",
                    phoneNumber = "+91 98450 11223"
                ),
                NearbyStore(
                    id = "store_apollo",
                    name = "Apollo Pharmacy",
                    distanceKm = "1.2 km",
                    stockStatus = "In stock",
                    openUntil = "open until 11 PM",
                    phoneNumber = "+91 98450 44556"
                )
            )
        )
    )

    val scheduleItems = listOf(
        MedicationScheduleItem(
            id = "sched_metformin_morning",
            medicineId = "med_metformin",
            medicineName = "Metformin",
            subtitle = "500 mg • 08:00 AM • After breakfast",
            prescriberNote = "Dr. Ananya Rao • After food",
            period = DosePeriod.MORNING
        ),
        MedicationScheduleItem(
            id = "sched_atorvastatin_morning",
            medicineId = "med_atorvastatin",
            medicineName = "Atorvastatin",
            subtitle = "10 mg • 08:00 PM • After dinner",
            prescriberNote = "Dr. Ananya Rao • After food",
            period = DosePeriod.MORNING
        ),
        MedicationScheduleItem(
            id = "sched_vitd_morning",
            medicineId = "med_vitamind3",
            medicineName = "Vitamin D3",
            subtitle = "60,000 IU • Sunday • 08:00 AM",
            prescriberNote = "Dr. Ananya Rao • After food",
            period = DosePeriod.MORNING
        ),
        MedicationScheduleItem(
            id = "sched_atorvastatin_night",
            medicineId = "med_atorvastatin",
            medicineName = "Atorvastatin",
            subtitle = "10 mg • 08:00 PM • After dinner",
            prescriberNote = "Dr. Ananya Rao • After food",
            period = DosePeriod.NIGHT,
            isGreenAccent = true
        )
    )

    val defaultTakenIds = setOf("sched_metformin_morning")

    val healthSnapshot = listOf(
        HealthSnapshotMetric(id = "hr", label = "Heart rate", value = "72", unit = "bpm"),
        HealthSnapshotMetric(id = "sleep", label = "Sleep", value = "7h 42m"),
        HealthSnapshotMetric(id = "spo2", label = "SpO2", value = "98%"),
        HealthSnapshotMetric(id = "steps", label = "Steps", value = "6,248")
    )

    val recentActivity = listOf(
        RecentActivityItem(
            id = "act_1",
            title = "Prescription updated",
            subtitle = "Dr. Ananya Rao • 2h ago",
            type = ActivityType.PRESCRIPTION
        ),
        RecentActivityItem(
            id = "act_2",
            title = "Medicine reminder completed",
            subtitle = "Metformin • 8:10 AM",
            type = ActivityType.REMINDER
        ),
        RecentActivityItem(
            id = "act_3",
            title = "Lab report added",
            subtitle = "Vitamin D panel • Yesterday",
            type = ActivityType.LAB_REPORT
        )
    )

    val consultations = listOf(
        ConsultationRecord(
            id = "rec_meera",
            doctorName = "Dr. Meera Kapoor",
            specialty = "Cardiology",
            hospital = "NovaCare Hospital",
            dateLabel = "18 Nov 2026",
            timeLabel = "10:30 AM",
            diagnosis = "Essential hypertension",
            status = "COMPLETED",
            verifiedLabel = "AI VERIFIED",
            patientName = "Ashish Allu",
            prescriptions = listOf(
                PrescriptionLine("Metformin 500 mg", "After breakfast"),
                PrescriptionLine("Atorvastatin 10 mg", "After dinner")
            ),
            followUpDate = "21 Dec 2026",
            recordingElapsedSeconds = 198,
            recordingTotalSeconds = 702,
            avatarRes = R.drawable.img_doctor_meera
        ),
        ConsultationRecord(
            id = "rec_ananya",
            doctorName = "Dr. Ananya Rao",
            specialty = "General Medicine",
            hospital = "NovaCare Hospital",
            dateLabel = "04 Nov 2026",
            timeLabel = "04:15 PM",
            diagnosis = "Glycemic & lipid review",
            status = "COMPLETED",
            verifiedLabel = "AI VERIFIED",
            patientName = "Ashish Allu",
            prescriptions = listOf(
                PrescriptionLine("Metformin 500 mg", "After breakfast"),
                PrescriptionLine("Vitamin D3 60,000 IU", "Weekly Sunday")
            ),
            followUpDate = "12 Dec 2026",
            recordingElapsedSeconds = 112,
            recordingTotalSeconds = 540,
            avatarRes = R.drawable.img_doctor_ananya
        )
    )

    val medicalReports = listOf(
        MedicalReport(
            id = "rep_lipid",
            title = "Complete Lipid & Cardiac Panel",
            labName = "NovaCare Diagnostics",
            orderedBy = "Dr. Meera Kapoor",
            dateLabel = "20 Nov 2026",
            category = "Blood Work",
            status = "OPTIMAL",
            summaryNote = "LDL cholesterol and triglycerides are well controlled on Atorvastatin 10 mg.",
            metrics = listOf(
                ReportMetric("Total Cholesterol", "164 mg/dL", "< 200 mg/dL", isFlagged = false),
                ReportMetric("LDL Cholesterol", "94 mg/dL", "< 100 mg/dL", isFlagged = false),
                ReportMetric("HDL Cholesterol", "52 mg/dL", "> 40 mg/dL", isFlagged = false),
                ReportMetric("Triglycerides", "118 mg/dL", "< 150 mg/dL", isFlagged = false)
            )
        ),
        MedicalReport(
            id = "rep_metabolic",
            title = "HbA1c & Fasting Glucose",
            labName = "Aster Reference Lab",
            orderedBy = "Dr. Ananya Rao",
            dateLabel = "14 Nov 2026",
            category = "Metabolic",
            status = "NORMAL",
            summaryNote = "Glycemic control improved by 0.3% since July. Continue Metformin 500 mg after breakfast.",
            metrics = listOf(
                ReportMetric("HbA1c", "6.1 %", "< 6.5 %", isFlagged = false),
                ReportMetric("Fasting Glucose", "98 mg/dL", "70 – 100 mg/dL", isFlagged = false),
                ReportMetric("eGFR (Kidney)", "96 mL/min", "> 60 mL/min", isFlagged = false)
            )
        ),
        MedicalReport(
            id = "rep_vitd",
            title = "Vitamin D3 (25-OH) & Calcium",
            labName = "Aster Reference Lab",
            orderedBy = "Dr. Kavya Nair",
            dateLabel = "06 Nov 2026",
            category = "Blood Work",
            status = "ATTENTION",
            summaryNote = "Vitamin D3 is mildly below optimal range; weekly 60,000 IU course is actively replenishing levels.",
            metrics = listOf(
                ReportMetric("25-OH Vitamin D3", "24 ng/mL", "30 – 100 ng/mL", isFlagged = true),
                ReportMetric("Serum Calcium", "9.4 mg/dL", "8.6 – 10.2 mg/dL", isFlagged = false)
            )
        ),
        MedicalReport(
            id = "rep_ecg",
            title = "12-Lead Resting ECG",
            labName = "Apollo Heart Center",
            orderedBy = "Dr. Arjun Menon",
            dateLabel = "29 Oct 2026",
            category = "Cardiology",
            status = "NORMAL",
            summaryNote = "Normal sinus rhythm at 72 bpm with no acute ST-T wave changes.",
            metrics = listOf(
                ReportMetric("Resting Rhythm", "Sinus (72 bpm)", "60 – 100 bpm", isFlagged = false),
                ReportMetric("QTc Interval", "408 ms", "< 440 ms", isFlagged = false)
            )
        )
    )

    val initialChatMessages = listOf(
        ChatMessage(
            id = "msg_welcome",
            isUser = false,
            text = "Hi Ashish. I am ready to help you understand your care plan.",
            timestamp = "09:15 AM"
        )
    )

    val aiHistoryThreads = listOf(
        AiHistoryThread(
            id = "thread_metformin",
            title = "Metformin & morning dose",
            category = "Medications",
            preview = "Take 500 mg right after breakfast with water to minimize nausea.",
            messages = listOf(
                ChatMessage(
                    id = "tm_1",
                    isUser = true,
                    text = "Can you check my morning Metformin timing and side effects?",
                    timestamp = "09:10 AM"
                ),
                ChatMessage(
                    id = "tm_2",
                    isUser = false,
                    text = "Of course! Dr. Ananya Rao prescribed Metformin 500 mg after breakfast. Taking it with food helps prevent stomach upset.",
                    timestamp = "09:10 AM"
                ),
                ChatMessage(
                    id = "tm_3",
                    isUser = true,
                    text = "Voice symptom check-in",
                    timestamp = "09:11 AM",
                    isVoiceNote = true,
                    voiceDurationLabel = "0:30"
                ),
                ChatMessage(
                    id = "tm_4",
                    isUser = false,
                    text = "Your resting heart rate (72 bpm) and sleep (7h 42m) look steady. You have 12 tablets remaining, so consider reordering soon.",
                    timestamp = "09:12 AM"
                ),
                ChatMessage(
                    id = "tm_5",
                    isUser = true,
                    text = "Yes, please remind me",
                    timestamp = "09:12 AM"
                )
            )
        ),
        AiHistoryThread(
            id = "thread_cardio",
            title = "Cardiology visit prep",
            category = "Reports",
            preview = "Questions and BP log summary prepared for Dr. Meera Kapoor.",
            messages = listOf(
                ChatMessage(
                    id = "tc_1",
                    isUser = true,
                    text = "Summarize what I should bring up with Dr. Meera Kapoor today.",
                    timestamp = "Yesterday"
                ),
                ChatMessage(
                    id = "tc_2",
                    isUser = false,
                    text = "Share your improved 7h 42m sleep average, steady 98% SpO2, and confirm your evening Atorvastatin 10 mg routine.",
                    timestamp = "Yesterday"
                )
            )
        ),
        AiHistoryThread(
            id = "thread_vitamind",
            title = "Vitamin D3 panel review",
            category = "Reports",
            preview = "Weekly 60,000 IU capsule course logged for bone health.",
            messages = listOf(
                ChatMessage(
                    id = "tv_1",
                    isUser = true,
                    text = "How should I take my Vitamin D3 capsule?",
                    timestamp = "2 days ago"
                ),
                ChatMessage(
                    id = "tv_2",
                    isUser = false,
                    text = "Take Vitamin D3 60,000 IU once weekly on Sunday morning after a meal containing healthy fats for best absorption.",
                    timestamp = "2 days ago"
                )
            )
        ),
        AiHistoryThread(
            id = "thread_sleep",
            title = "Sleep & blood pressure",
            category = "Symptoms",
            preview = "Sleep improved by 14% this week with consistent evening wind-down.",
            messages = listOf(
                ChatMessage(
                    id = "ts_1",
                    isUser = true,
                    text = "Does better sleep help my mild hypertension readings?",
                    timestamp = "3 days ago"
                ),
                ChatMessage(
                    id = "ts_2",
                    isUser = false,
                    text = "Yes — getting 7–8 hours of restful sleep supports lower overnight blood pressure and calmer morning heart rates.",
                    timestamp = "3 days ago"
                )
            )
        )
    )

    val notifications = listOf(
        NotificationItem(
            id = "n_1",
            title = "Cardiology consultation today",
            body = "Dr. Meera Kapoor at NovaCare Hospital • 10:30 AM",
            timeAgo = "30m ago",
            isUnread = true
        ),
        NotificationItem(
            id = "n_2",
            title = "Metformin stock running low",
            body = "12 of 30 tablets left — reorder recommended now.",
            timeAgo = "2h ago",
            isUnread = true
        ),
        NotificationItem(
            id = "n_3",
            title = "Apple Watch synced",
            body = "Sleep 7h 42m (+14%) and resting heart rate 72 bpm updated.",
            timeAgo = "Today, 7:45 AM",
            isUnread = false
        )
    )

    val hospitals = listOf(
        HospitalOption(
            id = "hosp_novacare",
            name = "NovaCare Hospital",
            departments = listOf("Cardiology", "General Medicine", "Neurology")
        ),
        HospitalOption(
            id = "hosp_aster",
            name = "Aster Clinic",
            departments = listOf("Endocrinology", "General Medicine", "Dermatology")
        ),
        HospitalOption(
            id = "hosp_apollo",
            name = "Apollo Specialty Center",
            departments = listOf("Cardiology", "Endocrinology", "Orthopedics")
        )
    )

    val doctorsDirectory = listOf(
        DoctorDirectoryEntry(
            id = "doc_meera",
            name = "Dr. Meera Kapoor",
            department = "Cardiology",
            hospital = "NovaCare Hospital",
            feeRupees = 800,
            avatarRes = R.drawable.img_doctor_meera
        ),
        DoctorDirectoryEntry(
            id = "doc_ananya",
            name = "Dr. Ananya Rao",
            department = "General Medicine",
            hospital = "NovaCare Hospital",
            feeRupees = 600,
            avatarRes = R.drawable.img_doctor_ananya
        ),
        DoctorDirectoryEntry(
            id = "doc_kavya",
            name = "Dr. Kavya Nair",
            department = "Endocrinology",
            hospital = "Aster Clinic",
            feeRupees = 750,
            avatarRes = R.drawable.img_doctor_meera
        ),
        DoctorDirectoryEntry(
            id = "doc_rohan",
            name = "Dr. Rohan Deshmukh",
            department = "Dermatology",
            hospital = "Aster Clinic",
            feeRupees = 650,
            avatarRes = R.drawable.img_doctor_ananya
        ),
        DoctorDirectoryEntry(
            id = "doc_arjun",
            name = "Dr. Arjun Menon",
            department = "Cardiology",
            hospital = "Apollo Specialty Center",
            feeRupees = 900,
            avatarRes = R.drawable.img_doctor_meera
        ),
        DoctorDirectoryEntry(
            id = "doc_priya",
            name = "Dr. Priya Iyer",
            department = "Neurology",
            hospital = "NovaCare Hospital",
            feeRupees = 850,
            avatarRes = R.drawable.img_doctor_ananya
        )
    )

    val bookingTimeSlots = listOf(
        "09:00 AM", "09:30 AM", "10:00 AM", "10:30 AM", "11:00 AM", "11:30 AM",
        "12:00 PM", "12:30 PM", "01:00 PM", "01:30 PM", "02:00 PM", "02:30 PM",
        "03:00 PM", "03:30 PM", "04:00 PM", "04:30 PM", "05:00 PM", "05:30 PM",
        "06:00 PM", "06:30 PM", "07:00 PM", "07:30 PM", "08:00 PM", "08:30 PM", "09:00 PM"
    )

    val medicalHistorySections = listOf(
        MedicalHistorySection(
            id = "mh_past",
            title = "Past medical history",
            summary = "Managed mild essential hypertension & borderline fasting glucose",
            details = listOf(
                "Mild essential hypertension diagnosed Jan 2024 — stable on lifestyle care",
                "Borderline fasting glucose noted Mar 2025 — improving on Metformin 500 mg",
                "Seasonal allergic rhinitis in 2022 — resolved"
            ),
            lastUpdated = "18 Nov 2026"
        ),
        MedicalHistorySection(
            id = "mh_surgical",
            title = "Past surgical history",
            summary = "1 minor procedure • No major surgeries",
            details = listOf(
                "Laparoscopic appendectomy — Aug 2019 (Aster Clinic, uncomplicated recovery)",
                "No implanted cardiac devices or orthopedic hardware"
            ),
            lastUpdated = "04 Nov 2026"
        ),
        MedicalHistorySection(
            id = "mh_allergies",
            title = "Allergies",
            summary = "No known drug allergies (NKDA) • Mild dust sensitivity",
            details = listOf(
                "Medications: No known drug allergies (Penicillin, NSAIDs, Sulfa tolerated)",
                "Environmental: Mild dust & pollen sensitivity during monsoon",
                "Food: None reported"
            ),
            lastUpdated = "18 Nov 2026"
        ),
        MedicalHistorySection(
            id = "mh_chronic",
            title = "Current chronic conditions",
            summary = "2 active monitored conditions",
            details = listOf(
                "Essential hypertension (Stage 1) — monitored with Dr. Meera Kapoor",
                "Prediabetes / Glycemic control — HbA1c 6.1%, managed with Dr. Ananya Rao"
            ),
            lastUpdated = "20 Nov 2026"
        ),
        MedicalHistorySection(
            id = "mh_family",
            title = "Family medical history",
            summary = "Hypertension & Type 2 diabetes in first-degree relatives",
            details = listOf(
                "Father (Ramesh Allu): Hypertension managed since age 52",
                "Mother: Type 2 diabetes mellitus, hypothyroidism",
                "No premature coronary artery disease in siblings"
            ),
            lastUpdated = "10 Oct 2026"
        ),
        MedicalHistorySection(
            id = "mh_lifestyle",
            title = "Lifestyle information",
            summary = "Non-smoker • 7h 42m sleep avg • 30 min brisk walk",
            details = listOf(
                "Tobacco & Smoking: Never smoked",
                "Alcohol: Occasional social (< 1 unit/week)",
                "Physical activity: Brisk walking 5 days/week synced via Google Fit",
                "Diet: Low-sodium balanced vegetarian & lean protein"
            ),
            lastUpdated = "23 Nov 2026"
        ),
        MedicalHistorySection(
            id = "mh_blood",
            title = "Blood information",
            summary = "Blood group O+ • Hemoglobin 14.2 g/dL",
            details = listOf(
                "ABO / Rh Type: O Positive (O+)",
                "Last CBC Hemoglobin: 14.2 g/dL (Normal)",
                "Last Lipid LDL: 94 mg/dL (Optimal)"
            ),
            lastUpdated = "20 Nov 2026"
        ),
        MedicalHistorySection(
            id = "mh_documents",
            title = "Medical documents",
            summary = "4 lab reports & 2 consultation summaries synced",
            details = listOf(
                "Complete Lipid & Cardiac Panel — 20 Nov 2026 (NovaCare Diagnostics)",
                "HbA1c & Fasting Glucose — 14 Nov 2026 (Aster Reference Lab)",
                "Vitamin D3 (25-OH) & Calcium — 06 Nov 2026",
                "12-Lead Resting ECG — 29 Oct 2026 (Apollo Heart Center)"
            ),
            lastUpdated = "20 Nov 2026"
        ),
        MedicalHistorySection(
            id = "mh_vaccination",
            title = "Vaccination history",
            summary = "Up to date • Influenza 2026 completed",
            details = listOf(
                "Influenza (Quadrivalent) — 12 Oct 2026",
                "Tdap Booster — 14 May 2024",
                "COVID-19 mRNA Booster — Completed",
                "Hepatitis B 3-dose series — Completed"
            ),
            lastUpdated = "12 Oct 2026"
        ),
        MedicalHistorySection(
            id = "mh_emergency",
            title = "Emergency medical information",
            summary = "Blood O+ • NKDA • Primary ICE: Priya Allu",
            details = listOf(
                "Active medications: Metformin 500 mg AM, Atorvastatin 10 mg PM",
                "Primary Emergency Contact: Priya Allu (+91 98450 12900)",
                "Preferred Emergency Center: NovaCare Hospital Cardiology Wing",
                "Insurance: Aster Health Platinum (Policy AHP-49-209184)"
            ),
            lastUpdated = "23 Nov 2026",
            isAlert = true
        )
    )

    val emergencyContacts = listOf(
        EmergencyContact(
            id = "ec_priya",
            name = "Priya Allu",
            relation = "Spouse",
            phoneNumber = "+91 98450 12900",
            isPrimary = true
        ),
        EmergencyContact(
            id = "ec_ramesh",
            name = "Ramesh Allu",
            relation = "Father",
            phoneNumber = "+91 98450 88712",
            isPrimary = false
        )
    )

    val insuranceInfo = InsuranceInfo(
        planName = "Aster Health Platinum",
        policyNumber = "AHP-49-209184",
        memberId = "VS-2048-731",
        annualCover = "₹10,00,000 annual cover",
        availableCover = "₹7,85,000 available",
        utilizationFraction = 0.785f,
        renewalAmount = "Premium ₹18,500",
        renewalDueDate = "due 15 December 2026",
        agentName = "Agent Priya Shah",
        agentPhone = "+91 98765 43210",
        cashlessEligible = true
    )

    val linkedHospitals = listOf(
        LinkedHospital(
            id = "lh_novacare",
            name = "NovaCare Hospital",
            address = "14 Residency Road, Bengaluru",
            distanceLabel = "2.4 km away",
            departments = listOf("Cardiology", "General Medicine", "Neurology"),
            isPrimary = true,
            isCashlessEnabled = true,
            lastVisitLabel = "Last visit: 18 Nov 2026",
            syncStatus = "Live EHR synced"
        ),
        LinkedHospital(
            id = "lh_aster",
            name = "Aster Clinic",
            address = "88 Indiranagar 100ft Road, Bengaluru",
            distanceLabel = "4.1 km away",
            departments = listOf("Endocrinology", "General Medicine", "Diagnostics"),
            isPrimary = false,
            isCashlessEnabled = true,
            lastVisitLabel = "Last visit: 06 Nov 2026",
            syncStatus = "Lab portal linked"
        ),
        LinkedHospital(
            id = "lh_apollo",
            name = "Apollo Specialty Center",
            address = "21 Bannerghatta Main Road, Bengaluru",
            distanceLabel = "6.8 km away",
            departments = listOf("Cardiology", "Orthopedics", "ECG Center"),
            isPrimary = false,
            isCashlessEnabled = true,
            lastVisitLabel = "Last visit: 29 Oct 2026",
            syncStatus = "Diagnostics synced"
        )
    )
}
