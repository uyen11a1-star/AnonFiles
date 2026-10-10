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
import android.view.View
import android.webkit.MimeTypeMap
import android.widget.EditText
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.addCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.view.GravityCompat
import androidx.core.view.WindowCompat
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

    // clipboard state
    private var clipPath: String? = null
    private var clipIsCut: Boolean = false

    // selection mode
    private var selectionMode = false

    private val SHIZUKU_REQ = 9001
    private val permLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()) { ensurePermission() }

    private val shizukuListener = Shizuku.OnRequestPermissionResultListener { req, res ->
        if (req == SHIZUKU_REQ) runOnUiThread {
            if (res == PackageManager.PERMISSION_GRANTED) {
                toast("✦ Đã cấp Shizuku"); refresh()
            } else toast("✘ Từ chối Shizuku")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, true)
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

        toolbar.setNavigationOnClickListener {
            if (selectionMode) exitSelection() else drawer.openDrawer(GravityCompat.START)
        }
        showMainMenu()

        navView.setNavigationItemSelectedListener { item ->
            handleNav(item.itemId); drawer.closeDrawers(); true
        }

        onBackPressedDispatcher.addCallback(this) {
            when {
                selectionMode -> exitSelection()
                drawer.isDrawerOpen(GravityCompat.START) -> drawer.closeDrawers()
                else -> goUp()
            }
        }

        Shizuku.addRequestPermissionResultListener(shizukuListener)
        ensurePermission()
    }

    override fun onDestroy() {
        super.onDestroy()
        try { Shizuku.removeRequestPermissionResultListener(shizukuListener) } catch (_: Throwable) {}
    }

    override fun onResume() { super.onResume(); updateStatusBar() }

    // ============================== MENU ==============================

    private fun showMainMenu() {
        toolbar.menu.clear()
        toolbar.inflateMenu(R.menu.toolbar_menu)
        toolbar.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.action_new_folder -> { promptNewFolder(); true }
                R.id.action_new_file -> { promptNewFile(); true }
                R.id.action_select -> { enterSelection(); true }
                R.id.action_paste -> { doPaste(); true }
                R.id.action_sort -> { showSortDialog(); true }
                R.id.action_refresh -> { refresh(); true }
                R.id.action_test_shell -> { runShellDiagnostic(); true }
                else -> false
            }
        }
        toolbar.subtitle = "${adapter.itemCount} mục"
    }

    private fun showSelectionMenu() {
        toolbar.menu.clear()
        toolbar.inflateMenu(R.menu.selection_menu)
        toolbar.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.sel_copy -> { selCopy(false); true }
                R.id.sel_cut -> { selCopy(true); true }
                R.id.sel_delete -> { confirmDelete(adapter.selectedItems()); true }
                R.id.sel_compress -> { promptCompressForSelection(); true }
                R.id.sel_share -> { shareSelected(); true }
                R.id.sel_select_all -> { adapter.selectAll(); updateSelectionSubtitle(); true }
                else -> false
            }
        }
    }

    // ============================== SELECTION ==============================

    private fun enterSelection() {
        selectionMode = true
        adapter.setSelectionMode(true)
        toolbar.setNavigationIcon(android.R.drawable.ic_menu_close_clear_cancel)
        showSelectionMenu()
        updateSelectionSubtitle()
    }

    private fun exitSelection() {
        selectionMode = false
        adapter.setSelectionMode(false)
        toolbar.setNavigationIcon(R.drawable.ic_menu_hamburger)
        showMainMenu()
    }

    private fun updateSelectionSubtitle() {
        toolbar.subtitle = "${adapter.selectedCount()} mục đã chọn"
    }

    private fun selCopy(cut: Boolean) {
        val items = adapter.selectedItems()
        if (items.isEmpty()) { toast("Chưa chọn gì"); return }
        if (items.size == 1) {
            clipPath = items[0].file.absolutePath
            clipIsCut = cut
            toast(if (cut) "✂ Đã cut" else "✦ Đã copy")
        } else {
            // nhiều file → chỉ hỗ trợ copy từng file khi paste; lưu list vào biến phụ
            clipPath = null
            clipIsCut = cut
            toast("Chỉ hỗ trợ copy 1 file — chọn 1 file")
        }
    }

    private fun doPaste() {
        val src = clipPath ?: run { toast("Clipboard trống"); return }
        val res = if (clipIsCut) FileOps.move(src, currentPath) else FileOps.copy(src, currentPath)
        if (res.startsWith("ERR") || res.contains("Permission") || res.contains("denied")) {
            toast(res)
        } else {
            toast(if (clipIsCut) "✦ Đã move" else "✦ Đã paste")
            if (clipIsCut) { clipPath = null; clipIsCut = false }
            refresh()
        }
    }

    // ============================== ITEM INTERACTION ==============================

    private fun onItemClick(item: FileItem) {
        if (selectionMode) {
            adapter.toggleSelect(item)
            if (adapter.selectedCount() == 0) exitSelection() else updateSelectionSubtitle()
            return
        }
        when {
            item.isDirectory -> { currentPath = item.file.absolutePath; refresh() }
            item.isApk -> confirmInstall(item)
            item.isText -> openEditor(item)
            item.isArchive -> confirmUnzip(item)
            else -> FileOps.openWith(this, item.file.absolutePath, mimeOf(item))
        }
    }

    private fun onItemLongClick(item: FileItem) {
        if (selectionMode) {
            adapter.toggleSelect(item)
            updateSelectionSubtitle()
            return
        }
        showItemMenu(item)
    }

    private fun showItemMenu(item: FileItem) {
        val labels = mutableListOf<String>()
        val actions = mutableListOf<() -> Unit>()

        when {
            item.isDirectory -> { labels += "Mở"; actions += { currentPath = item.file.absolutePath; refresh() } }
            item.isApk -> { labels += "Cài đặt"; actions += { confirmInstall(item) } }
            item.isText -> { labels += "Sửa văn bản"; actions += { openEditor(item) } }
            else -> { labels += "Mở bằng..."; actions += { FileOps.openWith(this, item.file.absolutePath, mimeOf(item)) } }
        }

        if (item.isArchive && !item.isDirectory) {
            labels += "Giải nén"; actions += { confirmUnzip(item) }
        }

        labels += "Copy"; actions += { clipPath = item.file.absolutePath; clipIsCut = false; toast("✦ Đã copy") }
        labels += "Cut"; actions += { clipPath = item.file.absolutePath; clipIsCut = true; toast("✂ Đã cut") }
        labels += "Đổi tên"; actions += { promptRename(item) }
        labels += "Nén (zip)"; actions += { promptCompress(listOf(item)) }
        labels += "Nén (.tar.gz)"; actions += { promptCompressTarGz(listOf(item)) }
        labels += "Chia sẻ"; actions += { shareFile(item) }
        labels += "Copy đường dẫn"; actions += { copyPath(item) }
        labels += "Xóa"; actions += { confirmDelete(listOf(item)) }

        AlertDialog.Builder(this)
            .setTitle(item.name)
            .setItems(labels.toTypedArray()) { _, i -> actions[i].invoke() }
            .show()
    }

    // ============================== FILE ACTIONS ==============================

    private fun promptNewFolder() {
        val ed = EditText(this).apply { hint = "Tên thư mục" }
        AlertDialog.Builder(this).setTitle("Thư mục mới").setView(ed)
            .setPositiveButton("Tạo") { _, _ ->
                val n = ed.text.toString().trim()
                if (n.isBlank()) return@setPositiveButton
                val err = FileOps.mkdir(currentPath, n)
                if (err.isBlank() || !err.contains("denied")) refresh() else toast(err)
            }.setNegativeButton("Hủy", null).show()
    }

    private fun promptNewFile() {
        val ed = EditText(this).apply { hint = "tên.txt" }
        AlertDialog.Builder(this).setTitle("File mới").setView(ed)
            .setPositiveButton("Tạo") { _, _ ->
                val n = ed.text.toString().trim()
                if (n.isBlank()) return@setPositiveButton
                val target = File(currentPath, n).absolutePath
                val err = FileOps.writeText(target, "")
                if (err.isBlank() || !err.contains("denied")) refresh() else toast(err)
            }.setNegativeButton("Hủy", null).show()
    }

    private fun promptRename(item: FileItem) {
        val ed = EditText(this).apply { setText(item.name) }
        AlertDialog.Builder(this).setTitle("Đổi tên").setView(ed)
            .setPositiveButton("OK") { _, _ ->
                val n = ed.text.toString().trim()
                if (n.isBlank() || n == item.name) return@setPositiveButton
                val dst = File(item.file.parentFile, n).absolutePath
                val err = FileOps.rename(item.file.absolutePath, dst)
                if (err.isBlank() || !err.contains("denied")) refresh() else toast(err)
            }.setNegativeButton("Hủy", null).show()
    }

    private fun confirmDelete(items: List<FileItem>) {
        if (items.isEmpty()) { toast("Chưa chọn gì"); return }
        val msg = if (items.size == 1) "Xóa: ${items[0].name}" else "Xóa ${items.size} mục?"
        AlertDialog.Builder(this).setTitle("Xác nhận xóa").setMessage(msg)
            .setPositiveButton("Xóa") { _, _ ->
                var errCount = 0
                items.forEach { if (FileOps.delete(it.file.absolutePath).contains("denied")) errCount++ }
                if (errCount > 0) toast("$errCount mục xóa lỗi")
                if (selectionMode) exitSelection() else refresh()
            }.setNegativeButton("Hủy", null).show()
    }

    private fun confirmInstall(item: FileItem) {
        AlertDialog.Builder(this).setTitle("Cài đặt APK")
            .setMessage("Cài ${item.name}?")
            .setPositiveButton("Cài") { _, _ -> FileOps.installApk(this, item.file.absolutePath) }
            .setNegativeButton("Hủy", null).show()
    }

    private fun confirmUnzip(item: FileItem) {
        val outName = item.name.substringBeforeLast('.')
        val outDir = File(currentPath, outName).absolutePath
        AlertDialog.Builder(this).setTitle("Giải nén")
            .setMessage("Giải nén vào:\n$outDir")
            .setPositiveButton("Giải nén") { _, _ ->
                toast("Đang giải nén...")
                val err = FileOps.unzip(item.file.absolutePath, outDir)
                if (err.isBlank()) { toast("✦ Xong"); refresh() } else toast(err)
            }.setNegativeButton("Hủy", null).show()
    }

    private fun promptCompress(items: List<FileItem>) {
        if (items.isEmpty()) { toast("Chưa chọn gì"); return }
        val ed = EditText(this).apply { setText("archive.zip") }
        AlertDialog.Builder(this).setTitle("Nén ZIP").setView(ed)
            .setPositiveButton("Nén") { _, _ ->
                val name = ed.text.toString().trim().ifBlank { "archive.zip" }
                val out = File(currentPath, name).absolutePath
                toast("Đang nén...")
                val err = FileOps.zipTo(items.map { it.file.absolutePath }, out)
                if (err.isBlank()) { toast("✦ Xong"); if (selectionMode) exitSelection() else refresh() } else toast(err)
            }.setNegativeButton("Hủy", null).show()
    }

    private fun promptCompressTarGz(items: List<FileItem>) {
        if (items.isEmpty()) { toast("Chưa chọn gì"); return }
        val ed = EditText(this).apply { setText("archive.tar.gz") }
        AlertDialog.Builder(this).setTitle("Nén TAR.GZ").setView(ed)
            .setPositiveButton("Nén") { _, _ ->
                val name = ed.text.toString().trim().ifBlank { "archive.tar.gz" }
                val out = File(currentPath, name).absolutePath
                toast("Đang nén...")
                val err = FileOps.zipToTarGz(items.map { it.file.absolutePath }, out)
                if (err.isBlank()) { toast("✦ Xong"); if (selectionMode) exitSelection() else refresh() } else toast(err)
            }.setNegativeButton("Hủy", null).show()
    }

    private fun promptCompressForSelection() {
        val items = adapter.selectedItems()
        if (items.isEmpty()) { toast("Chưa chọn gì"); return }
        AlertDialog.Builder(this).setTitle("Nén ${items.size} mục")
            .setItems(arrayOf("ZIP", "TAR.GZ")) { _, w ->
                if (w == 0) promptCompress(items) else promptCompressTarGz(items)
            }.show()
    }

    private fun openEditor(item: FileItem) {
        startActivity(Intent(this, TextEditorActivity::class.java).putExtra("path", item.file.absolutePath))
    }

    private fun shareFile(item: FileItem) {
        try {
            val f = if (FileOps.isProtected(item.file.absolutePath))
                FileOps.copyToCache(this, item.file.absolutePath) ?: run { toast("Copy cache lỗi"); return }
                else File(item.file.absolutePath)
            val uri: Uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", f)
            val i = Intent(Intent.ACTION_SEND).apply {
                type = mimeOf(item)
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(i, "Chia sẻ"))
        } catch (e: Exception) { toast("Share lỗi: ${e.message}") }
    }

    private fun shareSelected() {
        val items = adapter.selectedItems()
        if (items.isEmpty()) { toast("Chưa chọn gì"); return }
        if (items.size == 1) shareFile(items[0]) else toast("Chỉ share 1 file")
    }

    private fun copyPath(item: FileItem) {
        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("path", item.file.absolutePath))
        toast("✦ Đã copy đường dẫn")
    }

    private fun mimeOf(item: FileItem): String {
        if (item.isDirectory) return "*/*"
        val ext = item.extension
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: "*/*"
    }

    // ============================== SORT ==============================

    private enum class SortKey { NAME, SIZE, DATE, TYPE }
    private var sortKey = SortKey.NAME
    private var sortAsc = true

    private fun showSortDialog() {
        val opts = arrayOf("Tên", "Kích thước", "Ngày sửa", "Loại")
        AlertDialog.Builder(this).setTitle("Sắp xếp theo")
            .setItems(opts) { _, i ->
                sortKey = when(i) { 0 -> SortKey.NAME; 1 -> SortKey.SIZE; 2 -> SortKey.DATE; else -> SortKey.TYPE }
                sortAsc = !sortAsc
                refresh()
                toast("${opts[i]} ${if (sortAsc) "↑" else "↓"}")
            }.show()
    }

    private fun applySort(list: List<FileItem>): List<FileItem> {
        val cmp: Comparator<FileItem> = when (sortKey) {
            SortKey.NAME -> compareBy { it.name.lowercase() }
            SortKey.SIZE -> compareBy { it.size }
            SortKey.DATE -> compareBy { it.lastModified }
            SortKey.TYPE -> compareBy { it.extension.ifBlank { "~" } }
        }
        val sorted = if (sortAsc) list.sortedWith(cmp) else list.sortedWith(cmp.reversed())
        // folder luôn trên
        return sorted.sortedByDescending { it.isDirectory }
    }

    // ============================== NAV DRAWER ==============================

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
        if (selectionMode) exitSelection()
        refresh()
    }

    // ============================== PERMISSION ==============================

    private fun isProtected(path: String) = FileOps.isProtected(path)

    private fun hasAllFiles(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) Environment.isExternalStorageManager()
        else ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED

    private fun updateStatusBar() {
        val ok = hasAllFiles()
        val sh = ShizukuShell.isGranted()
        if (ok && sh) { permStatus.visibility = View.GONE; return }
        permStatus.visibility = View.VISIBLE
        val txt = when {
            !ok && !sh -> "Cần quyền Files + Shizuku"
            !ok -> "Cần cấp quyền Files"
            else -> "Cần cấp quyền Shizuku"
        }
        permStatus.text = "⚠ $txt"
        permStatus.setOnClickListener {
            if (!ok) requestAllFiles() else if (!sh) requestShizuku()
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
        toast("Bật 'All files access' cho AnonFiles")
        val i = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
        i.data = Uri.parse("package:$packageName")
        try { permLauncher.launch(i) } catch (e: Exception) {
            startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
        }
    }

    private fun requestShizuku() {
        if (!ShizukuShell.isRunning()) { toast("Shizuku chưa chạy"); return }
        if (ShizukuShell.isGranted()) { toast("✦ Shizuku đã có quyền"); refresh(); return }
        try { Shizuku.requestPermission(SHIZUKU_REQ) } catch (e: Throwable) { toast("Lỗi: ${e.message}") }
    }

    override fun onRequestPermissionsResult(rc: Int, p: Array<out String>, g: IntArray) {
        super.onRequestPermissionsResult(rc, p, g)
        if (g.isNotEmpty() && g.all { it == PackageManager.PERMISSION_GRANTED }) refresh()
    }

    // ============================== REFRESH ==============================

    private fun refresh() {
        updateStatusBar()

        val protected = isProtected(currentPath)
        val items: List<FileItem> = if (protected) {
            if (!ShizukuShell.isGranted()) {
                toast("Cần Shizuku cho Android/data"); emptyList()
            } else listViaShell(currentPath)
        } else {
            val dir = File(currentPath)
            val raw = try { dir.listFiles() } catch (e: Exception) { null } ?: emptyArray()
            raw.map { FileItem(it, it.name, it.isDirectory, it.length(), it.lastModified(), false) }
        }

        val sorted = applySort(items)
        adapter.update(sorted)
        pathText.text = currentPath
        if (!selectionMode) toolbar.subtitle = "${sorted.size} mục"
    }

    private fun listViaShell(path: String): List<FileItem> {
        val realPath = ShizukuShell.translate(path)
        val out = ShizukuShell.exec("ls -la ${ShizukuShell.quote(realPath)} 2>&1")
        Log.d("AnonFiles", "listViaShell path=$realPath out=$out")

        if (out.startsWith("ERR:")) { toast(out); return emptyList() }

        val result = mutableListOf<FileItem>()
        for (raw in out.lines()) {
            val line = raw.trim()
            if (line.isEmpty() || line.startsWith("total ")) continue
            val parts = line.split(Regex("\\s+"))
            if (parts.size < 8) continue
            val perm = parts[0]
            if (perm.length < 10) continue
            if (perm.startsWith("l")) continue
            val name = parts.drop(7).joinToString(" ")
            if (name == "." || name == "..") continue
            val size = parts[4].toLongOrNull() ?: 0L
            val isDir = perm.startsWith("d")
            val lm = try {
                java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.US)
                    .parse("${parts[5]} ${parts[6]}")?.time ?: 0L
            } catch (_: Exception) { 0L }
            result.add(FileItem(File(path, name), name, isDir, size, lm, true))
        }
        return result
    }

    // ============================== HELPERS ==============================

    private fun goUp() {
        if (currentPath == "/storage/emulated/0") return
        val parent = File(currentPath).parentFile?.absolutePath ?: return
        if (!parent.startsWith("/storage/emulated/0")) return
        currentPath = parent
        refresh()
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()

    private fun runShellDiagnostic() {
        val sb = StringBuilder()
        sb.append("Shizuku running: ${ShizukuShell.isRunning()}\n")
        sb.append("Shizuku granted: ${ShizukuShell.isGranted()}\n\n")
        for (c in listOf("id", "ls -d /storage/emulated/0/Android/data", "ls /storage/emulated/0/Android/data | head -5")) {
            sb.append("$ $c\n"); sb.append(ShizukuShell.exec(c).trim()); sb.append("\n---\n")
        }
        val tv = TextView(this).apply {
            text = sb.toString(); setPadding(40,40,40,40); textSize = 11f
            setTextIsSelectable(true); typeface = android.graphics.Typeface.MONOSPACE
        }
        AlertDialog.Builder(this).setTitle("Shizuku Test")
            .setView(ScrollView(this).apply { addView(tv) })
            .setPositiveButton("OK", null).show()
    }
}
