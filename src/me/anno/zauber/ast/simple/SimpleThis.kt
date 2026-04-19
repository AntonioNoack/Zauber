package me.anno.zauber.ast.simple

import me.anno.zauber.scope.Scope

data class SimpleThis(val scope: Scope, val isExplicitSelf: Boolean) {
    init {
        if (scope.isObjectLike()) {
            throw IllegalArgumentException("SimpleThis[$scope] should use SimpleGetObject instead")
        }
    }
}