package me.anno.zauber.typeresolution.members

import me.anno.zauber.ast.rich.member.Constructor
import me.anno.zauber.scope.Scope
import me.anno.zauber.typeresolution.ResolutionContext
import me.anno.zauber.types.Type

class ResolvedConstructor(
    constructor: Constructor,
    context: ResolutionContext, codeScope: Scope,
    matchScore: MatchScore,
) : ResolvedMember<Constructor>(
    constructor, context, codeScope, matchScore,
) {

    override fun getScopeOfResolved(): Scope = resolved.scope

    override fun resolveValueType(): Type {
        return resolved.selfTypeI.clazz.typeWithArgs
            .specialize(specialization)
    }

    private val thrownType = lazy {
        specialize(resolved.resolveThrownType(context))
    }

    private val yieldedType = lazy {
        specialize(resolved.resolveYieldedType(context))
    }

    override fun resolveThrownType(): Type {
        return thrownType.value
    }

    override fun resolveYieldedType(): Type {
        return yieldedType.value
    }

    override fun toString(): String {
        return "ResolvedConstructor(constructor=$resolved, generics=$specialization)"
    }
}