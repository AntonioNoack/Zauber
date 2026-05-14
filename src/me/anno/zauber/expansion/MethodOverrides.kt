package me.anno.zauber.expansion

import me.anno.utils.ResetThreadLocal.Companion.threadLocal
import me.anno.zauber.ast.rich.*
import me.anno.zauber.ast.rich.Flags.hasFlag
import me.anno.zauber.logging.LogManager
import me.anno.zauber.scope.Scope
import me.anno.zauber.scope.ScopeInit
import me.anno.zauber.scope.ScopeInitType
import me.anno.zauber.scope.ScopeType
import me.anno.zauber.typeresolution.ParameterList.Companion.resolveGenerics
import me.anno.zauber.types.Type

// also implement/support overridden fields, getters/setters
// start with high classes, and go down the hierarchy,
//  and make each class complete, s.t. all methods are present for easier resolution

// todo also check all sealed class, value class, open class, abstract class, etc having a valid type combination
object MethodOverrides {

    private val LOGGER = LogManager.getLogger(MethodOverrides::class)
    private val processedScopes by threadLocal { HashSet<Scope>() }

    val methodOverrideCreator = ScopeInit(ScopeInitType.ADD_OVERRIDES) { scope: Scope ->
        resolveOverrides(scope)
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

    private fun resolveOverrides(scope: Scope) {
        if (!scope.isClassLike() || scope.scopeType == ScopeType.PACKAGE) return
        if (!processedScopes.add(scope)) return

        scope[ScopeInitType.ADD_OVERRIDES]

        val selfMethods0 = scope.methods0
        val selfMethods = selfMethods0.groupBy { it.name }
        val foundMethods = HashSet<Method>()

        val selfFields0 = scope.fields
        val foundFields = HashSet<Field>()

        for (superCall in scope.superCalls) {
            val superScope = superCall.type.clazz
            resolveOverrides(superScope)
            addAllMethodOverrides(scope, superCall, superScope, selfMethods, foundMethods)
            addAllFieldOverrides(scope, superScope, selfFields0, foundFields)
        }

        // check that all methods with override-flag have found their partner
        for (method in selfMethods0) {
            if (method.flags.hasFlag(Flags.OVERRIDE) && method !in foundMethods) {
                LOGGER.warn("No base-method found for $scope: $method, found: $foundMethods")
            }
        }

        for (field in selfFields0) {
            if (field.flags.hasFlag(Flags.OVERRIDE) && field !in foundFields) {
                LOGGER.warn("No base-field found for $field in $scope")
            }
        }
    }

    private fun addAllMethodOverrides(
        scope: Scope, superCall: SuperCall, superScope: Scope,
        selfMethods: Map<String, List<Method>>,
        foundMethods: HashSet<Method>
    ) {
        val superMethods = superScope[ScopeInitType.ADD_OVERRIDES].methods0.filter { !it.explicitSelfType }
        for (superMethod in superMethods) {
            if (superMethod.isPrivate()) continue

            // find match
            val selfType = superMethod.selfType ?: scope.typeWithArgs
            val methodTypeParameters = superMethod.typeParameters.map { parameter ->
                superCall.type.typeParameters
                    .resolveGenerics(selfType, parameter.type)
                    .resolve(scope)
            }

            val methodValueParameters = superMethod.valueParameters.map { parameter ->
                superCall.type.typeParameters
                    .resolveGenerics(selfType, parameter.type)
                    .resolve(scope)
            }

            val selfMethods = (selfMethods[superMethod.name] ?: emptyList())
                .filter {
                    sameParameters(scope, it.typeParameters, methodTypeParameters) &&
                            sameParameters(scope, it.valueParameters, methodValueParameters)
                }

            if (selfMethods.size > 1) {
                LOGGER.warn("Expected only one candidate for $superMethod in $scope, but found $selfMethods")
            }

            val isOpen = superMethod.flags.hasFlag(Flags.OPEN) ||
                    superMethod.flags.hasFlag(Flags.OVERRIDE) ||
                    superScope.isInterface()

            val isExplicitlyClosed = superMethod.flags.hasFlag(Flags.FINAL)

            val selfMethod = selfMethods.firstOrNull()
            if (selfMethod == null) {

                // println("adding ${method.name} from $superScope to $scope, options: ${scope.methods0.map { it.name }}")

                // somehow create a new method linking to the old one
                val newScope = scope.generate("f:${superMethod.name}", ScopeType.METHOD)
                newScope.setTypeParams(superMethod.typeParameters)
                newScope.selfAsMethod = superMethod

            } else {

                foundMethods.add(selfMethod)

                if (false) check(selfMethod.flags.hasFlag(Flags.OVERRIDE)) {
                    "Expected $scope.$selfMethod to have override flag for $superScope.$superMethod"
                }
                if (false) check(isOpen && !isExplicitlyClosed) {
                    "$scope.$selfMethod cannot both be open and closed, got ${Flags.toString(selfMethod.flags)}"
                }

                superMethod.overriddenFor += selfMethod
                selfMethod.overriddenBy += superMethod
            }
        }
    }

    private fun addAllFieldOverrides(
        scope: Scope, superScope: Scope,
        selfFields0: List<Field>,
        foundFields: HashSet<Field>
    ) {
        val superFields = superScope.fields.filter { !it.explicitSelfType }
        for (superField in superFields) {
            if (superField.isPrivate()) continue

            // find match
            val selfField = selfFields0.firstOrNull { it.name == superField.name }

            val isOpen = superField.flags.hasFlag(Flags.OPEN) ||
                    superField.flags.hasFlag(Flags.OVERRIDE) ||
                    superScope.isInterface()
            val isExplicitlyClosed = superField.flags.hasFlag(Flags.FINAL)

            if (selfField == null) {
                // somehow create a new method linking to the old one
                scope.addField(superField)
            } else {
                foundFields.add(selfField)
                if (false) check(selfField.flags.hasFlag(Flags.OVERRIDE)) {
                    "Expected $scope.$selfField to have override flag for $superScope.$superField"
                }
                if (false) check(isOpen && !isExplicitlyClosed) {
                    "$scope.$selfField cannot both be open and closed, got ${Flags.toString(selfField.flags)}"
                }

                superField.overriddenFor += selfField
                selfField.overriddenBy += superField
            }
        }
    }

}