package com.anon.filemanager.ai

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.anon.filemanager.R
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import java.io.File

class LogActivity : AppCompatActivity() {

    private lateinit var body: TextView
    private lateinit var scroll: android.widget.ScrollView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_log)

        val toolbar: MaterialToolbar = findViewById(R.id.logToolbar)
        body = findViewById(R.id.logBody)
        scroll = findViewById(R.id.logScroll)
        val copyBtn: MaterialButton = findViewById(R.id.logCopy)
        val saveBtn: MaterialButton = findViewById(R.id.logSave)
        val clearBtn: MaterialButton = findViewById(R.id.logClear)

        toolbar.subtitle = "${AgentLog.size()} entries"
        toolbar.setNavigationOnClickListener { finish() }

        refresh()

        copyBtn.setOnClickListener {
            val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            cm.setPrimaryClip(ClipData.newPlainText("log", AgentLog.all()))
            toast("Đã copy ${AgentLog.size()} dòng")
        }
        saveBtn.setOnClickListener {
            try {
                val dir = getExternalFilesDir("logs") ?: cacheDir
                if (!dir.exists()) dir.mkdirs()
                val f = File(dir, "agent-log-${System.currentTimeMillis()}.txt")
                f.writeText(AgentLog.all())
                toast("Đã lưu: ${f.absolutePath}")
            } catch (e: Exception) { toast("Lỗi: ${e.message}") }
        }
        clearBtn.setOnClickListener {
            AgentLog.clear()
            refresh()
            toast("Đã xóa")
        }
    }

    private fun refresh() {
        val txt = AgentLog.all()
        body.text = if (txt.isBlank()) "(log trống)" else txt
        scroll.post { scroll.fullScroll(android.view.View.FOCUS_DOWN) }
    }

    private fun toast(m: String) = Toast.makeText(this, m, Toast.LENGTH_SHORT).show()
}
