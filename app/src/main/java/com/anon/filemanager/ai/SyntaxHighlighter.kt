package com.anon.filemanager.ai

import android.text.Spannable
import android.text.SpannableString
import android.text.style.ForegroundColorSpan

object SyntaxHighlighter {

    // Bảng màu (dark theme)
    private const val C_KEY = 0xFFFF6EC7.toInt()      // keyword — hồng
    private const val C_STR = 0xFF34D399.toInt()      // string — xanh lá
    private const val C_NUM = 0xFFFBBF24.toInt()      // number — vàng
    private const val C_COM = 0xFF6B7280.toInt()      // comment — xám
    private const val C_FUNC = 0xFF6EE7FF.toInt()     // function — cyan
    private const val C_TAG = 0xFFA855F7.toInt()      // html tag — tím

    private val KEYWORDS = setOf(
        // Generic
        "if","else","for","while","return","break","continue","class","fun","def","function",
        "var","val","let","const","public","private","protected","static","void","new","this",
        "try","catch","finally","throw","throws","import","package","from","as","in","is","not",
        "and","or","null","true","false","nil","None","True","False","self","super","extends",
        "implements","interface","enum","struct","type","namespace","using","async","await","yield",
        // Shell
        "cd","ls","pwd","echo","cat","grep","find","sed","awk","chmod","chown","rm","mv","cp",
        "mkdir","rmdir","touch","exit","export","source","sudo","apt","pkg","pip","npm","git",
        // Android/Kotlin
        "override","open","data","object","companion","sealed","when","object","init","suspend",
        "launch","runBlocking","context","receiver",
    )

    fun highlight(text: String, lang: String): CharSequence {
        val sp = SpannableString(text)
        val l = lang.lowercase()

        // Comments trước để không bị span đè
        when (l) {
            "html","htm","xml","svg" -> highlightHtml(sp, text)
            "json" -> highlightJson(sp, text)
            else -> highlightGeneric(sp, text)
        }
        return sp
    }

    private fun highlightGeneric(sp: Spannable, s: String) {
        // Line comments // và #
        Regex("//[^\\n]*|#[^\\n]*").findAll(s).forEach {
            sp.setSpan(ForegroundColorSpan(C_COM), it.range.first, it.range.last + 1, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        // Block comments /* */
        Regex("/\\*[\\s\\S]*?\\*/").findAll(s).forEach {
            sp.setSpan(ForegroundColorSpan(C_COM), it.range.first, it.range.last + 1, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        // Strings "..." '...' `...`
        Regex("\"(?:[^\"\\\\]|\\\\.)*\"|'(?:[^'\\\\]|\\\\.)*'|`[^`]*`").findAll(s).forEach {
            sp.setSpan(ForegroundColorSpan(C_STR), it.range.first, it.range.last + 1, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        // Numbers
        Regex("\\b\\d+(?:\\.\\d+)?\\b").findAll(s).forEach {
            sp.setSpan(ForegroundColorSpan(C_NUM), it.range.first, it.range.last + 1, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        // Keywords — word boundary
        Regex("\\b[A-Za-z_][A-Za-z0-9_]*\\b").findAll(s).forEach {
            if (it.value in KEYWORDS) {
                sp.setSpan(ForegroundColorSpan(C_KEY), it.range.first, it.range.last + 1, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
        }
        // Function calls name(
        Regex("\\b([A-Za-z_][A-Za-z0-9_]*)\\s*\\(").findAll(s).forEach {
            val g = it.groups[1] ?: return@forEach
            if (g.value !in KEYWORDS) {
                sp.setSpan(ForegroundColorSpan(C_FUNC), g.range.first, g.range.last + 1, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
        }
    }

    private fun highlightHtml(sp: Spannable, s: String) {
        // HTML comments
        Regex("<!--[\\s\\S]*?-->").findAll(s).forEach {
            sp.setSpan(ForegroundColorSpan(C_COM), it.range.first, it.range.last + 1, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        // Tag name
        Regex("</?([A-Za-z][A-Za-z0-9-]*)").findAll(s).forEach {
            val g = it.groups[1] ?: return@forEach
            sp.setSpan(ForegroundColorSpan(C_TAG), g.range.first, g.range.last + 1, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        // Attribute name
        Regex("\\s([A-Za-z-]+)=").findAll(s).forEach {
            val g = it.groups[1] ?: return@forEach
            sp.setSpan(ForegroundColorSpan(C_KEY), g.range.first, g.range.last + 1, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        // Attribute value
        Regex("\"[^\"]*\"").findAll(s).forEach {
            sp.setSpan(ForegroundColorSpan(C_STR), it.range.first, it.range.last + 1, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
    }

    private fun highlightJson(sp: Spannable, s: String) {
        // Key "key":
        Regex("\"([^\"]+)\"\\s*:").findAll(s).forEach {
            val g = it.groups[1] ?: return@forEach
            sp.setSpan(ForegroundColorSpan(C_KEY), g.range.first, g.range.last + 1, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        // String value
        Regex(":\\s*\"([^\"]*)\"").findAll(s).forEach {
            val g = it.groups[1] ?: return@forEach
            sp.setSpan(ForegroundColorSpan(C_STR), g.range.first, g.range.last + 1, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        // Numbers
        Regex(":\\s*(-?\\d+(?:\\.\\d+)?)").findAll(s).forEach {
            val g = it.groups[1] ?: return@forEach
            sp.setSpan(ForegroundColorSpan(C_NUM), g.range.first, g.range.last + 1, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        // Booleans
        Regex("\\b(true|false|null)\\b").findAll(s).forEach {
            sp.setSpan(ForegroundColorSpan(C_NUM), it.range.first, it.range.last + 1, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
    }
}
