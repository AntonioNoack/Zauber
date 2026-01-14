package me.anno.zauber.expansion

import me.anno.zauber.ast.rich.Keywords
import me.anno.zauber.ast.rich.Keywords.hasFlag
import me.anno.zauber.ast.rich.Keywords.withFlag
import me.anno.zauber.ast.rich.Keywords.withoutFlag
import me.anno.zauber.ast.rich.Method
import me.anno.zauber.ast.rich.Parameter
import me.anno.zauber.ast.rich.SuperCall
import me.anno.zauber.logging.LogManager
import me.anno.zauber.typeresolution.TypeResolution.forEachScope
import me.anno.zauber.typeresolution.members.ResolvedMember.Companion.resolveGenerics
import me.anno.zauber.types.Scope
import me.anno.zauber.types.ScopeType

// todo also implement/support overridden fields, getters/setters
// todo start with high classes, and go down the hierarchy,
//  and make each class complete, s.t. all methods are present for easier resolution

// todo also check all sealed class, value class, open class, abstract class, etc having a valid type combination
object OverriddenMethods {

    private val LOGGER = LogManager.getLogger(OverriddenMethods::class)

    private val processedScopes = HashSet<Scope>()

    fun resolveOverrides(root: Scope) {
        processedScopes.clear()
        forEachScope(root, ::resolveOverridesImpl)
    }

    private fun resolveOverridesImpl(scope: Scope) {
        if (!scope.isClassType()) return
        if (scope in processedScopes) return

        processedScopes += scope
        for (superCall in scope.superCalls) {
            val superScope = superCall.type.clazz
            resolveOverridesImpl(superScope)
            addAllOverrides(scope, superCall, superScope)
        }
    }

    private fun addAllOverrides(scope: Scope, superCall: SuperCall, superScope: Scope) {
        val selfMethods0 = scope.methods.filter { !it.explicitSelfType }
        val selfMethods = selfMethods0.groupBy { it.name }
        val foundMethods = HashSet<Method>()
        // todo check that all methods with override-flag have found their partner
        for (method in superScope.methods.filter { !it.explicitSelfType }) {
            if (method.isPrivate()) continue

            // todo find match
            val methodValueParameters = method.valueParameters.map {
                val newType = resolveGenerics(
                    method.selfType ?: scope.typeWithArgs, it.type,
                    superScope.typeParameters,
                    superCall.type.typeParameters
                )
                Parameter(it.name, newType, it.scope, it.origin)
            }

            val selfMethods = (selfMethods[method.name] ?: emptyList())
                .filter {
                    it.typeParameters == method.typeParameters &&
                            it.valueParameters == methodValueParameters
                }
            if (selfMethods.size > 1) {
                LOGGER.warn("Expected only one candidate for $method in $scope, but found $selfMethods")
            }

            val isOpen = method.keywords.hasFlag(Keywords.OPEN) ||
                    method.keywords.hasFlag(Keywords.OVERRIDE) ||
                    superScope.isInterface()
            val isExplicitlyClosed = method.keywords.hasFlag(Keywords.FINAL)

            val selfMethod = selfMethods.firstOrNull()
            if (selfMethod == null) {
                // somehow create a new method linking to the old one
                val newScope = scope.generate("f:${method.name}", ScopeType.METHOD)
                newScope.selfAsMethod = method
            } else {
                foundMethods.add(selfMethod)
                if (false) check(selfMethod.keywords.hasFlag(Keywords.OVERRIDE)) {
                    "Expected $scope.$selfMethod to have override flag for $superScope.$method"
                }
                if (false) check(isOpen && !isExplicitlyClosed) {
                    "$scope.$selfMethod cannot both be open and closed, got ${Keywords.toString(selfMethod.keywords)}"
                }

                method.overriddenMethods += selfMethod
                selfMethod.overriddenBy += method
            }
        }
        for (method in selfMethods0) {
            if (method.keywords.hasFlag(Keywords.OVERRIDE) && method !in foundMethods) {
                LOGGER.warn("No base-method found for $method in $scope")
            }
        }
    }

}