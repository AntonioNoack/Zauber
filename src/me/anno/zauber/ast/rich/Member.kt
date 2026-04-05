package me.anno.zauber.ast.rich

import me.anno.zauber.ast.FlagSet
import me.anno.zauber.scope.Scope
import me.anno.zauber.types.Type

abstract class Member(
    open val selfType: Type?,
    val explicitSelfType: Boolean,
    val name: String,
    var scope: Scope,
    var flags: FlagSet,
    val origin: Int
) {
    // due to multi-interface, there may be many of them
    var overriddenMembers: List<Member> = emptyList()
    var overriddenBy: List<Member> = emptyList()
}