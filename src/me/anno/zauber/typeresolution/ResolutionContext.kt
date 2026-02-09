package me.anno.zauber.typeresolution

import me.anno.zauber.ast.rich.Field
import me.anno.zauber.ast.rich.expression.Expression
import me.anno.zauber.typeresolution.TypeResolution.typeToScope
import me.anno.zauber.types.Type
import me.anno.zauber.types.specialization.Specialization

data class ResolutionContext(
    /**
     * this should only be set if we look for a field/method on a specific base type;
     * if this is specified, we MUST match it
     * if not, we can match any higher self, or imported/available objects
     * */
    val selfType: Type?,
    /**
     * any generic value should be defined in this specialization
     * for code generation and runtime; for IDEs, this may be incomplete
     * */
    val specialization: Specialization,
    /**
     * Whether something without type, like while(true){}, is supported;
     * This is meant to prevent assignments and while-loops in call arguments: AssignmentExpr will throw if !allowTypeless
     * */
    val allowTypeless: Boolean,
    val targetType: Type?,
    /**
     * This value is only set if we're currently resolving expressions for inlining calls.
     * Otherwise, just leave it empty; todo instead of key=name, use key=field/parameter??
     * */
    val knownLambdas: Map<Field, Expression>,
) {

    constructor(
        selfType: Type?, allowTypeless: Boolean, targetType: Type?,
        knownLambdas: Map<Field, Expression> = emptyMap()
    ) : this(selfType, Specialization.noSpecialization, allowTypeless, targetType, knownLambdas)

    constructor(selfType: Type?, specialization: Specialization, allowTypeless: Boolean, targetType: Type?) :
            this(selfType, specialization, allowTypeless, targetType, emptyMap())

    val selfScope = typeToScope(selfType)

    fun withTargetType(newTargetType: Type?): ResolutionContext {
        if (newTargetType == targetType) return this
        return ResolutionContext(selfType, specialization, allowTypeless, newTargetType, knownLambdas)
    }

    fun withSelfType(newSelfType: Type?): ResolutionContext {
        if (newSelfType == selfType) return this
        return ResolutionContext(newSelfType, specialization, allowTypeless, targetType, knownLambdas)
    }

    fun withAllowTypeless(newAllowTypeless: Boolean): ResolutionContext {
        if (allowTypeless == newAllowTypeless) return this
        return ResolutionContext(selfType, specialization, newAllowTypeless, targetType, knownLambdas)
    }

    fun withKnownLambdas(newKnownLambdas: Map<Field, Expression>): ResolutionContext {
        if (knownLambdas == newKnownLambdas) return this
        return ResolutionContext(selfType, specialization, allowTypeless, targetType, newKnownLambdas)
    }

    override fun toString(): String {
        return "ResolutionContext(selfType=$selfType, " +
                "spec=$specialization, " +
                "allowTypeless=$allowTypeless, " +
                "targetType=$targetType, " +
                "knownLambdas=$knownLambdas)"
    }
}