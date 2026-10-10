package com.anon.filemanager

import android.content.pm.PackageManager
import rikka.shizuku.Shizuku
import java.lang.reflect.Method

object ShizukuShell {

    fun isRunning(): Boolean = try { Shizuku.pingBinder() } catch (e: Throwable) { false }

    fun isGranted(): Boolean = try {
        Shizuku.pingBinder() && (Shizuku.isPreV11() || Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED)
    } catch (e: Throwable) { false }

    fun exec(cmd: String): String {
        if (!isRunning()) return "ERR: Shizuku chưa chạy"
        if (!isGranted()) return "ERR: Chưa cấp quyền Shizuku"
        return try {
            val cls = Class.forName("rikka.shizuku.Shizuku")
            val m: Method = cls.getDeclaredMethod(
                "newProcess",
                Array<String>::class.java,
                Array<String>::class.java,
                Array<String>::class.java
            )
            m.isAccessible = true
            val proc = m.invoke(null, arrayOf("sh", "-c", cmd), null, null) as Process
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
