package me.anno.zauber.ast.rich

import me.anno.zauber.ast.FlagSet
import me.anno.zauber.scope.Scope
import me.anno.zauber.types.Type

abstract class Member(
    open var selfType: Type?,
    explicitSelfType: Boolean,

    val name: String,
    var scope: Scope,
    var flags: FlagSet,

    var typeParameters: List<Parameter>,
    val valueParameters: List<Parameter>,
    var returnType: Type?,

    val origin: Long
) {

    init {
        check((selfType != null) == explicitSelfType)
    }

    val explicitSelfType get() = selfType != null
    val hasExpandingParameter = valueParameters.any { it.expansion != ParameterExpansion.NONE }

    fun addFlags(flags: FlagSet) {
        this.flags = this.flags or flags
    }

    abstract val ownerScope: Scope

    // due to multi-interface, there may be many of them
    var overriddenFor: List<Member> = emptyList()
    var overriddenBy: List<Member> = emptyList()
}