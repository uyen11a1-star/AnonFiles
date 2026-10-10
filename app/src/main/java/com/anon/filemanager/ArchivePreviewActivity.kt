package com.anon.filemanager

import android.os.Bundle
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.appbar.MaterialToolbar
import java.io.File
import java.util.Locale

class ArchivePreviewActivity : AppCompatActivity() {

    private lateinit var adapter: ArchiveAdapter
    private lateinit var info: TextView
    private var archivePath: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_archive_preview)

        archivePath = intent.getStringExtra("path") ?: run { finish(); return }
        val toolbar: MaterialToolbar = findViewById(R.id.archiveToolbar)
        info = findViewById(R.id.archiveInfo)
        val recycler: RecyclerView = findViewById(R.id.archiveRecycler)

        toolbar.subtitle = File(archivePath).name
        toolbar.setNavigationOnClickListener { finish() }

        adapter = ArchiveAdapter(mutableListOf(), ::onEntryClick)
        recycler.layoutManager = LinearLayoutManager(this)
        recycler.adapter = adapter

        loadEntries()
    }

    private fun loadEntries() {
        val entries = ArchiveReader.listEntries(archivePath)
        adapter.update(entries)
        val totalSize = entries.sumOf { it.size }
        val totalComp = entries.sumOf { it.compressedSize }
        val ratio = if (totalSize > 0) String.format(Locale.US, "%.1f%%", 100.0 * totalComp / totalSize) else "-"
        info.text = "${entries.size} mục  ·  gốc: ${fmtSize(totalSize)}  ·  nén: ${fmtSize(totalComp)}  ·  tỷ lệ: $ratio"
    }

    private fun onEntryClick(e: ArchiveEntryItem) {
        if (e.isDirectory) return
        AlertDialog.Builder(this)
            .setTitle(e.name)
            .setMessage("Size: ${fmtSize(e.size)}\nNén: ${fmtSize(e.compressedSize)}\n\nXem nhanh?")
            .setPositiveButton("Xem") { _, _ -> quickView(e) }
            .setNegativeButton("Hủy", null)
            .show()
    }

    private fun quickView(e: ArchiveEntryItem) {
        val tmp = File(cacheDir, e.name.substringAfterLast('/'))
        if (!ArchiveReader.extractEntry(archivePath, e.name, tmp)) {
            toast("Không đọc được entry"); return
        }
        val ext = e.name.substringAfterLast('.', "").lowercase()
        when {
            ext in setOf("txt","log","json","xml","html","css","js","md","yml","yaml","ini","cfg","kt","java","py","sh") ->
                startActivity(android.content.Intent(this, TextEditorActivity::class.java).putExtra("path", tmp.absolutePath))
            else -> {
                toast("Giải nén ra ${tmp.absolutePath}")
                // share cho app khác
                FileOps.openWith(this, tmp.absolutePath, "*/*")
            }
        }
    }

    private fun fmtSize(b: Long): String {
        if (b < 1024) return "$b B"
        val kb = b / 1024.0
        if (kb < 1024) return String.format(Locale.US, "%.1f KB", kb)
        val mb = kb / 1024.0
        if (mb < 1024) return String.format(Locale.US, "%.1f MB", mb)
        return String.format(Locale.US, "%.2f GB", mb / 1024.0)
    }

    private fun toast(m: String) = Toast.makeText(this, m, Toast.LENGTH_SHORT).show()
}
