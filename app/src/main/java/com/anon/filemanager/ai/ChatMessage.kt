package com.anon.filemanager.ai

enum class MsgType { USER, THINKING, ASSISTANT, TOOL_CALL, TOOL_RESULT, ARTIFACT, ERROR }

data class ChatMessage(
    val type: MsgType,
    val content: String,
    val toolName: String? = null,
    val toolArgs: String? = null,
    val toolResult: String? = null,
    val toolOk: Boolean? = null,
    val artifactPath: String? = null,
    val artifactMime: String? = null,
    val streaming: Boolean = false
)
