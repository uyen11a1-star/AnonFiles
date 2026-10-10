package com.anon.filemanager

import android.content.pm.PackageManager
import rikka.shizuku.Shizuku

object ShizukuShell {

    fun isRunning(): Boolean = try { Shizuku.pingBinder() } catch (e: Throwable) { false }

    fun isGranted(): Boolean = try {
        Shizuku.pingBinder() && (Shizuku.isPreV11() || Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED)
    } catch (e: Throwable) { false }

    fun exec(cmd: String): String {
        if (!isRunning()) return "ERR: Shizuku chưa chạy"
        if (!isGranted()) return "ERR: Chưa cấp quyền Shizuku"
        return try {
            val proc = Shizuku.newProcess(arrayOf("sh", "-c", cmd), null, null)
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
