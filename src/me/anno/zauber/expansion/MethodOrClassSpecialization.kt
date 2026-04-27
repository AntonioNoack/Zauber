package me.anno.zauber.expansion

import me.anno.zauber.ast.rich.Method
import me.anno.zauber.scope.Scope
import me.anno.zauber.types.specialization.Specialization

data class MethodOrClassSpecialization(val method: Method?, val clazz: Scope?, val specialization: Specialization) {
    constructor(method: Method, specialization: Specialization) : this(method, null, specialization)
    constructor(clazz: Scope, specialization: Specialization) : this(null, clazz, specialization)
}