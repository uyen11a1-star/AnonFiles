package com.anon.filemanager

import java.io.File
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object FileProperties {

    fun format(path: String): String {
        val f = File(path)
        if (FileOps.isProtected(path)) {
            // Dùng shell để lấy thông tin
            val real = ShizukuShell.translate(path)
            val stat = ShizukuShell.exec("stat -c '%n|%s|%Y|%a|%U|%G' ${ShizukuShell.quote(real)} 2>&1")
            val ls = ShizukuShell.exec("ls -ld ${ShizukuShell.quote(real)} 2>&1")
            val sb = StringBuilder()
            sb.append("Path:     $path\n")
            sb.append("Real:     $real\n\n")
            val line = stat.lines().firstOrNull { it.contains('|') }
            if (line != null) {
                val p = line.split('|')
                if (p.size >= 6) {
                    sb.append("Size:     ${fmtSize(p[1].toLongOrNull() ?: 0)}\n")
                    sb.append("Modified: ${fmtDate((p[2].toLongOrNull() ?: 0L) * 1000)}\n")
                    sb.append("Mode:     ${p[3]}\n")
                    sb.append("Owner:    ${p[4]}:${p[5]}\n\n")
                }
            }
            sb.append("--- ls -ld ---\n$ls\n\n")
            sb.append("--- hashes ---\n")
            sb.append("MD5:      ${shellHash(real, "md5sum")}\n")
            sb.append("SHA-256:  ${shellHash(real, "sha256sum")}\n")
            return sb.toString()
        }

        if (!f.exists()) return "File không tồn tại"
        val sb = StringBuilder()
        sb.append("Path:     ${f.absolutePath}\n")
        sb.append("Type:     ${if (f.isDirectory) "Directory" else "File"}\n")
        sb.append("Size:     ${fmtSize(f.length())} (${f.length()} bytes)\n")
        sb.append("Modified: ${fmtDate(f.lastModified())}\n")
        sb.append("Readable: ${f.canRead()}\n")
        sb.append("Writable: ${f.canWrite()}\n\n")
        if (f.isFile && f.length() < 100L * 1024 * 1024) {
            sb.append("--- hashes ---\n")
            try {
                sb.append("MD5:      ${hashOf(f, "MD5")}\n")
                sb.append("SHA-1:    ${hashOf(f, "SHA-1")}\n")
                sb.append("SHA-256:  ${hashOf(f, "SHA-256")}\n")
            } catch (e: Exception) { sb.append("Hash lỗi: ${e.message}\n") }
        } else if (f.isFile) {
            sb.append("(File >100MB, bỏ qua hash)\n")
        }
        return sb.toString()
    }

    private fun hashOf(f: File, algo: String): String {
        val md = MessageDigest.getInstance(algo)
        f.inputStream().use { input ->
            val buf = ByteArray(8192)
            var n: Int
            while (input.read(buf).also { n = it } > 0) md.update(buf, 0, n)
        }
        return md.digest().joinToString("") { "%02x".format(it) }
    }

    private fun shellHash(realPath: String, tool: String): String {
        val out = ShizukuShell.exec("$tool ${ShizukuShell.quote(realPath)} 2>&1")
        return out.split(Regex("\\s+")).firstOrNull()?.ifBlank { "n/a" } ?: "n/a"
    }

    fun fmtSize(b: Long): String {
        if (b < 1024) return "$b B"
        val kb = b / 1024.0
        if (kb < 1024) return String.format(Locale.US, "%.1f KB", kb)
        val mb = kb / 1024.0
        if (mb < 1024) return String.format(Locale.US, "%.1f MB", mb)
        return String.format(Locale.US, "%.2f GB", mb / 1024.0)
    }

    fun fmtDate(t: Long): String =
        if (t <= 0) "n/a" else SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault()).format(Date(t))
}
