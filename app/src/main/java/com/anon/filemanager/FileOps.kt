package com.anon.filemanager

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import androidx.core.content.FileProvider
import net.lingala.zip4j.ZipFile
import net.lingala.zip4j.model.ZipParameters
import net.lingala.zip4j.model.enums.CompressionLevel
import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream
import org.apache.commons.compress.compressors.gzip.GzipCompressorOutputStream
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

object FileOps {

    fun isProtected(path: String): Boolean =
        path.contains("/Android/data") || path.contains("/Android/obb")

    fun delete(path: String): String {
        if (isProtected(path)) {
            val p = ShizukuShell.translate(path)
            return ShizukuShell.exec("rm -rf ${ShizukuShell.quote(p)}")
        }
        return if (File(path).deleteRecursively()) "" else "Không xóa được"
    }

    fun rename(src: String, dst: String): String {
        if (isProtected(src)) {
            val s = ShizukuShell.translate(src)
            val d = ShizukuShell.translate(dst)
            return ShizukuShell.exec("mv ${ShizukuShell.quote(s)} ${ShizukuShell.quote(d)}")
        }
        return if (File(src).renameTo(File(dst))) "" else "Không đổi tên được"
    }

    fun copy(src: String, dstDir: String): String {
        val srcFile = File(src)
        val dst = File(dstDir, srcFile.name).absolutePath
        if (isProtected(src) || isProtected(dst)) {
            val s = ShizukuShell.translate(src)
            val d = ShizukuShell.translate(dst)
            return ShizukuShell.exec("cp -r ${ShizukuShell.quote(s)} ${ShizukuShell.quote(d)} 2>&1")
        }
        return try {
            srcFile.copyRecursively(File(dst), overwrite = true); ""
        } catch (e: Exception) { e.message ?: "Copy lỗi" }
    }

    fun move(src: String, dstDir: String): String {
        val srcFile = File(src)
        val dst = File(dstDir, srcFile.name).absolutePath
        if (isProtected(src) || isProtected(dst)) {
            val s = ShizukuShell.translate(src)
            val d = ShizukuShell.translate(dst)
            return ShizukuShell.exec("mv ${ShizukuShell.quote(s)} ${ShizukuShell.quote(d)} 2>&1")
        }
        return try {
            if (srcFile.renameTo(File(dst))) ""
            else { srcFile.copyRecursively(File(dst), true); srcFile.deleteRecursively(); "" }
        } catch (e: Exception) { e.message ?: "Move lỗi" }
    }

    fun mkdir(parent: String, name: String): String {
        val target = File(parent, name).absolutePath
        if (isProtected(parent)) {
            val t = ShizukuShell.translate(target)
            return ShizukuShell.exec("mkdir -p ${ShizukuShell.quote(t)} 2>&1")
        }
        return if (File(target).mkdirs()) "" else "Không tạo được"
    }

    fun writeText(path: String, content: String): String {
        if (isProtected(path)) {
            // Encode base64 để tránh escape
            val b64 = android.util.Base64.encodeToString(content.toByteArray(Charsets.UTF_8), android.util.Base64.NO_WRAP)
            val p = ShizukuShell.translate(path)
            return ShizukuShell.exec("echo ${ShizukuShell.quote(b64)} | base64 -d > ${ShizukuShell.quote(p)} 2>&1")
        }
        return try { File(path).writeText(content); "" } catch (e: Exception) { e.message ?: "Ghi lỗi" }
    }

    fun readText(path: String): String {
        if (isProtected(path)) {
            val p = ShizukuShell.translate(path)
            return ShizukuShell.exec("base64 ${ShizukuShell.quote(p)} 2>&1")
                .let { if (it.startsWith("ERR:")) "" else runCatching {
                    String(android.util.Base64.decode(it.trim(), android.util.Base64.DEFAULT), Charsets.UTF_8)
                }.getOrDefault("") }
        }
        return try { File(path).readText() } catch (e: Exception) { "" }
    }

    fun zipTo(sources: List<String>, outZip: String): String {
        return try {
            val zip = ZipFile(outZip)
            val params = ZipParameters().apply { compressionLevel = CompressionLevel.NORMAL }
            sources.forEach { s ->
                val f = File(s)
                if (f.isDirectory) zip.addFolder(f, params) else zip.addFile(f, params)
            }
            ""
        } catch (e: Exception) { e.message ?: "Nén lỗi" }
    }

    fun zipToTarGz(sources: List<String>, outTgz: String): String {
        return try {
            FileOutputStream(outTgz).use { fos ->
                GzipCompressorOutputStream(BufferedOutputStream(fos)).use { gz ->
                    TarArchiveOutputStream(gz).use { tar ->
                        tar.setLongFileMode(TarArchiveOutputStream.LONGFILE_POSIX)
                        sources.forEach { s -> addToTar(tar, File(s), File(s).name) }
                    }
                }
            }
            ""
        } catch (e: Exception) { e.message ?: "tar.gz lỗi" }
    }

    private fun addToTar(tar: TarArchiveOutputStream, f: File, entryName: String) {
        if (f.isDirectory) {
            val e = TarArchiveEntry(f, "$entryName/")
            tar.putArchiveEntry(e); tar.closeArchiveEntry()
            f.listFiles()?.forEach { addToTar(tar, it, "$entryName/${it.name}") }
        } else {
            val e = TarArchiveEntry(f, entryName)
            tar.putArchiveEntry(e)
            FileInputStream(f).use { it.copyTo(tar) }
            tar.closeArchiveEntry()
        }
    }

    fun unzip(zipPath: String, outDir: String): String {
        return try {
            val lower = zipPath.lowercase()
            File(outDir).mkdirs()
            when {
                lower.endsWith(".zip") || lower.endsWith(".jar") -> {
                    ZipFile(zipPath).extractAll(outDir)
                }
                lower.endsWith(".tar.gz") || lower.endsWith(".tgz") -> {
                    FileInputStream(zipPath).use { fis ->
                        GzipCompressorInputStream(BufferedInputStream(fis)).use { gz ->
                            TarArchiveInputStream(gz).use { tar ->
                                var e: TarArchiveEntry? = tar.nextTarEntry
                                while (e != null) {
                                    val out = File(outDir, e.name)
                                    if (e.isDirectory) out.mkdirs()
                                    else {
                                        out.parentFile?.mkdirs()
                                        FileOutputStream(out).use { tar.copyTo(it) }
                                    }
                                    e = tar.nextTarEntry
                                }
                            }
                        }
                    }
                }
                else -> return "Định dạng chưa hỗ trợ giải nén: ${zipPath.substringAfterLast('.')}"
            }
            ""
        } catch (e: Exception) { e.message ?: "Giải nén lỗi" }
    }

    // Copy file từ protected path ra temp để mở bằng app khác (APK install, media viewer...)
    fun copyToCache(ctx: Context, srcPath: String): File? {
        return try {
            val tmp = File(ctx.cacheDir, File(srcPath).name)
            if (isProtected(srcPath)) {
                val real = ShizukuShell.translate(srcPath)
                ShizukuShell.exec("cp ${ShizukuShell.quote(real)} ${ShizukuShell.quote(tmp.absolutePath)} 2>&1")
                if (!tmp.exists()) return null
            } else {
                File(srcPath).copyTo(tmp, overwrite = true)
            }
            tmp
        } catch (e: Exception) { null }
    }

    fun installApk(ctx: Context, path: String) {
        try {
            val apkFile = if (isProtected(path)) copyToCache(ctx, path) ?: return
                          else File(path)
            val uri: Uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", apkFile)
            val i = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            ctx.startActivity(i)
        } catch (e: Exception) {
            android.widget.Toast.makeText(ctx, "Cài APK lỗi: ${e.message}", android.widget.Toast.LENGTH_LONG).show()
        }
    }

    fun openWith(ctx: Context, path: String, mime: String) {
        try {
            val f = if (isProtected(path)) copyToCache(ctx, path) ?: return else File(path)
            val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", f)
            val i = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, mime)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            ctx.startActivity(Intent.createChooser(i, "Mở bằng"))
        } catch (e: Exception) {
            android.widget.Toast.makeText(ctx, "Không có app mở file này", android.widget.Toast.LENGTH_LONG).show()
        }
    }
}
