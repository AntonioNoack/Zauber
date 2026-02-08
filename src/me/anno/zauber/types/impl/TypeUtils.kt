package me.anno.zauber.types.impl

import me.anno.zauber.typeresolution.members.ResolvedMember.Companion.resolveGenerics
import me.anno.zauber.types.Scope

object TypeUtils {

    fun ClassType.isChildTypeOf(parent: ClassType): Boolean {

        // todo if either is an interface, we need other logic...
        if (clazz.isInterface()) return false
        if (parent.clazz.isInterface()) return false

        var t = this

        var td = t.clazz.getHierarchyDepth()
        val pd = parent.clazz.getHierarchyDepth()
        if (td <= pd) return false // our depth must be larger than that of the parent

        while (td > pd) {
            t = getSuperType(t, this)
            td--
        }

        return t == parent
    }

    fun isDistinctFrom(typeA: ClassType, typeB: ClassType): Boolean {
        // todo if either is an interface, we need other logic...
        if (typeA.clazz.isInterface()) return false
        if (typeB.clazz.isInterface()) return false

        var a = typeA
        var b = typeB

        var ad = a.clazz.getHierarchyDepth()
        var bd = b.clazz.getHierarchyDepth()

        while (ad > bd) {
            a = getSuperType(a, typeA)
            ad--
        }

        while (bd > ad) {
            b = getSuperType(b, typeB)
            bd--
        }

        return a != b
    }

    fun getSuperType(a: ClassType, selfType: ClassType): ClassType {
        val superCall = a.clazz.superCalls.first { it.valueParameters != null }
        return resolveGenerics(
            selfType, superCall.type,
            a.clazz.typeParameters,
            superCall.type.typeParameters
        ) as ClassType
    }

    fun Scope.getHierarchyDepth(): Int {
        var depth = 0
        var scope = this
        while (true) {
            val superCall = scope.superCalls.firstOrNull { it.valueParameters != null }
            scope = superCall?.type?.clazz ?: return depth
            depth++
        }
    }
}