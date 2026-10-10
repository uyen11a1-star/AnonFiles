package com.anon.filemanager.ai

sealed class AgentEvent {
    data class ThinkingDelta(val text: String) : AgentEvent()
    data class ContentDelta(val text: String) : AgentEvent()
    data class ToolCallReady(val name: String, val args: String) : AgentEvent()
    data class ToolResult(val name: String, val result: String, val ok: Boolean) : AgentEvent()
    data class Phase(val label: String) : AgentEvent()
    data class Err(val msg: String) : AgentEvent()
    object Done : AgentEvent()
}
