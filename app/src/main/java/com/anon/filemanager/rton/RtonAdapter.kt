package com.anon.filemanager.rton

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class RtonAdapter(
    private val items: MutableList<RtonNode>,
    private val onClick: (RtonNode) -> Unit
) : RecyclerView.Adapter<RtonAdapter.VH>() {

    class VH(v: View) : RecyclerView.ViewHolder(v) {
        val key: TextView = v.findViewById(android.R.id.text1)
        val valTv: TextView = v.findViewById(android.R.id.text2)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context)
            .inflate(android.R.layout.simple_list_item_2, parent, false)
        return VH(v)
    }

    override fun getItemCount() = items.size

    override fun onBindViewHolder(h: VH, p: Int) {
        val n = items[p]
        val indent = "  ".repeat(n.depth)
        h.key.text = "$indent${n.displayName}"
        h.key.typeface = android.graphics.Typeface.MONOSPACE
        h.key.setTextColor(if (n.isLeaf) 0xFF1A1A2E.toInt() else 0xFFFF1493.toInt())
        h.key.textSize = 14f

        h.valTv.typeface = android.graphics.Typeface.MONOSPACE
        h.valTv.textSize = 12f
        h.valTv.setTextColor(0xFF666680.toInt())
        h.valTv.text = if (n.isLeaf) "${n.typeTag} = ${n.valuePreview}"
                       else "${n.typeTag}  ▸ nhấn để sửa leaf bên trong"

        h.itemView.setBackgroundColor(0xFFFFFFFF.toInt())
        h.itemView.setOnClickListener { onClick(n) }
    }

    fun update(list: List<RtonNode>) {
        items.clear(); items.addAll(list); notifyDataSetChanged()
    }
}
