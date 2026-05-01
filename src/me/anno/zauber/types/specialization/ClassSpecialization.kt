package me.anno.zauber.types.specialization

import me.anno.zauber.scope.Scope
import me.anno.zauber.types.impl.ClassType

data class ClassSpecialization(val clazz: Scope, val specialization: Specialization) {
    constructor(classType: ClassType) : this(classType.clazz, Specialization(classType))
}