package com.example.data

import android.util.Log
import com.example.BuildConfig
import com.example.model.Appointment
import com.example.model.ConsultationType
import com.example.model.DoctorDirectoryEntry
import com.example.model.HospitalOption
import com.example.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.MultipartBody
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.RequestBody.Companion.asRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.TimeUnit
import java.util.UUID

data class PatientIdentity(
    val id: String,
    val email: String,
    val displayName: String
)

data class DirectGeminiSession(
    val sessionId: String,
    val credential: String,
    val model: String,
    val config: JSONObject
)

/** Authenticated client for the existing VerityScribe REST API. It never logs tokens or payloads. */
class PatientBackendClient(
    private val onSessionRefreshed: (accessToken: String, refreshToken: String?) -> Unit = { _, _ -> }
) {
    private val http = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(75, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .callTimeout(90, TimeUnit.SECONDS)
        .build()

    @Volatile private var accessToken: String? = null
    @Volatile private var refreshToken: String? = null
    private val refreshLock = Any()
    private val jsonType = "application/json; charset=utf-8".toMediaType()

    val isConfigured: Boolean
        get() = BuildConfig.SUPABASE_URL.isNotBlank() &&
            BuildConfig.SUPABASE_PUBLISHABLE_KEY.isNotBlank() && BuildConfig.API_BASE_URL.isNotBlank()

    suspend fun signIn(email: String, password: String): PatientIdentity = withContext(Dispatchers.IO) {
        requireConfigured()
        val response = execute(
            url = "${BuildConfig.SUPABASE_URL.trimEnd('/')}/auth/v1/token?grant_type=password",
            method = "POST",
            body = JSONObject().put("email", email.trim()).put("password", password),
            supabaseAuth = true
        )
        val token = response.optString("access_token")
        if (token.isBlank()) throw IOException("Authentication succeeded without an access token.")
        accessToken = token
        refreshToken = response.optString("refresh_token").takeIf(String::isNotBlank)
        val user = response.optJSONObject("user") ?: JSONObject()
        val metadata = user.optJSONObject("user_metadata") ?: JSONObject()
        val name = listOf(metadata.optString("first_name"), metadata.optString("last_name"))
            .filter { it.isNotBlank() }.joinToString(" ")
            .ifBlank { user.optString("email").substringBefore('@').replace('.', ' ').replace('_', ' ') }
        PatientIdentity(user.optString("id"), user.optString("email", email), name)
    }

    suspend fun signUp(name: String, email: String, password: String): Boolean = withContext(Dispatchers.IO) {
        requireConfigured()
        val response = execute(
            url = "${BuildConfig.SUPABASE_URL.trimEnd('/')}/auth/v1/signup",
            method = "POST",
            body = JSONObject().put("email", email.trim()).put("password", password)
                .put("data", JSONObject().put("first_name", name.trim().substringBeforeLast(" "))
                    .put("last_name", name.trim().substringAfterLast(" ", ""))),
            supabaseAuth = true
        )
        val token = response.optString("access_token")
        if (token.isNotBlank()) {
            accessToken = token
            refreshToken = response.optString("refresh_token").takeIf(String::isNotBlank)
        }
        token.isNotBlank()
    }

    suspend fun requestPasswordReset(email: String) = withContext(Dispatchers.IO) {
        requireConfigured()
        execute(
            url = "${BuildConfig.SUPABASE_URL.trimEnd('/')}/auth/v1/recover",
            method = "POST",
            body = JSONObject().put("email", email.trim()),
            supabaseAuth = true
        )
    }

    fun restoreAccessToken(token: String?, refresh: String? = null) {
        accessToken = token?.takeIf(String::isNotBlank)
        refreshToken = refresh?.takeIf(String::isNotBlank)
    }
    fun clearSession() { accessToken = null; refreshToken = null }
    fun currentAccessToken(): String? = accessToken
    fun currentRefreshToken(): String? = refreshToken

    suspend fun profile(): JSONObject = apiGet("/me")
    suspend fun patient(): JSONObject = apiGet("/me/patient")
    suspend fun medicalRecord(): JSONObject = apiGet("/patients/me/medical-record")
    suspend fun timeline(): JSONObject = apiGet("/patients/me/timeline")
    suspend fun prescriptions(): JSONObject = apiGet("/me/prescriptions")
    suspend fun medicationSchedules(): JSONObject = apiGet("/me/medication-schedules")
    suspend fun reports(): JSONObject = apiGet("/me/reports")
    suspend fun insurance(): JSONObject = apiGet("/me/insurance")
    suspend fun consultations(): JSONObject = apiGet("/me/consultations")
    suspend fun notifications(): JSONObject = apiGet("/notifications?page_size=100")
    suspend fun markAllNotificationsRead(): JSONObject = apiPost("/notifications/read-all", JSONObject())

    suspend fun markNotificationRead(notificationId: String): JSONObject =
        withContext(Dispatchers.IO) {
            execute("${BuildConfig.API_BASE_URL.trimEnd('/')}/notifications/$notificationId/read", "PATCH", JSONObject(), bearer = accessToken)
        }

    suspend fun uploadChatReport(sessionId: String, file: File, fileName: String, mimeType: String, reportType: String): JSONObject =
        withContext(Dispatchers.IO) {
            require(file.isFile && file.length() in 1..(10L * 1024L * 1024L)) {
                "Choose a non-empty report under 10 MB."
            }
            val form = MultipartBody.Builder().setType(MultipartBody.FORM)
                .addFormDataPart("report_type", reportType)
                .addFormDataPart("session_id", sessionId)
                .addFormDataPart("document", fileName, file.asRequestBody(mimeType.toMediaType()))
                .build()
            val uploadId = UUID.randomUUID().toString().replace("-", "")
            fun sendUpload(token: String?): JSONObject {
                val builder = Request.Builder()
                    .url("${BuildConfig.API_BASE_URL.trimEnd('/')}/chat/reports")
                    .header("Accept", "application/json")
                    .header("X-Upload-Id", uploadId)
                    .post(form)
                token?.takeIf(String::isNotBlank)?.let { builder.header("Authorization", "Bearer $it") }
                http.newCall(builder.build()).execute().use { response ->
                    val raw = response.body?.string().orEmpty()
                    val parsed = runCatching { JSONObject(raw) }.getOrElse { JSONObject() }
                    if (!response.isSuccessful) {
                        val detail = parsed.optString("detail").ifBlank { "Report upload failed (HTTP ${response.code})." }
                        Log.w(LOG_TAG, "POST /chat/reports failed with HTTP ${response.code}")
                        throw ApiFailure(response.code, detail)
                    }
                    return parsed
                }
            }
            try {
                sendUpload(accessToken)
            } catch (error: ApiFailure) {
                if (error.statusCode != 401 || refreshToken.isNullOrBlank()) throw error
                val oldToken = accessToken
                val renewed = synchronized(refreshLock) {
                    if (accessToken != oldToken) accessToken else refreshSupabaseSession()
                } ?: throw error
                sendUpload(renewed)
            }
        }

    suspend fun appointments(): List<Appointment> = withContext(Dispatchers.IO) {
        val data = apiGet("/me/appointments").optJSONArray("data") ?: JSONArray()
        (0 until data.length()).mapNotNull { index -> data.optJSONObject(index)?.toAppointment() }
    }

    suspend fun hospitalsAndDoctors(): Pair<List<HospitalOption>, List<DoctorDirectoryEntry>> = withContext(Dispatchers.IO) {
        val hospitalsJson = apiGet("/hospitals?page_size=100").optJSONArray("data") ?: JSONArray()
        val departmentsJson = apiGet("/departments?page_size=100").optJSONArray("data") ?: JSONArray()
        val doctorsJson = apiGet("/doctors?page_size=100").optJSONArray("data") ?: JSONArray()
        val departmentsByHospital = mutableMapOf<String, MutableList<String>>()
        for (i in 0 until departmentsJson.length()) {
            val row = departmentsJson.optJSONObject(i) ?: continue
            val hospitalId = row.optString("hospital_id")
            val name = row.optString("name")
            if (hospitalId.isNotBlank() && name.isNotBlank()) departmentsByHospital.getOrPut(hospitalId) { mutableListOf() }.add(name)
        }
        val hospitals = (0 until hospitalsJson.length()).mapNotNull { i ->
            hospitalsJson.optJSONObject(i)?.let { row ->
                HospitalOption(row.optString("id"), row.optString("name"), departmentsByHospital[row.optString("id")].orEmpty())
            }
        }
        val doctors = (0 until doctorsJson.length()).mapNotNull { i ->
            doctorsJson.optJSONObject(i)?.let { row ->
                val first = row.optString("first_name")
                val last = row.optString("last_name")
                val doctorName = ("Dr. " + listOf(first, last).filter(String::isNotBlank).joinToString(" ")).trim()
                DoctorDirectoryEntry(
                    id = row.optString("id"), name = doctorName,
                    department = row.optJSONObject("department")?.optString("name").orEmpty(),
                    hospital = row.optJSONObject("hospital")?.optString("name").orEmpty(),
                    feeRupees = row.optDouble("consultation_fee", 0.0).toInt(),
                    avatarRes = R.drawable.img_doctor_meera
                )
            }
        }
        hospitals to doctors
    }

    suspend fun bookAppointment(
        doctorId: String, hospitalId: String, date: String, time: String,
        type: String, reason: String, notes: String
    ): Appointment = withContext(Dispatchers.IO) {
        val normalizedType = when (type.lowercase(Locale.ROOT)) {
            "ai chat" -> "ai_chat"
            "ai virtual" -> "ai_virtual"
            "in person" -> "in_person"
            else -> "video"
        }
        val dateIso = toIsoDate(date)
        val response = apiPost("/appointments", JSONObject()
            .put("doctor_id", doctorId).put("hospital_id", hospitalId)
            .put("appointment_date", dateIso).put("appointment_time", time)
            .put("consultation_type", normalizedType).put("reason_for_visit", reason)
            .put("notes", notes))
        response.optJSONObject("data")?.toAppointment()
            ?: throw IOException("Booking response did not contain the appointment.")
    }

    suspend fun createChatSession(title: String? = null): String = withContext(Dispatchers.IO) {
        val response = apiPost("/chat/sessions", JSONObject().put("title", title ?: JSONObject.NULL))
        val data = response.optJSONObject("data") ?: response
        (data.optString("session_id").takeIf(String::isNotBlank)
            ?: data.optJSONObject("session")?.optString("session_id")?.takeIf(String::isNotBlank)
            ?: data.optJSONObject("session")?.optString("id")?.takeIf(String::isNotBlank))
            ?: throw IOException("Chat session response did not include a session ID.")
    }

    suspend fun sendChatMessage(
        sessionId: String,
        message: String,
        history: List<JSONObject>,
        attachmentText: String? = null
    ): String = withContext(Dispatchers.IO) {
        val historyJson = JSONArray()
        history.forEach(historyJson::put)
        val request = JSONObject()
            .put("session_id", sessionId)
            .put("message", message)
            .put("history", historyJson)
        attachmentText?.trim()?.takeIf(String::isNotEmpty)?.let { request.put("attachment", it) }
        val response = apiPost("/chat", request)
        val data = response.optJSONObject("data") ?: response
        (data.optString("answer").takeIf(String::isNotBlank)
            ?: data.optString("message").takeIf(String::isNotBlank)
            ?: response.optString("answer").takeIf(String::isNotBlank)
            ?: response.optString("message").takeIf(String::isNotBlank))
            ?: throw IOException("AI response was empty.")
    }

    suspend fun endChatSession(sessionId: String) { withContext(Dispatchers.IO) { apiPost("/chat/sessions/$sessionId/end", JSONObject()) } }
    suspend fun removeChatDocument(sessionId: String, documentId: String) = withContext(Dispatchers.IO) {
        apiPost("/chat/sessions/$sessionId/documents/$documentId/remove", JSONObject())
    }

    suspend fun startAiConsultation(
        appointmentId: String,
        mode: String,
        consent: Boolean,
        voiceConsent: Boolean = mode == "ai_virtual"
    ): String = withContext(Dispatchers.IO) {
        require(consent) { "Explicit consent is required before starting an AI consultation." }
        require(mode != "ai_virtual" || voiceConsent) { "Explicit voice consent is required before starting AI Virtual." }
        val response = apiPost("/ai-consultations/appointments/$appointmentId/start", JSONObject()
            .put("consent", consent).put("voice_consent", voiceConsent).put("similar_case_consent", false))
        response.optJSONObject("data")?.optJSONObject("session")?.optString("id")?.takeIf(String::isNotBlank)
            ?: throw IOException("Consultation session could not be started.")
    }

    suspend fun getAiConsultation(sessionId: String): JSONObject = withContext(Dispatchers.IO) {
        apiGet("/ai-consultations/$sessionId").optJSONObject("data")
            ?: throw IOException("Consultation response was empty.")
    }

    suspend fun sendAiConsultationMessage(sessionId: String, message: String, requestKey: String): String = withContext(Dispatchers.IO) {
        val response = apiPost("/ai-consultations/$sessionId/messages", JSONObject()
            .put("message", message).put("request_key", requestKey))
        val messages = response.optJSONObject("data")?.optJSONArray("messages") ?: JSONArray()
        (0 until messages.length()).mapNotNull { messages.optJSONObject(it) }
            .lastOrNull { it.optString("role") == "assistant" }?.optString("content")
            ?.takeIf(String::isNotBlank) ?: throw IOException("AI consultation response was empty.")
    }

    suspend fun endAiConsultation(sessionId: String): String = withContext(Dispatchers.IO) {
        val response = apiPost("/ai-consultations/$sessionId/end", JSONObject())
        val session = response.optJSONObject("data")?.optJSONObject("session") ?: JSONObject()
        session.optJSONObject("summary")?.optString("clinical_summary")?.takeIf(String::isNotBlank)
            ?: throw IOException("The consultation was saved, but its summary is not available yet.")
    }

    suspend fun issueLiveCredential(sessionId: String): JSONObject = withContext(Dispatchers.IO) {
        apiPost("/ai-consultations/$sessionId/live-credential", JSONObject())
            .optJSONObject("data") ?: throw IOException("Gemini Live credential response was empty.")
    }

    suspend fun startAiVirtual(appointmentId: String, consent: Boolean, voiceConsent: Boolean): DirectGeminiSession {
        val sessionId = startAiConsultation(appointmentId, "ai_virtual", consent, voiceConsent)
        val direct = issueLiveCredential(sessionId)
        val credential = direct.optString("credential")
        val model = direct.optString("model")
        val config = direct.optJSONObject("config")
        if (credential.isBlank() || model.isBlank() || config == null) {
            throw IOException("The backend returned incomplete Gemini Live session details.")
        }
        return DirectGeminiSession(sessionId, credential, model, config)
    }

    suspend fun persistDirectTurn(sessionId: String, requestKey: String, inputText: String, outputText: String) {
        withContext(Dispatchers.IO) {
            apiPost("/ai-consultations/$sessionId/direct-turn", JSONObject()
                .put("request_key", requestKey).put("input_text", inputText).put("output_text", outputText))
        }
    }

    suspend fun uploadAppointmentVoice(appointmentId: String, file: File): JSONObject = withContext(Dispatchers.IO) {
        require(file.isFile && file.length() > 0L) { "The recording is empty or unavailable." }
        val mediaType = "audio/mp4".toMediaType()
        val form = MultipartBody.Builder().setType(MultipartBody.FORM)
            .addFormDataPart("audio", file.name, file.asRequestBody(mediaType))
            .build()
        fun send(token: String?): JSONObject {
            val builder = Request.Builder()
                .url("${BuildConfig.API_BASE_URL.trimEnd('/')}/appointments/$appointmentId/voice")
                .header("Accept", "application/json")
                .post(form)
            token?.takeIf(String::isNotBlank)?.let { builder.header("Authorization", "Bearer $it") }
            http.newCall(builder.build()).execute().use { response ->
                val parsed = runCatching { JSONObject(response.body?.string().orEmpty()) }.getOrElse { JSONObject() }
                if (!response.isSuccessful) {
                    val detail = parsed.opt("detail")?.let { if (it is String) it else it.toString() }
                    Log.w(LOG_TAG, "POST /appointments/{id}/voice failed with HTTP ${response.code}")
                    throw ApiFailure(response.code, detail?.takeIf(String::isNotBlank)
                        ?: "Voice note upload failed (HTTP ${response.code}).")
                }
                return parsed
            }
        }
        try {
            send(accessToken)
        } catch (error: ApiFailure) {
            if (error.statusCode != 401 || refreshToken.isNullOrBlank()) throw error
            val oldToken = accessToken
            val renewed = synchronized(refreshLock) {
                if (accessToken != oldToken) accessToken else refreshSupabaseSession()
            } ?: throw error
            send(renewed)
        }
    }

    suspend fun saveVoiceTranscriptCorrection(transcriptId: String, text: String) = withContext(Dispatchers.IO) {
        require(text.isNotBlank()) { "Transcript correction cannot be empty." }
        apiPost("/voice-transcripts/$transcriptId/correction", JSONObject().put("transcript_text", text.trim()))
    }

    private suspend fun apiGet(path: String): JSONObject = withContext(Dispatchers.IO) {
        execute("${BuildConfig.API_BASE_URL.trimEnd('/')}$path", "GET", null, bearer = accessToken)
    }

    private suspend fun apiPost(path: String, body: JSONObject): JSONObject = withContext(Dispatchers.IO) {
        execute("${BuildConfig.API_BASE_URL.trimEnd('/')}$path", "POST", body, bearer = accessToken)
    }

    private fun execute(
        url: String, method: String, body: JSONObject?, supabaseAuth: Boolean = false, bearer: String? = null
    ): JSONObject {
        return try {
            executeOnce(url, method, body, supabaseAuth, bearer)
        } catch (error: ApiFailure) {
            if (error.statusCode != 401 || supabaseAuth || bearer.isNullOrBlank() || refreshToken.isNullOrBlank()) throw error
            val renewed = synchronized(refreshLock) {
                if (accessToken != bearer) accessToken else refreshSupabaseSession()
            } ?: throw error
            executeOnce(url, method, body, supabaseAuth = false, bearer = renewed)
        }
    }

    private fun executeOnce(
        url: String, method: String, body: JSONObject?, supabaseAuth: Boolean, bearer: String?
    ): JSONObject {
        val builder = Request.Builder().url(url).header("Accept", "application/json")
        if (supabaseAuth) builder.header("apikey", BuildConfig.SUPABASE_PUBLISHABLE_KEY)
        if (!bearer.isNullOrBlank()) builder.header("Authorization", "Bearer $bearer")
        if (body != null) builder.header("Content-Type", "application/json")
        val request = when (method) {
            "POST" -> builder.post((body?.toString() ?: "{}").toRequestBody(jsonType)).build()
            "PATCH" -> builder.patch((body?.toString() ?: "{}").toRequestBody(jsonType)).build()
            else -> builder.get().build()
        }
        http.newCall(request).execute().use { response ->
            val raw = response.body?.string().orEmpty()
            val parsed = runCatching { JSONObject(raw) }.getOrElse { JSONObject() }
            if (!response.isSuccessful) {
                val detail = parsed.optString("detail").ifBlank {
                    parsed.optString("msg").ifBlank { parsed.optString("message") }
                }
                Log.w(LOG_TAG, "$method API request failed with HTTP ${response.code}")
                throw ApiFailure(response.code, detail.ifBlank { "Service request failed (HTTP ${response.code})." })
            }
            return parsed
        }
    }

    private fun refreshSupabaseSession(): String? {
        val refresh = refreshToken ?: return null
        val response = executeOnce(
            url = "${BuildConfig.SUPABASE_URL.trimEnd('/')}/auth/v1/token?grant_type=refresh_token",
            method = "POST",
            body = JSONObject().put("refresh_token", refresh),
            supabaseAuth = true,
            bearer = null
        )
        val renewedAccess = response.optString("access_token").takeIf(String::isNotBlank) ?: return null
        accessToken = renewedAccess
        refreshToken = response.optString("refresh_token").takeIf(String::isNotBlank) ?: refresh
        onSessionRefreshed(renewedAccess, refreshToken)
        return renewedAccess
    }

    private class ApiFailure(val statusCode: Int, detail: String) : IOException("HTTP $statusCode: $detail")

    private fun requireConfigured() {
        if (!isConfigured) throw IOException("Backend configuration is missing. Set SUPABASE_URL, SUPABASE_PUBLISHABLE_KEY, and API_BASE_URL in local.properties or backend/.env.")
    }

    private fun JSONObject.toAppointment(): Appointment {
        val doctor = optJSONObject("doctor") ?: JSONObject()
        val doctorProfile = doctor.optJSONObject("profiles") ?: doctor
        val hospital = doctor.optJSONObject("hospitals") ?: doctor.optJSONObject("hospital") ?: JSONObject()
        val department = doctor.optJSONObject("departments") ?: doctor.optJSONObject("department") ?: JSONObject()
        val doctorName = ("Dr. " + listOf(doctorProfile.optString("first_name"), doctorProfile.optString("last_name"))
            .filter(String::isNotBlank).joinToString(" ")).trim().ifBlank { "Your care team" }
        val rawType = optString("consultation_type")
        val displayType = when (rawType) {
            "ai_chat" -> ConsultationType.AI_CHAT.label
            "ai_virtual" -> ConsultationType.AI_VIRTUAL.label
            "in_person" -> ConsultationType.IN_PERSON.label
            "video" -> ConsultationType.VIDEO.label
            else -> rawType.ifBlank { ConsultationType.VIDEO.label }
        }
        return Appointment(
            id = optString("id"), doctorName = doctorName,
            specialty = department.optString("name").ifBlank { "Consultation" },
            hospital = hospital.optString("name").ifBlank { "VerityScribe care network" },
            dateLabel = runCatching { SimpleDateFormat("EEE, dd MMM", Locale.getDefault()).format(SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).parse(optString("appointment_date"))!!) }.getOrDefault(optString("appointment_date")),
            timeLabel = optString("appointment_time"), consultationType = displayType,
            notes = listOf(optString("reason_for_visit"), optString("notes")).filter(String::isNotBlank).joinToString(" • "),
            avatarRes = R.drawable.img_doctor_meera,
            appointmentDateIso = optString("appointment_date"),
            appointmentTimeIso = optString("appointment_time")
        )
    }

    private fun toIsoDate(label: String): String {
        val formats = listOf("EEE, dd MMM yyyy", "EEE, dd MMM", "dd MMM yyyy", "dd MMM")
        for (format in formats) {
            val parsed = runCatching { SimpleDateFormat(format, Locale.getDefault()).parse(label) }.getOrNull()
            if (parsed != null) return SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).format(parsed)
        }
        if (Regex("^\\d{4}-\\d{2}-\\d{2}$").matches(label)) return label
        throw IOException("Could not read the selected appointment date.")
    }

    private companion object {
        const val LOG_TAG = "PatientBackendClient"
    }
}
