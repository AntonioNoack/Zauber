package me.anno.zauber.types.specialization

import me.anno.zauber.ast.rich.MethodLike

data class MethodSpecialization(val method: MethodLike, val specialization: Specialization)