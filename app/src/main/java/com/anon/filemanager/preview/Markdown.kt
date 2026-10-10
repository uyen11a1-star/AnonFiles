package com.anon.filemanager.preview

object Markdown {

    fun toHtml(md: String): String {
        val lines = md.lines()
        val sb = StringBuilder()
        var inCode = false
        var inList = false

        for (raw in lines) {
            val line = raw
            if (line.trimStart().startsWith("```")) {
                if (!inCode) { sb.append("<pre><code>"); inCode = true }
                else { sb.append("</code></pre>"); inCode = false }
                continue
            }
            if (inCode) { sb.append(escape(line)).append('\n'); continue }

            if (line.matches(Regex("^\\s*[-*+]\\s+.*"))) {
                if (!inList) { sb.append("<ul>"); inList = true }
                sb.append("<li>").append(inline(line.replaceFirst(Regex("^\\s*[-*+]\\s+"), ""))).append("</li>")
                continue
            } else if (inList) { sb.append("</ul>"); inList = false }

            when {
                line.startsWith("###### ") -> sb.append("<h6>").append(inline(line.drop(7))).append("</h6>")
                line.startsWith("##### ") -> sb.append("<h5>").append(inline(line.drop(6))).append("</h5>")
                line.startsWith("#### ") -> sb.append("<h4>").append(inline(line.drop(5))).append("</h4>")
                line.startsWith("### ") -> sb.append("<h3>").append(inline(line.drop(4))).append("</h3>")
                line.startsWith("## ") -> sb.append("<h2>").append(inline(line.drop(3))).append("</h2>")
                line.startsWith("# ") -> sb.append("<h1>").append(inline(line.drop(2))).append("</h1>")
                line.startsWith("> ") -> sb.append("<blockquote>").append(inline(line.drop(2))).append("</blockquote>")
                line.matches(Regex("^[-*_]{3,}\\s*$")) -> sb.append("<hr/>")
                line.isBlank() -> sb.append("<br/>")
                else -> sb.append("<p>").append(inline(line)).append("</p>")
            }
        }
        if (inList) sb.append("</ul>")
        if (inCode) sb.append("</code></pre>")

        return wrap(sb.toString())
    }

    private fun inline(s: String): String {
        var t = escape(s)
        t = t.replace(Regex("\\*\\*(.+?)\\*\\*"), "<b>$1</b>")
        t = t.replace(Regex("__(.+?)__"), "<b>$1</b>")
        t = t.replace(Regex("(?<!\\*)\\*(?!\\s)(.+?)(?<!\\s)\\*(?!\\*)"), "<i>$1</i>")
        t = t.replace(Regex("`([^`]+)`"), "<code>$1</code>")
        t = t.replace(Regex("\\[([^\\]]+)\\]\\(([^)]+)\\)"), "<a href=\"$2\">$1</a>")
        return t
    }

    private fun escape(s: String): String =
        s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")

    private fun wrap(body: String): String = """
        <!doctype html><html><head>
        <meta charset="utf-8"/>
        <meta name="viewport" content="width=device-width, initial-scale=1"/>
        <style>
            body { font-family: -apple-system, Roboto, sans-serif; line-height: 1.55;
                   padding: 16px; color: #1A1A2E; background: #FAFAFE; }
            h1,h2,h3,h4,h5,h6 { color: #FF1493; margin: 18px 0 8px 0; }
            code { background: #F0EFF7; padding: 1px 5px; border-radius: 4px;
                   font-family: monospace; font-size: 13px; }
            pre { background: #0F0524; color: #C4B5FD; padding: 12px;
                  border-radius: 8px; overflow-x: auto; }
            pre code { background: transparent; color: inherit; padding: 0; }
            blockquote { border-left: 3px solid #A855F7; padding-left: 12px;
                         color: #666680; margin: 8px 0; }
            a { color: #06B6D4; }
            hr { border: none; border-top: 1px solid #E5E5F0; margin: 16px 0; }
            ul { padding-left: 22px; }
        </style></head><body>
        $body
        </body></html>
    """.trimIndent()
}
