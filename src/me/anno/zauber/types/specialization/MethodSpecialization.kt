package me.anno.zauber.types.specialization

import me.anno.zauber.ast.rich.MethodLike

data class MethodSpecialization(val method: MethodLike, val specialization: Specialization) {
    init {
        check(method.methodScope == specialization.scope) {
            "Cannot create MethodSpec(${method.methodScope} with spec for ${specialization.scope})"
        }
    }
}