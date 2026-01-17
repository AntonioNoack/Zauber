package me.anno.zauber.typeresolution.members

import me.anno.zauber.ast.rich.Method
import me.anno.zauber.ast.rich.Parameter
import me.anno.zauber.logging.LogManager
import me.anno.zauber.typeresolution.ParameterList
import me.anno.zauber.typeresolution.ParameterList.Companion.resolveGenerics
import me.anno.zauber.typeresolution.ResolutionContext
import me.anno.zauber.types.Scope
import me.anno.zauber.types.Type
import me.anno.zauber.types.impl.ClassType

class ResolvedMethod(
    ownerTypes: ParameterList, method: Method, callTypes: ParameterList,
    context: ResolutionContext, codeScope: Scope
) : ResolvedMember<Method>(ownerTypes, callTypes, method, context, codeScope) {

    override fun getTypeFromCall(): Type {
        val method = resolved
        LOGGER.info("Resolved method $method, body: ${method.body}, returnType: ${method.returnType}")
        val ownerNames = selfTypeToTypeParams(method.selfType, context.selfType)
        val selfType = if (method.selfType != null) context.selfType else null
        val inGeneral = method.resolveReturnType(context)
        val forSelf = ownerTypes.resolveGenerics(selfType,inGeneral)
        val forCall = callTypes.resolveGenerics(selfType, forSelf)
        LOGGER.info("ReturnType for call: $inGeneral -${ownerNames}|$ownerTypes> $forSelf -${method.typeParameters}|$callTypes> $forCall")
        return forCall
    }

    override fun toString(): String {
        return "ResolvedMethod(method=$resolved, generics=$callTypes)"
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