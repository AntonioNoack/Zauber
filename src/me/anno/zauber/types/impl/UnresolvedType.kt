package me.anno.zauber.types.impl

import me.anno.zauber.logging.LogManager
import me.anno.zauber.scope.Scope
import me.anno.zauber.types.Import
import me.anno.zauber.types.Type
import java.io.InputStreamReader

class UnresolvedType(
    val className: String, val typeParameters: List<Type>?,
    val scope: Scope, val imports: List<Import>
) : Type() {

    companion object {
        private val LOGGER = LogManager.getLogger(UnresolvedType::class)
    }

    override val resolvedName: Type by lazy { resolve() }

    override fun toStringImpl(depth: Int): String {
        return "¿$className?<$typeParameters>"
    }

    override fun equals(other: Any?): Boolean {
        if (other === this) return true
        if (other === NullType || other === UnknownType) return false
        if (other is UnresolvedType && other.scope == scope)
            return other.className == className &&
                    other.typeParameters == typeParameters

        return other is Type && try {
            resolvedName == ((other as? UnknownType)?.resolvedName ?: other)
        } catch (e: IllegalStateException) {
            LOGGER.warn("Assuming $this != $other, failed to resolve: ${e.message}")
            false
        }
    }

    override fun hashCode(): Int = className.hashCode()
}