package me.anno.zauber.typeresolution.members

import me.anno.zauber.ast.rich.Parameter
import me.anno.zauber.ast.rich.TokenListIndex.resolveOrigin
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
        origin: Int
    ): ParameterList {
        val selfPart = mergeSelfPart(selfType, selfParams, origin)
        val callPart = mergeCallPart(callTypes, callParams, origin)
        return selfPart + callPart
    }

    fun mergeSelfPart(
        selfType: Type?,
        selfParams: List<Parameter>,
        origin: Int
    ): ParameterList {

        if (selfType !is ClassType || selfType.typeParameters == null) {
            if (selfParams.isEmpty()) return emptyParameterList()
            return ParameterList(selfParams)
        }

        val expectedSelfParams = selfParams.size
        val actualSelfParams = selfType.typeParameters.size
        check(actualSelfParams == expectedSelfParams) {
            "Mismatch in number of self-typeParameters: " +
                    "$selfParams vs $selfType at ${resolveOrigin(origin)}"
        }

        if (actualSelfParams == 0) return emptyParameterList()
        return selfType.typeParameters
    }

    fun mergeCallPart(
        callTypes: List<Parameter>,
        callParams: List<Type>?,
        origin: Int
    ): ParameterList {

        if (callParams == null) {
            return ParameterList(callTypes)
        }

        val expectedCallParams = callTypes.size
        val actualCallParams = callParams.size
        check(actualCallParams == expectedCallParams) {
            "Expected same number of call parameters, " +
                    "but got $actualCallParams instead of $expectedCallParams at ${resolveOrigin(origin)}"
        }

        if (callParams is ParameterList) return callParams
        return ParameterList(callTypes, callParams)
    }

}