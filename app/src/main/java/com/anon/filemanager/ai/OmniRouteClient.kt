package com.anon.filemanager.ai

import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

object OmniRouteClient {

    var baseUrl: String = com.anon.filemanager.BuildConfig.SHARED_SERVER_URL
    var apiKey: String = com.anon.filemanager.BuildConfig.SHARED_SERVER_KEY
    var model: String = com.anon.filemanager.BuildConfig.SHARED_SERVER_MODEL

    val isSharedBuild: Boolean
        get() = baseUrl.isNotBlank() &&
                !baseUrl.contains("127.0.0.1") &&
                !baseUrl.contains("localhost")

    data class ToolCall(val id: String, val name: String, val argsJson: String)

    data class Final(
        val content: String?,
        val thinking: String?,
        val toolCalls: List<ToolCall>
    )

    // Streaming chat — gọi onEvent cho mỗi delta. Chạy trên worker thread.
    fun chatStream(
        messages: JSONArray,
        tools: JSONArray?,
        onEvent: (AgentEvent) -> Unit
    ): Final {
        val url = URL("$baseUrl/v1/chat/completions")
        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            doOutput = true
            connectTimeout = 30000
            readTimeout = 180000
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Accept", "text/event-stream")
            if (apiKey.isNotBlank()) setRequestProperty("Authorization", "Bearer $apiKey")
        }

        val payload = JSONObject().apply {
            put("model", model)
            put("messages", messages)
            if (tools != null && tools.length() > 0) {
                put("tools", tools)
                put("tool_choice", "auto")
            }
            put("stream", true)
        }

        OutputStreamWriter(conn.outputStream, Charsets.UTF_8).use { it.write(payload.toString()) }

        val code = conn.responseCode
        if (code !in 200..299) {
            val errBody = conn.errorStream?.bufferedReader()?.use { it.readText() } ?: "(empty)"
            onEvent(AgentEvent.Err("HTTP $code: ${errBody.take(400)}"))
            return Final(null, null, emptyList())
        }

        val contentBuf = StringBuilder()
        val thinkBuf = StringBuilder()
        val toolCalls = LinkedHashMap<Int, JSONObject>() // index → partial

        BufferedReader(InputStreamReader(conn.inputStream, Charsets.UTF_8)).use { reader ->
            while (true) {
                val line = reader.readLine() ?: break
                if (line.isEmpty()) continue
                if (!line.startsWith("data:")) continue
                val data = line.removePrefix("data:").trim()
                if (data == "[DONE]") break

                val chunk = try { JSONObject(data) } catch (_: Exception) { continue }
                val choices = chunk.optJSONArray("choices") ?: continue
                if (choices.length() == 0) continue
                val delta = choices.getJSONObject(0).optJSONObject("delta") ?: continue

                // Thinking (deepseek-reasoner / một số provider trả field này)
                val reason = delta.optString("reasoning_content", "")
                if (reason.isNotEmpty()) {
                    thinkBuf.append(reason)
                    onEvent(AgentEvent.ThinkingDelta(reason))
                }

                val content = delta.optString("content", "")
                if (content.isNotEmpty()) {
                    contentBuf.append(content)
                    onEvent(AgentEvent.ContentDelta(content))
                }

                // Tool calls — Gom theo index
                val tcs = delta.optJSONArray("tool_calls")
                if (tcs != null) {
                    for (i in 0 until tcs.length()) {
                        val tc = tcs.getJSONObject(i)
                        val idx = tc.optInt("index", i)
                        val cur = toolCalls.getOrPut(idx) {
                            JSONObject().apply {
                                put("id", tc.optString("id", "call_$idx"))
                                put("type", "function")
                                put("function", JSONObject().apply {
                                    put("name", "")
                                    put("arguments", "")
                                })
                            }
                        }
                        tc.optString("id", "").takeIf { it.isNotBlank() }?.let { cur.put("id", it) }
                        val fn = tc.optJSONObject("function") ?: continue
                        fn.optString("name", "").takeIf { it.isNotBlank() }?.let {
                            cur.getJSONObject("function").put("name", it)
                        }
                        fn.optString("arguments", "").takeIf { it.isNotBlank() }?.let {
                            val old = cur.getJSONObject("function").optString("arguments", "")
                            cur.getJSONObject("function").put("arguments", old + it)
                        }
                    }
                }
            }
        }

        val calls = toolCalls.values.map { tc ->
            val fn = tc.getJSONObject("function")
            ToolCall(
                id = tc.optString("id", "call"),
                name = fn.optString("name", "unknown"),
                argsJson = fn.optString("arguments", "{}")
            )
        }

        // Emit ToolCallReady sau khi args hoàn chỉnh
        calls.forEach { onEvent(AgentEvent.ToolCallReady(it.name, it.argsJson)) }

        return Final(
            content = contentBuf.toString().takeIf { it.isNotBlank() },
            thinking = thinkBuf.toString().takeIf { it.isNotBlank() },
            toolCalls = calls
        )
    }

    // Non-streaming fallback
    fun chat(messages: JSONArray, tools: JSONArray?): Final {
        val url = URL("$baseUrl/v1/chat/completions")
        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            doOutput = true
            connectTimeout = 30000
            readTimeout = 180000
            setRequestProperty("Content-Type", "application/json")
            if (apiKey.isNotBlank()) setRequestProperty("Authorization", "Bearer $apiKey")
        }
        val payload = JSONObject().apply {
            put("model", model)
            put("messages", messages)
            if (tools != null && tools.length() > 0) {
                put("tools", tools); put("tool_choice", "auto")
            }
            put("stream", false)
        }
        OutputStreamWriter(conn.outputStream, Charsets.UTF_8).use { it.write(payload.toString()) }
        val code = conn.responseCode
        val stream = if (code in 200..299) conn.inputStream else conn.errorStream
        val body = BufferedReader(InputStreamReader(stream, Charsets.UTF_8)).use { it.readText() }
        if (code !in 200..299) error("HTTP $code: ${body.take(400)}")

        val json = JSONObject(body)
        val msg = json.getJSONArray("choices").getJSONObject(0).getJSONObject("message")
        val content = if (msg.has("content") && !msg.isNull("content")) msg.getString("content") else null
        val thinking = if (msg.has("reasoning_content") && !msg.isNull("reasoning_content"))
            msg.getString("reasoning_content") else null
        val calls = mutableListOf<ToolCall>()
        if (msg.has("tool_calls") && !msg.isNull("tool_calls")) {
            val arr = msg.getJSONArray("tool_calls")
            for (i in 0 until arr.length()) {
                val tc = arr.getJSONObject(i)
                val fn = tc.getJSONObject("function")
                calls.add(ToolCall(tc.optString("id", "call_$i"), fn.getString("name"), fn.optString("arguments", "{}")))
            }
        }
        return Final(content, thinking, calls)
    }
}
