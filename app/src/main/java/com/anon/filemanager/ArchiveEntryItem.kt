package com.anon.filemanager

data class ArchiveEntryItem(
    val name: String,
    val size: Long,
    val compressedSize: Long,
    val isDirectory: Boolean,
    val index: Int
)
