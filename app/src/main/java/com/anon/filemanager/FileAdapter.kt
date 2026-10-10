package com.anon.filemanager

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
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

    var selectionMode: Boolean = false
        private set
    private val selected = mutableSetOf<String>()

    class VH(v: View) : RecyclerView.ViewHolder(v) {
        val icon: ImageView = v.findViewById(R.id.icon)
        val name: TextView = v.findViewById(R.id.name)
        val meta: TextView = v.findViewById(R.id.meta)
        val check: CheckBox = v.findViewById(R.id.check)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
        VH(LayoutInflater.from(parent.context).inflate(R.layout.item_file, parent, false))

    override fun getItemCount() = items.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = items[position]
        holder.name.text = item.name

        // Nếu là package app trong Android/data → lấy icon từ PackageManager
        var appIconSet = false
        if (item.isDirectory && item.name.contains('.') && !item.name.startsWith('.')) {
            try {
                val pm = holder.itemView.context.packageManager
                var drawable: android.graphics.drawable.Drawable? = null
                // Cách 1: getApplicationIcon
                try { drawable = pm.getApplicationIcon(item.name) } catch (_: Exception) {}
                // Cách 2: getApplicationInfo + loadIcon
                if (drawable == null) {
                    try {
                        val info = pm.getApplicationInfo(item.name, 0)
                        drawable = info.loadIcon(pm)
                    } catch (_: Exception) {}
                }
                // Cách 3: getLaunchIntentForPackage → getActivityIcon
                if (drawable == null) {
                    try {
                        val i = pm.getLaunchIntentForPackage(item.name)
                        if (i != null) drawable = pm.getActivityIcon(i)
                    } catch (_: Exception) {}
                }
                // Cách 4: queryIntentActivities MAIN/LAUNCHER
                if (drawable == null) {
                    try {
                        val intent = android.content.Intent(android.content.Intent.ACTION_MAIN)
                            .addCategory(android.content.Intent.CATEGORY_LAUNCHER)
                            .setPackage(item.name)
                        val list = pm.queryIntentActivities(intent, 0)
                        if (list.isNotEmpty()) drawable = list[0].loadIcon(pm)
                    } catch (_: Exception) {}
                }
                if (drawable != null) {
                    holder.icon.setImageDrawable(drawable)
                    holder.icon.clearColorFilter()
                    appIconSet = true
                }
            } catch (_: Exception) {}
        }

        val (icoRes, tint) = when {
            item.isDirectory -> android.R.drawable.ic_menu_view to 0xFFFF1493.toInt()
            item.isApk -> android.R.drawable.ic_menu_upload to 0xFF06B6D4.toInt()
            item.isArchive -> android.R.drawable.ic_menu_save to 0xFFA855F7.toInt()
            item.isText -> android.R.drawable.ic_menu_agenda to 0xFF7C3AED.toInt()
            item.extension in setOf("png","jpg","jpeg","gif","webp","bmp") -> android.R.drawable.ic_menu_gallery to 0xFF10B981.toInt()
            item.extension in setOf("mp4","mkv","avi","mov","webm") -> android.R.drawable.ic_menu_slideshow to 0xFFF59E0B.toInt()
            item.extension in setOf("mp3","ogg","wav","flac","m4a") -> android.R.drawable.ic_lock_silent_mode_off to 0xFFEC4899.toInt()
            else -> android.R.drawable.ic_menu_save to 0xFF8888A0.toInt()
        }
        if (!appIconSet) {
            holder.icon.setImageResource(icoRes)
            holder.icon.setColorFilter(tint)
        }

        holder.meta.text = if (item.isDirectory) "Folder"
            else "${fmtSize(item.size)}  ·  ${fmtDate(item.lastModified)}"

        val isSel = selected.contains(item.file.absolutePath)
        holder.check.visibility = if (selectionMode) View.VISIBLE else View.GONE
        holder.check.isChecked = isSel

        holder.itemView.setBackgroundColor(if (isSel) 0x20FF1493 else 0x00000000)

        holder.itemView.setOnClickListener { onClick(item) }
        holder.itemView.setOnLongClickListener { onLongClick(item); true }
    }

    fun setSelectionMode(on: Boolean) {
        selectionMode = on
        if (!on) selected.clear()
        notifyDataSetChanged()
    }

    fun toggleSelect(item: FileItem): Boolean {
        val p = item.file.absolutePath
        if (selected.contains(p)) selected.remove(p) else selected.add(p)
        notifyDataSetChanged()
        return selected.isNotEmpty()
    }

    fun selectAll() {
        items.forEach { selected.add(it.file.absolutePath) }
        notifyDataSetChanged()
    }

    fun clearSelection() {
        selected.clear()
        notifyDataSetChanged()
    }

    fun selectedItems(): List<FileItem> = items.filter { selected.contains(it.file.absolutePath) }
    fun selectedCount(): Int = selected.size

    private fun fmtSize(b: Long): String {
        if (b < 1024) return "$b B"
        val kb = b / 1024.0
        if (kb < 1024) return String.format(Locale.US, "%.1f KB", kb)
        val mb = kb / 1024.0
        if (mb < 1024) return String.format(Locale.US, "%.1f MB", mb)
        return String.format(Locale.US, "%.2f GB", mb / 1024.0)
    }
    private fun fmtDate(t: Long): String =
        if (t <= 0) "" else SimpleDateFormat("dd/MM/yy HH:mm", Locale.getDefault()).format(Date(t))

    fun update(n: List<FileItem>) {
        items.clear()
        items.addAll(n)
        selected.retainAll(n.map { it.file.absolutePath }.toSet())
        notifyDataSetChanged()
    }
}
