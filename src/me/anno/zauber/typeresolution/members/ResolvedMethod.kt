package me.anno.zauber.typeresolution.members

import me.anno.zauber.ast.rich.member.Method
import me.anno.zauber.ast.rich.parameter.Parameter
import me.anno.zauber.logging.LogManager
import me.anno.zauber.scope.Scope
import me.anno.zauber.typeresolution.ParameterList.Companion.resolveGenerics
import me.anno.zauber.typeresolution.ResolutionContext
import me.anno.zauber.types.Type
import me.anno.zauber.types.impl.ClassType

class ResolvedMethod(method: Method, context: ResolutionContext, codeScope: Scope, matchScore: MatchScore) :
    ResolvedMember<Method>(method, context, codeScope, matchScore) {

    val returnType get() = resolved.returnType?.specialize(specialization)

    override fun getScopeOfResolved(): Scope = resolved.scope

    private val typeFromCallImpl = lazy {
        val method = resolved
        LOGGER.info("Resolved method $method, body: ${method.body}, returnType: ${method.returnType}")
        val selfType = if (method.selfType != null) context.selfType else method.ownerScope.typeWithArgs
        val inGeneral = method.resolveReturnType(context)
        val forSelf = specialization.typeParameters.resolveGenerics(selfType, inGeneral)
        forSelf.specialize(specialization)
    }

    override fun getTypeFromCall(): Type {
        return typeFromCallImpl.value
    }

    override fun toString(): String {
        return "ResolvedMethod(method=$resolved, generics=$specialization)"
    }

    companion object {
        private val LOGGER = LogManager.getLogger(ResolvedMethod::class)

        fun selfTypeToTypeParams(expectedSelfType: Type?, givenSelfType: Type?): List<Parameter> {
            if (expectedSelfType == null) return emptyList()
            if (expectedSelfType !is ClassType) {
                if (givenSelfType is ClassType) {
                    return givenSelfType.clazz.typeParameters
                }

                // println("Returning empty list, because $expectedSelfType !is ClassType")
                return emptyList()
            }
            return expectedSelfType.clazz.typeParameters
        }
    }
}