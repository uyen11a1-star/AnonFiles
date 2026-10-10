package com.anon.filemanager

import android.content.pm.PackageManager
import rikka.shizuku.Shizuku
import java.lang.reflect.Method

object ShizukuShell {

    private var cachedMethod: Method? = null

    // Dịch FUSE path -> underlying raw path để bypass scoped storage
    // /storage/emulated/0/... -> /data/media/0/...
    fun translate(path: String): String {
        return path
            .replace("/storage/emulated/0/", "/data/media/0/")
            .replace("/storage/self/primary/", "/data/media/0/")
            .replace("/sdcard/", "/data/media/0/")
    }

    fun isRunning(): Boolean = try { Shizuku.pingBinder() } catch (e: Throwable) { false }

    fun isGranted(): Boolean = try {
        Shizuku.pingBinder() && (Shizuku.isPreV11() || Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED)
    } catch (e: Throwable) { false }

    private fun findMethod(): Method? {
        if (cachedMethod != null) return cachedMethod
        return try {
            val cls = Class.forName("rikka.shizuku.Shizuku")
            // Signature đúng: newProcess(String[] cmd, String[] env, String dir)
            val m = cls.getDeclaredMethod(
                "newProcess",
                Array<String>::class.java,
                Array<String>::class.java,
                String::class.java
            )
            m.isAccessible = true
            cachedMethod = m
            m
        } catch (e: Throwable) {
            // Fallback: thử signature với File
            try {
                val cls = Class.forName("rikka.shizuku.Shizuku")
                val m = cls.getDeclaredMethod(
                    "newProcess",
                    Array<String>::class.java,
                    Array<String>::class.java,
                    java.io.File::class.java
                )
                m.isAccessible = true
                cachedMethod = m
                m
            } catch (e2: Throwable) { null }
        }
    }

    fun exec(cmd: String): String {
        if (!isRunning()) return "ERR: Shizuku chưa chạy"
        if (!isGranted()) return "ERR: Chưa cấp quyền Shizuku"
        val method = findMethod() ?: return "ERR: Shizuku API không tương thích — cần Shizuku v13.1.5+"
        return try {
            val proc = method.invoke(null, arrayOf("sh", "-c", cmd), null, null) as Process
            val out = proc.inputStream.bufferedReader().use { it.readText() }
            val err = proc.errorStream.bufferedReader().use { it.readText() }
            proc.waitFor()
            if (out.isNotBlank()) out else if (err.isNotBlank()) err else ""
        } catch (e: Throwable) {
            "ERR: ${e.javaClass.simpleName}: ${e.message}"
        }
    }

    fun quote(s: String): String = "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\""
}
