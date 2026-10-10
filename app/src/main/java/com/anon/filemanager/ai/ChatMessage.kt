package com.anon.filemanager.ai

data class ChatMessage(
    val isUser: Boolean,
    val content: String,
    val toolSummary: String? = null
)
