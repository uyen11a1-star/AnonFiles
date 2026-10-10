package com.anon.filemanager
import java.io.File
data class FileItem(val file: File, val name: String, val isDirectory: Boolean, val size: Long, val lastModified: Long)
