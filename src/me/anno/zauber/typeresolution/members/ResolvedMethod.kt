package me.anno.zauber.typeresolution.members

import me.anno.zauber.ast.rich.Method
import me.anno.zauber.ast.rich.Parameter
import me.anno.zauber.ast.rich.controlflow.ReturnExpression
import me.anno.zauber.logging.LogManager
import me.anno.zauber.typeresolution.ParameterList
import me.anno.zauber.typeresolution.ResolutionContext
import me.anno.zauber.typeresolution.TypeResolution
import me.anno.zauber.types.Type
import me.anno.zauber.types.impl.ClassType

class ResolvedMethod(ownerTypes: ParameterList, method: Method, callTypes: ParameterList, context: ResolutionContext) :
    ResolvedCallable<Method>(ownerTypes, callTypes, method, context) {

    override fun getTypeFromCall(): Type {
        val method = resolved
        LOGGER.info("Resolved method $method, body: ${method.body}, returnType: ${method.returnType}")
        val ownerNames = selfTypeToTypeParams(method.selfType)
        val selfType = if (method.selfType != null) context.selfType else null
        val inGeneral = method.resolveReturnType(context)
        val forSelf = resolveGenerics(selfType, inGeneral, ownerNames, ownerTypes)
        val forCall = resolveGenerics(selfType, forSelf, method.typeParameters, callTypes)
        LOGGER.info("ReturnType for call: $inGeneral -${ownerNames}|$ownerTypes> $forSelf -${method.typeParameters}|$callTypes> $forCall")
        return forCall
    }

    override fun toString(): String {
        return "ResolvedMethod(method=$resolved, generics=$callTypes)"
    }

    companion object {
        private val LOGGER = LogManager.getLogger(ResolvedMethod::class)

        fun selfTypeToTypeParams(selfType: Type?): List<Parameter> {
            return (selfType as? ClassType)?.clazz?.typeParameters ?: emptyList()
        }
    }
}