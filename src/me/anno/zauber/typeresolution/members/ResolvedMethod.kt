package me.anno.zauber.typeresolution.members

import me.anno.zauber.astbuilder.Method
import me.anno.zauber.astbuilder.Parameter
import me.anno.zauber.typeresolution.ResolutionContext
import me.anno.zauber.types.Type
import me.anno.zauber.types.impl.ClassType

class ResolvedMethod(ownerTypes: List<Type>, method: Method, callTypes: List<Type>, context: ResolutionContext) :
    ResolvedCallable<Method>(ownerTypes, callTypes, method, context) {

    override fun getTypeFromCall(): Type {
        val method = resolved
        val ownerNames = selfTypeToTypeParams(method.selfType)
        val inGeneral = method.returnType!!
        val forSelf = resolveGenerics(inGeneral, ownerNames, ownerTypes)
        val forCall = resolveGenerics(forSelf, method.typeParameters, callTypes)
        println("returnType for call: $inGeneral -${ownerNames}|$ownerTypes> $forSelf -${method.typeParameters}|$callTypes> $forCall")
        return forCall
    }

    override fun toString(): String {
        return "ResolvedMethod(method=$resolved, generics=$callTypes)"
    }

    companion object {
        fun selfTypeToTypeParams(selfType: Type?): List<Parameter> {
            return (selfType as? ClassType)?.clazz?.typeParameters ?: emptyList()
        }
    }
}