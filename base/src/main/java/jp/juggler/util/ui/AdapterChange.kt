package jp.juggler.util.ui

enum class AdapterChangeType {
    RangeInsert,
    RangeRemove,
    RangeChange,
}

class AdapterChange(val type: AdapterChangeType, val listIndex: Int, val count: Int = 1)
