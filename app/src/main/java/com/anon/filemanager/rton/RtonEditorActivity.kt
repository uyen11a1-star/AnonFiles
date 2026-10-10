package com.anon.filemanager.rton

import android.os.Bundle
import android.text.InputType
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.anon.filemanager.FileOps
import com.anon.filemanager.R
import com.google.android.material.appbar.MaterialToolbar
import java.io.File

class RtonEditorActivity : AppCompatActivity() {

    private lateinit var adapter: RtonAdapter
    private lateinit var info: TextView
    private var filePath: String = ""
    private var root: RtonValue = RtonValue.None
    private var dirty = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_rton_editor)

        filePath = intent.getStringExtra("path") ?: run { finish(); return }
        val toolbar: MaterialToolbar = findViewById(R.id.rtonToolbar)
        info = findViewById(R.id.rtonInfo)
        val recycler: RecyclerView = findViewById(R.id.rtonRecycler)

        toolbar.subtitle = File(filePath).name
        toolbar.setNavigationOnClickListener {
            if (dirty) confirmDiscard() else finish()
        }
        toolbar.inflateMenu(R.menu.rton_menu)
        toolbar.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.rton_save -> { save(); true }
                R.id.rton_search -> { promptSearch(); true }
                R.id.rton_reload -> { load(); true }
                R.id.rton_export_json -> { exportJson(); true }
                else -> false
            }
        }

        adapter = RtonAdapter(mutableListOf(), ::onNodeClick)
        recycler.layoutManager = LinearLayoutManager(this)
        recycler.adapter = adapter

        load()
    }

    private fun load() {
        try {
            val tmp = copyToLocalCache()
            root = RtonReader.read(tmp)
            val nodes = RtonFlatten.flatten(root)
            adapter.update(nodes)
            info.text = "$filePath  ·  ${nodes.size} node  ·  RTON v1"
            dirty = false
        } catch (e: Exception) {
            Toast.makeText(this, "Đọc RTON lỗi: ${e.message}", Toast.LENGTH_LONG).show()
            info.text = "Lỗi: ${e.message}"
        }
    }

    private fun copyToLocalCache(): File {
        // File trong Android/data phải copy ra cache để đọc trực tiếp
        return if (FileOps.isProtected(filePath)) {
            val f = File(cacheDir, File(filePath).name + ".rton")
            val real = com.anon.filemanager.ShizukuShell.translate(filePath)
            com.anon.filemanager.ShizukuShell.exec("cp ${com.anon.filemanager.ShizukuShell.quote(real)} ${com.anon.filemanager.ShizukuShell.quote(f.absolutePath)} 2>&1")
            if (!f.exists() || f.length() == 0L) error("Copy cache thất bại")
            f
        } else File(filePath)
    }

    private fun onNodeClick(n: RtonNode) {
        if (!n.isLeaf) {
            // Nhấn vào obj/array → mở dialog nhỏ xem giá trị con cháu
            val children = flattenChildren(n)
            AlertDialog.Builder(this)
                .setTitle("${n.displayName}  ${n.typeTag}")
                .setMessage(children)
                .setPositiveButton("OK", null)
                .show()
            return
        }
        editLeaf(n)
    }

    private fun flattenChildren(n: RtonNode): String {
        val sb = StringBuilder()
        when (val v = n.value) {
            is RtonValue.Obj -> v.entries.forEach { (k, vv) -> sb.append("$k = ${preview(vv)}\n") }
            is RtonValue.Arr -> v.items.forEachIndexed { i, vv -> sb.append("[$i] = ${preview(vv)}\n") }
            else -> sb.append("—")
        }
        return sb.toString().ifBlank { "(rỗng)" }
    }

    private fun preview(v: RtonValue): String = when (v) {
        is RtonValue.None -> "none"
        is RtonValue.Bool -> v.value.toString()
        is RtonValue.Int -> v.value.toString()
        is RtonValue.Float -> v.value.toString()
        is RtonValue.Str -> "\"${v.value.take(40)}\""
        is RtonValue.Arr -> "array[${v.items.size}]"
        is RtonValue.Obj -> "obj{${v.entries.size}}"
    }

    private fun editLeaf(n: RtonNode) {
        val ed = EditText(this)
        when (val v = n.value) {
            is RtonValue.Int -> { ed.setText(v.value.toString()); ed.inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_SIGNED }
            is RtonValue.Float -> { ed.setText(v.value.toString()); ed.inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL or InputType.TYPE_NUMBER_FLAG_SIGNED }
            is RtonValue.Bool -> { ed.setText(v.value.toString()); ed.hint = "true / false" }
            is RtonValue.Str -> { ed.setText(v.value); ed.inputType = InputType.TYPE_CLASS_TEXT }
            else -> { ed.setText(""); ed.hint = "không sửa được" }
        }
        AlertDialog.Builder(this)
            .setTitle("Sửa: ${n.path.joinToString(".")}  (${n.typeTag})")
            .setView(ed)
            .setPositiveButton("OK") { _, _ ->
                val txt = ed.text.toString()
                val newVal = when (val v = n.value) {
                    is RtonValue.Int -> RtonValue.Int(txt.toLongOrNull() ?: v.value)
                    is RtonValue.Float -> RtonValue.Float(txt.toFloatOrNull() ?: v.value)
                    is RtonValue.Bool -> RtonValue.Bool(txt.equals("true", true) || txt == "1")
                    is RtonValue.Str -> RtonValue.Str(txt)
                    else -> v
                }
                root = RtonFlatten.setValue(root, n.path, newVal)
                dirty = true
                adapter.update(RtonFlatten.flatten(root))
                Toast.makeText(this, "Đã sửa (chưa lưu)", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Hủy", null)
            .show()
    }

    private fun save() {
        if (!dirty) { Toast.makeText(this, "Không có thay đổi", Toast.LENGTH_SHORT).show(); return }
        try {
            val tmp = File(cacheDir, "out_" + File(filePath).name)
            RtonWriter.write(tmp, root)
            if (FileOps.isProtected(filePath)) {
                // Copy ngược lại qua shell
                val real = com.anon.filemanager.ShizukuShell.translate(filePath)
                // Backup
                com.anon.filemanager.ShizukuShell.exec("cp ${com.anon.filemanager.ShizukuShell.quote(real)} ${com.anon.filemanager.ShizukuShell.quote("$real.bak")} 2>&1")
                val res = com.anon.filemanager.ShizukuShell.exec("cp ${com.anon.filemanager.ShizukuShell.quote(tmp.absolutePath)} ${com.anon.filemanager.ShizukuShell.quote(real)} 2>&1")
                if (res.contains("denied") || res.startsWith("ERR")) error(res)
            } else {
                val bak = File(filePath + ".bak")
                File(filePath).copyTo(bak, overwrite = true)
                tmp.copyTo(File(filePath), overwrite = true)
            }
            dirty = false
            Toast.makeText(this, "✦ Đã lưu (backup .bak đã tạo)", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Lưu lỗi: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun promptSearch() {
        val ed = EditText(this).apply { hint = "tên key" }
        AlertDialog.Builder(this).setTitle("Tìm key").setView(ed)
            .setPositiveButton("Tìm") { _, _ ->
                val q = ed.text.toString().trim().lowercase()
                if (q.isBlank()) return@setPositiveButton
                val nodes = RtonFlatten.flatten(root).filter {
                    it.key.lowercase().contains(q) || it.path.joinToString(".").lowercase().contains(q)
                }
                adapter.update(nodes)
                info.text = "Tìm '$q': ${nodes.size} kết quả (nhấn Reload để quay lại)"
            }
            .setNegativeButton("Hủy", null).show()
    }

    private fun exportJson() {
        try {
            val json = toJson(root, 0)
            val out = File(cacheDir, File(filePath).name + ".json")
            out.writeText(json)
            Toast.makeText(this, "Đã export: ${out.absolutePath}", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Export lỗi: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun toJson(v: RtonValue, indent: Int): String {
        val pad = "  ".repeat(indent)
        return when (v) {
            is RtonValue.None -> "null"
            is RtonValue.Bool -> v.value.toString()
            is RtonValue.Int -> v.value.toString()
            is RtonValue.Float -> v.value.toString()
            is RtonValue.Str -> "\"" + v.value.replace("\\", "\\\\").replace("\"", "\\\"") + "\""
            is RtonValue.Arr -> if (v.items.isEmpty()) "[]" else
                v.items.joinToString(",\n", "[\n", "\n$pad]") { "$pad  ${toJson(it, indent + 1)}" }
            is RtonValue.Obj -> if (v.entries.isEmpty()) "{}" else
                v.entries.entries.joinToString(",\n", "{\n", "\n$pad}") { (k, vv) ->
                    "$pad  \"$k\": ${toJson(vv, indent + 1)}"
                }
        }
    }

    private fun confirmDiscard() {
        AlertDialog.Builder(this)
            .setTitle("Chưa lưu")
            .setMessage("Có thay đổi chưa lưu. Thoát?")
            .setPositiveButton("Thoát") { _, _ -> finish() }
            .setNegativeButton("Ở lại", null)
            .show()
    }
}
