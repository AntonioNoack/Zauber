package me.anno.zauber.types.specialization

import me.anno.zauber.scope.Scope
import me.anno.zauber.scope.ScopeInitType
import me.anno.zauber.typeresolution.ParameterList
import me.anno.zauber.types.Types
import me.anno.zauber.types.impl.ClassType
import me.anno.zauber.types.specialization.Specialization.Companion.noSpecialization

data class ClassSpecialization(val clazz: Scope, val specialization: Specialization) {
    constructor(classType: ClassType) : this(classType.clazz, Specialization(classType))

    val superType: ClassSpecialization?
        get() {
            if (clazz.isPackage()) {
                return ClassSpecialization(Types.Any.clazz, noSpecialization)
            }

            val superClass = clazz[ScopeInitType.AFTER_DISCOVERY]
                .superCalls.firstOrNull { superCall -> superCall.isClassCall }
                ?: return null

            val generics = superClass.type.clazz.typeParameters
            if (generics.isEmpty()) {
                return ClassSpecialization(superClass.type.clazz, noSpecialization)
            }

            val typeParams = superClass.type.typeParameters ?: emptyList()
            val superTypeParams = typeParams.map { type -> type.specialize(specialization) }
            val spec = Specialization(ParameterList(generics, superTypeParams))
            return ClassSpecialization(superClass.type.clazz, spec)
        }
}