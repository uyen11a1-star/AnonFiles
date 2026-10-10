package com.anon.filemanager

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class FileAdapter(
    private val items: MutableList<FileItem>,
    private val onClick: (FileItem) -> Unit,
    private val onLongClick: (FileItem) -> Unit
) : RecyclerView.Adapter<FileAdapter.VH>() {

    class VH(v: View) : RecyclerView.ViewHolder(v) {
        val icon: ImageView = v.findViewById(R.id.icon)
        val name: TextView = v.findViewById(R.id.name)
        val meta: TextView = v.findViewById(R.id.meta)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
        VH(LayoutInflater.from(parent.context).inflate(R.layout.item_file, parent, false))

    override fun getItemCount() = items.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = items[position]
        holder.name.text = item.name
        val (icoRes, tint) = when {
            item.isDirectory -> android.R.drawable.ic_menu_view to 0xFFFF6EC7.toInt()
            item.name.endsWith(".apk", true) -> android.R.drawable.ic_menu_upload to 0xFF6EE7FF.toInt()
            item.name.endsWith(".dat", true) || item.name.endsWith(".bin", true) -> android.R.drawable.ic_menu_edit to 0xFFFFB86C.toInt()
            item.name.endsWith(".txt", true) || item.name.endsWith(".json", true) -> android.R.drawable.ic_menu_agenda to 0xFFA78BFA.toInt()
            item.name.endsWith(".png", true) || item.name.endsWith(".jpg", true) || item.name.endsWith(".jpeg", true) -> android.R.drawable.ic_menu_gallery to 0xFF34D399.toInt()
            else -> android.R.drawable.ic_menu_save to 0xFFF0F0F0.toInt()
        }
        holder.icon.setImageResource(icoRes)
        holder.icon.setColorFilter(tint)
        holder.meta.text =
            if (item.isDirectory) "📁 Folder"
            else "${fmtSize(item.size)}  ·  ${fmtDate(item.lastModified)}"

        holder.itemView.setOnClickListener { onClick(item) }
        holder.itemView.setOnLongClickListener { onLongClick(item); true }
    }

    private fun fmtSize(b: Long): String {
        if (b < 1024) return "$b B"
        val kb = b / 1024.0
        if (kb < 1024) return String.format(Locale.US, "%.1f KB", kb)
        val mb = kb / 1024.0
        if (mb < 1024) return String.format(Locale.US, "%.1f MB", mb)
        return String.format(Locale.US, "%.1f GB", mb / 1024.0)
    }
    private fun fmtDate(t: Long) =
        SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date(t))

    fun update(n: List<FileItem>) {
        items.clear()
        items.addAll(n)
        notifyDataSetChanged()
    }
}
