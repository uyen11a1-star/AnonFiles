package com.anon.filemanager

import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.appbar.MaterialToolbar
import java.io.File
import java.io.RandomAccessFile
import java.util.Locale

class HexViewActivity : AppCompatActivity() {

    private var filePath: String = ""
    private var offset: Long = 0L
    private val CHUNK = 4096L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_hex_view)

        filePath = intent.getStringExtra("path") ?: run { finish(); return }
        val toolbar: MaterialToolbar = findViewById(R.id.hexToolbar)
        val info: TextView = findViewById(R.id.hexInfo)
        val body: TextView = findViewById(R.id.hexBody)

        toolbar.subtitle = File(filePath).name
        toolbar.setNavigationOnClickListener { finish() }
        toolbar.inflateMenu(R.menu.hex_menu)
        toolbar.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.hex_next -> { offset += CHUNK; render(info, body); true }
                R.id.hex_prev -> { offset = (offset - CHUNK).coerceAtLeast(0); render(info, body); true }
                R.id.hex_start -> { offset = 0; render(info, body); true }
                else -> false
            }
        }

        render(info, body)
    }

    private fun render(info: TextView, body: TextView) {
        val f = File(filePath)
        val total = if (f.exists()) f.length() else -1L
        info.text = "${f.absolutePath}  ·  ${FileProperties.fmtSize(total)}  ·  offset 0x${offset.toString(16)}"

        val sb = StringBuilder()
        try {
            RandomAccessFile(f, "r").use { raf ->
                raf.seek(offset)
                val buf = ByteArray(CHUNK.toInt())
                val read = raf.read(buf)
                if (read <= 0) { body.text = "(hết file)"; return }
                for (i in 0 until read step 16) {
                    sb.append(String.format(Locale.US, "%08X  ", offset + i))
                    val line = StringBuilder()
                    for (j in 0 until 16) {
                        if (i + j < read) {
                            val b = buf[i + j].toInt() and 0xFF
                            sb.append(String.format(Locale.US, "%02X ", b))
                            line.append(if (b in 32..126) b.toChar() else '.')
                        } else sb.append("   ")
                        if (j == 7) sb.append(" ")
                    }
                    sb.append(" |").append(line).append("|\n")
                }
            }
        } catch (e: Exception) { sb.append("Lỗi: ${e.message}") }
        body.text = sb.toString()
    }
}
