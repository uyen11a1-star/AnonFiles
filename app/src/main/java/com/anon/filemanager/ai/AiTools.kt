package com.anon.filemanager.ai

import android.content.Context
import com.anon.filemanager.FileItem
import com.anon.filemanager.FileOps
import com.anon.filemanager.ShizukuShell
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

object AiTools {

    fun definitions(): JSONArray {
        fun tool(name: String, desc: String, props: Map<String, Pair<String, String>>, required: List<String>): JSONObject {
            val params = JSONObject().apply {
                put("type", "object")
                val p = JSONObject()
                for ((k, v) in props) {
                    p.put(k, JSONObject().apply {
                        put("type", v.first)
                        put("description", v.second)
                    })
                }
                put("properties", p)
                put("required", JSONArray(required))
            }
            return JSONObject().apply {
                put("type", "function")
                put("function", JSONObject().apply {
                    put("name", name)
                    put("description", desc)
                    put("parameters", params)
                })
            }
        }

        val arr = JSONArray()
        arr.put(tool("list_directory",
            "Liệt kê file/thư mục trong path. Hỗ trợ cả Android/data.",
            mapOf("path" to ("string" to "Đường dẫn tuyệt đối, ví dụ /storage/emulated/0/Download")),
            listOf("path")))
        arr.put(tool("read_file",
            "Đọc nội dung file text. Trả về tối đa max_bytes byte. Với file binary, trả về base64.",
            mapOf(
                "path" to ("string" to "Đường dẫn tuyệt đối"),
                "max_bytes" to ("integer" to "Số byte tối đa, mặc định 200000")
            ),
            listOf("path")))
        arr.put(tool("write_file",
            "Ghi nội dung text vào file (tạo mới hoặc ghi đè). Tự động backup .bak nếu file đã tồn tại.",
            mapOf(
                "path" to ("string" to "Đường dẫn tuyệt đối"),
                "content" to ("string" to "Nội dung text đầy đủ")
            ),
            listOf("path","content")))
        arr.put(tool("create_folder",
            "Tạo thư mục mới.",
            mapOf(
                "parent" to ("string" to "Thư mục cha"),
                "name" to ("string" to "Tên thư mục")
            ),
            listOf("parent","name")))
        arr.put(tool("delete_path",
            "Xóa file hoặc thư mục (đệ quy).",
            mapOf("path" to ("string" to "Đường dẫn cần xóa")),
            listOf("path")))
        arr.put(tool("rename_path",
            "Đổi tên hoặc di chuyển file/thư mục.",
            mapOf(
                "src" to ("string" to "Đường dẫn nguồn"),
                "dst" to ("string" to "Đường dẫn đích")
            ),
            listOf("src","dst")))
        arr.put(tool("search_files",
            "Tìm file theo tên trong thư mục (đệ quy tối đa 4 cấp).",
            mapOf(
                "query" to ("string" to "Từ khóa tên file"),
                "dir" to ("string" to "Thư mục bắt đầu tìm")
            ),
            listOf("query","dir")))
        arr.put(tool("file_info",
            "Thông tin file: size, ngày sửa, MD5, SHA-256.",
            mapOf("path" to ("string" to "Đường dẫn")),
            listOf("path")))
        arr.put(tool("open_preview",
            "Mở màn hình preview nội bộ cho file html/pdf/ảnh/text. Dùng khi user muốn xem file.",
            mapOf("path" to ("string" to "Đường dẫn file")),
            listOf("path")))
        arr.put(tool("run_shell",
            "Chạy lệnh shell qua Shizuku. Dùng để test code, chạy script, kiểm tra môi trường. " +
            "Có timeout 30 giây. Chỉ chạy khi user đã cho phép trong Settings.",
            mapOf(
                "cmd" to ("string" to "Lệnh shell đầy đủ, ví dụ: python3 /storage/emulated/0/Download/fib.py"),
                "cwd" to ("string" to "Working directory (tùy chọn)")
            ),
            listOf("cmd")))
        arr.put(tool("github_push_file",
            "Đẩy 1 file lên GitHub repo (tạo mới hoặc cập nhật). Cần GitHub PAT trong Settings.",
            mapOf(
                "repo" to ("string" to "Tên repo (không cần owner)"),
                "path" to ("string" to "Đường dẫn file trong repo, ví dụ src/main.kt"),
                "local_path" to ("string" to "Đường dẫn file local để đọc nội dung"),
                "message" to ("string" to "Commit message")
            ),
            listOf("repo","path","local_path","message")))
        arr.put(tool("github_create_repo",
            "Tạo repo mới trên GitHub.",
            mapOf(
                "name" to ("string" to "Tên repo"),
                "description" to ("string" to "Mô tả ngắn"),
                "private" to ("boolean" to "true nếu muốn private")
            ),
            listOf("name")))
        arr.put(tool("github_list_files",
            "Liệt kê file trong repo GitHub.",
            mapOf(
                "repo" to ("string" to "Tên repo"),
                "path" to ("string" to "Đường dẫn thư mục trong repo (để trống = root)")
            ),
            listOf("repo")))
        arr.put(tool("github_get_file",
            "Đọc nội dung 1 file trên GitHub.",
            mapOf(
                "repo" to ("string" to "Tên repo"),
                "path" to ("string" to "Đường dẫn file trong repo")
            ),
            listOf("repo","path")))
        arr.put(tool("list_github_repos",
            "Liệt kê các repo GitHub của user (đã cấu hình PAT).",
            emptyMap(),
            emptyList()))
        return arr
    }

    // Cờ từ settings — activity set khi mở chat
    var allowShell: Boolean = false
    var allowGitHub: Boolean = false

    // Callback để activity mở PreviewActivity khi AI gọi open_preview
    var onOpenPreview: ((String) -> Unit)? = null

    fun dispatch(name: String, argsJson: String, ctx: Context): String {
        val args = try { JSONObject(argsJson) } catch (e: Exception) { JSONObject() }
        return when (name) {
            "list_directory" -> listDirectory(args)
            "read_file" -> readFile(args)
            "write_file" -> writeFile(args, ctx)
            "create_folder" -> createFolder(args)
            "delete_path" -> deletePath(args)
            "rename_path" -> renamePath(args)
            "search_files" -> searchFiles(args)
            "file_info" -> com.anon.filemanager.FileProperties.format(args.getString("path"))
            "open_preview" -> {
                val p = args.getString("path")
                onOpenPreview?.invoke(p)
                "OK: đã mở preview cho $p"
            }
            "run_shell" -> runShell(args)
            "github_push_file" -> githubPush(args)
            "github_create_repo" -> githubCreateRepo(args)
            "github_list_files" -> githubListFiles(args)
            "github_get_file" -> githubGetFile(args)
            "list_github_repos" -> listGithubRepos()
            else -> "ERR: tool không hỗ trợ: $name"
        }
    }

    // ============ SHELL ============

    private fun runShell(a: JSONObject): String {
        if (!allowShell) return "ERR: AI chưa được cấp quyền chạy shell. Bật trong Settings."
        if (!ShizukuShell.isGranted()) return "ERR: cần Shizuku"
        val cmd = a.getString("cmd")
        val cwd = a.optString("cwd", "").ifBlank { "/storage/emulated/0" }
        val wrapped = "cd ${ShizukuShell.quote(cwd)} && timeout 30 sh -c ${ShizukuShell.quote(cmd)} 2>&1"
        val out = ShizukuShell.exec(wrapped)
        return if (out.isBlank()) "(không có output)" else out.take(8000)
    }

    // ============ GITHUB ============

    private fun githubPush(a: JSONObject): String {
        if (!allowGitHub) return "ERR: AI chưa được cấp quyền GitHub. Bật trong Settings."
        val repo = a.getString("repo")
        val path = a.getString("path")
        val localPath = a.getString("local_path")
        val msg = a.getString("message")
        val content = try {
            if (FileOps.isProtected(localPath)) {
                val real = ShizukuShell.translate(localPath)
                val b64 = ShizukuShell.exec("base64 ${ShizukuShell.quote(real)} 2>&1")
                String(android.util.Base64.decode(b64.trim(), android.util.Base64.DEFAULT), Charsets.UTF_8)
            } else File(localPath).readText()
        } catch (e: Exception) { return "ERR: đọc file local thất bại: ${e.message}" }

        val r = GitHubClient.pushFile(repo, path, content, msg)
        return if (r.ok) "OK: đã push $path → $repo ($msg)"
               else "ERR: GitHub API ${r.status}: ${r.body.take(300)}"
    }

    private fun githubCreateRepo(a: JSONObject): String {
        if (!allowGitHub) return "ERR: chưa bật GitHub"
        val r = GitHubClient.createRepo(
            a.getString("name"),
            a.optString("description", ""),
            a.optBoolean("private", false)
        )
        return if (r.ok) "OK: đã tạo repo ${a.getString("name")}"
               else "ERR: ${r.status}: ${r.body.take(300)}"
    }

    private fun githubListFiles(a: JSONObject): String {
        if (!allowGitHub) return "ERR: chưa bật GitHub"
        val r = GitHubClient.listFiles(a.getString("repo"), a.optString("path", ""))
        if (!r.ok) return "ERR: ${r.status}: ${r.body.take(300)}"
        return try {
            val arr = org.json.JSONArray(r.body)
            (0 until arr.length()).joinToString("\n") {
                val o = arr.getJSONObject(it)
                "${o.getString("type")}  ${o.optLong("size", 0)}  ${o.getString("path")}"
            }.ifBlank { "(trống)" }
        } catch (e: Exception) { r.body.take(800) }
    }

    private fun githubGetFile(a: JSONObject): String {
        if (!allowGitHub) return "ERR: chưa bật GitHub"
        val r = GitHubClient.getFile(a.getString("repo"), a.getString("path"))
        return if (r.ok) r.body.take(8000) else "ERR: ${r.status}: ${r.body.take(300)}"
    }

    private fun listGithubRepos(): String {
        if (!allowGitHub) return "ERR: chưa bật GitHub"
        val names = GitHubClient.listRepoNames()
        return if (names.isEmpty()) "(không có repo hoặc PAT chưa đúng)" else names.joinToString("\n")
    }

    private fun listDirectory(a: JSONObject): String {
        val path = a.getString("path")
        val isProt = FileOps.isProtected(path)
        if (isProt) {
            if (!ShizukuShell.isGranted()) return "ERR: cần Shizuku cho $path"
            val real = ShizukuShell.translate(path)
            val out = ShizukuShell.exec("ls -la ${ShizukuShell.quote(real)} 2>&1")
            if (out.startsWith("ERR:")) return out
            val lines = out.lines().filter { it.isNotBlank() && !it.startsWith("total ") }
            val items = mutableListOf<String>()
            for (l in lines) {
                val parts = l.trim().split(Regex("\\s+"))
                if (parts.size < 8) continue
                if (parts[0].startsWith("l")) continue
                val n = parts.drop(7).joinToString(" ")
                if (n == "." || n == "..") continue
                val isDir = parts[0].startsWith("d")
                val size = parts[4].toLongOrNull() ?: 0L
                items.add("${if (isDir) "d" else "f"} ${FilePropertiesFmt.size(size)}  $n")
            }
            return items.joinToString("\n").ifBlank { "(trống)" }
        }
        val dir = File(path)
        if (!dir.exists()) return "ERR: không tồn tại: $path"
        if (!dir.isDirectory) return "ERR: không phải thư mục: $path"
        val list = dir.listFiles()?.sortedWith(compareByDescending<File> { it.isDirectory }.thenBy { it.name.lowercase() }) ?: emptyList()
        return list.take(300).joinToString("\n") {
            "${if (it.isDirectory) "d" else "f"} ${FilePropertiesFmt.size(it.length())}  ${it.name}"
        }.ifBlank { "(trống)" }
    }

    private fun readFile(a: JSONObject): String {
        val path = a.getString("path")
        val maxBytes = a.optInt("max_bytes", 200_000)
        val text = if (FileOps.isProtected(path)) {
            val real = ShizukuShell.translate(path)
            val out = ShizukuShell.exec("head -c $maxBytes ${ShizukuShell.quote(real)} | base64 2>&1")
            if (out.startsWith("ERR:")) return out
            try { String(android.util.Base64.decode(out.trim(), android.util.Base64.DEFAULT), Charsets.UTF_8) }
            catch (e: Exception) { out }
        } else {
            val f = File(path)
            if (!f.exists()) return "ERR: không tồn tại: $path"
            if (f.length() > maxBytes) {
                val buf = ByteArray(maxBytes)
                f.inputStream().use { it.read(buf) }
                String(buf, Charsets.UTF_8) + "\n\n[...cắt bớt, file ${FilePropertiesFmt.size(f.length())}]"
            } else f.readText()
        }
        return text
    }

    private fun writeFile(a: JSONObject, ctx: Context): String {
        val path = a.getString("path")
        val content = a.getString("content")
        // backup nếu đã tồn tại
        try {
            if (FileOps.isProtected(path)) {
                val real = ShizukuShell.translate(path)
                ShizukuShell.exec("cp ${ShizukuShell.quote(real)} ${ShizukuShell.quote("$real.bak")} 2>&1")
            } else {
                val f = File(path)
                if (f.exists()) f.copyTo(File(path + ".bak"), overwrite = true)
            }
        } catch (_: Exception) {}
        val err = FileOps.writeText(path, content)
        return if (err.isBlank() || !err.contains("denied")) "OK: ghi ${content.length} ký tự vào $path (backup .bak nếu tồn tại)" else err
    }

    private fun createFolder(a: JSONObject): String {
        val err = FileOps.mkdir(a.getString("parent"), a.getString("name"))
        return if (err.isBlank() || !err.contains("denied")) "OK" else err
    }

    private fun deletePath(a: JSONObject): String {
        val err = FileOps.delete(a.getString("path"))
        return if (err.isBlank() || !err.contains("denied")) "OK" else err
    }

    private fun renamePath(a: JSONObject): String {
        val err = FileOps.rename(a.getString("src"), a.getString("dst"))
        return if (err.isBlank() || !err.contains("denied")) "OK" else err
    }

    private fun searchFiles(a: JSONObject): String {
        val q = a.getString("query").lowercase()
        val dir = a.getString("dir")
        val out = mutableListOf<String>()
        if (FileOps.isProtected(dir)) {
            val real = ShizukuShell.translate(dir)
            val res = ShizukuShell.exec("find ${ShizukuShell.quote(real)} -maxdepth 4 -iname \"*${q.replace("\"","\\\"")}*\" 2>/dev/null | head -200")
            res.lines().filter { it.isNotBlank() }.forEach {
                out.add(it.replace("/data/media/0/", "/storage/emulated/0/"))
            }
        } else {
            searchRec(File(dir), q, out, 0)
        }
        return if (out.isEmpty()) "(không tìm thấy)" else out.joinToString("\n")
    }

    private fun searchRec(dir: File, q: String, out: MutableList<String>, depth: Int) {
        if (depth > 4 || out.size > 200) return
        val list = try { dir.listFiles() } catch (_: Exception) { null } ?: return
        for (f in list) {
            if (f.name.lowercase().contains(q)) out.add(f.absolutePath)
            if (f.isDirectory) searchRec(f, q, out, depth + 1)
        }
    }
}

private object FilePropertiesFmt {
    fun size(b: Long): String {
        if (b < 1024) return "$b B"
        val kb = b / 1024.0
        if (kb < 1024) return String.format(java.util.Locale.US, "%.1f KB", kb)
        val mb = kb / 1024.0
        if (mb < 1024) return String.format(java.util.Locale.US, "%.1f MB", mb)
        return String.format(java.util.Locale.US, "%.2f GB", mb / 1024.0)
    }
}
