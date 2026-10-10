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
import android.util.Log
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.addCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.navigation.NavigationView
import rikka.shizuku.Shizuku
import java.io.File

class MainActivity : AppCompatActivity() {

    private lateinit var drawer: DrawerLayout
    private lateinit var navView: NavigationView
    private lateinit var recycler: RecyclerView
    private lateinit var adapter: FileAdapter
    private lateinit var pathText: TextView
    private lateinit var permStatus: TextView
    private lateinit var toolbar: MaterialToolbar

    private var currentPath: String = Environment.getExternalStorageDirectory().absolutePath

    private val SHIZUKU_REQ = 9001
    private val permLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()) { ensurePermission() }

    private val shizukuListener = Shizuku.OnRequestPermissionResultListener { req, res ->
        if (req == SHIZUKU_REQ) {
            runOnUiThread {
                if (res == PackageManager.PERMISSION_GRANTED) {
                    Toast.makeText(this, "✦ Đã cấp Shizuku", Toast.LENGTH_SHORT).show()
                    refresh()
                } else {
                    Toast.makeText(this, "✘ Từ chối cấp Shizuku", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        drawer = findViewById(R.id.drawerLayout)
        navView = findViewById(R.id.navView)
        recycler = findViewById(R.id.recycler)
        pathText = findViewById(R.id.pathText)
        permStatus = findViewById(R.id.permStatus)
        toolbar = findViewById(R.id.toolbar)

        adapter = FileAdapter(mutableListOf(), ::onItemClick, ::onItemLongClick)
        recycler.layoutManager = LinearLayoutManager(this)
        recycler.adapter = adapter

        toolbar.setNavigationOnClickListener { drawer.openDrawer(GravityCompat.START) }
        toolbar.inflateMenu(R.menu.toolbar_menu)
        toolbar.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.action_new_folder -> { newFolderDialog(); true }
                R.id.action_refresh -> { refresh(); true }
                else -> false
            }
        }

        navView.setNavigationItemSelectedListener { item ->
            handleNav(item.itemId)
            drawer.closeDrawers()
            true
        }

        onBackPressedDispatcher.addCallback(this) {
            if (drawer.isDrawerOpen(GravityCompat.START)) drawer.closeDrawers()
            else goUp()
        }

        Shizuku.addRequestPermissionResultListener(shizukuListener)
        ensurePermission()
    }

    override fun onDestroy() {
        super.onDestroy()
        try { Shizuku.removeRequestPermissionResultListener(shizukuListener) } catch (_: Throwable) {}
    }

    override fun onResume() { super.onResume(); updateStatusBar() }

    private fun handleNav(id: Int) {
        val base = Environment.getExternalStorageDirectory()
        when (id) {
            R.id.nav_home -> currentPath = base.absolutePath
            R.id.nav_download -> currentPath = File(base, "Download").absolutePath
            R.id.nav_pictures -> currentPath = File(base, "Pictures").absolutePath
            R.id.nav_dcim -> currentPath = File(base, "DCIM").absolutePath
            R.id.nav_movies -> currentPath = File(base, "Movies").absolutePath
            R.id.nav_music -> currentPath = File(base, "Music").absolutePath
            R.id.nav_documents -> currentPath = File(base, "Documents").absolutePath
            R.id.nav_data -> currentPath = File(base, "Android/data").absolutePath
            R.id.nav_obb -> currentPath = File(base, "Android/obb").absolutePath
            R.id.nav_shizuku_grant -> { requestShizuku(); return }
        }
        refresh()
    }

    private fun isInProtected(path: String): Boolean =
        path.contains("/Android/data") || path.contains("/Android/obb")

    private fun hasAllFiles(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) Environment.isExternalStorageManager()
        else ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED

    private fun updateStatusBar() {
        val ok = hasAllFiles()
        val sh = ShizukuShell.isGranted()
        val txt = buildString {
            append(if (ok) "✦ Files: OK" else "✘ Files: chưa cấp")
            append("   ·   ")
            append(if (sh) "✦ Shizuku: OK" else "✘ Shizuku: chưa cấp")
        }
        permStatus.text = txt
        permStatus.setTextColor(if (ok && sh) 0xFF6EE7FF.toInt() else 0xFFFF6EC7.toInt())
        permStatus.setOnClickListener {
            if (!ok) requestAllFiles()
            else if (!sh) requestShizuku()
        }
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
        val i = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
        i.data = Uri.parse("package:$packageName")
        try { permLauncher.launch(i) } catch (e: Exception) {
            startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
        }
    }

    private fun requestShizuku() {
        if (!ShizukuShell.isRunning()) {
            Toast.makeText(this, "Shizuku chưa chạy.", Toast.LENGTH_LONG).show()
            return
        }
        if (ShizukuShell.isGranted()) {
            Toast.makeText(this, "✦ Shizuku đã có quyền", Toast.LENGTH_SHORT).show()
            refresh(); return
        }
        try { Shizuku.requestPermission(SHIZUKU_REQ) } catch (e: Throwable) {
            Toast.makeText(this, "Lỗi xin quyền: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    override fun onRequestPermissionsResult(rc: Int, p: Array<out String>, g: IntArray) {
        super.onRequestPermissionsResult(rc, p, g)
        if (g.isNotEmpty() && g.all { it == PackageManager.PERMISSION_GRANTED }) refresh()
    }

    private fun refresh() {
        updateStatusBar()

        val protected = isInProtected(currentPath)
        val items: List<FileItem> = if (protected) {
            if (!ShizukuShell.isGranted()) {
                Toast.makeText(this, "Cần cấp quyền Shizuku để đọc Android/data", Toast.LENGTH_LONG).show()
                emptyList()
            } else listViaShell(currentPath)
        } else {
            val dir = File(currentPath)
            val raw = try { dir.listFiles() } catch (e: Exception) { null } ?: emptyArray()
            raw.sortedWith(compareByDescending<File> { it.isDirectory }.thenBy { it.name.lowercase() })
                .map { FileItem(it, it.name, it.isDirectory, it.length(), it.lastModified()) }
        }

        adapter.update(items)
        pathText.text = currentPath
        toolbar.subtitle = "${items.size} mục"
    }

    private fun listViaShell(path: String): List<FileItem> {
        // Dịch path FUSE -> /data/media/0
        val realPath = ShizukuShell.translate(path)
        Log.d("AnonFiles", "listViaShell: FUSE=$path  REAL=$realPath")

        // Kiểm tra path có tồn tại không
        val check = ShizukuShell.exec("ls -d ${ShizukuShell.quote(realPath)} 2>&1")
        if (!check.contains(realPath)) {
            val msg = "Không truy cập được: $realPath\n$check"
            Log.e("AnonFiles", msg)
            Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
            return emptyList()
        }

        // List với đường dẫn raw
        val out = ShizukuShell.exec("ls -la ${ShizukuShell.quote(realPath)} 2>&1")
        Log.d("AnonFiles", "ls output: $out")

        if (out.startsWith("ERR:")) {
            Toast.makeText(this, out, Toast.LENGTH_LONG).show()
            return emptyList()
        }

        val result = mutableListOf<FileItem>()
        for (raw in out.lines()) {
            val line = raw.trim()
            if (line.isEmpty() || line.startsWith("total ")) continue
            val parts = line.split(Regex("\\s+"), 9)
            if (parts.size < 9) continue
            val perm = parts[0]
            if (perm.length < 10) continue
            val name = parts[8]
            if (name == "." || name == "..") continue
            if (perm.startsWith("l")) continue
            val size = parts[4].toLongOrNull() ?: 0L
            val isDir = perm.startsWith("d")
            result.add(FileItem(File(path, name), name, isDir, size, 0L))
        }
        return result.sortedWith(compareByDescending<FileItem> { it.isDirectory }.thenBy { it.name.lowercase() })
    }

    private fun onItemClick(item: FileItem) {
        if (!item.isDirectory) return
        currentPath = File(currentPath, item.name).absolutePath
        refresh()
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
                if (isInProtected(currentPath)) {
                    val src = ShizukuShell.translate(item.file.absolutePath)
                    val dst = ShizukuShell.translate(File(currentPath, n).absolutePath)
                    val res = ShizukuShell.exec("mv ${ShizukuShell.quote(src)} ${ShizukuShell.quote(dst)}")
                    if (res.startsWith("ERR:")) Toast.makeText(this, res, Toast.LENGTH_LONG).show()
                    else refresh()
                } else {
                    if (item.file.renameTo(File(item.file.parentFile, n))) {
                        Toast.makeText(this, "Đã đổi tên", Toast.LENGTH_SHORT).show(); refresh()
                    } else Toast.makeText(this, "Không đổi được", Toast.LENGTH_SHORT).show()
                }
            }.setNegativeButton("Hủy", null).show()
    }

    private fun deleteDialog(item: FileItem) {
        AlertDialog.Builder(this).setTitle("Xóa?").setMessage("Xóa vĩnh viễn: ${item.name}")
            .setPositiveButton("Xóa") { _, _ ->
                if (isInProtected(currentPath)) {
                    val p = ShizukuShell.translate(item.file.absolutePath)
                    val res = ShizukuShell.exec("rm -rf ${ShizukuShell.quote(p)}")
                    if (res.startsWith("ERR:")) Toast.makeText(this, res, Toast.LENGTH_LONG).show()
                    else { Toast.makeText(this, "Đã xóa", Toast.LENGTH_SHORT).show(); refresh() }
                } else {
                    if (item.file.deleteRecursively()) {
                        Toast.makeText(this, "Đã xóa", Toast.LENGTH_SHORT).show(); refresh()
                    } else Toast.makeText(this, "Không xóa được", Toast.LENGTH_SHORT).show()
                }
            }.setNegativeButton("Hủy", null).show()
    }

    private fun newFolderDialog() {
        val edit = EditText(this).apply { hint = "Tên thư mục" }
        AlertDialog.Builder(this).setTitle("Thư mục mới").setView(edit)
            .setPositiveButton("Tạo") { _, _ ->
                val n = edit.text.toString().trim()
                if (n.isEmpty()) return@setPositiveButton
                val target = File(currentPath, n).absolutePath
                if (isInProtected(currentPath)) {
                    val p = ShizukuShell.translate(target)
                    val res = ShizukuShell.exec("mkdir -p ${ShizukuShell.quote(p)}")
                    if (res.startsWith("ERR:")) Toast.makeText(this, res, Toast.LENGTH_LONG).show()
                    else refresh()
                } else {
                    if (File(target).mkdirs()) refresh()
                    else Toast.makeText(this, "Không tạo được", Toast.LENGTH_SHORT).show()
                }
            }.setNegativeButton("Hủy", null).show()
    }

    private fun goUp() {
        if (currentPath == "/storage/emulated/0") return
        val parent = File(currentPath).parentFile?.absolutePath ?: return
        if (!parent.startsWith("/storage/emulated/0")) return
        currentPath = parent
        refresh()
    }
}
