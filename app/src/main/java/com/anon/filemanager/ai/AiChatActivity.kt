package com.anon.filemanager.ai

import android.content.Context
import android.os.Bundle
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.anon.filemanager.R
import com.anon.filemanager.preview.PreviewActivity
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.Executors

class AiChatActivity : AppCompatActivity() {

    private lateinit var toolbar: MaterialToolbar
    private lateinit var contextBar: TextView
    private lateinit var recycler: RecyclerView
    private lateinit var input: EditText
    private lateinit var send: MaterialButton
    private lateinit var adapter: ChatAdapter

    // Lịch sử tin nhắn gửi lên server (khác với UI list)
    private val history = JSONArray()
    private val executor = Executors.newSingleThreadExecutor()
    private var busy = false

    private val prefs by lazy { getSharedPreferences("ai_cfg", MODE_PRIVATE) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_ai_chat)

        toolbar = findViewById(R.id.aiToolbar)
        contextBar = findViewById(R.id.aiContext)
        recycler = findViewById(R.id.aiRecycler)
        input = findViewById(R.id.aiInput)
        send = findViewById(R.id.aiSend)

        toolbar.setNavigationOnClickListener { finish() }
        toolbar.inflateMenu(R.menu.ai_menu)
        toolbar.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.ai_clear -> { clearChat(); true }
                R.id.ai_settings -> { showSettings(); true }
                else -> false
            }
        }

        adapter = ChatAdapter(mutableListOf()) { path ->
            try {
                startActivity(android.content.Intent(this, PreviewActivity::class.java)
                    .putExtra("path", path))
            } catch (_: Exception) {}
        }
        recycler.layoutManager = LinearLayoutManager(this)
        recycler.adapter = adapter

        // Nạp cấu hình
        OmniRouteClient.baseUrl = prefs.getString("url", "http://127.0.0.1:20128") ?: "http://127.0.0.1:20128"
        OmniRouteClient.apiKey = prefs.getString("key", "") ?: ""
        OmniRouteClient.model = prefs.getString("model", "gpt-4o-mini") ?: "gpt-4o-mini"

        // Context bar hiển thị cấu hình
        updateContextBar()

        // Setup preview callback
        AiTools.onOpenPreview = { path ->
            runOnUiThread {
                try {
                    startActivity(android.content.Intent(this, PreviewActivity::class.java)
                        .putExtra("path", path))
                } catch (_: Exception) {}
            }
        }

        // Lời chào đầu tiên
        if (adapter.itemCount == 0) {
            adapter.add(ChatMessage(false,
                "Xin chào. Tôi có thể đọc, sửa, tạo, xóa file trong máy bạn — kể cả Android/data. Hãy hỏi bằng tiếng Việt tự nhiên, ví dụ:\n\n" +
                "• \"Đọc file /storage/emulated/0/Download/test.txt\"\n" +
                "• \"Đổi tên save.txt thành save_old.txt\"\n" +
                "• \"Tạo thư mục /storage/emulated/0/Download/backup\"\n" +
                "• \"Liệt kê Android/data/com.ea.game.pvz2_row/files/No_Backup\""
            ))
        }

        send.setOnClickListener { sendCurrent() }
    }

    override fun onDestroy() {
        super.onDestroy()
        AiTools.onOpenPreview = null
        executor.shutdown()
    }

    private fun updateContextBar() {
        contextBar.text = "🌐 ${OmniRouteClient.baseUrl}  ·  ${OmniRouteClient.model}"
    }

    private fun clearChat() {
        AlertDialog.Builder(this)
            .setTitle("Xóa hội thoại?")
            .setMessage("Xóa toàn bộ tin nhắn và lịch sử.")
            .setPositiveButton("Xóa") { _, _ ->
                adapter = ChatAdapter(mutableListOf()) { path ->
                    startActivity(android.content.Intent(this, PreviewActivity::class.java).putExtra("path", path))
                }
                recycler.adapter = adapter
                while (history.length() > 0) history.remove(0)
                adapter.add(ChatMessage(false, "Đã xóa. Bắt đầu lại — hỏi gì cũng được."))
            }
            .setNegativeButton("Hủy", null).show()
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

        val d = AlertDialog.Builder(this)
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
                Toast.makeText(this, "✦ Đã lưu cấu hình", Toast.LENGTH_SHORT).show()
            }
            .setNeutralButton("Test kết nối") { _, _ ->
                OmniRouteClient.baseUrl = url.text.toString().trim().ifBlank { "http://127.0.0.1:20128" }
                OmniRouteClient.apiKey = key.text.toString().trim()
                OmniRouteClient.model = model.text.toString().trim().ifBlank { "gpt-4o-mini" }
                executor.execute {
                    val r = try {
                        val msgs = JSONArray().apply {
                            put(JSONObject().apply {
                                put("role", "user")
                                put("content", "ping")
                            })
                        }
                        val reply = OmniRouteClient.chat(msgs, null)
                        "OK: ${(reply.content ?: "").take(80)}"
                    } catch (e: Exception) {
                        "ERR: ${e.message}"
                    }
                    runOnUiThread { test.text = r }
                }
            }
            .setNegativeButton("Hủy", null)
            .create()

        d.show()
    }

    private fun sendCurrent() {
        if (busy) { Toast.makeText(this, "Đang xử lý...", Toast.LENGTH_SHORT).show(); return }
        val text = input.text.toString().trim()
        if (text.isBlank()) return
        input.setText("")

        adapter.add(ChatMessage(true, text))
        scrollToBottom()

        // Đưa vào history
        history.put(JSONObject().apply {
            put("role", "user")
            put("content", text)
        })

        // Thêm tin nhắn "đang xử lý"
        adapter.add(ChatMessage(false, "…"))
        scrollToBottom()

        busy = true
        executor.execute { runLoop() }
    }

    // Tool-calling loop: gửi → nhận → nếu tool_calls thì execute → gửi lại → lặp tối đa 6 vòng
    private fun runLoop() {
        var iteration = 0
        val maxIter = 6
        val toolLog = StringBuilder()

        try {
            while (iteration < maxIter) {
                iteration++
                val reply = OmniRouteClient.chat(history, AiTools.definitions())

                if (reply.toolCalls.isEmpty()) {
                    // Không có tool → trả lời cuối cùng
                    val content = reply.content ?: "(không có nội dung)"
                    history.put(JSONObject().apply {
                        put("role", "assistant")
                        put("content", content)
                    })
                    runOnUiThread {
                        adapter.updateLast(ChatMessage(false, content,
                            toolSummary = if (toolLog.isEmpty()) null else toolLog.toString().trim()))
                        scrollToBottom()
                        busy = false
                    }
                    return
                }

                // Có tool calls → đưa tin nhắn assistant với tool_calls vào history
                val asstMsg = JSONObject().apply {
                    put("role", "assistant")
                    if (!reply.content.isNullOrBlank()) put("content", reply.content)
                    else put("content", JSONObject.NULL)
                    put("tool_calls", JSONArray().apply {
                        reply.toolCalls.forEach { tc ->
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
                history.put(asstMsg)

                // Execute từng tool call
                for (tc in reply.toolCalls) {
                    val result = try { AiTools.dispatch(tc.name, tc.argsJson, this) }
                                 catch (e: Exception) { "ERR: ${e.message}" }

                    toolLog.append("🔧 ${tc.name}  ").append(result.take(80).replace("\n", " ⏎ ")).append("\n")

                    // Cập nhật UI tạm thời
                    val summary = toolLog.toString().trim()
                    runOnUiThread {
                        adapter.updateLast(ChatMessage(false,
                            reply.content ?: "Đang gọi ${reply.toolCalls.size} tool…",
                            toolSummary = summary))
                        scrollToBottom()
                    }

                    history.put(JSONObject().apply {
                        put("role", "tool")
                        put("tool_call_id", tc.id)
                        put("content", result)
                    })
                }
                // Vòng lặp tiếp: gửi lại cho AI với tool results
            }

            // Quá maxIter
            runOnUiThread {
                adapter.updateLast(ChatMessage(false, "(đã vượt quá số vòng tool cho phép)",
                    toolSummary = toolLog.toString().trim()))
                busy = false
            }
        } catch (e: Exception) {
            runOnUiThread {
                adapter.updateLast(ChatMessage(false, "ERR: ${e.message}",
                    toolSummary = if (toolLog.isEmpty()) null else toolLog.toString().trim()))
                busy = false
            }
        }
    }

    private fun scrollToBottom() {
        recycler.post { recycler.scrollToPosition(adapter.itemCount - 1) }
    }
}
