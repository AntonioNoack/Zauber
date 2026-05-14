package me.anno.zauber.typeresolution

import me.anno.zauber.ast.rich.Field
import me.anno.zauber.ast.rich.expression.Expression
import me.anno.zauber.typeresolution.TypeResolution.typeToScope
import me.anno.zauber.types.Type
import me.anno.zauber.types.specialization.Specialization

data class ResolutionContext(
    /**
     * this must be set exactly iff we look for a field/method on a specific base type;
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
     * Otherwise, just leave it empty
     * */
    val knownLambdas: Map<Field, Expression>,
    /**
     * when methods are resolved, this is some extra 'this' possibilities;
     * these come from lambdas with self-type, e.g. S.()->Boolean
     * */
    val extensionThis: List<ExtensionThis>,
) {

    constructor(
        selfType: Type?, allowTypeless: Boolean, targetType: Type?,
        knownLambdas: Map<Field, Expression> = emptyMap(),
        extensionThis: List<ExtensionThis> = emptyList(),
    ) : this(
        selfType, Specialization.noSpecialization, allowTypeless,
        targetType, knownLambdas, extensionThis
    )

    constructor(selfType: Type?, specialization: Specialization, allowTypeless: Boolean, targetType: Type?) :
            this(selfType, specialization, allowTypeless, targetType, emptyMap(), emptyList())

    val selfScope = typeToScope(selfType)

    fun withTargetType(newTargetType: Type?): ResolutionContext {
        if (newTargetType == targetType) return this
        return ResolutionContext(selfType, specialization, allowTypeless, newTargetType, knownLambdas, extensionThis)
    }

    fun withSelfType(newSelfType: Type?): ResolutionContext {
        if (newSelfType == selfType) return this
        return ResolutionContext(newSelfType?.specialize(specialization), specialization, allowTypeless, targetType, knownLambdas, extensionThis)
    }

    fun withAllowTypeless(newAllowTypeless: Boolean): ResolutionContext {
        if (allowTypeless == newAllowTypeless) return this
        return ResolutionContext(selfType, specialization, newAllowTypeless, targetType, knownLambdas, extensionThis)
    }

    fun withKnownLambdas(newKnownLambdas: Map<Field, Expression>): ResolutionContext {
        if (knownLambdas == newKnownLambdas) return this
        return ResolutionContext(selfType, specialization, allowTypeless, targetType, newKnownLambdas, extensionThis)
    }

    fun withSpec(specialization: Specialization): ResolutionContext {
        if (this.specialization == specialization) return this
        return ResolutionContext(selfType?.specialize(specialization), specialization, allowTypeless, targetType, knownLambdas, extensionThis)
    }

    fun withExtensionThis(extensionThis: ExtensionThis): ResolutionContext {
        return ResolutionContext(
            selfType, specialization, allowTypeless, targetType, knownLambdas,
            this.extensionThis + extensionThis
        )
    }

    override fun toString(): String {
        return "ResolutionContext(selfType=$selfType, " +
                "spec=$specialization, " +
                "allowTypeless=$allowTypeless, " +
                "targetType=$targetType, " +
                "knownLambdas=$knownLambdas, " +
                "extensionThis=$extensionThis)"
    }

    companion object {
        val minimal = ResolutionContext(null, false, null)
    }
}