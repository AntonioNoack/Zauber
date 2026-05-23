package me.anno.generation.jvm

import me.anno.zauber.scope.Scope
import me.anno.zauber.types.Specialization

data class JVMClass(
    val scope: Scope,
    val specialization: Specialization,
    val methods: Collection<Specialization>,
    val fields: Collection<Specialization>,
    val className: String,
    val packageScope: Scope,
    val internalName: String,
)