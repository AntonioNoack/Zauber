package me.anno.zauber.typeresolution.members

import me.anno.zauber.ast.rich.Parameter
import me.anno.zauber.ast.rich.TokenListIndex.resolveOrigin
import me.anno.zauber.logging.LogManager
import me.anno.zauber.typeresolution.ParameterList
import me.anno.zauber.types.Type
import me.anno.zauber.types.impl.ClassType
import me.anno.zauber.types.specialization.Specialization

object MergeTypeParams {

    private val LOGGER = LogManager.getLogger(MergeTypeParams::class)

    fun collectSpecialization(
        expectedTypeParams: List<Parameter>,
        selfType: Type?,

        actualTypeParams: ParameterList?,
        ctxSpec: Specialization,
        origin: Long
    ): ParameterList {
        val pl = ParameterList(expectedTypeParams)
        ctxSpec.typeParameters.fillInto(pl)
        val selfType = selfType?.resolve()
        if (selfType is ClassType) {
            selfType.typeParameters?.fillInto(pl)
        }
        actualTypeParams?.fillInto(pl)
        if (false) LOGGER.info(
            "Combined $selfType,$actualTypeParams,$ctxSpec into $pl for ${
                expectedTypeParams.map { "${it.scope}.${it.name}" }
            },\n  at ${resolveOrigin(origin)}"
        )
        return pl
    }

    fun ParameterList.fillInto(dst: ParameterList) {
        for (srcI in generics.indices) {
            val type = types[srcI] ?: continue
            val g = generics[srcI]
            val dstI = dst.generics.indexOf(g)
            if (dstI >= 0) {
                dst.set(dstI, type, insertModes[srcI])
            }
        }
    }
}