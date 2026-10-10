package com.anon.filemanager.preview

import android.graphics.Bitmap
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.anon.filemanager.R

class PdfPageAdapter(
    private val pages: MutableList<Bitmap?>,
    private val onBind: (Int, ImageView, TextView) -> Unit
) : RecyclerView.Adapter<PdfPageAdapter.VH>() {

    class VH(v: View) : RecyclerView.ViewHolder(v) {
        val img: ImageView = v.findViewById(R.id.pdfPageImg)
        val num: TextView = v.findViewById(R.id.pdfPageNum)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
        VH(LayoutInflater.from(parent.context).inflate(R.layout.item_pdf_page, parent, false))

    override fun getItemCount() = pages.size

    override fun onBindViewHolder(h: VH, p: Int) {
        val bmp = pages[p]
        if (bmp != null) h.img.setImageBitmap(bmp)
        else h.img.setImageResource(android.R.drawable.ic_menu_gallery)
        h.num.text = "Trang ${p + 1} / ${pages.size}"
        onBind(p, h.img, h.num)
    }
}
