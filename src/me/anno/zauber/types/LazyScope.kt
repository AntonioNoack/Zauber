package me.anno.zauber.types

class LazyScope(
    val fileName: String, val name: String,
    val scopeType: ScopeType?, val scope: Lazy<Scope>
)