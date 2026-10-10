package com.anon.filemanager.ai

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.anon.filemanager.R

class ChatAdapter(
    private val items: MutableList<ChatMessage>,
    private val onFilePathClick: (String) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        const val TYPE_USER = 0
        const val TYPE_AI = 1
    }

    class UserVH(v: View) : RecyclerView.ViewHolder(v) {
        val msg: TextView = v.findViewById(R.id.userMsg)
    }
    class AiVH(v: View) : RecyclerView.ViewHolder(v) {
        val role: TextView = v.findViewById(R.id.aiRole)
        val msg: TextView = v.findViewById(R.id.aiMsg)
        val tools: TextView = v.findViewById(R.id.aiTools)
    }

    override fun getItemViewType(position: Int) = if (items[position].isUser) TYPE_USER else TYPE_AI

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return if (viewType == TYPE_USER)
            UserVH(LayoutInflater.from(parent.context).inflate(R.layout.item_chat_user, parent, false))
        else
            AiVH(LayoutInflater.from(parent.context).inflate(R.layout.item_chat_ai, parent, false))
    }

    override fun getItemCount() = items.size

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val m = items[position]
        if (holder is UserVH) {
            holder.msg.text = m.content
        } else if (holder is AiVH) {
            holder.msg.text = m.content
            holder.msg.movementMethod = android.text.method.LinkMovementMethod.getInstance()
            holder.msg.text = linkifyPaths(m.content, onFilePathClick)

            if (m.toolSummary.isNullOrBlank()) holder.tools.visibility = View.GONE
            else {
                holder.tools.visibility = View.VISIBLE
                holder.tools.text = m.toolSummary
            }
        }
    }

    private fun linkifyPaths(text: String, onClick: (String) -> Unit): CharSequence {
        val sp = android.text.SpannableString(text)
        val re = Regex("(/storage/emulated/0[^\\s,)\\]]+)")
        for (m in re.findAll(text)) {
            val start = m.range.first
            val end = m.range.last + 1
            sp.setSpan(object : android.text.style.ClickableSpan() {
                override fun onClick(w: View) { onClick(m.value) }
                override fun updateDrawState(ds: android.text.TextPaint) {
                    ds.color = 0xFF06B6D4.toInt()
                    ds.isUnderlineText = true
                }
            }, start, end, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        return sp
    }

    fun add(m: ChatMessage) {
        items.add(m)
        notifyItemInserted(items.size - 1)
    }
    fun updateLast(m: ChatMessage) {
        if (items.isEmpty()) return
        items[items.size - 1] = m
        notifyItemChanged(items.size - 1)
    }
}
