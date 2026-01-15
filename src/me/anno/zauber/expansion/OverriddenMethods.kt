package me.anno.zauber.expansion

import me.anno.zauber.ast.rich.*
import me.anno.zauber.ast.rich.Keywords.hasFlag
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
            addAllMethodOverrides(scope, superCall, superScope)
            addAllFieldOverrides(scope, superScope)
        }
    }

    private fun addAllMethodOverrides(scope: Scope, superCall: SuperCall, superScope: Scope) {
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

    private fun addAllFieldOverrides(scope: Scope, superScope: Scope) {
        val selfFields0 = scope.fields.filter { !it.explicitSelfType }
        val foundFields = HashSet<Field>()
        // todo check that all methods with override-flag have found their partner
        for (field in superScope.fields.filter { !it.explicitSelfType }) {
            if (field.isPrivate()) continue

            // find match
            val selfField = selfFields0.firstOrNull { it.name == field.name }

            val isOpen = field.keywords.hasFlag(Keywords.OPEN) ||
                    field.keywords.hasFlag(Keywords.OVERRIDE) ||
                    superScope.isInterface()
            val isExplicitlyClosed = field.keywords.hasFlag(Keywords.FINAL)

            if (selfField == null) {
                // somehow create a new method linking to the old one
                scope.addField(field)
            } else {
                foundFields.add(selfField)
                if (false) check(selfField.keywords.hasFlag(Keywords.OVERRIDE)) {
                    "Expected $scope.$selfField to have override flag for $superScope.$field"
                }
                if (false) check(isOpen && !isExplicitlyClosed) {
                    "$scope.$selfField cannot both be open and closed, got ${Keywords.toString(selfField.keywords)}"
                }

                field.overriddenFields += selfField
                selfField.overriddenBy += field
            }
        }
        for (field in selfFields0) {
            if (field.keywords.hasFlag(Keywords.OVERRIDE) && field !in foundFields) {
                LOGGER.warn("No base-method found for $field in $scope")
            }
        }
    }

}