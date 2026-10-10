package com.anon.filemanager

import java.io.File

data class FileItem(
    val file: File,
    val name: String,
    val isDirectory: Boolean,
    val size: Long,
    val lastModified: Long,
    val isProtected: Boolean = false
) {
    val extension: String
        get() = name.substringAfterLast('.', "").lowercase()

    val isArchive: Boolean
        get() = extension in setOf("zip", "7z", "rar", "tar", "gz", "tgz", "bz2", "xz", "jar")

    val isApk: Boolean
        get() = extension == "apk"

    val isText: Boolean
        get() = extension in setOf(
            "txt", "log", "json", "xml", "html", "htm", "css", "js", "kt", "java",
            "py", "sh", "md", "yml", "yaml", "ini", "cfg", "conf", "properties",
            "cpp", "h", "hpp", "c", "cs", "rs", "go", "ts", "php", "rb", "sql"
        )
}
