package com.anon.filemanager

import android.content.Intent
import android.os.Bundle
import android.text.method.ScrollingMovementMethod
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.appbar.MaterialToolbar

class TextEditorActivity : AppCompatActivity() {

    private lateinit var editor: EditText
    private lateinit var info: TextView
    private var filePath: String = ""
    private var wrap = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_text_editor)

        filePath = intent.getStringExtra("path") ?: run { finish(); return }

        val toolbar: MaterialToolbar = findViewById(R.id.editorToolbar)
        editor = findViewById(R.id.editor)
        info = findViewById(R.id.editorInfo)

        toolbar.subtitle = filePath.substringAfterLast('/')
        toolbar.setNavigationOnClickListener { finish() }
        toolbar.inflateMenu(R.menu.editor_menu)
        toolbar.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.action_save -> { save(); true }
                R.id.action_wrap -> {
                    wrap = !wrap
                    editor.setHorizontallyScrolling(!wrap)
                    if (wrap) editor.setMaxLines(Int.MAX_VALUE) else editor.setMaxLines(1)
                    true
                }
                R.id.action_reload -> { load(); true }
                else -> false
            }
        }

        editor.movementMethod = ScrollingMovementMethod()
        load()
    }

    private fun load() {
        val content = FileOps.readText(filePath)
        editor.setText(content)
        info.text = "$filePath  ·  ${content.length} ký tự"
    }

    private fun save() {
        val res = FileOps.writeText(filePath, editor.text.toString())
        if (res.isBlank() || !res.startsWith("ERR")) {
            Toast.makeText(this, "✦ Đã lưu", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, res, Toast.LENGTH_LONG).show()
        }
    }
}
