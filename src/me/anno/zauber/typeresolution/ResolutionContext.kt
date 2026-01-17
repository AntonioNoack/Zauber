package me.anno.zauber.typeresolution

import me.anno.zauber.ast.rich.expression.Expression
import me.anno.zauber.typeresolution.TypeResolution.typeToScope
import me.anno.zauber.types.Scope
import me.anno.zauber.types.Type

class ResolutionContext(
    /**
     * this should only be set if we look for a field/method on a specific base type;
     * if this is specified, we MUST match it
     * if not, we can match any higher self, or imported/available objects
     * */
    val selfType: Type?,
    // todo there may be multiple selves:
    //  one for each scope:
    //  in a method, self,
    //  if inside a class, that scope,
    //  if inside an inner class, also the outer class
    val higherSelves: Map<Scope, Type>,
    /**
     * Whether something without type, like while(true){}, is supported;
     * This is meant to prevent assignments and while-loops in call arguments: AssignmentExpr will throw if !allowTypeless
     * */
    val allowTypeless: Boolean,
    val targetType: Type?,
    /**
     * This value is only set if we're currently resolving expressions for inlining calls.
     * Otherwise, just leave it empty.
     * */
    val knownLambdas: Map<String, Expression>,
) {

    constructor(
        selfType: Type?, allowTypeless: Boolean, targetType: Type?,
        knownLambdas: Map<String, Expression> = emptyMap()
    ) : this(selfType, emptyMap(), allowTypeless, targetType, knownLambdas)

    val selfScope = typeToScope(selfType)

    fun withTargetType(newTargetType: Type?): ResolutionContext {
        if (newTargetType == targetType) return this
        return ResolutionContext(selfType, higherSelves, allowTypeless, newTargetType, knownLambdas)
    }

    fun withSelfType(newSelfType: Type?): ResolutionContext {
        if (newSelfType == selfType) return this
        return ResolutionContext(newSelfType, higherSelves, allowTypeless, targetType, knownLambdas)
    }

    fun withAllowTypeless(newAllowTypeless: Boolean): ResolutionContext {
        if (allowTypeless == newAllowTypeless) return this
        return ResolutionContext(selfType, higherSelves, newAllowTypeless, targetType, knownLambdas)
    }

    fun withKnownLambdas(newKnownLambdas: Map<String, Expression>): ResolutionContext {
        if (knownLambdas == newKnownLambdas) return this
        return ResolutionContext(selfType, higherSelves, allowTypeless, targetType, newKnownLambdas)
    }

    override fun toString(): String {
        return "ResolutionContext(selfType=$selfType, " +
                "higherSelves: $higherSelves, " +
                "allowTypeless=$allowTypeless, " +
                "targetType=$targetType, " +
                "knownLambdas=$knownLambdas)"
    }
}