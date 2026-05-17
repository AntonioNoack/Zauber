package me.anno.zauber.ast.simple.expression

import me.anno.zauber.ast.rich.member.MethodLike
import me.anno.zauber.ast.simple.controlflow.Flow
import me.anno.zauber.ast.simple.SimpleField
import me.anno.zauber.scope.Scope
import me.anno.zauber.types.Specialization

abstract class SimpleCallable(
    dst: SimpleField,
    val self: SimpleField,
    val sample: MethodLike,
    val specialization: Specialization,
    scope: Scope, origin: Long
) : SimpleAssignment(dst, scope, origin) {

    @Deprecated("Just use specialization directly")
    val methodSpec get() = specialization

    // todo set this, where necessary
    var onThrown: Flow? = null

}