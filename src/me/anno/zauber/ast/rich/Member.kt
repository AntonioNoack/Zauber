package me.anno.zauber.ast.rich

import me.anno.zauber.ast.FlagSet
import me.anno.zauber.scope.Scope
import me.anno.zauber.types.Type

abstract class Member(
    open var selfType: Type?,
    val explicitSelfType: Boolean,
    val name: String,
    var scope: Scope,
    var flags: FlagSet,
    val origin: Int
) {
    fun addFlags(flags: FlagSet) {
        this.flags = this.flags or flags
    }

    // due to multi-interface, there may be many of them
    var overriddenFor: List<Member> = emptyList()
    var overriddenBy: List<Member> = emptyList()
}