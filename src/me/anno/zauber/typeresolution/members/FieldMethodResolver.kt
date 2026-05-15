package me.anno.zauber.typeresolution.members

import me.anno.zauber.ast.rich.Field
import me.anno.zauber.ast.rich.Member
import me.anno.zauber.ast.rich.Method
import me.anno.zauber.logging.LogManager
import me.anno.zauber.scope.Scope
import me.anno.zauber.typeresolution.ParameterList
import me.anno.zauber.typeresolution.ResolutionContext
import me.anno.zauber.typeresolution.ValueParameter
import me.anno.zauber.typeresolution.members.MemberResolver.Companion.findGenericsForMatch
import me.anno.zauber.typeresolution.members.MergeTypeParams.collectSpecialization
import me.anno.zauber.types.Type
import me.anno.zauber.types.specialization.Specialization

object FieldMethodResolver {

    private val LOGGER = LogManager.getLogger(FieldMethodResolver::class)

    private fun findOwnerClass(scope: Scope): Scope? {
        var scope = scope
        while (true) {
            if (scope.isObjectLike()) return null
            if (scope.isClass()) return scope
            scope = scope.parent!!
        }
    }

    fun <M : Member> findMemberMatch(
        member: M,
        methodReturnType: Type?,

        returnType: Type?, // sometimes, we know what to expect from the return type
        selfType: Type?, // if inside Companion/Object/Class/Interface, this is defined; else null

        actualTypeParameters: List<Type>?,
        actualValueParameters: List<ValueParameter>,
        ctxSpec: Specialization,
        codeScope: Scope, origin: Long
    ): ResolvedMember<*>? {

        val avp = actualValueParameters.size
        val mvp = member.valueParameters.size
        if (if (member.hasExpandingParameter) avp + 1 < mvp else avp < mvp) {
            LOGGER.info("Rejecting $member, not enough value parameters")
            return null
        }
        if (avp > mvp && !member.hasExpandingParameter) {
            LOGGER.info("Rejecting $member, too many value parameters")
            return null
        }
        if (actualTypeParameters != null && actualTypeParameters.size != member.typeParameters.size) {
            LOGGER.info("Rejecting $member, mismatch in number of type parameters")
            return null
        }

        val matchScore = MatchScore(member.valueParameters.size + 2)
        val memberSelfType = member.selfType?.resolvedName
            ?: findOwnerClass(member.scope)?.typeWithArgs

        println("OwnerScope[$member]: ${member.scope}[${member.scope.scopeType}] -> $memberSelfType")

        val expectedTypeParams = Specialization.collectGenerics(member.scope)
        val actualTypeParams = collectSpecialization(
            expectedTypeParams, selfType,
            if (actualTypeParameters != null) {
                ParameterList(member.typeParameters, actualTypeParameters)
            } else null,
            ctxSpec, origin
        )

        if (LOGGER.isInfoEnabled) LOGGER.info("Resolving generics for $member")
        // println("Resolving generics for $method")
        val generics = findGenericsForMatch(
            memberSelfType, selfType,
            methodReturnType, returnType,
            expectedTypeParams, actualTypeParams,
            member.valueParameters, actualValueParameters, matchScore
        ) ?: return null

        val selfType1 = selfType ?: memberSelfType
        val specialization = Specialization(member.scope, generics)
        val context = ResolutionContext(selfType1, specialization, false, returnType)
        return when (member) {
            is Method -> ResolvedMethod(member, context, codeScope, matchScore)
            is Field -> ResolvedField(member, context, codeScope, matchScore)
            else -> throw NotImplementedError()
        }
    }

}