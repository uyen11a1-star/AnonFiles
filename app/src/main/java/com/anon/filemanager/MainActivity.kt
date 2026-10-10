package com.anon.filemanager

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.addCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.appbar.MaterialToolbar
import java.io.File

class MainActivity : AppCompatActivity() {
    private lateinit var recycler: RecyclerView
    private lateinit var adapter: FileAdapter
    private lateinit var pathText: TextView
    private lateinit var permStatus: TextView
    private lateinit var toolbar: MaterialToolbar
    private var currentDir: File = File(Environment.getExternalStorageDirectory(), "Android/data")

    private val permLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()) { ensurePermission() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        recycler = findViewById(R.id.recycler)
        pathText = findViewById(R.id.pathText)
        permStatus = findViewById(R.id.permStatus)
        toolbar = findViewById(R.id.toolbar)
        adapter = FileAdapter(mutableListOf(), ::onItemClick, ::onItemLongClick)
        recycler.layoutManager = LinearLayoutManager(this)
        recycler.adapter = adapter
        toolbar.setNavigationOnClickListener { goUp() }
        onBackPressedDispatcher.addCallback(this) { goUp() }
        ensurePermission()
    }

    private fun hasAllFiles(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) Environment.isExternalStorageManager()
        else ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED

    private fun updateStatusBar() {
        val ok = hasAllFiles()
        permStatus.text = if (ok) "✦ All Files Access: ĐÃ CẤP"
                          else "✘ All Files Access: CHƯA CẤP — nhấn để cấp"
        permStatus.setTextColor(if (ok) 0xFF6EE7FF.toInt() else 0xFFFF6EC7.toInt())
        permStatus.setOnClickListener { if (!ok) requestAllFiles() }
    }

    private fun ensurePermission() {
        updateStatusBar()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) { requestAllFiles(); return }
        } else {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE), 1)
                return
            }
        }
        refresh()
    }

    private fun requestAllFiles() {
        Toast.makeText(this, "Bật 'All files access' cho AnonFiles", Toast.LENGTH_LONG).show()
        val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
        intent.data = Uri.parse("package:$packageName")
        try { permLauncher.launch(intent) }
        catch (e: Exception) { startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)) }
    }

    override fun onRequestPermissionsResult(rc: Int, p: Array<out String>, g: IntArray) {
        super.onRequestPermissionsResult(rc, p, g)
        if (g.isNotEmpty() && g.all { it == PackageManager.PERMISSION_GRANTED }) refresh()
    }

    override fun onResume() { super.onResume(); updateStatusBar() }

    private fun refresh() {
        updateStatusBar()
        if (!currentDir.exists()) currentDir = Environment.getExternalStorageDirectory()
        val files: List<File> = currentDir.listFiles()?.sortedWith(
            compareByDescending<File> { it.isDirectory }.thenBy { it.name.lowercase() }
        ) ?: emptyList()

        if (files.isEmpty() && currentDir.absolutePath.contains("Android/data")) {
            Toast.makeText(this,
                "Không đọc được Android/data. Kiểm tra All Files Access đã bật chưa.",
                Toast.LENGTH_LONG).show()
        }

        adapter.update(files.map { FileItem(it, it.name, it.isDirectory, it.length(), it.lastModified()) })
        pathText.text = currentDir.absolutePath
        toolbar.subtitle = "${files.size} mục"
    }

    private fun onItemClick(item: FileItem) {
        if (item.isDirectory) {
            if (!item.file.canRead()) {
                Toast.makeText(this, "Không đọc được thư mục này", Toast.LENGTH_SHORT).show()
                return
            }
            currentDir = item.file
            refresh()
        }
    }

    private fun onItemLongClick(item: FileItem) {
        AlertDialog.Builder(this).setTitle(item.name)
            .setItems(arrayOf("Đổi tên", "Xóa", "Copy đường dẫn")) { _, which ->
                when (which) {
                    0 -> renameDialog(item)
                    1 -> deleteDialog(item)
                    2 -> {
                        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        cm.setPrimaryClip(ClipData.newPlainText("path", item.file.absolutePath))
                        Toast.makeText(this, "Đã copy", Toast.LENGTH_SHORT).show()
                    }
                }
            }.show()
    }

    private fun renameDialog(item: FileItem) {
        val edit = EditText(this).apply { setText(item.name) }
        AlertDialog.Builder(this).setTitle("Đổi tên").setView(edit)
            .setPositiveButton("OK") { _, _ ->
                val n = edit.text.toString().trim()
                if (n.isEmpty()) return@setPositiveButton
                if (item.file.renameTo(File(item.file.parentFile, n))) {
                    Toast.makeText(this, "Đã đổi tên", Toast.LENGTH_SHORT).show(); refresh()
                } else Toast.makeText(this, "Không đổi được", Toast.LENGTH_SHORT).show()
            }.setNegativeButton("Hủy", null).show()
    }

    private fun deleteDialog(item: FileItem) {
        AlertDialog.Builder(this).setTitle("Xóa?").setMessage("Xóa vĩnh viễn: ${item.name}")
            .setPositiveButton("Xóa") { _, _ ->
                if (item.file.deleteRecursively()) {
                    Toast.makeText(this, "Đã xóa", Toast.LENGTH_SHORT).show(); refresh()
                } else Toast.makeText(this, "Không xóa được", Toast.LENGTH_SHORT).show()
            }.setNegativeButton("Hủy", null).show()
    }

    private fun goUp() {
        val p = currentDir.parentFile
        if (p != null) { currentDir = p; refresh() }
    }
}
