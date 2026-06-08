package me.anno.zauber.types.impl.unresolved

import me.anno.zauber.logging.LogManager
import me.anno.zauber.scope.Scope
import me.anno.zauber.scope.ScopeInitType
import me.anno.zauber.typeresolution.ParameterList
import me.anno.zauber.types.Type
import me.anno.zauber.types.impl.ClassType
import me.anno.zauber.types.impl.GenericType
import me.anno.zauber.types.impl.arithmetic.NullType
import me.anno.zauber.types.impl.arithmetic.UnknownType

/**
 * ClassType for Scope.typeWithArgs, which doesn't need Scope.getParameterList()
 * */
class UnresolvedClassType(val clazz: Scope) : Type() {

    companion object {
        private val LOGGER = LogManager.getLogger(UnresolvedClassType::class)
    }

    fun getParameterList(): ParameterList {
        clazz[ScopeInitType.AFTER_DISCOVERY]

        val scopeType = clazz.scopeType
        if (!clazz.hasTypeParameters && scopeType?.needsTypeParams() != true) {
            if (scopeType == null) LOGGER.warn("Missing scopeType for $this, assuming no-type-params")
            clazz.setEmptyTypeParams()
        }

        check(clazz.hasTypeParameters) { "Missing type-params for $this ($scopeType) to take typeWithArgs" }
        return ParameterList(
            clazz.typeParameters,
            clazz.typeParameters.map { GenericType(it.scope, it.name) })
    }

    override val resolvedName: ClassType by lazy {
        ClassType(clazz, getParameterList())
    }

    override fun not(): Type = UnresolvedNotType(this)

    override fun toStringImpl(depth: Int): String {
        return "$clazz<?>"
    }

    override fun equals(other: Any?): Boolean {
        if (other === this) return true
        if (other === NullType || other === UnknownType) return false
        if (other is UnresolvedClassType) return other.clazz == clazz

        return other is Type && try {
            resolvedName == other.resolvedName
        } catch (e: IllegalStateException) {
            LOGGER.warn("Assuming $this != $other, failed to resolve: ${e.message}")
            false
        }
    }

    override fun hashCode(): Int = clazz.pathStr.hashCode()
}