package com.example.data

import com.example.model.ChatMessage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import org.json.JSONObject

/** Uses the authenticated VerityScribe chat endpoint; no model credentials ship in the APK. */
class BackendAiRepository(private val backend: PatientBackendClient) : AiRepository {
    private val sessionIdsByThread = mutableMapOf<String, String>()
    override val isLiveAiEnabled: Boolean = backend.isConfigured

    override fun buildHomeSummary(upcomingCount: Int, pendingMedicinesCount: Int, sleepDuration: String): String {
        val medicineText = if (pendingMedicinesCount == 0) "no doses pending" else "$pendingMedicinesCount doses pending"
        return "$upcomingCount appointments in your schedule, $medicineText."
    }

    override fun streamClinicalReply(
        userPrompt: String,
        clinicalContext: String,
        threadId: String,
        history: List<ChatMessage>,
        attachmentText: String?
    ): Flow<String> = flow {
        val id = ensureSession(threadId, "Verity AI chat")
        val requestHistory = history.filterNot { it.id.startsWith("welcome_") }.map { message ->
            JSONObject()
                .put("text", message.text)
                .put("role", if (message.isUser) "user" else "assistant")
        }
        emit(backend.sendChatMessage(id, userPrompt, requestHistory, attachmentText))
    }

    suspend fun ensureSession(threadId: String, title: String = "Verity AI chat"): String {
        synchronized(sessionIdsByThread) { sessionIdsByThread[threadId] }?.let { return it }
        val created = backend.createChatSession(title)
        return synchronized(sessionIdsByThread) {
            sessionIdsByThread[threadId] ?: created.also { sessionIdsByThread[threadId] = it }
        }
    }

    suspend fun sessionForReportUpload(threadId: String): String = ensureSession(threadId, "Medical report review")

    suspend fun removeDocument(threadId: String, documentId: String) {
        val sessionId = synchronized(sessionIdsByThread) { sessionIdsByThread[threadId] }
            ?: throw IllegalStateException("The chat session is not available.")
        backend.removeChatDocument(sessionId, documentId)
    }

    suspend fun endThread(threadId: String) {
        val id = synchronized(sessionIdsByThread) { sessionIdsByThread.remove(threadId) } ?: return
        backend.endChatSession(id)
    }

    suspend fun endAllSessions() {
        val ids = synchronized(sessionIdsByThread) {
            sessionIdsByThread.values.toList().also { sessionIdsByThread.clear() }
        }
        ids.forEach { runCatching { backend.endChatSession(it) } }
    }

    fun forgetSessions() { synchronized(sessionIdsByThread) { sessionIdsByThread.clear() } }
}
