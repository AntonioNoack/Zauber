package me.anno.zauber.types.specialization

import me.anno.utils.ResetThreadLocal.Companion.threadLocal
import me.anno.zauber.scope.Scope
import me.anno.zauber.scope.ScopeInitType
import me.anno.zauber.typeresolution.ParameterList
import me.anno.zauber.typeresolution.ParameterList.Companion.emptyParameterList
import me.anno.zauber.types.Types
import me.anno.zauber.types.impl.ClassType

data class ClassSpecialization(val clazz: Scope, val specialization: Specialization) {

    @Deprecated("Be careful, this may miss const-values or outer-class params")
    constructor(classType: ClassType) : this(classType.clazz, Specialization(classType))

    companion object {
        private val cache by threadLocal { HashMap<Scope, ClassSpecialization>() }
        fun fromSimple(scope: Scope): ClassSpecialization {
            check(scope.typeParameters.isEmpty())
            return cache.getOrPut(scope) {
                val spec = Specialization(scope, emptyParameterList())
                ClassSpecialization(scope, spec)
            }
        }
    }

    init {
        check(clazz == specialization.scope)
    }

    val superType: ClassSpecialization?
        get() {

            if (clazz.isPackage()) {
                return fromSimple(Types.Any.clazz)
            }

            val superClass = clazz[ScopeInitType.AFTER_DISCOVERY]
                .superCalls.firstOrNull { superCall -> superCall.isClassCall }
                ?: return null

            val superScope = superClass.type.clazz

            // todo we must also check const value-params
            val generics = superScope.typeParameters
            if (generics.isEmpty() && !superScope.isInnerClass()) {
                return fromSimple(superScope)
            }

            // todo we must also check const value-params
            val typeParams = superClass.type.typeParameters ?: emptyList()
            val superTypeParams = typeParams.map { type -> type.specialize(specialization) }
            val spec = Specialization(clazz, ParameterList(generics, superTypeParams))
            return ClassSpecialization(superScope, spec)
        }
}