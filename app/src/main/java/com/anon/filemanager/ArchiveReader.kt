package com.anon.filemanager

import com.github.junrar.Archive
import net.lingala.zip4j.ZipFile
import org.apache.commons.compress.archivers.sevenz.SevenZFile
import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.io.InputStream

object ArchiveReader {

    fun listEntries(path: String): List<ArchiveEntryItem> {
        val lower = path.lowercase()
        return try {
            when {
                lower.endsWith(".zip") || lower.endsWith(".jar") -> listZip(path)
                lower.endsWith(".7z") -> list7z(path)
                lower.endsWith(".rar") -> listRar(path)
                lower.endsWith(".tar.gz") || lower.endsWith(".tgz") -> listTarGz(path)
                else -> emptyList()
            }
        } catch (e: Exception) { emptyList() }
    }

    private fun listZip(path: String): List<ArchiveEntryItem> {
        val zf = ZipFile(path)
        return zf.fileHeaders.mapIndexed { i, h ->
            ArchiveEntryItem(
                name = h.fileName,
                size = h.uncompressedSize,
                compressedSize = h.compressedSize,
                isDirectory = h.isDirectory,
                index = i
            )
        }.sortedWith(compareByDescending<ArchiveEntryItem> { it.isDirectory }.thenBy { it.name })
    }

    private fun list7z(path: String): List<ArchiveEntryItem> {
        val result = mutableListOf<ArchiveEntryItem>()
        SevenZFile(File(path)).use { sz ->
            var i = 0
            var entry = sz.nextEntry
            while (entry != null) {
                result.add(ArchiveEntryItem(
                    name = entry.name,
                    size = entry.size,
                    compressedSize = entry.size,
                    isDirectory = entry.isDirectory,
                    index = i++
                ))
                entry = sz.nextEntry
            }
        }
        return result.sortedWith(compareByDescending<ArchiveEntryItem> { it.isDirectory }.thenBy { it.name })
    }

    private fun listRar(path: String): List<ArchiveEntryItem> {
        val result = mutableListOf<ArchiveEntryItem>()
        val ar = Archive(File(path))
        try {
            ar.fileHeaders.forEachIndexed { i, h ->
                result.add(ArchiveEntryItem(
                    name = h.fileName ?: "",
                    size = h.fullUnpackSize,
                    compressedSize = h.fullPackSize,
                    isDirectory = h.isDirectory,
                    index = i
                ))
            }
        } finally {
            ar.close()
        }
        return result.sortedWith(compareByDescending<ArchiveEntryItem> { it.isDirectory }.thenBy { it.name })
    }

    private fun listTarGz(path: String): List<ArchiveEntryItem> {
        val result = mutableListOf<ArchiveEntryItem>()
        FileInputStream(path).use { fis ->
            GzipCompressorInputStream(BufferedInputStream(fis)).use { gz ->
                TarArchiveInputStream(gz).use { tar ->
                    var i = 0
                    var e: TarArchiveEntry? = tar.nextTarEntry
                    while (e != null) {
                        result.add(ArchiveEntryItem(
                            name = e.name,
                            size = e.size,
                            compressedSize = e.size,
                            isDirectory = e.isDirectory,
                            index = i++
                        ))
                        e = tar.nextTarEntry
                    }
                }
            }
        }
        return result.sortedWith(compareByDescending<ArchiveEntryItem> { it.isDirectory }.thenBy { it.name })
    }

    fun extractEntry(archivePath: String, entryName: String, outFile: File): Boolean {
        val lower = archivePath.lowercase()
        return try {
            when {
                lower.endsWith(".zip") || lower.endsWith(".jar") -> {
                    val zf = ZipFile(archivePath)
                    val header = zf.getFileHeader(entryName) ?: return false
                    zf.getInputStream(header).use { input ->
                        outFile.outputStream().use { input.copyTo(it) }
                    }
                    true
                }
                lower.endsWith(".7z") -> {
                    SevenZFile(File(archivePath)).use { sz ->
                        var e = sz.nextEntry
                        while (e != null) {
                            if (e.name == entryName) {
                                outFile.outputStream().use { out ->
                                    val buf = ByteArray(8192)
                                    var n: Int
                                    while (sz.read(buf).also { n = it } > 0) {
                                        out.write(buf, 0, n)
                                    }
                                }
                                return true
                            }
                            e = sz.nextEntry
                        }
                    }
                    false
                }
                lower.endsWith(".tar.gz") || lower.endsWith(".tgz") -> {
                    FileInputStream(archivePath).use { fis ->
                        GzipCompressorInputStream(BufferedInputStream(fis)).use { gz ->
                            TarArchiveInputStream(gz).use { tar ->
                                var e = tar.nextTarEntry
                                while (e != null) {
                                    if (e.name == entryName && !e.isDirectory) {
                                        outFile.outputStream().use { out -> tar.copyTo(out) }
                                        return true
                                    }
                                    e = tar.nextTarEntry
                                }
                            }
                        }
                    }
                    false
                }
                else -> false
            }
        } catch (e: Exception) { false }
    }

    fun extractEntryToDir(archivePath: String, entryName: String, outDir: String): String {
        return try {
            val out = File(outDir, entryName)
            out.parentFile?.mkdirs()
            if (extractEntry(archivePath, entryName, out)) "" else "Không extract được: $entryName"
        } catch (e: Exception) { e.message ?: "Lỗi" }
    }
}
