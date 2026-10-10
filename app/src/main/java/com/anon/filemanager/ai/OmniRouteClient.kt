package com.anon.filemanager.ai

import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

object OmniRouteClient {

    private const val DEFAULT_BASE = "http://127.0.0.1:20128"

    var baseUrl: String = DEFAULT_BASE
    var apiKey: String = ""
    var model: String = "gpt-4o-mini"

    data class Reply(
        val content: String?,
        val toolCalls: List<ToolCall>
    )
    data class ToolCall(val id: String, val name: String, val argsJson: String)

    fun chat(messages: JSONArray, tools: JSONArray?): Reply {
        val url = URL("$baseUrl/v1/chat/completions")
        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            doOutput = true
            connectTimeout = 30000
            readTimeout = 120000
            setRequestProperty("Content-Type", "application/json")
            if (apiKey.isNotBlank()) setRequestProperty("Authorization", "Bearer $apiKey")
        }

        val payload = JSONObject().apply {
            put("model", model)
            put("messages", messages)
            if (tools != null && tools.length() > 0) {
                put("tools", tools)
                put("tool_choice", "auto")
            }
            put("stream", false)
        }

        OutputStreamWriter(conn.outputStream, Charsets.UTF_8).use { it.write(payload.toString()) }

        val code = conn.responseCode
        val stream = if (code in 200..299) conn.inputStream else conn.errorStream
        val body = BufferedReader(InputStreamReader(stream, Charsets.UTF_8)).use { it.readText() }

        if (code !in 200..299) error("HTTP $code: ${body.take(400)}")

        val json = JSONObject(body)
        val choice = json.getJSONArray("choices").getJSONObject(0)
        val msg = choice.getJSONObject("message")

        val content = if (msg.has("content") && !msg.isNull("content")) msg.getString("content") else null
        val calls = mutableListOf<ToolCall>()
        if (msg.has("tool_calls") && !msg.isNull("tool_calls")) {
            val arr = msg.getJSONArray("tool_calls")
            for (i in 0 until arr.length()) {
                val tc = arr.getJSONObject(i)
                val fn = tc.getJSONObject("function")
                calls.add(ToolCall(
                    id = tc.optString("id", "call_$i"),
                    name = fn.getString("name"),
                    argsJson = fn.optString("arguments", "{}")
                ))
            }
        }
        return Reply(content, calls)
    }
}
