package com.anon.filemanager.ai

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.webkit.MimeTypeMap
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.anon.filemanager.FileOps
import com.anon.filemanager.R
import com.anon.filemanager.preview.PreviewActivity
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.CancellationException
import java.util.concurrent.Executors

class AiChatActivity : AppCompatActivity() {

    private lateinit var toolbar: MaterialToolbar
    private lateinit var contextBar: TextView
    private lateinit var recycler: RecyclerView
    private lateinit var input: EditText
    private lateinit var send: MaterialButton
    private lateinit var statusBar: View
    private lateinit var statusText: TextView
    private lateinit var statusStop: TextView
    private lateinit var adapter: ChatAdapter

    private val history = JSONArray()
    private val executor = Executors.newSingleThreadExecutor()

    @Volatile private var busy = false
    @Volatile private var stopRequested = false

    private var currentThinkingIdx = -1
    private var currentContentIdx = -1
    private var currentToolIdx = -1
    private var stepCount = 0

    private val prefs by lazy { getSharedPreferences("ai_cfg", MODE_PRIVATE) }

    private val MAX_STEPS = 24

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_ai_chat)

        toolbar = findViewById(R.id.aiToolbar)
        contextBar = findViewById(R.id.aiContext)
        recycler = findViewById(R.id.aiRecycler)
        input = findViewById(R.id.aiInput)
        send = findViewById(R.id.aiSend)
        statusBar = findViewById(R.id.statusBar)
        statusText = findViewById(R.id.statusText)
        statusStop = findViewById(R.id.statusStop)

        toolbar.setNavigationOnClickListener {
            if (busy) confirmExitWhileBusy() else finish()
        }
        toolbar.inflateMenu(R.menu.ai_menu)
        toolbar.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.ai_clear -> { if (!busy) clearChat(); true }
                R.id.ai_settings -> {
                    if (OmniRouteClient.isSharedBuild) {
                        toast("Bản chia sẻ — server do admin cấu hình")
                    } else showSettings()
                    true
                }
                else -> false
            }
        }

        adapter = ChatAdapter(
            mutableListOf(),
            onOpenFile = { path ->
                try {
                    startActivity(Intent(this, PreviewActivity::class.java).putExtra("path", path))
                } catch (_: Exception) { toast("Không mở được: $path") }
            },
            onOpenArtifact = { path ->
                try {
                    startActivity(Intent(this, PreviewActivity::class.java).putExtra("path", path))
                } catch (_: Exception) {}
            }
        )
        recycler.layoutManager = LinearLayoutManager(this)
        recycler.adapter = adapter

        // Config
        if (OmniRouteClient.isSharedBuild) {
            prefs.edit()
                .putString("url", OmniRouteClient.baseUrl)
                .putString("key", OmniRouteClient.apiKey)
                .putString("model", OmniRouteClient.model)
                .apply()
        } else {
            OmniRouteClient.baseUrl = prefs.getString("url", OmniRouteClient.baseUrl) ?: OmniRouteClient.baseUrl
            OmniRouteClient.apiKey = prefs.getString("key", OmniRouteClient.apiKey) ?: OmniRouteClient.apiKey
            OmniRouteClient.model = prefs.getString("model", OmniRouteClient.model) ?: OmniRouteClient.model
        }
        updateContextBar()

        AiTools.onOpenPreview = { path ->
            runOnUiThread {
                try {
                    startActivity(Intent(this, PreviewActivity::class.java).putExtra("path", path))
                } catch (_: Exception) {}
            }
        }

        if (adapter.itemCount == 0) {
            adapter.add(ChatMessage(
                MsgType.ASSISTANT,
                "ANON online. Giao việc cho tôi — tôi sẽ tự chia bước, tự chạy, tự kiểm tra, chỉ dừng khi xong.\n\n" +
                "Ví dụ:\n" +
                "• \"Đọc pp.dat trong Android/data/com.ea.game.pvz2_row/files/No_Backup và cho biết format\"\n" +
                "• \"Tìm tất cả file .log trong Download, tổng hợp nội dung vào summary.txt\"\n" +
                "• \"Tạo một trang HTML hello world trong Download rồi preview\""
            ))
        }

        send.setOnClickListener { sendCurrent() }
        statusStop.setOnClickListener { requestStop() }
    }

    override fun onDestroy() {
        stopRequested = true
        try { AiTools.onOpenPreview = null } catch (_: Throwable) {}
        executor.shutdownNow()
        super.onDestroy()
    }

    // ============================== CONFIG ==============================

    private fun updateContextBar() {
        contextBar.text = "🌐 ${OmniRouteClient.baseUrl}  ·  ${OmniRouteClient.model}"
    }

    private fun showSettings() {
        val dlg = layoutInflater.inflate(R.layout.dialog_ai_settings, null)
        val url: EditText = dlg.findViewById(R.id.cfgUrl)
        val key: EditText = dlg.findViewById(R.id.cfgKey)
        val model: EditText = dlg.findViewById(R.id.cfgModel)
        val test: TextView = dlg.findViewById(R.id.cfgTest)

        url.setText(OmniRouteClient.baseUrl)
        key.setText(OmniRouteClient.apiKey)
        model.setText(OmniRouteClient.model)

        AlertDialog.Builder(this)
            .setTitle("Cấu hình AI")
            .setView(dlg)
            .setPositiveButton("Lưu") { _, _ ->
                OmniRouteClient.baseUrl = url.text.toString().trim().ifBlank { "http://127.0.0.1:20128" }
                OmniRouteClient.apiKey = key.text.toString().trim()
                OmniRouteClient.model = model.text.toString().trim().ifBlank { "gpt-4o-mini" }
                prefs.edit()
                    .putString("url", OmniRouteClient.baseUrl)
                    .putString("key", OmniRouteClient.apiKey)
                    .putString("model", OmniRouteClient.model)
                    .apply()
                updateContextBar()
                toast("✦ Đã lưu cấu hình")
            }
            .setNeutralButton("Test") { _, _ ->
                OmniRouteClient.baseUrl = url.text.toString().trim().ifBlank { "http://127.0.0.1:20128" }
                OmniRouteClient.apiKey = key.text.toString().trim()
                OmniRouteClient.model = model.text.toString().trim().ifBlank { "gpt-4o-mini" }
                executor.execute {
                    val msg = try {
                        val msgs = JSONArray().apply {
                            put(JSONObject().apply { put("role", "user"); put("content", "ping") })
                        }
                        val f = OmniRouteClient.chat(msgs, null)
                        "OK: ${(f.content ?: "").take(80)}"
                    } catch (e: Exception) { "ERR: ${e.message}" }
                    runOnUiThread { test.text = msg }
                }
            }
            .setNegativeButton("Hủy", null)
            .show()
    }

    private fun clearChat() {
        AlertDialog.Builder(this)
            .setTitle("Xóa hội thoại?")
            .setMessage("Xóa toàn bộ tin nhắn và lịch sử.")
            .setPositiveButton("Xóa") { _, _ ->
                adapter.clear()
                while (history.length() > 0) history.remove(0)
                stepCount = 0
                adapter.add(ChatMessage(MsgType.ASSISTANT, "Đã xóa. Bắt đầu lại."))
            }
            .setNegativeButton("Hủy", null).show()
    }

    private fun confirmExitWhileBusy() {
        AlertDialog.Builder(this)
            .setTitle("Đang chạy")
            .setMessage("Agent đang xử lý. Dừng và thoát?")
            .setPositiveButton("Dừng & Thoát") { _, _ -> requestStop(); finish() }
            .setNegativeButton("Ở lại", null).show()
    }

    // ============================== AGENT ==============================

    private fun sendCurrent() {
        if (busy) { toast("Agent đang chạy — nhấn Dừng để hủy"); return }
        val text = input.text.toString().trim()
        if (text.isBlank()) return
        input.setText("")

        adapter.add(ChatMessage(MsgType.USER, text))
        scrollBottom()

        history.put(JSONObject().apply {
            put("role", "user")
            put("content", text)
        })

        startAgent()
    }

    private fun startAgent() {
        busy = true
        stopRequested = false
        stepCount = 0
        statusBar.visibility = View.VISIBLE
        send.isEnabled = false
        setStatus("Khởi động…")
        executor.execute { runAgentLoop() }
    }

    private fun requestStop() {
        if (!busy) return
        stopRequested = true
        setStatus("Đang hủy…")
    }

    private fun setStatus(msg: String) {
        runOnUiThread { statusText.text = msg }
    }

    private fun endAgent(reason: String) {
        busy = false
        runOnUiThread {
            statusBar.visibility = View.GONE
            send.isEnabled = true
            if (reason.isNotBlank()) toast(reason)
        }
    }

    private fun runAgentLoop() {
        // Đảm bảo system prompt ở đầu history
        if (history.length() == 0 || history.getJSONObject(0).optString("role") != "system") {
            val sys = JSONObject().apply {
                put("role", "system")
                put("content", AgentPrompt.system())
            }
            val newArr = JSONArray()
            newArr.put(sys)
            for (i in 0 until history.length()) newArr.put(history.getJSONObject(i))
            while (history.length() > 0) history.remove(0)
            for (i in 0 until newArr.length()) history.put(newArr.getJSONObject(i))
        }

        try {
            var step = 0
            while (step < MAX_STEPS) {
                if (stopRequested) throw CancellationException()
                step++
                stepCount = step
                setStatus("Bước $step — gửi yêu cầu…")

                currentThinkingIdx = -1
                currentContentIdx = -1

                val final = OmniRouteClient.chatStream(
                    history,
                    AiTools.definitions()
                ) { ev -> handleEvent(ev) }

                if (stopRequested) throw CancellationException()

                // Nếu không có tool call → kết thúc
                if (final.toolCalls.isEmpty()) {
                    val content = final.content ?: "(không có nội dung)"
                    // Đảm bảo content đã được hiển thị
                    val idx = currentContentIdx
                    if (idx >= 0) {
                        runOnUiThread {
                            adapter.updateAt(idx) { it.copy(content = content, streaming = false) }
                            scrollBottom()
                        }
                    } else {
                        runOnUiThread {
                            adapter.add(ChatMessage(MsgType.ASSISTANT, content))
                            scrollBottom()
                        }
                    }
                    history.put(JSONObject().apply {
                        put("role", "assistant")
                        put("content", content)
                    })
                    endAgent("")
                    return
                }

                // Có tool calls → ghi assistant message + execute
                val asst = JSONObject().apply {
                    put("role", "assistant")
                    if (!final.content.isNullOrBlank()) put("content", final.content)
                    else put("content", JSONObject.NULL)
                    put("tool_calls", JSONArray().apply {
                        final.toolCalls.forEach { tc ->
                            put(JSONObject().apply {
                                put("id", tc.id)
                                put("type", "function")
                                put("function", JSONObject().apply {
                                    put("name", tc.name)
                                    put("arguments", tc.argsJson)
                                })
                            })
                        }
                    })
                }
                history.put(asst)

                for (tc in final.toolCalls) {
                    if (stopRequested) throw CancellationException()

                    setStatus("Bước $step — ${tc.name}")
                    val toolIdx = runOnUiThreadSync {
                        adapter.add(ChatMessage(
                            MsgType.TOOL_CALL,
                            content = "",
                            toolName = tc.name,
                            toolArgs = tc.argsJson,
                            toolResult = "đang chạy…",
                            toolOk = null
                        ))
                    }
                    scrollBottom()

                    val result = try {
                        AiTools.dispatch(tc.name, tc.argsJson, this)
                    } catch (e: Exception) {
                        "ERR: ${e.message}"
                    }

                    val isErr = result.startsWith("ERR") || result.contains("Permission denied", true)
                    runOnUiThread {
                        adapter.updateAt(toolIdx) { it.copy(toolResult = result, toolOk = !isErr) }
                        scrollBottom()
                    }

                    // Artifact nếu write_file tạo file html/ảnh/svg
                    if (tc.name == "write_file" && !isErr) {
                        try {
                            val args = JSONObject(tc.argsJson)
                            val path = args.optString("path", "")
                            val mime = guessMime(path)
                            if (mime != null) {
                                runOnUiThread {
                                    adapter.add(ChatMessage(
                                        MsgType.ARTIFACT,
                                        content = "",
                                        toolName = File(path).name,
                                        toolArgs = path,
                                        artifactPath = path,
                                        artifactMime = mime
                                    ))
                                    scrollBottom()
                                }
                            }
                        } catch (_: Exception) {}
                    }

                    history.put(JSONObject().apply {
                        put("role", "tool")
                        put("tool_call_id", tc.id)
                        put("content", result)
                    })
                }
            }

            endAgent("Đã chạm giới hạn $MAX_STEPS bước — agent dừng để tránh lặp.")
        } catch (ce: CancellationException) {
            endAgent("Đã hủy.")
        } catch (e: Exception) {
            runOnUiThread {
                adapter.add(ChatMessage(MsgType.ERROR, "ERR: ${e.message}"))
                scrollBottom()
            }
            endAgent("Lỗi.")
        }
    }

    private fun handleEvent(ev: AgentEvent) {
        if (stopRequested) throw CancellationException()
        when (ev) {
            is AgentEvent.ThinkingDelta -> {
                if (currentThinkingIdx < 0) {
                    currentThinkingIdx = runOnUiThreadSync {
                        adapter.add(ChatMessage(MsgType.THINKING, ev.text, streaming = true))
                    }
                    scrollBottom()
                } else {
                    val idx = currentThinkingIdx
                    runOnUiThread {
                        adapter.updateAt(idx) { it.copy(content = it.content + ev.text) }
                    }
                }
            }

            is AgentEvent.ContentDelta -> {
                // Lần delta đầu → chốt thinking (đổi nhãn) và tạo assistant bubble
                if (currentContentIdx < 0) {
                    if (currentThinkingIdx >= 0) {
                        val ti = currentThinkingIdx
                        runOnUiThread { adapter.updateAt(ti) { it.copy(streaming = false) } }
                    }
                    currentContentIdx = runOnUiThreadSync {
                        adapter.add(ChatMessage(MsgType.ASSISTANT, ev.text, streaming = true))
                    }
                    scrollBottom()
                } else {
                    val idx = currentContentIdx
                    runOnUiThread {
                        adapter.updateAt(idx) { it.copy(content = it.content + ev.text) }
                        scrollBottom()
                    }
                }
            }

            is AgentEvent.ToolCallReady -> { /* Đã hiện khi execute; bỏ qua để tránh double */ }

            is AgentEvent.ToolResult -> { /* Adapter đã update khi execute */ }

            is AgentEvent.Phase -> setStatus(ev.label)

            is AgentEvent.Err -> {
                runOnUiThread {
                    adapter.add(ChatMessage(MsgType.ERROR, ev.msg))
                    scrollBottom()
                }
            }

            AgentEvent.Done -> {}
        }
    }

    // ============================== HELPERS ==============================

    private fun <T> runOnUiThreadSync(block: () -> T): T {
        if (android.os.Looper.myLooper() == android.os.Looper.getMainLooper()) {
            return block()
        }
        var result: T? = null
        val latch = java.util.concurrent.CountDownLatch(1)
        runOnUiThread {
            try { result = block() } finally { latch.countDown() }
        }
        latch.await()
        @Suppress("UNCHECKED_CAST")
        return result as T
    }

    private fun guessMime(path: String): String? {
        val ext = File(path).extension.lowercase()
        return when (ext) {
            "html", "htm" -> "text/html"
            "svg" -> "image/svg+xml"
            "png", "jpg", "jpeg", "gif", "webp", "bmp" -> "image/$ext"
            "md", "markdown" -> "text/markdown"
            "pdf" -> "application/pdf"
            "json" -> "application/json"
            "xml" -> "application/xml"
            "yml", "yaml" -> "text/yaml"
            "txt", "log" -> "text/plain"
            "kt", "kts", "java", "py", "sh", "bash", "js", "ts", "cpp", "c", "h",
            "cs", "rs", "go", "rb", "php", "swift", "sql", "gradle", "toml",
            "ini", "cfg", "properties" -> "text/x-code"
            else -> null
        }
    }

    private fun scrollBottom() {
        runOnUiThread {
            recycler.post { recycler.scrollToPosition(adapter.itemCount - 1) }
        }
    }

    private fun toast(m: String) = Toast.makeText(this, m, Toast.LENGTH_SHORT).show()
}
