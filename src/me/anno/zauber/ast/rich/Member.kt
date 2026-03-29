package me.anno.zauber.ast.rich

import me.anno.zauber.ast.KeywordSet
import me.anno.zauber.scope.Scope
import me.anno.zauber.types.Type

abstract class Member(
    open val selfType: Type?,
    val explicitSelfType: Boolean,
    val name: String,
    var scope: Scope,
    var keywords: KeywordSet,
    val origin: Int
)