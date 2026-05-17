package me.anno.zauber.ast.simple.expression

import me.anno.zauber.ast.simple.SimpleField
import me.anno.zauber.ast.simple.controlflow.Flow
import me.anno.zauber.scope.Scope
import me.anno.zauber.types.Specialization

abstract class SimpleCallable(
    dst: SimpleField,
    val self: SimpleField,
    val specialization: Specialization,
    scope: Scope, origin: Long
) : SimpleAssignment(dst, scope, origin) {

    val sample get() = specialization.method

    init {
        // works or crashes
        check(specialization.scope == sample.scope)
    }

    // todo set this, where necessary
    var onThrown: Flow? = null

}