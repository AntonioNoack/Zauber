package me.anno.zauber.typeresolution.members

import me.anno.zauber.ast.rich.Parameter
import me.anno.zauber.logging.LogManager
import me.anno.zauber.typeresolution.CallWithNames.resolveNamedParameters
import me.anno.zauber.typeresolution.Inheritance.isSubTypeOf
import me.anno.zauber.typeresolution.InsertMode
import me.anno.zauber.typeresolution.ParameterList
import me.anno.zauber.typeresolution.ResolutionContext
import me.anno.zauber.typeresolution.TypeResolution.catchFailures
import me.anno.zauber.typeresolution.TypeResolution.langScope
import me.anno.zauber.typeresolution.ValueParameter
import me.anno.zauber.types.Scope
import me.anno.zauber.types.ScopeType
import me.anno.zauber.types.Type
import me.anno.zauber.types.Types.UnitType
import me.anno.zauber.types.impl.ClassType

abstract class MemberResolver<Resource, Resolved : ResolvedMember<Resource>> {

    companion object {
        private val LOGGER = LogManager.getLogger(MemberResolver::class)

        fun findGenericsForMatch(
            expectedSelfType: Type?,
            actualSelfType: Type?,

            expectedReturnType: Type?, /* null if nothing is expected */
            actualReturnType: Type?, // can help deducting types

            expectedTypeParameters: List<Parameter>,
            actualTypeParameters: ParameterList?,

            expectedValueParameters: List<Parameter>,
            actualValueParameters: List<ValueParameter>
        ): ParameterList? { // found generic values for a match

            if (expectedSelfType is ClassType && !expectedSelfType.clazz.isClassType()) {
                throw IllegalArgumentException("Expected type cannot be $expectedSelfType, because type is unknown")
            }

            // todo objects don't need actualSelfType, if properly in scope or imported...
            if ((expectedSelfType != null) != (actualSelfType != null)) {
                LOGGER.info("SelfType mismatch: $expectedSelfType vs $actualSelfType")
                return null
            }

            LOGGER.info("Checking types: $expectedTypeParameters vs $actualTypeParameters")
            LOGGER.info("  and   values: $expectedValueParameters vs $actualValueParameters")
            LOGGER.info("  and   selves: $expectedSelfType vs $actualSelfType")
            LOGGER.info("  and  returns: $expectedReturnType vs $actualReturnType")

            // first match everything by name
            // todo resolve default values... -> could be expanded earlier :)
            // todo resolve varargs...

            val isVararg = expectedValueParameters.lastOrNull()?.isVararg == true
            if (isVararg) {
                if (expectedValueParameters.size > actualValueParameters.size + 1) {
                    LOGGER.info("  param-size too low, ${expectedValueParameters.size} vs ${actualValueParameters.size}")
                    return null
                }
            } else {
                if (expectedValueParameters.size != actualValueParameters.size) {
                    LOGGER.info("  param-size mismatch: expected ${expectedValueParameters.size}, but got ${actualValueParameters.size}")
                    return null
                }
            }

            if (actualTypeParameters != null && actualTypeParameters.size != expectedTypeParameters.size) {
                LOGGER.info("  type-param-size mismatch: expected ${expectedTypeParameters.size}, but got ${actualTypeParameters.size}")
                return null
            }

            val sortedValueParameters = resolveNamedParameters(expectedValueParameters, actualValueParameters)
                ?: run {
                    LOGGER.info("  param-name mismatch")
                    return null
                }

            check(sortedValueParameters.size == expectedValueParameters.size) {
                "Incorrectly sorted value parameters, expected ${expectedValueParameters.size} but got ${sortedValueParameters.size}"
            }

            val resolvedTypes = actualTypeParameters?.readonly()
                ?: ParameterList(expectedTypeParameters)

            // LOGGER.info("Checking method-match, self-types: $expectedSelfType vs $actualSelfType")
            if (expectedSelfType != null && actualSelfType != expectedSelfType) {
                LOGGER.info("Start checking self-type")
                val matchesSelfType = isSubTypeOf(
                    expectedSelfType, actualSelfType!!,
                    expectedTypeParameters,
                    resolvedTypes,
                    InsertMode.STRONG
                )
                LOGGER.info("Done checking self-type")

                if (!matchesSelfType) {
                    LOGGER.info("  selfType-mismatch: $actualSelfType !is $expectedSelfType")
                    return null
                }
            }

            // todo this should only be executed sometimes...
            //  missing generic parameters can be temporarily inserted...
            // LOGGER.info("matchesReturnType($expectedReturnType vs $actualReturnType)")
            if (expectedReturnType != null && actualReturnType != null) {
                LOGGER.info("Start checking return-type")
                /*val matchesReturnType =*/ isSubTypeOf(
                    expectedReturnType,
                    actualReturnType,
                    expectedTypeParameters,
                    resolvedTypes,
                    InsertMode.WEAK,
                )
                LOGGER.info("Done checking return-type")

                /*if (!matchesReturnType) {
                    LOGGER.info("  returnType-mismatch: $actualReturnType !is $expectedReturnType")
                    return null
                }*/
            } else {
                LOGGER.info(
                    "Skipped return-type matching: " +
                            "$expectedReturnType != null && " +
                            "$actualReturnType != null"
                )
            }

            if (expectedValueParameters.isNotEmpty()) {
                LOGGER.info("Start checking arguments")
                for (i in expectedValueParameters.indices) {
                    val expectedValueParameter = expectedValueParameters[i]
                    val actualValueParameter = sortedValueParameters[i]
                    LOGGER.info("Start checking argument[$i]: $expectedValueParameter vs $actualValueParameter")
                    if (!isSubTypeOf(
                            actualSelfType,
                            expectedValueParameter, actualValueParameter,
                            expectedTypeParameters, resolvedTypes,
                            InsertMode.STRONG
                        )
                    ) {
                        val type = actualValueParameter.getType(expectedValueParameter.type)
                        LOGGER.info("  type mismatch: $type is not always a ${expectedValueParameter.type}")
                        LOGGER.info("End checking arguments")
                        LOGGER.info("End checking argument[$i]")
                        return null
                    }
                    LOGGER.info("End checking argument[$i]")
                }
                LOGGER.info("End checking arguments")
            }

            LOGGER.info("Found match: $resolvedTypes")
            return resolvedTypes.readonly()
        }
    }

    /**
     * finds a method, returns the method and any inserted type parameters
     * */
    fun findMemberInFile(
        scope: Scope?, origin: Int, name: String,

        returnType: Type?, // sometimes, we know what to expect from the return type
        selfType: Type?, // if inside Companion/Object/Class/Interface, this is defined; else null

        typeParameters: List<Type>?,
        valueParameters: List<ValueParameter>,

        ): Resolved? {
        var scope = scope ?: return null
        while (true) {
            val method = findMemberInScope(
                scope, origin, name,
                returnType, selfType,
                typeParameters, valueParameters,
            )
            if (method != null) return method

            scope = scope.parentIfSameFile ?: return null
        }
    }

    abstract fun findMemberInScope(
        scope: Scope?, origin: Int,
        name: String,

        returnType: Type?, // sometimes, we know what to expect from the return type
        selfType: Type?, // if inside Companion/Object/Class/Interface, this is defined; else null

        typeParameters: List<Type>?,
        valueParameters: List<ValueParameter>,
    ): Resolved?

    private fun getOuterClassDepth(scope: Scope?): Int {
        // if there is a method in the tree, we can use its class, too
        //  -> find the lowest method
        var scopeI = scope ?: return 0
        var lowestMethodScope: Scope? = null
        while (true) {
            val scopeType = scopeI.scopeType
            if (scopeType == ScopeType.METHOD) {
                lowestMethodScope = scopeI
            }
            scopeI = scopeI.parentIfSameFile ?: break
        }

        var scopeJ = lowestMethodScope ?: scope
        while (true) {
            if (scopeJ.isClassType()) {
                return scopeJ.path.size
            }

            scopeJ = scopeJ.parentIfSameFile
                ?: return scopeJ.path.size
        }
    }

    fun <R : Any> resolveInCodeScope(
        context: ResolutionContext, codeScope: Scope,
        callback: (scope: Scope, selfType: Type) -> R?
    ): R? {

        if (!catchFailures) println("ResolveInCodeScope($codeScope, ${context.selfScope}, ${context.selfType})")

        if (context.selfScope != null && context.selfType != null) {
            if (!catchFailures) println("Checking[0] ${context.selfScope} with ${context.selfType}")
            val result = callback(context.selfScope, context.selfType)
            if (result != null) return result

            val selfCompanion = context.selfScope.companionObject
            if (selfCompanion != null) {
                if (!catchFailures) println("Checking[1] $selfCompanion")
                val result = callback(selfCompanion, selfCompanion.typeWithArgs)
                if (result != null) return result
            } else {
                if (!catchFailures) println("${context.selfScope} has no companion")
            }
        }

        val scopes = ArrayList<Scope>()
        val selfTypes = ArrayList<Type?>()
        listScopeTypeCandidates(context, codeScope, scopes, selfTypes)

        if (!catchFailures) println("Scopes: $scopes, selfTypes: $selfTypes")

        val selfType0 = selfTypes.firstOrNull() ?: UnitType

        // selfType goes over all scopes below it...
        for (scopeIndex in scopes.indices) {
            val scope = scopes[scopeIndex] // should be unique by itself
            var lastType: Type? = null // to avoid duplicate checking
            var hadLastType = false
            var hadUnit = false
            for (typeIndex in 0..scopeIndex) {
                val type = selfTypes[typeIndex]
                if ((type == lastType && hadLastType) || (type == null && hadUnit)) continue
                val selfType = context.selfType ?: type ?: selfType0
                if (!catchFailures) println("Checking[i] $scope with $type -> $selfType")
                val result = callback(scope, selfType)
                if (result != null) return result
                lastType = type
                hadLastType = true
                if (type == null) hadUnit = true
            }
        }

        if (!catchFailures) println("Checking[z] $langScope with ${context.selfType ?: selfType0}")
        val result = callback(langScope, context.selfType ?: selfType0)
        if (result != null) return result

        return null
    }

    private fun listScopeTypeCandidates(
        context: ResolutionContext, codeScope: Scope,
        scopes: ArrayList<Scope>,
        selfTypes: ArrayList<Type?>,
    ) {
        listScopeTypeCandidates(context, codeScope) { scope, selfType ->
            scopes.add(scope)
            val selfType = // replace useless package types with Unit s.t. we need to check fewer cases
                if (selfType is ClassType && !selfType.clazz.isClassType())
                    null else selfType
            selfTypes.add(selfType)

            // if scope has a companion object, list that, too
            // unless we're already inside it...
            val companionObject = scope.companionObject
            if (companionObject != null && companionObject != scopes.getOrNull(scopes.size - 2)) {
                scopes.add(companionObject)
                selfTypes.add(companionObject.typeWithArgs)
            }
        }
    }

    private fun listScopeTypeCandidates(
        context: ResolutionContext, codeScope: Scope,
        callback: (scope: Scope, selfType: Type) -> Unit
    ) {
        var scope: Scope? = codeScope
        val outerClassDepth = getOuterClassDepth(scope)
        // println("Outer class depth: $outerClassDepth")
        while (scope != null) {
            if (isScopeAvailable(scope, outerClassDepth)) {
                // println("Checking for field '$name' in $maybeSelfScope")
                val selfType = resolveTypeFromScoping(scope, context)
                callback(scope, selfType)
            } else {
                // println("Skipping scope '$scope'")
            }
            scope = scope.parentIfSameFile
        }
    }

    private fun resolveTypeFromScoping(candidateScope: Scope, context: ResolutionContext): Type {
        var candidateScope: Scope = candidateScope
        while (candidateScope.scopeType?.isInsideExpression() == true) {
            candidateScope = candidateScope.parentIfSameFile ?: break
        }
        if (candidateScope == context.selfType) {
            // println("Found context.selfType: $candidateScope")
            return context.selfType
        }
        // if candidateScope is method & has self type, use that instead
        val selfAsMethod = candidateScope.selfAsMethod
        if (selfAsMethod != null) {
            val selfType = selfAsMethod.selfType
            if (selfType != null) {
                // println("Found method.selfType: $selfType")
                return selfType
            }
        }
        if (candidateScope.isClassType() ||
            candidateScope.scopeType == ScopeType.PACKAGE
        ) {
            // println("Found class: $candidateScope")
            return candidateScope.typeWithoutArgs
        }
        // println("Using scope blindly: $candidateScope")
        return candidateScope.typeWithoutArgs
    }

    private fun isScopeAvailable(scope: Scope, originalSelfScope: Int): Boolean {
        return when (scope.scopeType) {
            // todo we need to filter by visibility
            ScopeType.INLINE_CLASS, ScopeType.PACKAGE, ScopeType.OBJECT, ScopeType.ENUM_CLASS,
            ScopeType.METHOD, ScopeType.METHOD_BODY, ScopeType.WHEN_CASES, ScopeType.WHEN_ELSE,
            ScopeType.LAMBDA -> true // only one instance
            ScopeType.INTERFACE, ScopeType.NORMAL_CLASS ->
                scope.path.size >= originalSelfScope
            else -> true // idk
        }
    }

}