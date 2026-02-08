package me.anno.zauber.ast.simple.expression

import me.anno.zauber.ast.simple.Flow
import me.anno.zauber.ast.simple.SimpleField
import me.anno.zauber.types.Scope

abstract class SimpleCallable(
    dst: SimpleField,
    scope: Scope, origin: Int
) : SimpleAssignment(dst, scope, origin) {

    // todo set this, where necessary
    var onThrown: Flow? = null

}