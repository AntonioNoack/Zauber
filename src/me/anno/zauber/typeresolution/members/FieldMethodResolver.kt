package me.anno.zauber.typeresolution.members

import me.anno.zauber.ast.rich.Field
import me.anno.zauber.ast.rich.Member
import me.anno.zauber.ast.rich.Method
import me.anno.zauber.logging.LogManager
import me.anno.zauber.scope.Scope
import me.anno.zauber.typeresolution.ResolutionContext
import me.anno.zauber.typeresolution.ValueParameter
import me.anno.zauber.typeresolution.members.MemberResolver.Companion.findGenericsForMatch
import me.anno.zauber.typeresolution.members.MergeTypeParams.mergeCallPart
import me.anno.zauber.typeresolution.members.MergeTypeParams.mergeTypeParameters
import me.anno.zauber.typeresolution.members.ResolvedMethod.Companion.selfTypeToTypeParams
import me.anno.zauber.types.Type
import me.anno.zauber.types.specialization.Specialization

object FieldMethodResolver {

    private val LOGGER = LogManager.getLogger(FieldMethodResolver::class)

    fun <M : Member> findMemberMatch(
        method: M,
        methodReturnType: Type?,

        returnType: Type?, // sometimes, we know what to expect from the return type
        selfType: Type?, // if inside Companion/Object/Class/Interface, this is defined; else null

        typeParameters: List<Type>?,
        actualValueParameters: List<ValueParameter>,
        codeScope: Scope, origin: Int
    ): ResolvedMember<*>? {

        val avp = actualValueParameters.size
        val mvp = method.valueParameters.size
        if (if (method.hasVarargParameter) avp + 1 < mvp else avp < mvp) {
            LOGGER.info("Rejecting $method, not enough value parameters")
            return null
        }
        if (avp > mvp && !method.hasVarargParameter) {
            LOGGER.info("Rejecting $method, too many value parameters")
            return null
        }

        val matchScore = MatchScore(method.valueParameters.size + 2)
        val methodSelfType = if (!method.ownerScope.isClass()) {
            // may be null
            method.selfType?.resolvedName
        } else {
            method.selfType?.resolvedName ?: method.ownerScope.typeWithArgs
        }

        if (methodSelfType == null) {

            val actualTypeParameters = mergeCallPart(method.typeParameters, typeParameters, origin)
            if (LOGGER.isInfoEnabled) LOGGER.info("Merged ${method.typeParameters} with $typeParameters into $actualTypeParameters")

            if (LOGGER.isInfoEnabled) LOGGER.info("Resolving generics for $method")
            val generics = findGenericsForMatch(
                null, null,
                methodReturnType, returnType,
                method.typeParameters, actualTypeParameters,
                method.valueParameters, actualValueParameters, matchScore
            ) ?: return null

            val specialization = Specialization(method.scope, generics)
            val context = ResolutionContext(null, specialization = specialization, false, targetType = returnType)
            return when (method) {
                is Method -> ResolvedMethod(method, context, codeScope, matchScore)
                is Field -> ResolvedField(method, context, codeScope, matchScore)
                else -> throw NotImplementedError()
            }
        } else {

            val methodSelfParams = selfTypeToTypeParams(methodSelfType, selfType)
            val actualTypeParams = mergeTypeParameters(
                methodSelfParams, selfType,
                method.typeParameters, typeParameters, origin
            )

            if (LOGGER.isInfoEnabled) LOGGER.info("Resolving generics for $method")
            // println("Resolving generics for $method")
            val generics = findGenericsForMatch(
                methodSelfType, selfType,
                methodReturnType, returnType,
                methodSelfParams + method.typeParameters, actualTypeParams,
                method.valueParameters, actualValueParameters, matchScore
            ) ?: return null

            val selfType1 = selfType ?: methodSelfType
            val specialization = Specialization(method.scope, generics)
            val context = ResolutionContext(selfType1, specialization, false, returnType)
            return when (method) {
                is Method -> ResolvedMethod(method, context, codeScope, matchScore)
                is Field -> ResolvedField(method, context, codeScope, matchScore)
                else -> throw NotImplementedError()
            }
        }
    }

}