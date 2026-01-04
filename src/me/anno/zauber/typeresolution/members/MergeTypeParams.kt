package me.anno.zauber.typeresolution.members

import me.anno.zauber.astbuilder.Parameter
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

        val expectedSelfParams = selfParams.size
        val actualSelfParams = (selfType as? ClassType)?.typeParameters?.size ?: 0
        check(actualSelfParams == expectedSelfParams)

        if (actualSelfParams == 0) return emptyParameterList()
        selfType as ClassType
        return selfType.typeParameters!!
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