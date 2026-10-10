package com.anon.filemanager.ai

import android.content.Intent
import android.net.Uri
import android.text.SpannableString
import android.text.Spanned
import android.text.method.LinkMovementMethod
import android.text.style.ClickableSpan
import android.text.style.ForegroundColorSpan
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.anon.filemanager.R
import java.io.File

class ChatAdapter(
    private val items: MutableList<ChatMessage>,
    private val onOpenFile: (String) -> Unit,
    private val onOpenArtifact: (String) -> Unit
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

            is AsstVH -> {
                val base = m.content.ifBlank { "…" }
                holder.msg.text = linkifyPaths(base, onOpenFile)
                holder.msg.movementMethod = LinkMovementMethod.getInstance()
                holder.msg.setTextColor(
                    if (m.type == MsgType.ERROR) 0xFFB91C1C.toInt() else 0xFF1A1A2E.toInt()
                )
            }

            is ToolVH -> {
                val ok = m.toolOk
                val status = when (ok) {
                    true -> "✓"
                    false -> "✗"
                    null -> "⋯"
                }
                holder.head.text = "$status ${m.toolName ?: "tool"}"
                if (m.toolArgs.isNullOrBlank()) holder.args.visibility = View.GONE
                else {
                    holder.args.visibility = View.VISIBLE
                    holder.args.text = m.toolArgs
                }
                holder.result.text = m.toolResult ?: "đang chạy…"
                holder.head.setTextColor(
                    when (ok) {
                        true -> 0xFF059669.toInt()
                        false -> 0xFFDC2626.toInt()
                        null -> 0xFF0369A1.toInt()
                    }
                )
            }

            is ArtVH -> bindArtifact(holder, m)
        }
    }

    private fun bindArtifact(holder: ArtVH, m: ChatMessage) {
        val path = m.artifactPath
        val mime = m.artifactMime ?: ""
        val ext = if (path != null) File(path).extension.lowercase() else ""
        val f = if (path != null) File(path) else null

        // Reset visibility
        holder.web.visibility = View.GONE
        holder.img.visibility = View.GONE
        holder.codeScroll.visibility = View.GONE
        holder.pdfPage.visibility = View.GONE

        holder.head.text = m.toolName ?: "artifact"
        holder.meta.text = path ?: ""

        // Icon theo loại
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
            holder.open.setOnClickListener { path?.let { onOpenArtifact(it) } }
            holder.copy.visibility = View.GONE
            return
        }

        holder.copy.visibility = View.VISIBLE

        // WebView setup (cho HTML/MD/SVG)
        holder.web.settings.javaScriptEnabled = true
        holder.web.settings.loadWithOverviewMode = true
        holder.web.settings.useWideViewPort = true
        holder.web.settings.builtInZoomControls = true
        holder.web.settings.displayZoomControls = false
        holder.web.webViewClient = WebViewClient()

        when {
            mime.contains("html") || ext in setOf("html","htm") -> {
                holder.web.visibility = View.VISIBLE
                val html = runCatching { f.readText() }.getOrElse { "<p>Không đọc được file</p>" }
                holder.web.loadDataWithBaseURL(
                    f.parentFile?.toURI()?.toString(),
                    html, "text/html", "utf-8", null
                )
            }

            ext == "svg" -> {
                holder.web.visibility = View.VISIBLE
                val svg = runCatching { f.readText() }.getOrElse { "" }
                val wrapped = """
                    <!doctype html><html><head>
                    <meta name="viewport" content="width=device-width, initial-scale=1"/>
                    <style>body{margin:0;padding:10px;background:#FAFAFE;text-align:center}svg{max-width:100%;height:auto}</style>
                    </head><body>$svg</body></html>
                """.trimIndent()
                holder.web.loadDataWithBaseURL(null, wrapped, "text/html", "utf-8", null)
            }

            ext in setOf("md","markdown") -> {
                holder.web.visibility = View.VISIBLE
                val md = runCatching { f.readText() }.getOrElse { "" }
                val html = com.anon.filemanager.preview.Markdown.toHtml(md)
                holder.web.loadDataWithBaseURL(null, html, "text/html", "utf-8", null)
            }

            mime.startsWith("image/") || ext in setOf("png","jpg","jpeg","gif","webp","bmp") -> {
                holder.img.visibility = View.VISIBLE
                try { holder.img.setImageURI(Uri.fromFile(f)) }
                catch (_: Exception) { holder.img.setImageResource(android.R.drawable.ic_menu_gallery) }
            }

            ext == "pdf" -> {
                holder.pdfPage.visibility = View.VISIBLE
                holder.pdfPage.setImageResource(android.R.drawable.ic_menu_gallery)
                // Render trang 1 ở background
                Thread {
                    try {
                        val pfd = android.os.ParcelFileDescriptor.open(f, android.os.ParcelFileDescriptor.MODE_READ_ONLY)
                        val renderer = android.graphics.pdf.PdfRenderer(pfd)
                        if (renderer.pageCount > 0) {
                            val page = renderer.openPage(0)
                            val scale = 2
                            val bmp = android.graphics.Bitmap.createBitmap(
                                page.width * scale, page.height * scale,
                                android.graphics.Bitmap.Config.ARGB_8888
                            )
                            bmp.eraseColor(android.graphics.Color.WHITE)
                            page.render(bmp, null, null, android.graphics.pdf.PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                            page.close(); renderer.close(); pfd.close()
                            holder.pdfPage.post {
                                holder.pdfPage.setImageBitmap(bmp)
                            }
                        }
                    } catch (_: Exception) {}
                }.start()
            }

            ext in setOf("kt","java","py","sh","js","ts","cpp","c","h","cs","rs","go","rb","php","swift",
                         "json","xml","yml","yaml","txt","log","ini","cfg","properties","gradle","toml","sql") -> {
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
            }

            else -> {
                // Fallback: đọc như text
                holder.codeScroll.visibility = View.VISIBLE
                val txt = runCatching { f.readText().take(50_000) }.getOrElse { "(binary)" }
                holder.code.text = txt
            }
        }

        holder.open.setOnClickListener { path?.let { onOpenArtifact(it) } }
        holder.copy.setOnClickListener {
            try {
                val cm = holder.itemView.context.getSystemService(android.content.Context.CLIPBOARD_SERVICE)
                    as android.content.ClipboardManager
                val content = f.readText().take(100_000)
                cm.setPrimaryClip(android.content.ClipData.newPlainText("artifact", content))
                android.widget.Toast.makeText(holder.itemView.context, "Đã copy nội dung file", android.widget.Toast.LENGTH_SHORT).show()
            } catch (_: Exception) {
                android.widget.Toast.makeText(holder.itemView.context, "Không copy được", android.widget.Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun linkifyPaths(text: String, onOpen: (String) -> Unit): CharSequence {
        val sp = SpannableString(text)
        // Bắt đường dẫn tuyệt đối
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
        // Highlight inline code `...`
        val reCode = Regex("`([^`]+)`")
        for (mt in reCode.findAll(text)) {
            val s = mt.range.first
            val e = mt.range.last + 1
            sp.setSpan(ForegroundColorSpan(0xFF7C3AED.toInt()), s, e, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        return sp
    }

    // --- Mutation API cho streaming ---

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

    fun removeAt(index: Int) {
        if (index !in items.indices) return
        items.removeAt(index)
        notifyItemRemoved(index)
    }

    fun addAll(list: List<ChatMessage>) {
        val start = items.size
        items.addAll(list)
        notifyItemRangeInserted(start, list.size)
    }

    fun clear() {
        items.clear()
        notifyDataSetChanged()
    }

    fun lastIndex(): Int = items.size - 1
}
