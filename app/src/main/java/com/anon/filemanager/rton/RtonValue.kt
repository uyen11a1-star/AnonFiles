package com.anon.filemanager.rton

sealed class RtonValue {
    object None : RtonValue()
    data class Bool(val value: Boolean) : RtonValue()
    data class Int(val value: Long) : RtonValue()
    data class Float(val value: kotlin.Float) : RtonValue()
    data class Str(val value: String) : RtonValue()
    data class Arr(val items: MutableList<RtonValue>) : RtonValue()
    data class Obj(val entries: LinkedHashMap<String, RtonValue>) : RtonValue()
}
