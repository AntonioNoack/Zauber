package me.anno.zauber.scope.lazy

import me.anno.zauber.scope.Scope
import me.anno.zauber.scope.ScopeType

class LazyScope(
    val fileName: String, val name: String,
    val scopeType: ScopeType?, val scope: Lazy<Scope>
)