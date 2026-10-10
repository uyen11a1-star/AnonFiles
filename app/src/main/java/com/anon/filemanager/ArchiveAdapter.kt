package com.anon.filemanager

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import java.util.Locale

class ArchiveAdapter(
    private val items: MutableList<ArchiveEntryItem>,
    private val onClick: (ArchiveEntryItem) -> Unit
) : RecyclerView.Adapter<ArchiveAdapter.VH>() {

    class VH(v: View) : RecyclerView.ViewHolder(v) {
        val icon: ImageView = v.findViewById(R.id.aIcon)
        val name: TextView = v.findViewById(R.id.aName)
        val meta: TextView = v.findViewById(R.id.aMeta)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
        VH(LayoutInflater.from(parent.context).inflate(R.layout.item_archive_entry, parent, false))

    override fun getItemCount() = items.size

    override fun onBindViewHolder(h: VH, p: Int) {
        val e = items[p]
        h.name.text = e.name
        val ratio = if (e.size > 0 && e.compressedSize > 0)
            String.format(Locale.US, "  ·  %.0f%%", 100.0 * e.compressedSize / e.size) else ""
        h.meta.text = if (e.isDirectory) "Folder"
            else "${fmtSize(e.size)}$ratio  ·  nén: ${fmtSize(e.compressedSize)}"
        h.icon.setImageResource(
            if (e.isDirectory) android.R.drawable.ic_menu_view
            else android.R.drawable.ic_menu_save
        )
        h.icon.setColorFilter(if (e.isDirectory) 0xFFFF1493.toInt() else 0xFFA855F7.toInt())
        h.itemView.setOnClickListener { onClick(e) }
    }

    private fun fmtSize(b: Long): String {
        if (b < 1024) return "$b B"
        val kb = b / 1024.0
        if (kb < 1024) return String.format(Locale.US, "%.1f KB", kb)
        val mb = kb / 1024.0
        if (mb < 1024) return String.format(Locale.US, "%.1f MB", mb)
        return String.format(Locale.US, "%.2f GB", mb / 1024.0)
    }

    fun update(n: List<ArchiveEntryItem>) {
        items.clear(); items.addAll(n); notifyDataSetChanged()
    }
}
