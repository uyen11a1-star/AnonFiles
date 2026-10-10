package com.anon.filemanager.ai

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.text.SpannableString
import android.text.Spanned
import android.text.method.LinkMovementMethod
import android.text.style.ClickableSpan
import android.text.style.ForegroundColorSpan
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.RecyclerView
import com.anon.filemanager.R
import java.io.File

class ChatAdapter(
    private val items: MutableList<ChatMessage>,
    private val onOpenFile: (String) -> Unit,
    private val onOpenArtifact: (String) -> Unit,
    private val onRunCode: ((String) -> Unit)? = null,
    private val onFixArtifact: ((path: String, report: String) -> Unit)? = null
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        const val TYPE_USER = 0
        const val TYPE_THINKING = 1
        const val TYPE_ASSISTANT = 2
        const val TYPE_TOOL = 3
        const val TYPE_ARTIFACT = 4
        const val TYPE_ERROR = 5
    }

    class UserVH(v: View) : RecyclerView.ViewHolder(v) {
        val msg: TextView = v.findViewById(R.id.userMsg)
    }
    class ThinkVH(v: View) : RecyclerView.ViewHolder(v) {
        val head: TextView = v.findViewById(R.id.thinkHead)
        val body: TextView = v.findViewById(R.id.thinkBody)
    }
    class AsstVH(v: View) : RecyclerView.ViewHolder(v) {
        val msg: TextView = v.findViewById(R.id.aiMsg)
        val liveBox: View = v.findViewById(R.id.livePreviewBox)
        val liveDot: TextView = v.findViewById(R.id.livePreviewDot)
        val liveLabel: TextView = v.findViewById(R.id.livePreviewLabel)
        val liveHealth: TextView = v.findViewById(R.id.livePreviewHealth)
        val liveWeb: WebView = v.findViewById(R.id.livePreviewWeb)
        val liveErrors: TextView = v.findViewById(R.id.livePreviewErrors)
    }
    class ToolVH(v: View) : RecyclerView.ViewHolder(v) {
        val head: TextView = v.findViewById(R.id.toolHead)
        val args: TextView = v.findViewById(R.id.toolArgs)
        val result: TextView = v.findViewById(R.id.toolResult)
    }
    class ArtVH(v: View) : RecyclerView.ViewHolder(v) {
        val head: TextView = v.findViewById(R.id.artHead)
        val meta: TextView = v.findViewById(R.id.artMeta)
        val icon: TextView = v.findViewById(R.id.artIcon)
        val healthDot: TextView = v.findViewById(R.id.artHealthDot)
        val healthBanner: View = v.findViewById(R.id.artHealthBanner)
        val healthText: TextView = v.findViewById(R.id.artHealthText)
        val healthFix: View = v.findViewById(R.id.artHealthFix)
        val web: WebView = v.findViewById(R.id.artWeb)
        val img: ImageView = v.findViewById(R.id.artImg)
        val codeScroll: View = v.findViewById(R.id.artCodeScroll)
        val code: TextView = v.findViewById(R.id.artCode)
        val pdfPage: ImageView = v.findViewById(R.id.artPdfPage)
        val open: View = v.findViewById(R.id.artOpen)
        val copy: View = v.findViewById(R.id.artCopy)
    }

    override fun getItemViewType(position: Int): Int = when (items[position].type) {
        MsgType.USER -> TYPE_USER
        MsgType.THINKING -> TYPE_THINKING
        MsgType.ASSISTANT -> TYPE_ASSISTANT
        MsgType.TOOL_CALL, MsgType.TOOL_RESULT -> TYPE_TOOL
        MsgType.ARTIFACT -> TYPE_ARTIFACT
        MsgType.ERROR -> TYPE_ERROR
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inf = LayoutInflater.from(parent.context)
        return when (viewType) {
            TYPE_USER -> UserVH(inf.inflate(R.layout.item_chat_user, parent, false))
            TYPE_THINKING -> ThinkVH(inf.inflate(R.layout.item_chat_thinking, parent, false))
            TYPE_ASSISTANT, TYPE_ERROR -> AsstVH(inf.inflate(R.layout.item_chat_assistant, parent, false))
            TYPE_TOOL -> ToolVH(inf.inflate(R.layout.item_chat_tool, parent, false))
            TYPE_ARTIFACT -> ArtVH(inf.inflate(R.layout.item_chat_artifact, parent, false))
            else -> AsstVH(inf.inflate(R.layout.item_chat_assistant, parent, false))
        }
    }

    override fun getItemCount() = items.size

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val m = items[position]
        when (holder) {
            is UserVH -> holder.msg.text = m.content

            is ThinkVH -> {
                holder.head.text = if (m.streaming) "💭 Đang suy nghĩ…" else "💭 Suy nghĩ"
                holder.body.text = m.content.ifBlank { "…" }
            }

            is AsstVH -> bindAssistant(holder, m)

            is ToolVH -> bindTool(holder, m)

            is ArtVH -> bindArtifact(holder, m)
        }
    }

    // ============================== ASSISTANT ==============================

    private fun bindAssistant(holder: AsstVH, m: ChatMessage) {
        val base = m.content.ifBlank { "…" }
        holder.msg.text = linkifyPaths(base, onOpenFile)
        holder.msg.movementMethod = LinkMovementMethod.getInstance()
        holder.msg.setTextColor(
            if (m.type == MsgType.ERROR) 0xFFB91C1C.toInt() else 0xFF1A1A2E.toInt()
        )

        // Long-press → dialog bash / copy
        holder.msg.setOnLongClickListener { v ->
            val runMatch = Regex("```(?:bash|sh)\\n([\\s\\S]*?)```").find(base)
            if (runMatch != null && onRunCode != null) {
                android.app.AlertDialog.Builder(v.context)
                    .setTitle("Chạy bash block?")
                    .setMessage(runMatch.groupValues[1].take(400))
                    .setPositiveButton("Chạy") { _, _ -> onRunCode.invoke(runMatch.groupValues[1]) }
                    .setNegativeButton("Hủy", null)
                    .setNeutralButton("Copy") { _, _ -> copyToClip(v.context, runMatch.groupValues[1]) }
                    .show()
            } else {
                copyToClip(v.context, base)
                Toast.makeText(v.context, "Đã copy tin nhắn", Toast.LENGTH_SHORT).show()
            }
            true
        }

        // ===== Live preview =====
        val htmlChunk = extractOpenHtmlBlock(m.content)
        if (htmlChunk != null && htmlChunk.isNotBlank()) {
            holder.liveBox.visibility = View.VISIBLE
            holder.liveDot.setTextColor(if (m.streaming) 0xFFFBBF24.toInt() else 0xFF059669.toInt())
            holder.liveLabel.text = if (m.streaming) "Live preview — đang viết…" else "Live preview — hoàn tất"

            holder.liveWeb.settings.javaScriptEnabled = true
            holder.liveWeb.settings.loadWithOverviewMode = true
            holder.liveWeb.settings.useWideViewPort = true
            holder.liveWeb.settings.builtInZoomControls = true
            holder.liveWeb.settings.displayZoomControls = false

            // Tag để tránh update sai khi recycle
            val tag = "live_${System.identityHashCode(m)}_${htmlChunk.length}"
            if (holder.liveWeb.tag != tag) {
                holder.liveWeb.tag = tag
                holder.liveWeb.webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView?, url: String?) {
                        super.onPageFinished(view, url)
                        if (!m.streaming) {
                            // probe lần cuối khi stream xong
                            runHealthProbe(view, holder.liveHealth, holder.liveErrors, null, null)
                        }
                    }
                }
                holder.liveWeb.loadDataWithBaseURL(
                    null, wrapHtmlFragment(htmlChunk), "text/html", "utf-8", null
                )
            }
            holder.liveHealth.visibility = View.GONE
            holder.liveErrors.visibility = View.GONE
        } else {
            holder.liveBox.visibility = View.GONE
        }
    }

    // Trích xuất code block ```html đang mở (chưa có ``` đóng) hoặc đã đóng
    private fun extractOpenHtmlBlock(text: String): String? {
        // Case 1: ```html ... ``` (đã đóng hoàn toàn)
        Regex("```html\\n([\\s\\S]*?)```").find(text)?.let { return it.groupValues[1] }
        // Case 2: ```html ... (chưa đóng)
        Regex("```html\\n([\\s\\S]*)$").find(text)?.let { return it.groupValues[1] }
        return null
    }

    private fun wrapHtmlFragment(fragment: String): String {
        // Nếu fragment đã có <html> → dùng nguyên
        if (fragment.contains("<html", ignoreCase = true)) return fragment
        return """
            <!doctype html><html><head>
            <meta charset="utf-8"/>
            <meta name="viewport" content="width=device-width, initial-scale=1"/>
            <style>body{margin:0;padding:10px;font-family:-apple-system,Roboto,sans-serif}</style>
            </head><body>$fragment</body></html>
        """.trimIndent()
    }

    // ============================== TOOL ==============================

    private fun bindTool(holder: ToolVH, m: ChatMessage) {
        val ok = m.toolOk
        val status = when (ok) { true -> "✓"; false -> "✗"; null -> "⋯" }
        holder.head.text = "$status ${m.toolName ?: "tool"}"
        holder.head.setTextColor(when (ok) {
            true -> 0xFF059669.toInt()
            false -> 0xFFDC2626.toInt()
            null -> 0xFF0369A1.toInt()
        })
        if (m.toolArgs.isNullOrBlank()) holder.args.visibility = View.GONE
        else {
            holder.args.visibility = View.VISIBLE
            holder.args.text = m.toolArgs
        }
        holder.result.text = m.toolResult ?: "đang chạy…"
    }

    // ============================== ARTIFACT ==============================

    private fun bindArtifact(holder: ArtVH, m: ChatMessage) {
        val path = m.artifactPath
        val mime = m.artifactMime ?: ""
        val ext = if (path != null) File(path).extension.lowercase() else ""
        val f = if (path != null) File(path) else null

        holder.web.visibility = View.GONE
        holder.img.visibility = View.GONE
        holder.codeScroll.visibility = View.GONE
        holder.pdfPage.visibility = View.GONE
        holder.healthBanner.visibility = View.GONE

        holder.head.text = m.toolName ?: "artifact"
        holder.meta.text = path ?: ""

        holder.icon.text = when {
            mime.contains("html") -> "🌐"
            ext == "svg" -> "🎨"
            mime.startsWith("image/") -> "🖼"
            ext == "pdf" -> "📄"
            ext in setOf("kt","java","py","sh","js","ts","cpp","c","h","cs","rs","go","rb","php","swift") -> "💻"
            ext in setOf("json","xml","yml","yaml") -> "📋"
            ext in setOf("md","markdown") -> "📝"
            else -> "📄"
        }

        if (f == null || !f.exists()) {
            holder.meta.text = "File không tồn tại: $path"
            holder.healthDot.setTextColor(0xFFDC2626.toInt())
            holder.open.setOnClickListener { path?.let { onOpenArtifact(it) } }
            holder.copy.visibility = View.GONE
            return
        }

        holder.copy.visibility = View.VISIBLE
        holder.healthDot.setTextColor(0xFFFBBF24.toInt()) // vàng: đang kiểm tra

        // WebView setup
        holder.web.settings.javaScriptEnabled = true
        holder.web.settings.loadWithOverviewMode = true
        holder.web.settings.useWideViewPort = true
        holder.web.settings.builtInZoomControls = true
        holder.web.settings.displayZoomControls = false

        val isWebContent = mime.contains("html") || ext in setOf("html","htm","svg") ||
                           ext in setOf("md","markdown")

        if (isWebContent) {
            holder.web.visibility = View.VISIBLE
            val probeHandler = object {
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
                        val empty = obj.optBoolean("isEmpty", false)
                        val hasIssues = failed > 0 || errs.isNotEmpty() || empty

                        holder.itemView.post {
                            if (hasIssues) {
                                holder.healthDot.setTextColor(0xFFDC2626.toInt())
                                holder.healthBanner.visibility = View.VISIBLE
                                val sb = StringBuilder()
                                if (failed > 0) {
                                    sb.append("⚠ $failed/$total ảnh không load được:\n")
                                    srcs.take(3).forEach { sb.append("  • ${it.take(70)}\n") }
                                }
                                if (errs.isNotEmpty()) {
                                    sb.append("⚠ ${errs.size} lỗi JS:\n")
                                    errs.take(2).forEach { sb.append("  • ${it.take(70)}\n") }
                                }
                                if (empty) sb.append("⚠ Nội dung rỗng\n")
                                holder.healthText.text = sb.toString().trim()
                                holder.healthFix.setOnClickListener {
                                    onFixArtifact?.invoke(path, sb.toString().trim())
                                }
                            } else {
                                holder.healthDot.setTextColor(0xFF059669.toInt())
                                holder.healthBanner.visibility = View.GONE
                            }
                        }
                    } catch (_: Exception) {}
                }
            }
            holder.web.removeJavascriptInterface("AnonProbe")
            holder.web.addJavascriptInterface(probeHandler, "AnonProbe")
            holder.web.webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    runHealthProbe(view, holder.healthText, null, holder.healthDot, holder.healthBanner)
                }
            }
            val html = when {
                mime.contains("html") || ext in setOf("html","htm") ->
                    runCatching { f.readText() }.getOrElse { "<p>Không đọc được file</p>" }
                ext == "svg" -> {
                    val svg = runCatching { f.readText() }.getOrElse { "" }
                    """<!doctype html><html><head><meta name="viewport" content="width=device-width,initial-scale=1"/><style>body{margin:0;padding:10px;background:#FAFAFE;text-align:center}svg{max-width:100%;height:auto}</style></head><body>$svg</body></html>"""
                }
                else -> {
                    val md = runCatching { f.readText() }.getOrElse { "" }
                    com.anon.filemanager.preview.Markdown.toHtml(md)
                }
            }
            holder.web.loadDataWithBaseURL(f.parentFile?.toURI()?.toString(), html, "text/html", "utf-8", null)
        }
        else when {
            mime.startsWith("image/") || ext in setOf("png","jpg","jpeg","gif","webp","bmp") -> {
                holder.img.visibility = View.VISIBLE
                try { holder.img.setImageURI(Uri.fromFile(f)) }
                catch (_: Exception) { holder.img.setImageResource(android.R.drawable.ic_menu_gallery) }
                holder.healthDot.setTextColor(0xFF059669.toInt())
            }

            ext == "pdf" -> {
                holder.pdfPage.visibility = View.VISIBLE
                holder.pdfPage.setImageResource(android.R.drawable.ic_menu_gallery)
                Thread {
                    try {
                        val pfd = ParcelFileDescriptor.open(f, ParcelFileDescriptor.MODE_READ_ONLY)
                        val renderer = PdfRenderer(pfd)
                        if (renderer.pageCount > 0) {
                            val page = renderer.openPage(0)
                            val scale = 2
                            val bmp = Bitmap.createBitmap(page.width * scale, page.height * scale, Bitmap.Config.ARGB_8888)
                            bmp.eraseColor(Color.WHITE)
                            page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                            page.close(); renderer.close(); pfd.close()
                            holder.pdfPage.post { holder.pdfPage.setImageBitmap(bmp) }
                        }
                    } catch (_: Exception) {}
                }.start()
                holder.healthDot.setTextColor(0xFF059669.toInt())
            }

            else -> {
                holder.codeScroll.visibility = View.VISIBLE
                val txt = runCatching {
                    if (f.length() > 100_000) f.readText().take(100_000) + "\n\n[...cắt bớt]"
                    else f.readText()
                }.getOrElse { "(không đọc được)" }
                val lang = when (ext) {
                    "kt","kts" -> "kotlin"
                    "js","ts" -> "javascript"
                    "html","htm" -> "html"
                    "xml" -> "xml"
                    "json" -> "json"
                    "sh","bash" -> "bash"
                    "py" -> "python"
                    "md","markdown" -> "markdown"
                    else -> ext
                }
                holder.code.text = SyntaxHighlighter.highlight(txt, lang)
                holder.healthDot.setTextColor(0xFF059669.toInt())
            }
        }

        holder.open.setOnClickListener { path?.let { onOpenArtifact(it) } }
        holder.copy.setOnClickListener {
            try {
                val cm = holder.itemView.context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                cm.setPrimaryClip(ClipData.newPlainText("artifact", f.readText().take(100_000)))
                Toast.makeText(holder.itemView.context, "Đã copy nội dung file", Toast.LENGTH_SHORT).show()
            } catch (_: Exception) {
                Toast.makeText(holder.itemView.context, "Không copy được", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun runHealthProbe(
        view: WebView?,
        banner: TextView?,
        inlineErr: TextView?,
        dot: TextView?,
        bannerBox: View?
    ) {
        if (view == null) return
        val js = """
            (function(){
                try {
                    var imgs = document.querySelectorAll('img');
                    var failedSrcs = []; var failedCount = 0;
                    for (var i = 0; i < imgs.length; i++) {
                        var img = imgs[i];
                        if (!img.complete || img.naturalWidth === 0) {
                            failedCount++;
                            var s = img.getAttribute('src') || '(no-src)';
                            if (failedSrcs.length < 6) failedSrcs.push(s);
                        }
                    }
                    var jsErrs = window.__anonJSErrors || [];
                    var bodyH = document.body ? document.body.scrollHeight : 0;
                    var isEmpty = bodyH < 20 && (!document.body || (document.body.innerText || '').trim().length < 5);
                    var payload = {
                        imagesTotal: imgs.length, imagesFailed: failedCount,
                        failedSources: failedSrcs, jsErrors: jsErrs,
                        bodyHeight: bodyH, isEmpty: isEmpty
                    };
                    if (window.AnonProbe && window.AnonProbe.report) {
                        window.AnonProbe.report(JSON.stringify(payload));
                    }
                } catch(e) {}
            })();
        """.trimIndent()
        view.postDelayed({ try { view.evaluateJavascript(js, null) } catch (_: Exception) {} }, 800)
    }

    // ============================== HELPERS ==============================

    private fun copyToClip(ctx: Context, text: String) {
        val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("anon", text))
    }

    private fun linkifyPaths(text: String, onOpen: (String) -> Unit): CharSequence {
        val sp = SpannableString(text)
        val re = Regex("(/storage/emulated/0[^\\s,;)\\]]+)")
        for (mt in re.findAll(text)) {
            val s = mt.range.first
            val e = mt.range.last + 1
            sp.setSpan(object : ClickableSpan() {
                override fun onClick(w: View) { onOpen(mt.value) }
                override fun updateDrawState(ds: android.text.TextPaint) {
                    ds.color = 0xFF06B6D4.toInt()
                    ds.isUnderlineText = true
                }
            }, s, e, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        Regex("`([^`]+)`").findAll(text).forEach {
            sp.setSpan(ForegroundColorSpan(0xFF7C3AED.toInt()), it.range.first, it.range.last + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        return sp
    }

    // ============================== MUTATION ==============================

    fun add(m: ChatMessage): Int {
        items.add(m)
        notifyItemInserted(items.size - 1)
        return items.size - 1
    }

    fun updateAt(index: Int, transform: (ChatMessage) -> ChatMessage) {
        if (index !in items.indices) return
        items[index] = transform(items[index])
        notifyItemChanged(index)
    }

    fun replaceAt(index: Int, m: ChatMessage) {
        if (index !in items.indices) return
        items[index] = m
        notifyItemChanged(index)
    }

    fun clear() {
        items.clear()
        notifyDataSetChanged()
    }
}
