package com.anon.filemanager

import android.content.pm.PackageManager
import rikka.shizuku.Shizuku
import java.io.File
import java.lang.reflect.Method

object ShizukuShell {

    private var cachedMethod: Method? = null

    fun isRunning(): Boolean = try { Shizuku.pingBinder() } catch (e: Throwable) { false }

    fun isGranted(): Boolean = try {
        Shizuku.pingBinder() && (Shizuku.isPreV11() || Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED)
    } catch (e: Throwable) { false }

    private fun findMethod(): Method? {
        if (cachedMethod != null) return cachedMethod
        return try {
            val cls = Class.forName("rikka.shizuku.Shizuku")
            val sigs = listOf<Array<Class<*>>>(
                arrayOf(Array<String>::class.java, Array<String>::class.java, String::class.java),
                arrayOf(Array<String>::class.java, Array<String>::class.java, File::class.java),
                arrayOf(Array<String>::class.java, Array<String>::class.java, Array<String>::class.java)
            )
            for (sig in sigs) {
                try {
                    val m = cls.getDeclaredMethod("newProcess", *sig)
                    m.isAccessible = true
                    cachedMethod = m
                    return m
                } catch (_: NoSuchMethodException) { }
            }
            null
        } catch (e: Throwable) { null }
    }

    fun exec(cmd: String): String {
        if (!isRunning()) return "ERR: Shizuku chưa chạy"
        if (!isGranted()) return "ERR: Chưa cấp quyền Shizuku"
        val method = findMethod() ?: return "ERR: Shizuku API không tương thích — thử update Shizuku"
        return try {
            val proc = method.invoke(null, arrayOf("sh", "-c", cmd), null, null) as Process
            val out = proc.inputStream.bufferedReader().use { it.readText() }
            val err = proc.errorStream.bufferedReader().use { it.readText() }
            proc.waitFor()
            if (out.isNotBlank()) out else err
        } catch (e: Throwable) {
            "ERR: ${e.javaClass.simpleName}: ${e.message}"
        }
    }

    fun quote(s: String): String = "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\""
}
