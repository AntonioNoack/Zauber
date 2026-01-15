package me.anno.zauber.typeresolution

import me.anno.zauber.typeresolution.TypeResolution.typeToScope
import me.anno.zauber.types.Type

class ResolutionContext(
    val selfType: Type?,
    /**
     * Whether something without type, like while(true){}, is supported;
     * This is meant to prevent assignments and while-loops in call arguments: AssignmentExpr will throw if !allowTypeless
     * */
    val allowTypeless: Boolean,
    val targetType: Type?
) {

    val selfScope = typeToScope(selfType)

    fun withTargetType(newTargetType: Type?): ResolutionContext {
        if (newTargetType == targetType) return this
        return ResolutionContext(
            selfType,
            allowTypeless, newTargetType
        )
    }

    fun withSelfType(newSelfType: Type?): ResolutionContext {
        if (newSelfType == selfType) return this
        return ResolutionContext(
            newSelfType,
            allowTypeless, targetType
        )
    }

    override fun toString(): String {
        return "ResolutionContext(selfType=$selfType, allowTypeless=$allowTypeless, targetType=$targetType)"
    }

    fun withAllowTypeless(newAllowTypeless: Boolean): ResolutionContext {
        if (allowTypeless == newAllowTypeless) return this
        return ResolutionContext(selfType, newAllowTypeless, targetType)
    }
}