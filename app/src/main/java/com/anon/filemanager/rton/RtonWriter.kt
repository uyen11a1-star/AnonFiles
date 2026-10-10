package com.anon.filemanager.rton

import java.io.File
import java.io.OutputStream

object RtonWriter {

    fun write(file: File, root: RtonValue) {
        // Thu thập string table theo thứ tự xuất hiện
        val table = LinkedHashSet<String>()
        collectStrings(root, table)
        val stringList = table.toList()
        if (stringList.size > 256) error("String table >256, RTON v1 không hỗ trợ")
        val index = HashMap<String, Int>(stringList.size)
        stringList.forEachIndexed { i, s -> index[s] = i }

        file.outputStream().buffered().use { out ->
            out.write("RTON".toByteArray(Charsets.US_ASCII))
            writeInt32LE(out, 1)
            writeInt32LE(out, stringList.size)
            for (s in stringList) {
                val bytes = s.toByteArray(Charsets.UTF_8)
                writeInt16LE(out, bytes.size)
                out.write(bytes)
            }
            writeValue(out, root, index)
            out.write("DONE".toByteArray(Charsets.US_ASCII))
        }
    }

    private fun collectStrings(v: RtonValue, set: MutableSet<String>) {
        when (v) {
            is RtonValue.Obj -> v.entries.forEach { (k, vv) -> set.add(k); collectStrings(vv, set) }
            is RtonValue.Arr -> v.items.forEach { collectStrings(it, set) }
            is RtonValue.Str -> set.add(v.value)
            else -> {}
        }
    }

    private fun writeValue(out: OutputStream, v: RtonValue, si: Map<String, Int>) {
        when (v) {
            is RtonValue.None -> out.write(0x00)
            is RtonValue.Bool -> { out.write(0x01); out.write(if (v.value) 1 else 0) }
            is RtonValue.Int -> { out.write(0x02); writeSignedVarint(out, v.value) }
            is RtonValue.Float -> {
                out.write(0x03)
                val bits = v.value.toRawBits()
                out.write(bits and 0xFF)
                out.write((bits ushr 8) and 0xFF)
                out.write((bits ushr 16) and 0xFF)
                out.write((bits ushr 24) and 0xFF)
            }
            is RtonValue.Str -> {
                out.write(0x04)
                out.write(si[v.value] ?: 0)
            }
            is RtonValue.Arr -> {
                out.write(0x05)
                writeSignedVarint(out, v.items.size.toLong())
                v.items.forEach { writeValue(out, it, si) }
            }
            is RtonValue.Obj -> {
                out.write(0x06)
                writeSignedVarint(out, v.entries.size.toLong())
                for ((k, vv) in v.entries) {
                    out.write(si[k] ?: 0)
                    writeValue(out, vv, si)
                }
            }
        }
    }

    private fun writeInt32LE(out: OutputStream, v: Int) {
        out.write(v and 0xFF)
        out.write((v ushr 8) and 0xFF)
        out.write((v ushr 16) and 0xFF)
        out.write((v ushr 24) and 0xFF)
    }
    private fun writeInt16LE(out: OutputStream, v: Int) {
        out.write(v and 0xFF)
        out.write((v ushr 8) and 0xFF)
    }

    private fun writeSignedVarint(out: OutputStream, value: Long) {
        var v = value
        while (true) {
            val b = (v and 0x7F).toInt()
            v = v shr 7 // arithmetic shift cho signed
            val done = (v == 0L && (b and 0x40) == 0) || (v == -1L && (b and 0x40) != 0)
            if (done) { out.write(b); return }
            out.write(b or 0x80)
        }
    }
}
