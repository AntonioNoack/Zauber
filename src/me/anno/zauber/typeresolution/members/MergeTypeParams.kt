package me.anno.zauber.typeresolution.members

import me.anno.zauber.ast.rich.Parameter
import me.anno.zauber.ast.rich.TokenListIndex.resolveOrigin
import me.anno.zauber.logging.LogManager
import me.anno.zauber.typeresolution.ParameterList
import me.anno.zauber.typeresolution.ParameterList.Companion.emptyParameterList
import me.anno.zauber.types.Type
import me.anno.zauber.types.impl.ClassType

object MergeTypeParams {

    private val LOGGER = LogManager.getLogger(MergeTypeParams::class)

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
        actualSelfType: Type?,
        expectedSelfParams: List<Parameter>,
        origin: Int
    ): ParameterList {

        if (actualSelfType !is ClassType || actualSelfType.typeParameters == null) {
            if (expectedSelfParams.isEmpty()) return emptyParameterList()
            return ParameterList(expectedSelfParams)
        }

        val numExpectedSelfParams = expectedSelfParams.size
        val numActualSelfParams = actualSelfType.typeParameters.size
        if (numActualSelfParams != numExpectedSelfParams) {
            LOGGER.warn(
                "Mismatch in number of self-typeParameters: " +
                        "$expectedSelfParams vs $actualSelfType at ${resolveOrigin(origin)}"
            )
            return ParameterList(expectedSelfParams)
        }

        if (numActualSelfParams == 0) return emptyParameterList()
        return actualSelfType.typeParameters
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