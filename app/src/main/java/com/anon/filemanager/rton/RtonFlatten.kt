package com.anon.filemanager.rton

object RtonFlatten {

    fun flatten(root: RtonValue): List<RtonNode> {
        val out = mutableListOf<RtonNode>()
        walk(root, emptyList(), "", 0, -1, out)
        return out
    }

    private fun walk(v: RtonValue, path: List<String>, key: String, depth: Int, idx: Int, out: MutableList<RtonNode>) {
        when (v) {
            is RtonValue.Obj -> {
                out.add(RtonNode(path, key.ifBlank { "root" }, v, depth, idx))
                for ((k, vv) in v.entries) {
                    walk(vv, path + k, k, depth + 1, -1, out)
                }
            }
            is RtonValue.Arr -> {
                out.add(RtonNode(path, key.ifBlank { "array" }, v, depth, idx))
                v.items.forEachIndexed { i, vv ->
                    walk(vv, path + i.toString(), "item$i", depth + 1, i, out)
                }
            }
            else -> out.add(RtonNode(path, key, v, depth, idx))
        }
    }

    // Đặt giá trị mới theo path. Path là list key/index từ root.
    fun setValue(root: RtonValue, path: List<String>, newValue: RtonValue): RtonValue {
        if (path.isEmpty()) return newValue
        return when (root) {
            is RtonValue.Obj -> {
                val head = path.first()
                val tail = path.drop(1)
                val entries = LinkedHashMap(root.entries)
                val child = entries[head] ?: return root
                entries[head] = setValue(child, tail, newValue)
                RtonValue.Obj(entries)
            }
            is RtonValue.Arr -> {
                val i = path.first().toIntOrNull() ?: return root
                if (i !in root.items.indices) return root
                val items = root.items.toMutableList()
                items[i] = setValue(items[i], path.drop(1), newValue)
                RtonValue.Arr(items)
            }
            else -> root
        }
    }
}
