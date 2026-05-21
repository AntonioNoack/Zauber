package me.anno.zauber.ast.simple

import me.anno.zauber.scope.Scope

@Deprecated("Use SimpleField with an attached SelfConstantRef")
data class SimpleThis(val scope: Scope, val isExplicitSelf: Boolean) {
    init {
        if (scope.isObjectLike()) {
            throw IllegalArgumentException("SimpleThis[$scope] should use SimpleGetObject instead")
        }
    }
}