package com.anon.filemanager.rton

data class RtonNode(
    val path: List<String>,   // đường dẫn key từ root
    val key: String,          // tên field (hoặc index cho array)
    val value: RtonValue,     // giá trị tại node này
    val depth: Int,           // độ sâu để indent
    val indexInParent: Int = -1 // cho array
) {
    val isLeaf: Boolean
        get() = value is RtonValue.Int || value is RtonValue.Float ||
                value is RtonValue.Str || value is RtonValue.Bool || value is RtonValue.None

    val displayName: String
        get() = if (indexInParent >= 0) "[$indexInParent]" else key

    val typeTag: String
        get() = when (value) {
            is RtonValue.None -> "none"
            is RtonValue.Bool -> "bool"
            is RtonValue.Int -> "int"
            is RtonValue.Float -> "float"
            is RtonValue.Str -> "str"
            is RtonValue.Arr -> "array[${value.items.size}]"
            is RtonValue.Obj -> "obj{${value.entries.size}}"
        }

    val valuePreview: String
        get() = when (value) {
            is RtonValue.None -> "—"
            is RtonValue.Bool -> value.value.toString()
            is RtonValue.Int -> value.value.toString()
            is RtonValue.Float -> value.value.toString()
            is RtonValue.Str -> if (value.value.length > 60) value.value.take(60) + "…" else value.value
            is RtonValue.Arr -> ""
            is RtonValue.Obj -> ""
        }
}
