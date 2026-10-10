package com.anon.filemanager.ai

import android.webkit.JavascriptInterface

class ArtifactProbe(private val onReport: (ProbeReport) -> Unit) {

    data class ProbeReport(
        val imagesTotal: Int,
        val imagesFailed: Int,
        val failedSources: List<String>,
        val jsErrors: List<String>,
        val bodyHeight: Int,
        val isEmpty: Boolean
    ) {
        fun summary(): String {
            val sb = StringBuilder()
            if (imagesFailed > 0) {
                sb.append("⚠ $imagesFailed/$imagesTotal ảnh không load được:\n")
                failedSources.take(4).forEach { sb.append("  • ${it.take(80)}\n") }
            }
            if (jsErrors.isNotEmpty()) {
                sb.append("⚠ ${jsErrors.size} lỗi JS:\n")
                jsErrors.take(3).forEach { sb.append("  • ${it.take(80)}\n") }
            }
            if (isEmpty) sb.append("⚠ Nội dung render rỗng\n")
            return sb.toString().trim()
        }

        fun hasProblems(): Boolean = imagesFailed > 0 || jsErrors.isNotEmpty() || isEmpty
    }

    @JavascriptInterface
    fun report(json: String) {
        try {
            val obj = org.json.JSONObject(json)
            val total = obj.optInt("imagesTotal", 0)
            val failed = obj.optInt("imagesFailed", 0)
            val srcs = mutableListOf<String>()
            obj.optJSONArray("failedSources")?.let { arr ->
                for (i in 0 until arr.length()) srcs.add(arr.getString(i))
            }
            val errs = mutableListOf<String>()
            obj.optJSONArray("jsErrors")?.let { arr ->
                for (i in 0 until arr.length()) errs.add(arr.getString(i))
            }
            val h = obj.optInt("bodyHeight", 0)
            val empty = obj.optBoolean("isEmpty", false)
            onReport(ProbeReport(total, failed, srcs, errs, h, empty))
        } catch (_: Exception) {}
    }
}
