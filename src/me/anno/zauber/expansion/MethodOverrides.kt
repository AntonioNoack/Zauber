package me.anno.zauber.expansion

import me.anno.zauber.ast.rich.*
import me.anno.zauber.ast.rich.Flags.hasFlag
import me.anno.zauber.logging.LogManager
import me.anno.zauber.scope.Scope
import me.anno.zauber.scope.ScopeInitType
import me.anno.zauber.scope.ScopeType
import me.anno.zauber.typeresolution.ParameterList.Companion.resolveGenerics
import me.anno.zauber.types.Type

// todo also implement/support overridden fields, getters/setters
// todo start with high classes, and go down the hierarchy,
//  and make each class complete, s.t. all methods are present for easier resolution

// todo also check all sealed class, value class, open class, abstract class, etc having a valid type combination
object MethodOverrides {

    private val LOGGER = LogManager.getLogger(MethodOverrides::class)

    private val processedScopes = HashSet<Scope>()

    fun resolveOverrides(root: Scope) {
        processedScopes.clear()
        root.forEachScopeLazy(ScopeInitType.ADD_OVERRIDES, ::resolveOverridesImpl)
    }

    fun sameParameters(selfScope: Scope, options: List<Parameter>, expected: List<Type>): Boolean {
        if (expected.size != options.size) {
            println("size mismatch: ${expected.size} != ${options.size}")
            return false
        }
        for (index in options.indices) {
            // if (option.name != expected.name) return false // <- just a warning
            val optionType = options[index].type.resolve(selfScope)
            val expectedType = expected[index]
            if (optionType != expectedType) {
                println("type mismatch: $optionType != $expectedType")
                return false
            }
        }
        return true
    }

    private fun resolveOverridesImpl(scope: Scope) {
        if (!scope.isClassType()) return
        if (scope in processedScopes) return

        processedScopes += scope
        for (superCall in scope[ScopeInitType.ADD_OVERRIDES].superCalls) {
            val superScope = superCall.type.clazz
            resolveOverridesImpl(superScope)
            addAllMethodOverrides(scope, superCall, superScope)
            addAllFieldOverrides(scope, superScope)
        }
    }

    private fun addAllMethodOverrides(scope: Scope, superCall: SuperCall, superScope: Scope) {
        val selfMethods0 = scope[ScopeInitType.ADD_OVERRIDES].methods0.filter { !it.explicitSelfType }
        val selfMethods = selfMethods0.groupBy { it.name }
        val foundMethods = HashSet<Method>()
        // todo check that all methods with override-flag have found their partner
        val superMethods = superScope[ScopeInitType.ADD_OVERRIDES].methods0.filter { !it.explicitSelfType }
        for (method in superMethods) {
            if (method.isPrivate()) continue

            // find match
            val selfType = method.selfType ?: scope.typeWithArgs
            val methodTypeParameters = method.typeParameters.map { parameter ->
                superCall.type.typeParameters
                    .resolveGenerics(selfType, parameter.type)
                    .resolve(scope)
            }

            val methodValueParameters = method.valueParameters.map { parameter ->
                superCall.type.typeParameters
                    .resolveGenerics(selfType, parameter.type)
                    .resolve(scope)
            }

            val selfMethods = (selfMethods[method.name] ?: emptyList())
                .filter {
                    sameParameters(scope, it.typeParameters, methodTypeParameters) &&
                            sameParameters(scope, it.valueParameters, methodValueParameters)
                }
            if (selfMethods.size > 1) {
                LOGGER.warn("Expected only one candidate for $method in $scope, but found $selfMethods")
            }

            val isOpen = method.flags.hasFlag(Flags.OPEN) ||
                    method.flags.hasFlag(Flags.OVERRIDE) ||
                    superScope.isInterface()
            val isExplicitlyClosed = method.flags.hasFlag(Flags.FINAL)

            val selfMethod = selfMethods.firstOrNull()
            if (selfMethod == null) {

                // println("adding ${method.name} to $scope, options: ${scope.methods0.map { it.name }}")

                // somehow create a new method linking to the old one
                val newScope = scope.generate("f:${method.name}", ScopeType.METHOD)
                newScope.typeParameters = method.typeParameters
                newScope.hasTypeParameters = true
                newScope.selfAsMethod = method

            } else {
                foundMethods.add(selfMethod)
                if (false) check(selfMethod.flags.hasFlag(Flags.OVERRIDE)) {
                    "Expected $scope.$selfMethod to have override flag for $superScope.$method"
                }
                if (false) check(isOpen && !isExplicitlyClosed) {
                    "$scope.$selfMethod cannot both be open and closed, got ${Flags.toString(selfMethod.flags)}"
                }

                method.overriddenMembers += selfMethod
                selfMethod.overriddenBy += method
            }
        }
        for (method in selfMethods0) {
            if (method.flags.hasFlag(Flags.OVERRIDE) && method !in foundMethods) {
                LOGGER.warn("No base-method found for $method in $scope")
            }
        }
    }

    private fun addAllFieldOverrides(scope: Scope, superScope: Scope) {
        val selfFields0 = scope.fields.filter { !it.explicitSelfType }
        val foundFields = HashSet<Field>()
        // todo check that all methods with override-flag have found their partner
        val superFields = superScope.fields.filter { !it.explicitSelfType }
        for (field in superFields) {
            if (field.isPrivate()) continue

            // find match
            val selfField = selfFields0.firstOrNull { it.name == field.name }

            val isOpen = field.flags.hasFlag(Flags.OPEN) ||
                    field.flags.hasFlag(Flags.OVERRIDE) ||
                    superScope.isInterface()
            val isExplicitlyClosed = field.flags.hasFlag(Flags.FINAL)

            if (selfField == null) {
                // somehow create a new method linking to the old one
                scope.addField(field)
            } else {
                foundFields.add(selfField)
                if (false) check(selfField.flags.hasFlag(Flags.OVERRIDE)) {
                    "Expected $scope.$selfField to have override flag for $superScope.$field"
                }
                if (false) check(isOpen && !isExplicitlyClosed) {
                    "$scope.$selfField cannot both be open and closed, got ${Flags.toString(selfField.flags)}"
                }

                field.overriddenMembers += selfField
                selfField.overriddenBy += field
            }
        }
        for (field in selfFields0) {
            if (field.flags.hasFlag(Flags.OVERRIDE) && field !in foundFields) {
                LOGGER.warn("No base-method found for $field in $scope")
            }
        }
    }

}