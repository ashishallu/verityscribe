package com.example.data

import com.example.model.AppointmentStatus
import com.example.model.AssistMessage
import com.example.model.AssistSession
import com.example.model.AssistSessionCategory
import com.example.model.ClinicalAppointment
import com.example.model.ConsultationType
import com.example.model.DoctorHistoryRecord
import com.example.model.DoctorNotification
import com.example.model.DoctorProfile
import com.example.model.MedicalReport
import com.example.model.PrescriptionItem
import com.example.model.SimilarCase
import com.example.model.StructuredAiSummary
import com.example.model.TranscriptSender
import com.example.model.TranscriptTurn
import com.example.model.VitalsSnapshot

object MockClinicalRepository {

    val initialDoctorProfile = DoctorProfile(
        fullName = "Dr. Aarav Sharma",
        greetingName = "Dr. Sharma",
        specialtyTitle = "Senior Cardiologist",
        department = "Cardiology",
        hospital = "Verity General Hospital",
        licenseNumber = "MED-IND-88427",
        experienceYears = 14,
        languages = "English, Hindi, Marathi",
        workingHours = "Mon–Sat, 08:00–17:00",
        isAvailable = true
    )

    val initialAssistMessages = listOf(
        AssistMessage(
            id = "ast-1",
            isFromDoctor = false,
            text = "I can help you explore synchronized clinical records and prepare consultation summaries.",
            timestamp = "09:12 AM"
        ),
        AssistMessage(
            id = "ast-2",
            isFromDoctor = false,
            text = "Ananya Mehta has been taking Metformin 500 mg twice daily since March. Her last HbA1c was 7.1%, showing a positive trend.",
            timestamp = "09:14 AM"
        )
    )

    val initialAssistSessions = listOf(
        AssistSession(
            id = "sess-1",
            title = "Rohan Kapoor — Chest Pain Intake",
            subtitle = "Draft summary ready for physician review...",
            category = AssistSessionCategory.INTAKE_DRAFTS,
            linkedAppointmentId = "apt-2",
            updatedAt = "10:02 AM",
            messages = listOf(
                AssistMessage(
                    id = "m1",
                    isFromDoctor = true,
                    text = "Can you describe your chest pain?",
                    timestamp = "09:42 AM"
                ),
                AssistMessage(
                    id = "m2",
                    isFromDoctor = false,
                    text = "It's a heavy, pressing sensation in the center of my chest when walking up stairs. It started 3 days ago and lasts about 4 minutes.",
                    timestamp = "09:43 AM"
                ),
                AssistMessage(
                    id = "m3",
                    isFromDoctor = true,
                    text = "Voice follow-up prompt recorded",
                    timestamp = "09:43 AM",
                    isVoiceNote = true,
                    voiceDuration = "0:15"
                ),
                AssistMessage(
                    id = "m4",
                    isFromDoctor = false,
                    text = "I understand. Does the pain spread to your arm, neck, or jaw? Are you experiencing shortness of breath or sweating?",
                    timestamp = "09:44 AM"
                ),
                AssistMessage(
                    id = "m5",
                    isFromDoctor = true,
                    text = "Slight ache in left shoulder, no sweating. hs-Troponin-I is negative (8.2 ng/L).",
                    timestamp = "09:45 AM"
                )
            )
        ),
        AssistSession(
            id = "sess-2",
            title = "Lab Report OCR Extraction",
            subtitle = "Extracted HbA1c, eGFR and BP telemetry from PDF...",
            category = AssistSessionCategory.RECORDS,
            linkedAppointmentId = "apt-1",
            updatedAt = "09:19 AM",
            messages = listOf(
                AssistMessage(
                    id = "m21",
                    isFromDoctor = true,
                    text = "Show blood sugar & renal trend for Ananya Mehta",
                    timestamp = "09:16 AM"
                ),
                AssistMessage(
                    id = "m22",
                    isFromDoctor = false,
                    text = "Ananya Mehta has been taking Metformin 500 mg twice daily since March. Her last HbA1c was 7.1% (19 Jul 2026), fasting glucose 118 mg/dL, and eGFR 84 mL/min — showing a positive trend.",
                    timestamp = "09:17 AM"
                )
            )
        ),
        AssistSession(
            id = "sess-3",
            title = "Priya Nair — Follow-up Notes",
            subtitle = "Symptom course and current medications organized...",
            category = AssistSessionCategory.SUMMARIES,
            linkedAppointmentId = "apt-3",
            updatedAt = "10:15 AM",
            messages = listOf(
                AssistMessage(
                    id = "m31",
                    isFromDoctor = true,
                    text = "Summarise home glucometer log and medications",
                    timestamp = "10:14 AM"
                ),
                AssistMessage(
                    id = "m32",
                    isFromDoctor = false,
                    text = "14-day fasting mean is 119 mg/dL (range 112–126) with zero hypoglycemic episodes on Metformin SR 500 mg and Rosuvastatin 10 mg.",
                    timestamp = "10:15 AM"
                )
            )
        ),
        AssistSession(
            id = "sess-4",
            title = "Ananya Mehta — Hypertension Intake",
            subtitle = "Morning home BP readings (139/87 mmHg avg)...",
            category = AssistSessionCategory.INTAKE_DRAFTS,
            linkedAppointmentId = "apt-1",
            updatedAt = "09:14 AM",
            messages = listOf(
                AssistMessage(
                    id = "m41",
                    isFromDoctor = true,
                    text = "Review pre-visit hypertension intake",
                    timestamp = "09:12 AM"
                ),
                AssistMessage(
                    id = "m42",
                    isFromDoctor = false,
                    text = "7-day home BP mean is 139/87 mmHg following travel dietary sodium increase. Currently on Telmisartan 40 mg OD and Metformin 500 mg BD. Allergy: Sulfa drugs.",
                    timestamp = "09:14 AM"
                )
            )
        ),
        AssistSession(
            id = "sess-5",
            title = "Arjun Malhotra — Post-Op Notes",
            subtitle = "Day-14 post-CABG echo LVEF 50% cleared...",
            category = AssistSessionCategory.SUMMARIES,
            linkedAppointmentId = "apt-4",
            updatedAt = "08:55 AM",
            messages = listOf(
                AssistMessage(
                    id = "m51",
                    isFromDoctor = true,
                    text = "Prepare post-op cardiac rehab clearance summary",
                    timestamp = "08:54 AM"
                ),
                AssistMessage(
                    id = "m52",
                    isFromDoctor = false,
                    text = "Day-14 post-CABG sternotomy clean & dry, LVEF improved to 50% on 2D Echo, minimal pericardial effusion resolved. Cleared for Phase II cardiac rehab.",
                    timestamp = "08:55 AM"
                )
            )
        )
    )

    val initialNotifications = listOf(
        DoctorNotification(
            id = "notif-1",
            title = "AI Intake Draft Ready • Ananya Mehta",
            description = "Pre-visit structured summary and consent transcript synced for 09:30 AM consultation.",
            timeAgo = "6m ago",
            isUrgent = false,
            isRead = false,
            linkedAppointmentId = "apt-1"
        ),
        DoctorNotification(
            id = "notif-2",
            title = "Troponin-I Panel Uploaded • Rohan Kapoor",
            description = "High-sensitivity Troponin-I & 12-lead ECG OCR report requires immediate review.",
            timeAgo = "14m ago",
            isUrgent = true,
            isRead = false,
            linkedAppointmentId = "apt-2"
        ),
        DoctorNotification(
            id = "notif-3",
            title = "Summary Generation Failed • Priya Nair",
            description = "Voice-to-summary pipeline timed out on 10:30 AM AI Chat intake. Tap to retry.",
            timeAgo = "22m ago",
            isUrgent = true,
            isRead = false,
            linkedAppointmentId = "apt-3"
        )
    )

    fun recoveredPriyaSummary(): StructuredAiSummary = StructuredAiSummary(
        chiefProblem = "Routine quarterly diabetes and cardiovascular risk review; mild post-prandial fatigue over 10 days.",
        symptomCourse = "Patient reports stable fasting capillary blood glucose (112–126 mg/dL) using home glucometer. Occasional afternoon drowsiness after lunch; denies polyuria, blurry vision, or paresthesia.",
        currentMedications = listOf(
            "Metformin SR 500 mg — Once daily with dinner",
            "Rosuvastatin 10 mg — Once daily at bedtime"
        ),
        medicalHistoryAndAllergies = "Type 2 Diabetes Mellitus (diagnosed 2021), Dyslipidemia. Allergies: No Known Drug Allergies (NKDA).",
        clarificationsOrUnknowns = listOf(
            "Latest fasting lipid profile pending upload from morning phlebotomy.",
            "Clarify frequency of home SMBG logs over the past 2 weeks."
        ),
        providerName = "VerityClinical Intake Engine",
        modelVersion = "v4.2-clinical-structured",
        generatedAt = "23 Jul, 10:18 AM (Recovered)",
        generationLatency = "1.4s",
        generationFailed = false,
        failureReason = null
    )

    val initialAppointments = listOf(
        ClinicalAppointment(
            id = "apt-1",
            patientName = "Ananya Mehta",
            age = 54,
            gender = "Female",
            mrn = "VS-10482",
            bloodGroup = "B+",
            allergies = listOf("Sulfa drugs (rash)"),
            vitals = VitalsSnapshot(bp = "138/86 mmHg", hr = "76 bpm", spo2 = "98%", temp = "98.4°F"),
            scheduledTime = "09:30 AM",
            isMorningShift = true,
            consultationType = ConsultationType.AI_VIRTUAL,
            chiefComplaint = "Hypertension follow-up",
            status = AppointmentStatus.AWAITING_REVIEW,
            consentVerifiedAt = "23 Jul 2026, 09:08 AM",
            aiSummary = StructuredAiSummary(
                chiefProblem = "Elevated morning home blood pressure readings (136–144/86–92 mmHg) despite adherence to antihypertensive therapy; mild occipital headache twice this week.",
                symptomCourse = "Home BP trend rose gradually over 10 days following increased dietary sodium during family travel. Headaches resolve within 1 hour of rest; patient denies chest pain, dyspnea, orthopnea, visual aura, or pedal edema.",
                currentMedications = listOf(
                    "Telmisartan 40 mg — Once daily (morning)",
                    "Metformin 500 mg — Twice daily with meals (since March)",
                    "Atorvastatin 10 mg — Once daily (night)"
                ),
                medicalHistoryAndAllergies = "Essential Hypertension (6 yrs), Type 2 Diabetes Mellitus (well-controlled; last HbA1c 7.1% in June). Allergy: Sulfonamides (cutaneous rash in 2018).",
                clarificationsOrUnknowns = listOf(
                    "Verify cuff calibration and resting posture for home BP readings.",
                    "Confirm whether serum potassium & creatinine panel was completed this morning."
                ),
                providerName = "VerityClinical Intake Engine",
                modelVersion = "v4.2-clinical-structured",
                generatedAt = "23 Jul, 09:14 AM",
                generationLatency = "1.8s"
            ),
            transcript = listOf(
                TranscriptTurn("t1", TranscriptSender.AI_INTERVIEWER, "Hello Ananya. Before Dr. Sharma joins, what brings you in for today's hypertension follow-up?", "09:09 AM"),
                TranscriptTurn("t2", TranscriptSender.PATIENT, "My morning blood pressure readings have been hovering around 140 over 88 for the past 10 days, and I had a mild headache behind my head twice this week.", "09:10 AM"),
                TranscriptTurn("t3", TranscriptSender.AI_INTERVIEWER, "Have you noticed any chest tightness, shortness of breath, blurred vision, or swelling in your ankles?", "09:10 AM"),
                TranscriptTurn("t4", TranscriptSender.PATIENT, "No chest pain or breathing issues at all, and no ankle swelling. The headache went away after resting.", "09:11 AM"),
                TranscriptTurn("t5", TranscriptSender.AI_INTERVIEWER, "Are you still taking Telmisartan 40 mg and Metformin 500 mg twice daily?", "09:12 AM"),
                TranscriptTurn("t6", TranscriptSender.PATIENT, "Yes, I haven't missed a dose of Telmisartan or Metformin since March. We were travelling last week so restaurant food was saltier than usual.", "09:13 AM")
            ),
            reports = listOf(
                MedicalReport(
                    id = "rep-101",
                    title = "Glycemic & Metabolic Follow-up Panel",
                    category = "Lab Panel",
                    date = "19 Jul 2026",
                    facility = "Verity Diagnostics Lab",
                    flaggedFindings = listOf("HbA1c: 7.1% (Improving)", "eGFR: 84 mL/min/1.73m²"),
                    ocrExtractedText = "PATIENT: Ananya Mehta | ID: VS-10482 | DATE: 19-JUL-2026\n--------------------------------------------------\nHbA1c (HPLC):         7.1 %    [Ref: <5.7% | Target <7.0%]\nFasting Glucose:      118 mg/dL [Ref: 70-99 mg/dL]\nSerum Creatinine:     0.86 mg/dL [Ref: 0.60-1.10]\nSerum Potassium (K+): 4.3 mEq/L  [Ref: 3.5-5.1]\neGFR (CKD-EPI):       84 mL/min  [Normal]"
                ),
                MedicalReport(
                    id = "rep-102",
                    title = "Home BP 7-Day Telemetry Log",
                    category = "Home Monitoring",
                    date = "22 Jul 2026",
                    facility = "Patient Uploaded PDF",
                    flaggedFindings = listOf("7-day avg: 139/87 mmHg"),
                    ocrExtractedText = "OMRON CONNECT 7-DAY AM/PM LOG\nMon 08:00: 142/90 | HR 74\nTue 08:15: 138/86 | HR 76\nWed 07:50: 144/92 | HR 78\nThu 08:05: 136/84 | HR 72\nFri 08:10: 140/88 | HR 75\nSat 08:30: 135/85 | HR 74\nSun 08:20: 138/86 | HR 76\n7-DAY MEAN: 139/87 mmHg"
                )
            ),
            similarCases = listOf(
                SimilarCase(
                    id = "sim-1",
                    caseCode = "VGH-CARD-2025-884",
                    demographics = "56F • Essential HTN + T2DM",
                    presentation = "Transient morning BP rise (140/88) after dietary sodium surge; Telmisartan 40 mg monotherapy.",
                    intervention = "Reinforced DASH sodium restriction (<1.5g/day) + 2-week ambulatory log before escalating dose.",
                    outcome = "BP normalized to 126/78 mmHg at Day 14 without dose escalation.",
                    matchPercent = 94
                ),
                SimilarCase(
                    id = "sim-2",
                    caseCode = "VGH-CARD-2026-119",
                    demographics = "52F • HTN + Metabolic Syndrome",
                    presentation = "Persistent morning SBP 142–148 mmHg with mild occipital headache; normal renal panel.",
                    intervention = "Added Amlodipine 2.5 mg OD alongside Telmisartan 40 mg; rechecked K+ & Cr.",
                    outcome = "Sustained target BP <130/80 achieved within 3 weeks.",
                    matchPercent = 87
                )
            ),
            clinicalNote = "54F with HTN and T2DM presenting with mild transient elevation in morning BP (7-day mean 139/87 mmHg) likely secondary to increased dietary sodium during recent travel. No red-flag cardiovascular or neurological symptoms. Recent HbA1c 7.1% shows positive glycemic trajectory on Metformin 500mg BD.",
            diagnoses = listOf("Essential Hypertension (I10)", "Type 2 Diabetes Mellitus without complications (E11.9)"),
            prescriptions = listOf(
                PrescriptionItem("rx-1", "Telmisartan", "40 mg", "Once daily", "30 days", "Take at 08:00 AM with water"),
                PrescriptionItem("rx-2", "Metformin", "500 mg", "Twice daily", "30 days", "Take immediately after breakfast & dinner")
            )
        ),
        ClinicalAppointment(
            id = "apt-2",
            patientName = "Rohan Kapoor",
            age = 62,
            gender = "Male",
            mrn = "VS-10437",
            bloodGroup = "O+",
            allergies = listOf("Penicillin (anaphylaxis)", "Aspirin intolerance: None"),
            vitals = VitalsSnapshot(bp = "146/90 mmHg", hr = "88 bpm", spo2 = "97%", temp = "98.6°F"),
            scheduledTime = "10:00 AM",
            isMorningShift = true,
            consultationType = ConsultationType.VIDEO,
            chiefComplaint = "Chest discomfort",
            status = AppointmentStatus.IN_PROGRESS,
            consentVerifiedAt = "23 Jul 2026, 09:41 AM",
            aiSummary = StructuredAiSummary(
                chiefProblem = "Intermittent exertional substernal chest tightness lasting 4–5 minutes when climbing two flights of stairs; relieved with rest.",
                symptomCourse = "Onset 3 days ago, 2 episodes total. Characterized as mild pressure (4/10) radiating briefly to left shoulder. No diaphoresis, nausea, or syncope. Last episode yesterday evening.",
                currentMedications = listOf(
                    "Aspirin 75 mg — Once daily",
                    "Atorvastatin 20 mg — Once daily at night",
                    "Metoprolol Succinate 25 mg — Once daily"
                ),
                medicalHistoryAndAllergies = "Coronary Artery Disease (non-obstructive on 2023 angiogram), Hyperlipidemia. SEVERE ALLERGY: Penicillin (anaphylaxis).",
                clarificationsOrUnknowns = listOf(
                    "Determine exact timing of last Troponin-I draw relative to yesterday's episode.",
                    "Assess availability of sublingual GTN at home."
                ),
                providerName = "VerityClinical Intake Engine",
                modelVersion = "v4.2-clinical-structured",
                generatedAt = "23 Jul, 09:45 AM",
                generationLatency = "2.1s"
            ),
            transcript = listOf(
                TranscriptTurn("t21", TranscriptSender.AI_INTERVIEWER, "Good morning Rohan. Can you describe the chest discomfort you experienced?", "09:42 AM"),
                TranscriptTurn("t22", TranscriptSender.PATIENT, "It feels like a dull pressure right in the center of my chest when I walk up the stairs quickly. It goes away if I sit down for 4 minutes.", "09:43 AM"),
                TranscriptTurn("t23", TranscriptSender.AI_INTERVIEWER, "Did the discomfort travel to your arm, jaw, or back, or cause sweating?", "09:43 AM"),
                TranscriptTurn("t24", TranscriptSender.PATIENT, "A slight ache in my left shoulder, no sweating or nausea. I took my morning Metoprolol and Aspirin.", "09:44 AM")
            ),
            reports = listOf(
                MedicalReport(
                    id = "rep-201",
                    title = "High-Sensitivity Troponin-I & 12-Lead ECG",
                    category = "Cardiac Workup",
                    date = "23 Jul 2026",
                    facility = "Verity Emergency Lab",
                    flaggedFindings = listOf("hs-Troponin-I: 8.2 ng/L (Normal <19)", "ECG: Mild T-wave flattening V4-V5"),
                    ocrExtractedText = "CARDIAC BIOMARKER & ECG OCR SUMMARY\nhs-Troponin-I: 8.2 ng/L [Ref: <19.0 ng/L Negative]\nCK-MB:         2.1 ng/mL [Ref: <5.0]\nBNP:           64 pg/mL  [Ref: <100]\n12-LEAD ECG: Sinus rhythm 86 bpm, normal axis, nonspecific mild T-wave flattening in V4-V5. No acute ST elevation."
                )
            ),
            similarCases = listOf(
                SimilarCase(
                    id = "sim-21",
                    caseCode = "VGH-CARD-2026-042",
                    demographics = "64M • Known CAD, Exertional Angina",
                    presentation = "CCS Class II exertional chest pressure, negative hs-Troponin, mild lateral T flattening.",
                    intervention = "Scheduled urgent stress echocardiogram + uptitrated Metoprolol to 50 mg OD + prescribed PRN sublingual GTN.",
                    outcome = "Stress echo mildly positive in LAD territory; elective PCI performed without complications.",
                    matchPercent = 91
                )
            ),
            clinicalNote = "62M with history of non-obstructive CAD presenting with new-onset CCS Class II exertional angina over 3 days. Negative hs-Troponin-I (8.2 ng/L) today; ECG shows mild nonspecific T flattening in V4-V5 without acute ischemia.",
            diagnoses = listOf("Stable Angina Pectoris (I20.8)", "Atherosclerotic Heart Disease (I25.10)"),
            prescriptions = listOf(
                PrescriptionItem("rx-21", "Metoprolol Succinate", "50 mg", "Once daily", "30 days", "Monitor pulse; hold if HR <55 bpm"),
                PrescriptionItem("rx-22", "Nitroglycerin SL", "0.4 mg", "PRN chest pain", "90 days", "Place 1 tablet under tongue for chest tightness; repeat q5min x3 max")
            )
        ),
        ClinicalAppointment(
            id = "apt-3",
            patientName = "Priya Nair",
            age = 41,
            gender = "Female",
            mrn = "VS-10394",
            bloodGroup = "A+",
            allergies = listOf("NKDA"),
            vitals = VitalsSnapshot(bp = "122/78 mmHg", hr = "72 bpm", spo2 = "99%", temp = "98.2°F"),
            scheduledTime = "10:30 AM",
            isMorningShift = true,
            consultationType = ConsultationType.AI_CHAT,
            chiefComplaint = "Diabetes review",
            status = AppointmentStatus.FAILED_SUMMARY,
            consentVerifiedAt = "23 Jul 2026, 10:11 AM",
            aiSummary = StructuredAiSummary(
                chiefProblem = "Summary generation failed during automated structuring.",
                symptomCourse = "Raw patient intake transcript is preserved below and awaits regeneration or manual clinician review.",
                currentMedications = listOf("Metformin SR 500 mg (from prior EHR record)"),
                medicalHistoryAndAllergies = "Type 2 Diabetes Mellitus, Dyslipidemia. NKDA.",
                clarificationsOrUnknowns = listOf("Structured extraction timed out — tap 'Retry Summary' below."),
                providerName = "VerityClinical Intake Engine",
                modelVersion = "v4.2-clinical-structured",
                generatedAt = "Failed at 10:15 AM",
                generationLatency = "Timeout (12.0s)",
                generationFailed = true,
                failureReason = "Upstream clinical NLP parser timed out while normalizing patient-uploaded glucometer attachment. Full transcript was safely preserved."
            ),
            transcript = listOf(
                TranscriptTurn("t31", TranscriptSender.AI_INTERVIEWER, "Hi Priya! How have your blood sugar levels been since our last check-in?", "10:12 AM"),
                TranscriptTurn("t32", TranscriptSender.PATIENT, "My fasting sugars have been staying nicely between 112 and 126 mg/dL! Just feeling a bit sleepy after lunch for the past 10 days.", "10:13 AM"),
                TranscriptTurn("t33", TranscriptSender.AI_INTERVIEWER, "Are you taking Metformin SR 500 mg and Rosuvastatin 10 mg regularly?", "10:14 AM"),
                TranscriptTurn("t34", TranscriptSender.PATIENT, "Yes, every evening without fail. I had my morning lipid blood draw done today and am waiting for the PDF.", "10:14 AM")
            ),
            reports = listOf(
                MedicalReport(
                    id = "rep-301",
                    title = "Home Glucometer 14-Day Export",
                    category = "Glycemic Log",
                    date = "22 Jul 2026",
                    facility = "Patient Glucometer Sync",
                    flaggedFindings = listOf("14-Day Fasting Avg: 119 mg/dL", "Zero hypoglycemic events"),
                    ocrExtractedText = "ACCU-CHEK 14-DAY SUMMARY\nFasting Mean: 119 mg/dL (Range 112-126)\nPost-prandial Mean (2h): 154 mg/dL\nHypoglycemia (<70 mg/dL): 0 events\nEstimated HbA1c: ~6.7%"
                )
            ),
            similarCases = emptyList(),
            clinicalNote = "",
            diagnoses = listOf("Type 2 Diabetes Mellitus (E11.9)"),
            prescriptions = emptyList()
        ),
        ClinicalAppointment(
            id = "apt-4",
            patientName = "Arjun Malhotra",
            age = 68,
            gender = "Male",
            mrn = "VS-10276",
            bloodGroup = "AB+",
            allergies = listOf("Iodine contrast (mild urticaria)"),
            vitals = VitalsSnapshot(bp = "124/76 mmHg", hr = "70 bpm", spo2 = "98%", temp = "98.1°F"),
            scheduledTime = "11:15 AM",
            isMorningShift = true,
            consultationType = ConsultationType.IN_PERSON,
            chiefComplaint = "Post-operative review",
            status = AppointmentStatus.COMPLETED,
            consentVerifiedAt = "23 Jul 2026, 08:50 AM",
            aiSummary = StructuredAiSummary(
                chiefProblem = "Day-14 post-CABG sternotomy wound and cardiac rehabilitation clearance check.",
                symptomCourse = "Incision clean and dry without erythema or discharge. Walking 15 minutes twice daily without dyspnea or chest pain. Appetite and sleep steadily improving.",
                currentMedications = listOf(
                    "Clopidogrel 75 mg — Once daily",
                    "Aspirin 75 mg — Once daily",
                    "Atorvastatin 40 mg — Once daily at night",
                    "Bisoprolol 2.5 mg — Once daily"
                ),
                medicalHistoryAndAllergies = "Triple-vessel CAD s/p CABG (09 Jul 2026), Mild LV systolic dysfunction (LVEF 48%). Allergy: Iodine contrast.",
                clarificationsOrUnknowns = emptyList(),
                providerName = "VerityClinical Intake Engine",
                modelVersion = "v4.2-clinical-structured",
                generatedAt = "23 Jul, 08:55 AM",
                generationLatency = "1.5s"
            ),
            transcript = listOf(
                TranscriptTurn("t41", TranscriptSender.AI_INTERVIEWER, "Welcome back Arjun. How is your sternotomy incision healing two weeks after surgery?", "08:51 AM"),
                TranscriptTurn("t42", TranscriptSender.PATIENT, "Very well — no redness, swelling, or fever. I can walk around the garden for 15 minutes twice a day comfortably.", "08:52 AM")
            ),
            reports = listOf(
                MedicalReport(
                    id = "rep-401",
                    title = "Post-CABG Day 14 Echocardiogram",
                    category = "Imaging",
                    date = "22 Jul 2026",
                    facility = "Verity Heart Institute",
                    flaggedFindings = listOf("LVEF improved to 50%", "Minimal pericardial effusion resolved"),
                    ocrExtractedText = "2D TRANSTHORACIC ECHOCARDIOGRAM\nLVEF: 50% (improved from 45% pre-op)\nValves: Trace MR, no AS\nPericardium: No significant effusion\nConclusion: Satisfactory post-CABG recovery."
                )
            ),
            similarCases = emptyList(),
            clinicalNote = "Day-14 post-CABG review satisfactory. Sternotomy scar well-healed, lungs clear bilaterally, no peripheral edema. Cleared for Phase II outpatient cardiac rehabilitation.",
            diagnoses = listOf("Post-operative cardiac surgery follow-up (Z48.812)", "Coronary Artery Disease (I25.10)"),
            prescriptions = listOf(
                PrescriptionItem("rx-41", "Aspirin", "75 mg", "Once daily", "90 days", "Continue post-breakfast"),
                PrescriptionItem("rx-42", "Clopidogrel", "75 mg", "Once daily", "90 days", "Dual antiplatelet therapy per CABG protocol"),
                PrescriptionItem("rx-43", "Atorvastatin", "40 mg", "Once daily", "90 days", "Take at bedtime")
            )
        ),
        ClinicalAppointment(
            id = "apt-5",
            patientName = "Meera Deshmukh",
            age = 49,
            gender = "Female",
            mrn = "VS-10519",
            bloodGroup = "O-",
            allergies = listOf("NKDA"),
            vitals = VitalsSnapshot(bp = "118/74 mmHg", hr = "94 bpm", spo2 = "99%", temp = "98.4°F"),
            scheduledTime = "11:45 AM",
            isMorningShift = true,
            consultationType = ConsultationType.PHONE,
            chiefComplaint = "Palpitations & dizziness",
            status = AppointmentStatus.AWAITING_REVIEW,
            consentVerifiedAt = "23 Jul 2026, 11:10 AM",
            aiSummary = StructuredAiSummary(
                chiefProblem = "Brief episodes of rapid fluttering heartbeat lasting 20–30 seconds accompanied by mild lightheadedness over the past 5 days.",
                symptomCourse = "Occurs mostly in the late afternoon after 2–3 cups of coffee. No syncope, chest pain, or dyspnea. Patient completed a 24-hour Holter monitor yesterday.",
                currentMedications = listOf("Levothyroxine 50 mcg — Once daily fasting"),
                medicalHistoryAndAllergies = "Hypothyroidism (well-controlled), Iron deficiency anemia (2024). NKDA.",
                clarificationsOrUnknowns = listOf("Review 24-hour Holter burden of PACs/PVCs and latest TSH."),
                providerName = "VerityClinical Intake Engine",
                modelVersion = "v4.2-clinical-structured",
                generatedAt = "23 Jul, 11:14 AM",
                generationLatency = "1.7s"
            ),
            transcript = listOf(
                TranscriptTurn("t51", TranscriptSender.AI_INTERVIEWER, "Hello Meera. Tell me about the palpitations you've been noticing.", "11:11 AM"),
                TranscriptTurn("t52", TranscriptSender.PATIENT, "My heart feels like it skips a beat or flutters for about 20 seconds, mostly in the afternoon. I wore the Holter patch yesterday.", "11:12 AM")
            ),
            reports = listOf(
                MedicalReport(
                    id = "rep-501",
                    title = "24-Hour Holter Monitor Summary",
                    category = "Arrhythmia Monitoring",
                    date = "22 Jul 2026",
                    facility = "Verity Electrophysiology Lab",
                    flaggedFindings = listOf("Frequent isolated PACs (4.2%)", "Zero sustained VT/AFib"),
                    ocrExtractedText = "24-HR AMBULATORY HOLTER REPORT\nPredominant Rhythm: Normal Sinus Rhythm (Min 58, Max 124, Avg 78 bpm)\nPACs: 4,120 isolated beats (4.2% burden), 2 brief couplets\nPVCs: 118 isolated (<0.2%)\nPauses >2.0s: None | AFib/Flutter: None"
                )
            ),
            similarCases = emptyList(),
            clinicalNote = "",
            diagnoses = listOf("Palpitations (R00.2)", "Premature Atrial Contractions (I49.1)"),
            prescriptions = emptyList()
        ),
        ClinicalAppointment(
            id = "apt-6",
            patientName = "Vikram Sethi",
            age = 57,
            gender = "Male",
            mrn = "VS-10544",
            bloodGroup = "B-",
            allergies = listOf("ACE Inhibitors (dry cough)"),
            vitals = VitalsSnapshot(bp = "128/80 mmHg", hr = "68 bpm", spo2 = "98%", temp = "98.3°F"),
            scheduledTime = "12:15 PM",
            isMorningShift = true,
            consultationType = ConsultationType.CHAT,
            chiefComplaint = "Ankle swelling query",
            status = AppointmentStatus.APPROVED,
            consentVerifiedAt = "23 Jul 2026, 11:30 AM",
            aiSummary = StructuredAiSummary(
                chiefProblem = "Mild bilateral ankle puffiness noted in the evenings since starting Amlodipine 5 mg 3 weeks ago.",
                symptomCourse = "Non-tender pitting ankle edema worse at day's end, resolves overnight. Denies orthopnea, PND, or weight gain.",
                currentMedications = listOf("Amlodipine 5 mg — Once daily", "Losartan 50 mg — Once daily"),
                medicalHistoryAndAllergies = "Hypertension (8 yrs). Allergy/Intolerance: Ramipril (persistent dry cough).",
                clarificationsOrUnknowns = emptyList(),
                providerName = "VerityClinical Intake Engine",
                modelVersion = "v4.2-clinical-structured",
                generatedAt = "23 Jul, 11:34 AM",
                generationLatency = "1.3s"
            ),
            transcript = listOf(
                TranscriptTurn("t61", TranscriptSender.AI_INTERVIEWER, "Hi Vikram, what symptoms prompted your message today?", "11:31 AM"),
                TranscriptTurn("t62", TranscriptSender.PATIENT, "Since increasing Amlodipine 3 weeks ago, my ankles look slightly puffy by evening, though it's gone in the morning.", "11:32 AM")
            ),
            reports = emptyList(),
            similarCases = emptyList(),
            clinicalNote = "Likely CCB-induced peripheral vasodilatory edema; consider switching Amlodipine to S-Amlodipine 2.5 mg or Chlorthalidone 12.5 mg.",
            diagnoses = listOf("Medication-induced peripheral edema (T46.1X5A)"),
            prescriptions = emptyList()
        ),
        ClinicalAppointment(
            id = "apt-7",
            patientName = "Kavita Joshi",
            age = 36,
            gender = "Female",
            mrn = "VS-10588",
            bloodGroup = "A-",
            allergies = listOf("NKDA"),
            vitals = VitalsSnapshot(bp = "116/74 mmHg", hr = "102 bpm", spo2 = "99%", temp = "98.5°F"),
            scheduledTime = "02:00 PM",
            isMorningShift = false,
            consultationType = ConsultationType.AI_VIRTUAL,
            chiefComplaint = "Resting tachycardia",
            status = AppointmentStatus.AWAITING_REVIEW,
            consentVerifiedAt = "23 Jul 2026, 01:15 PM",
            aiSummary = StructuredAiSummary(
                chiefProblem = "Smartwatch notifications of resting heart rate 98–106 bpm over the past week while recovering from viral URI.",
                symptomCourse = "Recent upper respiratory viral illness 8 days ago; fever resolved 5 days ago. Mild fatigue on exertion; no chest pain or syncope.",
                currentMedications = listOf("Multivitamin daily"),
                medicalHistoryAndAllergies = "No chronic cardiac history. NKDA.",
                clarificationsOrUnknowns = listOf("Rule out post-viral sinus tachycardia vs. anemia or hyperthyroidism."),
                providerName = "VerityClinical Intake Engine",
                modelVersion = "v4.2-clinical-structured",
                generatedAt = "23 Jul, 01:19 PM",
                generationLatency = "1.6s"
            ),
            transcript = listOf(
                TranscriptTurn("t71", TranscriptSender.AI_INTERVIEWER, "Hello Kavita. What alerted you to schedule today's virtual cardiology check?", "01:16 PM"),
                TranscriptTurn("t72", TranscriptSender.PATIENT, "My watch has been warning me about a resting heart rate around 102 since my cold last week.", "01:17 PM")
            ),
            reports = emptyList(),
            similarCases = emptyList(),
            clinicalNote = "",
            diagnoses = emptyList(),
            prescriptions = emptyList()
        ),
        ClinicalAppointment(
            id = "apt-8",
            patientName = "Suresh Kulkarni",
            age = 71,
            gender = "Male",
            mrn = "VS-10602",
            bloodGroup = "O+",
            allergies = listOf("NKDA"),
            vitals = VitalsSnapshot(bp = "130/78 mmHg", hr = "74 bpm", spo2 = "98%", temp = "98.2°F"),
            scheduledTime = "02:45 PM",
            isMorningShift = false,
            consultationType = ConsultationType.IN_PERSON,
            chiefComplaint = "AFib anticoagulation check",
            status = AppointmentStatus.COMPLETED,
            consentVerifiedAt = "23 Jul 2026, 02:10 PM",
            aiSummary = StructuredAiSummary(
                chiefProblem = "Routine 3-month Apixaban anticoagulation and renal function check for paroxysmal AFib.",
                symptomCourse = "No bleeding gums, epistaxis, melena, or unusual bruising. Rate well controlled.",
                currentMedications = listOf("Apixaban 5 mg — Twice daily", "Diltiazem CD 120 mg — Once daily"),
                medicalHistoryAndAllergies = "Paroxysmal Atrial Fibrillation (CHA2DS2-VASc 3), Hypertension. NKDA.",
                clarificationsOrUnknowns = emptyList(),
                providerName = "VerityClinical Intake Engine",
                modelVersion = "v4.2-clinical-structured",
                generatedAt = "23 Jul, 02:14 PM",
                generationLatency = "1.4s"
            ),
            transcript = listOf(
                TranscriptTurn("t81", TranscriptSender.AI_INTERVIEWER, "Hello Suresh. Have you noticed any bruising or bleeding since your last Apixaban check?", "02:11 PM"),
                TranscriptTurn("t82", TranscriptSender.PATIENT, "None at all. I feel energetic and haven't had any irregular heartbeat episodes.", "02:12 PM")
            ),
            reports = emptyList(),
            similarCases = emptyList(),
            clinicalNote = "Paroxysmal AFib rate-controlled, no bleeding events on Apixaban 5 mg BD. CrCl 68 mL/min supports continuing full dose.",
            diagnoses = listOf("Paroxysmal Atrial Fibrillation (I48.0)"),
            prescriptions = listOf(
                PrescriptionItem("rx-81", "Apixaban", "5 mg", "Twice daily", "90 days", "Take 12 hours apart; do not skip doses")
            )
        )
    )

    val initialDoctorHistory = listOf(
        DoctorHistoryRecord(
            id = "hist-1",
            patientName = "Vikram Malhotra",
            age = 49,
            gender = "Male",
            mrn = "VS-10391",
            visitDate = "23 Jul 2026",
            daysAgo = 0,
            consultationType = ConsultationType.VIDEO,
            diagnosisSummary = "Dyslipidemia — Rosuvastatin Titration",
            clinicalOutcome = "LDL improved to 94 mg/dL. Dose maintained; repeat fasting lipid panel ordered in 12 weeks.",
            medicationsPrescribed = listOf("Rosuvastatin 20 mg — Nightly", "Omega-3 Ethyl Esters 1g — Daily"),
            vitalsSummary = "BP 124/78 mmHg • HR 68 bpm • SpO2 99%",
            followUpRecommended = false,
            linkedAppointmentId = "apt-4"
        ),
        DoctorHistoryRecord(
            id = "hist-2",
            patientName = "Suresh Kulkarni",
            age = 71,
            gender = "Male",
            mrn = "VS-10602",
            visitDate = "23 Jul 2026",
            daysAgo = 0,
            consultationType = ConsultationType.IN_PERSON,
            diagnosisSummary = "Paroxysmal Atrial Fibrillation (I48.0)",
            clinicalOutcome = "Rate controlled with no bleeding events on Apixaban 5 mg BD. CrCl 68 mL/min stable.",
            medicationsPrescribed = listOf("Apixaban 5 mg — Twice daily", "Diltiazem CD 120 mg — Once daily"),
            vitalsSummary = "BP 130/78 mmHg • HR 74 bpm • SpO2 98%",
            followUpRecommended = false,
            linkedAppointmentId = "apt-8"
        ),
        DoctorHistoryRecord(
            id = "hist-3",
            patientName = "Nandini Deshpande",
            age = 58,
            gender = "Female",
            mrn = "VS-10214",
            visitDate = "20 Jul 2026",
            daysAgo = 3,
            consultationType = ConsultationType.AI_VIRTUAL,
            diagnosisSummary = "Essential Hypertension & Left Ventricular Hypertrophy",
            clinicalOutcome = "Adjusted Telmisartan to 40 mg morning + Chlorthalidone 12.5 mg. Home BP log requested.",
            medicationsPrescribed = listOf("Telmisartan 40 mg — Morning", "Chlorthalidone 12.5 mg — Morning"),
            vitalsSummary = "BP 146/88 mmHg • HR 76 bpm • SpO2 98%",
            followUpRecommended = true
        ),
        DoctorHistoryRecord(
            id = "hist-4",
            patientName = "Arvind Swaminathan",
            age = 64,
            gender = "Male",
            mrn = "VS-10189",
            visitDate = "17 Jul 2026",
            daysAgo = 6,
            consultationType = ConsultationType.IN_PERSON,
            diagnosisSummary = "Post-PCI Dual Antiplatelet Review (LAD Stent)",
            clinicalOutcome = "Asymptomatic 6 months post-DES. Troponin-I baseline negative; continue DAPT per protocol.",
            medicationsPrescribed = listOf("Ticagrelor 90 mg — Twice daily", "Aspirin 75 mg — Daily", "Atorvastatin 40 mg — Nightly"),
            vitalsSummary = "BP 122/76 mmHg • HR 64 bpm • SpO2 99%",
            followUpRecommended = false
        ),
        DoctorHistoryRecord(
            id = "hist-5",
            patientName = "Farhan Siddiqui",
            age = 52,
            gender = "Male",
            mrn = "VS-10142",
            visitDate = "14 Jul 2026",
            daysAgo = 9,
            consultationType = ConsultationType.VIDEO,
            diagnosisSummary = "Benign Premature Ventricular Contractions (I49.3)",
            clinicalOutcome = "24-hr Holter showed <1.2% PVC burden, no NSVT. Electrolytes normal; caffeine reduction advised.",
            medicationsPrescribed = listOf("Magnesium Glycinate 250 mg — Nightly"),
            vitalsSummary = "BP 118/74 mmHg • HR 72 bpm • SpO2 99%",
            followUpRecommended = false
        ),
        DoctorHistoryRecord(
            id = "hist-6",
            patientName = "Lakshmi Narayanan",
            age = 67,
            gender = "Female",
            mrn = "VS-10098",
            visitDate = "10 Jul 2026",
            daysAgo = 13,
            consultationType = ConsultationType.IN_PERSON,
            diagnosisSummary = "Heart Failure with Preserved Ejection Fraction (I50.3)",
            clinicalOutcome = "Weight down 1.8 kg; ankle edema improved. Initiated Empagliflozin 10 mg; monitor eGFR in 14 days.",
            medicationsPrescribed = listOf("Empagliflozin 10 mg — Morning", "Torsemide 10 mg — Morning"),
            vitalsSummary = "BP 126/80 mmHg • HR 78 bpm • SpO2 97%",
            followUpRecommended = true
        ),
        DoctorHistoryRecord(
            id = "hist-7",
            patientName = "Deepak Chawla",
            age = 45,
            gender = "Male",
            mrn = "VS-10064",
            visitDate = "06 Jul 2026",
            daysAgo = 17,
            consultationType = ConsultationType.AI_CHAT,
            diagnosisSummary = "Costochondritis vs. Non-Cardiac Chest Wall Pain",
            clinicalOutcome = "Reproducible chest wall tenderness, normal 12-lead ECG. Reassured & prescribed short course topical NSAID.",
            medicationsPrescribed = listOf("Diclofenac Gel 1% — Topical TID PRN"),
            vitalsSummary = "BP 120/76 mmHg • HR 70 bpm • SpO2 99%",
            followUpRecommended = false
        ),
        DoctorHistoryRecord(
            id = "hist-8",
            patientName = "Rekha Menon",
            age = 60,
            gender = "Female",
            mrn = "VS-09973",
            visitDate = "02 Jul 2026",
            daysAgo = 21,
            consultationType = ConsultationType.PHONE,
            diagnosisSummary = "Subclinical Hypothyroidism & Sinus Bradycardia",
            clinicalOutcome = "TSH 7.4 mIU/L noted on annual screening; coordinated Levothyroxine titration with Endocrinology.",
            medicationsPrescribed = listOf("Levothyroxine 50 mcg — Early morning fasting"),
            vitalsSummary = "BP 114/70 mmHg • HR 56 bpm • SpO2 98%",
            followUpRecommended = true
        ),
        DoctorHistoryRecord(
            id = "hist-9",
            patientName = "Harishankar Iyer",
            age = 74,
            gender = "Male",
            mrn = "VS-09912",
            visitDate = "28 Jun 2026",
            daysAgo = 25,
            consultationType = ConsultationType.IN_PERSON,
            diagnosisSummary = "Mild Aortic Sclerosis — Annual Echocardiogram",
            clinicalOutcome = "Transthoracic echo showed preserved LVEF 62% with trivial MR. Cleared for routine 12-month surveillance.",
            medicationsPrescribed = listOf("Aspirin 75 mg — Daily", "Atorvastatin 20 mg — Nightly"),
            vitalsSummary = "BP 132/80 mmHg • HR 69 bpm • SpO2 98%",
            followUpRecommended = false
        ),
        DoctorHistoryRecord(
            id = "hist-10",
            patientName = "Sunita Kulkarni",
            age = 55,
            gender = "Female",
            mrn = "VS-09850",
            visitDate = "25 Jun 2026",
            daysAgo = 28,
            consultationType = ConsultationType.VIDEO,
            diagnosisSummary = "White-Coat Hypertension — ABPM Evaluation",
            clinicalOutcome = "24-hour ambulatory BP averaged 122/76 mmHg. Deferred pharmacological escalation; lifestyle reinforcement.",
            medicationsPrescribed = listOf("DASH Sodium Plan • Home BP Monitoring"),
            vitalsSummary = "BP 138/84 mmHg (clinic) • HR 75 bpm • SpO2 99%",
            followUpRecommended = false
        )
    )
}
