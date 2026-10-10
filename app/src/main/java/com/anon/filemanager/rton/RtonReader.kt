package com.anon.filemanager.rton

import java.io.ByteArrayInputStream
import java.io.File
import java.io.InputStream

object RtonReader {

    fun isRton(file: File): Boolean {
        if (file.length() < 8) return false
        return try {
            file.inputStream().use {
                val buf = ByteArray(4)
                if (it.read(buf) != 4) return false
                String(buf, Charsets.US_ASCII) == "RTON"
            }
        } catch (e: Exception) { false }
    }

    fun read(file: File): RtonValue {
        val bytes = file.readBytes()
        try {
            return readStream(ByteArrayInputStream(bytes))
        } catch (e: Exception) {
            val preview = bytes.take(64).joinToString(" ") { "%02X".format(it.toInt() and 0xFF) }
            val ascii = bytes.take(64).map { b ->
                val c = b.toInt() and 0xFF
                if (c in 32..126) c.toChar() else '.'
            }.joinToString("")
            throw RuntimeException("${e.message}\n\n64B hex:\n$preview\n\nASCII:\n$ascii")
        }
    }

    fun readStream(input: InputStream): RtonValue {
        val magic = ByteArray(4)
        if (input.read(magic) != 4) error("Không đủ dữ liệu")
        val magicStr = String(magic, Charsets.US_ASCII)
        if (magicStr != "RTON") error("Không phải RTON: '$magicStr' (hex: ${magic.joinToString(" ") { "%02X".format(it.toInt() and 0xFF) }})")

        val version = readInt32LE(input)
        if (version !in 1..10) error("Version bất thường: $version (0x${Integer.toHexString(version)})")

        val stringCount = readInt32LE(input)
        if (stringCount < 0 || stringCount > 100000)
            error("String count bất thường: $stringCount (0x${Integer.toHexString(stringCount)})")

        val strings = ArrayList<String>(stringCount)
        repeat(stringCount) {
            val len = readInt16LE(input)
            if (len < 0 || len > 4096) error("String len bất thường: $len")
            val buf = ByteArray(len)
            var read = 0
            while (read < len) {
                val n = input.read(buf, read, len - read)
                if (n < 0) error("String table cắt ngang")
                read += n
            }
            strings.add(String(buf, Charsets.UTF_8))
        }

        val root = readValue(input, strings)
        val done = ByteArray(4)
        input.read(done)
        return root
    }

    private fun readValue(input: InputStream, strings: List<String>): RtonValue {
        val tag = input.read()
        if (tag < 0) error("Hết stream khi đọc value tag")
        return when (tag) {
            0x00 -> RtonValue.None
            0x01 -> RtonValue.Bool(input.read() != 0)
            0x02 -> RtonValue.Int(readSignedVarint(input))
            0x03 -> {
                val b = ByteArray(4); readFully(input, b)
                val bits = (b[0].toInt() and 0xFF) or ((b[1].toInt() and 0xFF) shl 8) or
                           ((b[2].toInt() and 0xFF) shl 16) or ((b[3].toInt() and 0xFF) shl 24)
                RtonValue.Float(kotlin.Float.fromBits(bits))
            }
            0x04 -> {
                val idx = input.read() and 0xFF
                RtonValue.Str(strings.getOrElse(idx) { "" })
            }
            0x05 -> {
                val count = readSignedVarint(input).toInt()
                val arr = ArrayList<RtonValue>(count.coerceAtLeast(0))
                repeat(count) { arr.add(readValue(input, strings)) }
                RtonValue.Arr(arr)
            }
            0x06 -> {
                val count = readSignedVarint(input).toInt()
                val map = LinkedHashMap<String, RtonValue>(count.coerceAtLeast(0))
                repeat(count) {
                    val keyIdx = input.read() and 0xFF
                    val key = strings.getOrElse(keyIdx) { "?" }
                    map[key] = readValue(input, strings)
                }
                RtonValue.Obj(map)
            }
            else -> error("Tag không xác định: 0x${Integer.toHexString(tag)}")
        }
    }

    private fun readFully(input: InputStream, buf: ByteArray) {
        var r = 0
        while (r < buf.size) {
            val n = input.read(buf, r, buf.size - r)
            if (n < 0) error("Hết stream")
            r += n
        }
    }

    private fun readInt32LE(input: InputStream): Int {
        val b = ByteArray(4); readFully(input, b)
        return (b[0].toInt() and 0xFF) or ((b[1].toInt() and 0xFF) shl 8) or
               ((b[2].toInt() and 0xFF) shl 16) or ((b[3].toInt() and 0xFF) shl 24)
    }
    private fun readInt16LE(input: InputStream): Int {
        val b = ByteArray(2); readFully(input, b)
        return (b[0].toInt() and 0xFF) or ((b[1].toInt() and 0xFF) shl 8)
    }

    private fun readSignedVarint(input: InputStream): Long {
        var result = 0L
        var shift = 0
        while (shift <= 63) {
            val b = input.read()
            if (b < 0) error("Varint cắt ngang")
            result = result or ((b.toLong() and 0x7F) shl shift)
            shift += 7
            if (b and 0x80 == 0) {
                if (shift < 64 && (b and 0x40) != 0) result = result or (-1L shl shift)
                return result
            }
        }
        error("Varint quá dài")
    }
}
