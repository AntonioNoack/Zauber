package me.anno.zauber.expansion

import me.anno.zauber.ast.rich.parameter.SuperCall
import me.anno.zauber.scope.Scope
import me.anno.zauber.scope.ScopeInit
import me.anno.zauber.scope.ScopeInitType
import me.anno.zauber.scope.ScopeType
import me.anno.zauber.types.Types

object AddSuperCallToPackages {
    val addSuperCallToPackages = ScopeInit(ScopeInitType.DISCOVER_MEMBERS) { scope: Scope ->
        if (scope.scopeType == ScopeType.PACKAGE && scope.superCalls.isEmpty()) {
            scope.superCalls.add(SuperCall(Types.Any, emptyList(), null, -1))
        }
    }
}