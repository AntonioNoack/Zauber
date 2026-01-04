package me.anno.zauber.typeresolution.members

import me.anno.zauber.ast.rich.Parameter
import me.anno.zauber.typeresolution.ParameterList
import me.anno.zauber.typeresolution.ParameterList.Companion.emptyParameterList
import me.anno.zauber.types.Type
import me.anno.zauber.types.impl.ClassType

object MergeTypeParams {

    fun mergeTypeParameters(
        selfParams: List<Parameter>,
        selfType: Type?,

        callTypes: List<Parameter>,
        callParams: List<Type>?,
    ): ParameterList {
        val selfPart = mergeSelfPart(selfType, selfParams)
        val callPart = mergeCallPart(callTypes, callParams)
        return selfPart + callPart
    }

    fun mergeSelfPart(
        selfType: Type?,
        selfParams: List<Parameter>,
    ): ParameterList {

        if (selfType !is ClassType || selfType.typeParameters == null) {
            if (selfParams.isEmpty()) return emptyParameterList()
            return ParameterList(selfParams)
        }

        val expectedSelfParams = selfParams.size
        val actualSelfParams = selfType.typeParameters.size
        check(actualSelfParams == expectedSelfParams)

        if (actualSelfParams == 0) return emptyParameterList()
        return selfType.typeParameters
    }

    fun mergeCallPart(
        callTypes: List<Parameter>,
        callParams: List<Type>?,
    ): ParameterList {

        if (callParams == null) {
            return ParameterList(callTypes)
        }

        val expectedCallParams = callTypes.size
        val actualCallParams = callParams.size
        check(actualCallParams == expectedCallParams)

        if (callParams is ParameterList) return callParams
        return ParameterList(callTypes, callParams)
    }

}