package com.anon.filemanager.ai

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object AgentLog {

    private val entries = mutableListOf<Entry>()
    private val fmt = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)

    data class Entry(val ts: Long, val level: String, val tag: String, val msg: String) {
        fun render(): String = "${fmt.format(Date(ts))}  [$level]  $tag  $msg"
    }

    fun add(level: String, tag: String, msg: String) {
        synchronized(entries) {
            entries.add(Entry(System.currentTimeMillis(), level, tag, msg))
            if (entries.size > 2000) entries.removeAt(0)
        }
    }

    fun info(tag: String, msg: String) = add("I", tag, msg)
    fun warn(tag: String, msg: String) = add("W", tag, msg)
    fun err(tag: String, msg: String) = add("E", tag, msg)
    fun tool(name: String, args: String) = add("T", name, args.take(300))
    fun result(name: String, ok: Boolean, out: String) = add(if (ok) "✓" else "✗", name, out.take(500))

    fun all(): String {
        synchronized(entries) {
            return entries.joinToString("\n") { it.render() }
        }
    }

    fun clear() {
        synchronized(entries) { entries.clear() }
    }

    fun size(): Int = synchronized(entries) { entries.size }
}
