package me.anno.zauber.ast.simple.expression

import me.anno.zauber.ast.rich.MethodLike
import me.anno.zauber.ast.simple.Flow
import me.anno.zauber.ast.simple.SimpleField
import me.anno.zauber.scope.Scope
import me.anno.zauber.types.specialization.MethodSpecialization
import me.anno.zauber.types.specialization.Specialization

abstract class SimpleCallable(
    dst: SimpleField,
    val self: SimpleField,
    val sample: MethodLike,
    val specialization: Specialization,
    scope: Scope, origin: Int
) : SimpleAssignment(dst, scope, origin) {

    val methodSpec = MethodSpecialization(sample, specialization)

    // todo set this, where necessary
    var onThrown: Flow? = null

}