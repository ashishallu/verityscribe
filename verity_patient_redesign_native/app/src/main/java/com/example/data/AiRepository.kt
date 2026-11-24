package com.example.data

import com.example.model.ChatMessage
import kotlinx.coroutines.flow.Flow

interface AiRepository {
    val isLiveAiEnabled: Boolean
    fun streamClinicalReply(
        userPrompt: String,
        clinicalContext: String,
        threadId: String,
        history: List<ChatMessage>,
        attachmentText: String? = null
    ): Flow<String>
    fun buildHomeSummary(
        upcomingCount: Int,
        pendingMedicinesCount: Int,
        sleepDuration: String = "7h 42m"
    ): String
}

