package me.anno.zauber.typeresolution

import me.anno.zauber.typeresolution.TypeResolution.forEachScope
import me.anno.zauber.types.Scope

object NameResolution {
    fun resolveNameExpressions(root: Scope) {
        forEachScope(root, ::resolveNameExpressions)
    }

    private fun resolveNameExpressionsImpl(scope: Scope) {
        TODO("Resolve all NameExpressions inside $scope")
    }
}