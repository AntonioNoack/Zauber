package me.anno.zauber.typeresolution.members

import me.anno.zauber.ast.rich.Parameter
import me.anno.zauber.logging.LogManager
import me.anno.zauber.scope.Scope
import me.anno.zauber.scope.ScopeType
import me.anno.zauber.typeresolution.CallWithNames.resolveNamedParameters
import me.anno.zauber.typeresolution.Inheritance.isSubTypeOf
import me.anno.zauber.typeresolution.InsertMode
import me.anno.zauber.typeresolution.ParameterList
import me.anno.zauber.typeresolution.ResolutionContext
import me.anno.zauber.typeresolution.TypeResolution.catchFailures
import me.anno.zauber.typeresolution.TypeResolution.langScope
import me.anno.zauber.typeresolution.ValueParameter
import me.anno.zauber.types.Type
import me.anno.zauber.types.Types
import me.anno.zauber.types.impl.ClassType
import me.anno.zauber.utils.PairArrayList

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
            actualValueParameters: List<ValueParameter>,

            matchScore: MatchScore?
        ): ParameterList? { // found generic values for a match

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

            LOGGER.info("Sorted value parameters: $sortedValueParameters")

            check(sortedValueParameters.size == expectedValueParameters.size) {
                "Incorrectly sorted value parameters, expected ${expectedValueParameters.size} but got ${sortedValueParameters.size}"
            }

            val actualTypeParametersI = actualTypeParameters?.readonly()
                ?: ParameterList(expectedTypeParameters)

            LOGGER.info("Actual type parameters: $actualTypeParametersI from $actualTypeParameters, expected: $expectedTypeParameters")

            // LOGGER.info("Checking method-match, self-types: $expectedSelfType vs $actualSelfType")
            if (expectedSelfType != null &&
                actualSelfType != null &&
                actualSelfType != expectedSelfType
            ) {
                LOGGER.info("Start checking self-type")
                val matchesSelfType = isSubTypeOf(
                    expectedSelfType, actualSelfType,
                    expectedTypeParameters,
                    actualTypeParametersI,
                    InsertMode.STRONG,
                    matchScore?.at(0)
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
                    actualTypeParametersI,
                    InsertMode.WEAK,
                    matchScore?.at(expectedValueParameters.size + 1)
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

            for (i in expectedValueParameters.indices) {
                val expectedValueParameter = expectedValueParameters[i]
                val actualValueParameter = sortedValueParameters[i]
                LOGGER.info("Start checking argument[$i]: $expectedValueParameter vs $actualValueParameter")
                LOGGER.info("                       [$i]: $expectedTypeParameters vs $actualTypeParametersI")
                if (!isSubTypeOf(
                        actualSelfType,
                        expectedValueParameter, actualValueParameter,
                        expectedTypeParameters, actualTypeParametersI,
                        InsertMode.STRONG, matchScore?.at(i + 1)
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

            LOGGER.info("Found match: $actualTypeParametersI")
            return actualTypeParametersI.readonly()
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
        context: ResolutionContext,
    ): Resolved? {
        var scope = scope ?: return null
        while (true) {
            val method = findMemberInScope(
                scope, origin, name,
                returnType, selfType,
                typeParameters, valueParameters,
                context
            )
            if (method != null) return method

            scope = scope.parentIfSameFile ?: return null
        }
    }

    fun findMemberInScope(
        scope: Scope?, origin: Int, name: String,

        returnType: Type?, // sometimes, we know what to expect from the return type
        selfType: Type?, // if inside Companion/Object/Class/Interface, this is defined; else null

        typeParameters: List<Type>?,
        valueParameters: List<ValueParameter>,
        context: ResolutionContext
    ): Resolved? = findMemberInScope(
        scope, origin, name,
        typeParameters, valueParameters,
        context.withSelfType(selfType)
            .withTargetType(returnType),
    )

    abstract fun findMemberInScope(
        scope: Scope?, origin: Int,
        name: String,
        typeParameters: List<Type>?,
        valueParameters: List<ValueParameter>,
        context: ResolutionContext
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

        val contextSelfScope = context.selfScope?.scope
        val contextSelfType = context.selfType?.specialize(context)

        val print = !catchFailures && LOGGER.isInfoEnabled
        if (print) LOGGER.info("ResolveInCodeScope($codeScope, ${contextSelfScope}, ${contextSelfType})")

        if (contextSelfScope != null && contextSelfType != null) {
            if (print) LOGGER.info(
                "Checking[0] $contextSelfScope with ${contextSelfType}, " +
                        "fields: ${contextSelfScope.fields.map { it.name }}, methods: ${contextSelfScope.methods.map { it.name }}"
            )
            val result = callback(contextSelfScope, contextSelfType)
            if (result != null) return result

            // doesn't need specialization, because only one instance can exist
            val selfCompanion = contextSelfScope.companionObject
            if (selfCompanion != null) {
                if (print) LOGGER.info("Checking[1] $selfCompanion")
                val result = callback(selfCompanion, selfCompanion.typeWithArgs)
                if (result != null) return result
            } else {
                if (print) LOGGER.info("$contextSelfScope has no companion")
            }
        }

        val scopes = PairArrayList<Scope, Type?>()
        listScopeTypeCandidates(context, codeScope, scopes)

        if (print) LOGGER.info("Scopes/selfTypes: $scopes")

        val selfType0 = scopes.firstBOrNull() ?: Types.Unit
        val selfTypeZ = context.selfType ?: selfType0
        var handledLangScope = false

        // selfType goes over all scopes below it...
        for (scopeIndex in scopes.indices) {
            val scope = scopes.getA(scopeIndex) // should be unique by itself
            var lastType: Type? = null // to avoid duplicate checking
            var hadLastType = false
            var hadUnit = false
            for (typeIndex in 0..scopeIndex) {
                val type = scopes.getB(typeIndex)
                if ((type == lastType && hadLastType) || (type == null && hadUnit)) continue // already done
                val selfType = context.selfType ?: type ?: selfType0
                if (print) LOGGER.info("Checking[i] $scope with $type -> $selfType")
                if (scope == langScope && selfType == selfTypeZ) handledLangScope = true
                val result = callback(scope, selfType)
                if (result != null) return result
                lastType = type
                hadLastType = true
                if (type == null) hadUnit = true
            }
        }

        // we're missing the self-case... process it now
        if (contextSelfScope == null && selfTypeZ is ClassType) {
            val result = callback(selfTypeZ.clazz, selfTypeZ)
            if (result != null) return result
        }

        if (!handledLangScope) {
            if (print) LOGGER.info("Checking[z] $langScope with $selfTypeZ")
            val result = callback(langScope, selfTypeZ)
            if (result != null) return result
        }

        return null
    }

    private fun listScopeTypeCandidates(
        context: ResolutionContext, codeScope: Scope,
        result: PairArrayList<Scope, Type?>
    ) {
        listScopeTypeCandidates(context, codeScope) { scope, selfType ->
            val selfType = // replace useless package types with Unit s.t. we need to check fewer cases
                if (selfType is ClassType && !selfType.clazz.isClassType())
                    null else selfType
            result.add(scope, selfType)

            // if scope has a companion object, list that, too
            // unless we're already inside it...
            val companionObject = scope.companionObject
            if (companionObject != null && companionObject != result.getAOrNull(result.size - 2)) {
                result.add(companionObject, companionObject.typeWithArgs)
            }
        }
    }

    private fun listScopeTypeCandidates(
        context: ResolutionContext, codeScope: Scope,
        callback: (scope: Scope, selfType: Type) -> Unit
    ) {
        var scope = codeScope
        val outerClassDepth = getOuterClassDepth(scope)
        // println("Outer class depth: $outerClassDepth")
        while (true) {
            if (isScopeAvailable(scope, outerClassDepth)) {
                val selfType = resolveTypeFromScoping(scope, context).specialize(context)
                println("SelfType[$scope]: $selfType")
                callback(scope, selfType)
            } else {
                // println("Skipping scope '$scope'")
            }
            scope = scope.parentIfSameFile ?: return
        }
    }

    private fun resolveTypeFromScoping(candidateScope: Scope, context: ResolutionContext): Type {
        var candidateScope: Scope = candidateScope
        while (candidateScope.isInsideExpression()) {
            candidateScope = candidateScope.parentIfSameFile ?: break
        }
        if (candidateScope == context.selfType) {
            println("Found context.selfType: $candidateScope")
            return context.selfType
        }
        // if candidateScope is method & has self type, use that instead
        val selfAsMethod = candidateScope.selfAsMethod
        if (selfAsMethod != null) {
            val selfType = selfAsMethod.selfType
            if (selfType != null) {
                println("Found method.selfType: $selfType")
                return selfType
            }
        }
        if (candidateScope.isClassType() ||
            candidateScope.scopeType == ScopeType.PACKAGE
        ) {
            // println("Found class: $candidateScope")
            return candidateScope.typeWithArgs
        }
        // println("Using scope blindly: $candidateScope")
        return candidateScope.typeWithArgs
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