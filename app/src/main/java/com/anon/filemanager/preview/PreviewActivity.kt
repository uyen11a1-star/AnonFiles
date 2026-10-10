package com.anon.filemanager.preview

import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.view.View
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.anon.filemanager.FileOps
import com.anon.filemanager.R
import com.google.android.material.appbar.MaterialToolbar
import java.io.File

class PreviewActivity : AppCompatActivity() {

    private lateinit var web: WebView
    private lateinit var pdfList: RecyclerView
    private lateinit var loading: LinearLayout
    private lateinit var loadingText: TextView
    private var filePath: String = ""
    private var pdfAdapter: PdfPageAdapter? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_preview)

        filePath = intent.getStringExtra("path") ?: run { finish(); return }
        val toolbar: MaterialToolbar = findViewById(R.id.previewToolbar)
        web = findViewById(R.id.previewWeb)
        pdfList = findViewById(R.id.previewPdfList)
        loading = findViewById(R.id.previewLoading)
        loadingText = findViewById(R.id.previewLoadingText)

        toolbar.subtitle = File(filePath).name
        toolbar.setNavigationOnClickListener { finish() }
        toolbar.inflateMenu(R.menu.preview_menu)
        toolbar.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.preview_edit -> {
                    startActivity(android.content.Intent(this, com.anon.filemanager.TextEditorActivity::class.java)
                        .putExtra("path", filePath))
                    true
                }
                R.id.preview_fullscreen -> {
                    toggleFullscreen()
                    true
                }
                R.id.preview_open_with -> {
                    com.anon.filemanager.FileOps.openWith(this, filePath, "*/*")
                    true
                }
                R.id.preview_reload -> {
                    recreate()
                    true
                }
                else -> false
            }
        }

        web.settings.javaScriptEnabled = true
        web.settings.loadWithOverviewMode = true
        web.settings.useWideViewPort = true
        web.settings.builtInZoomControls = true
        web.settings.displayZoomControls = false
        web.webViewClient = WebViewClient()

        val ext = filePath.substringAfterLast('.', "").lowercase()
        when (ext) {
            "pdf" -> renderPdf()
            "html", "htm" -> renderHtml()
            "md", "markdown" -> renderMarkdown()
            "svg" -> renderSvg()
            "txt", "log", "json", "xml", "yml", "yaml", "ini", "cfg" -> renderText()
            else -> renderText()
        }
    }

    private fun showLoading(msg: String) {
        loading.visibility = View.VISIBLE
        loadingText.text = msg
        web.visibility = View.GONE
        pdfList.visibility = View.GONE
    }

    private fun showWeb() {
        loading.visibility = View.GONE
        web.visibility = View.VISIBLE
        pdfList.visibility = View.GONE
    }

    private fun showPdf() {
        loading.visibility = View.GONE
        web.visibility = View.GONE
        pdfList.visibility = View.VISIBLE
    }

    private fun localFile(): File? {
        return try {
            if (FileOps.isProtected(filePath)) {
                FileOps.copyToCache(this, filePath)
            } else File(filePath)
        } catch (e: Exception) { null }
    }

    // ============ HTML ============

    private fun renderHtml() {
        showLoading("Đang tải HTML...")
        val f = localFile() ?: run { toast("Không đọc được file"); return }
        try {
            val html = f.readText()
            web.loadDataWithBaseURL(
                f.parentFile?.toURI()?.toString() ?: "file:///",
                html, "text/html", "utf-8", null
            )
            showWeb()
        } catch (e: Exception) { toast("Lỗi HTML: ${e.message}") }
    }

    // ============ Markdown ============

    private fun renderMarkdown() {
        showLoading("Đang parse Markdown...")
        val f = localFile() ?: run { toast("Không đọc được file"); return }
        try {
            val html = Markdown.toHtml(f.readText())
            web.loadDataWithBaseURL(null, html, "text/html", "utf-8", null)
            showWeb()
        } catch (e: Exception) { toast("Lỗi Markdown: ${e.message}") }
    }

    // ============ SVG ============

    private fun renderSvg() {
        showLoading("Đang tải SVG...")
        val f = localFile() ?: run { toast("Không đọc được file"); return }
        try {
            val svg = f.readText()
            val wrapped = """
                <!doctype html><html><head>
                <meta name="viewport" content="width=device-width, initial-scale=1"/>
                <style>body{margin:0;padding:12px;background:#FAFAFE;text-align:center}svg{max-width:100%;height:auto}</style>
                </head><body>$svg</body></html>
            """.trimIndent()
            web.loadDataWithBaseURL(null, wrapped, "text/html", "utf-8", null)
            showWeb()
        } catch (e: Exception) { toast("Lỗi SVG: ${e.message}") }
    }

    // ============ Text thuần ============

    private fun renderText() {
        showLoading("Đang tải...")
        val f = localFile() ?: run { toast("Không đọc được file"); return }
        try {
            val txt = f.readText()
            val escaped = txt.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
            val html = """
                <!doctype html><html><head>
                <meta charset="utf-8"/>
                <meta name="viewport" content="width=device-width, initial-scale=1"/>
                <style>
                    body { margin: 0; padding: 12px; background: #FFFFFF; }
                    pre { font-family: monospace; font-size: 12px; white-space: pre-wrap;
                          word-break: break-all; color: #1A1A2E; line-height: 1.45; margin: 0; }
                </style>
                </head><body><pre>$escaped</pre></body></html>
            """.trimIndent()
            web.loadDataWithBaseURL(null, html, "text/html", "utf-8", null)
            toolbar.subtitle = "${File(filePath).name}  ·  ${txt.length} ký tự"
            showWeb()
        } catch (e: Exception) { toast("Lỗi: ${e.message}") }
    }

    // ============ PDF ============

    private fun renderPdf() {
        showLoading("Đang render PDF...")
        val f = localFile() ?: run { toast("Không đọc được file"); return }
        Thread {
            try {
                val pfd = ParcelFileDescriptor.open(f, ParcelFileDescriptor.MODE_READ_ONLY)
                val renderer = PdfRenderer(pfd)
                val pageCount = renderer.pageCount
                val pages = mutableListOf<Bitmap?>()
                repeat(pageCount) { pages.add(null) }

                runOnUiThread {
                    toolbar.subtitle = "${File(filePath).name}  ·  $pageCount trang"
                    val adapter = PdfPageAdapter(pages) { idx, _, _ ->
                        renderPageLazy(renderer, pages, adapter0(), idx)
                    }
                    pdfAdapter = adapter
                    pdfList.layoutManager = LinearLayoutManager(this)
                    pdfList.adapter = adapter
                    showPdf()
                }
            } catch (e: Exception) {
                runOnUiThread { toast("Render PDF lỗi: ${e.message}") }
            }
        }.start()
    }

    private fun adapter0(): PdfPageAdapter? = pdfAdapter

    private fun renderPageLazy(
        renderer: PdfRenderer,
        pages: MutableList<Bitmap?>,
        adapter: PdfPageAdapter?,
        idx: Int
    ) {
        if (pages[idx] != null || adapter == null) return
        Thread {
            try {
                synchronized(renderer) {
                    val page = renderer.openPage(idx)
                    val scale = 2
                    val bmp = Bitmap.createBitmap(
                        page.width * scale,
                        page.height * scale,
                        Bitmap.Config.ARGB_8888
                    )
                    bmp.eraseColor(android.graphics.Color.WHITE)
                    page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    page.close()
                    pages[idx] = bmp
                    runOnUiThread { adapter.notifyItemChanged(idx) }
                }
            } catch (e: Exception) { /* ignore */ }
        }.start()
    }

    private var fullscreen = false
    private fun toggleFullscreen() {
        fullscreen = !fullscreen
        val controller = androidx.core.view.WindowCompat.getInsetsController(window, window.decorView)
        if (fullscreen) {
            controller.hide(androidx.core.view.WindowInsetsCompat.Type.systemBars())
            controller.systemBarsBehavior =
                androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        } else {
            controller.show(androidx.core.view.WindowInsetsCompat.Type.systemBars())
        }
    }

    private fun toast(m: String) = Toast.makeText(this, m, Toast.LENGTH_SHORT).show()
}
