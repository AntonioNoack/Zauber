package me.anno.zauber.scope

class ScopeInit(val type: ScopeInitType, val runnable: (Scope) -> Unit) : Comparable<ScopeInit> {
    override fun compareTo(other: ScopeInit): Int = other.type.compareTo(type) // descending
}