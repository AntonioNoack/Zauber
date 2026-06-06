package me.anno.zauber.types.impl

import me.anno.utils.StringStyles
import me.anno.utils.StringStyles.GREEN
import me.anno.utils.StringStyles.LIGHT_BLUE
import me.anno.utils.StringStyles.style
import me.anno.zauber.ast.rich.parameter.Parameter
import me.anno.zauber.scope.Scope
import me.anno.zauber.types.Type
import me.anno.zauber.types.Types

/**
 * Generic type, named 'name', defined in 'scope';
 * look at scope to find the type
 * */
class GenericType(val scope: Scope, val name: String) : Type() {

    val byTypeParameter: Parameter
        get() = scope.typeParameters.firstOrNull { it.name == name /*&& it.scope == scope -> automatically filtered for */ }
            ?: error(
                "Missing generic parameter ${style(name, GREEN)} " +
                        "in ${style(scope.pathStr, StringStyles.DARK_BLUE)}, " +
                        "options: ${scope.typeParameters.joinToString { style(it.name, LIGHT_BLUE) }}"
            )

    val superBounds: Type
        get() = byTypeParameter.type

    val byTypeParameterOrNull: Parameter?
        get() = scope.typeParameters.firstOrNull { it.name == name /*&& it.scope == scope -> automatically filtered for */ }

    val superBoundsOrNull: Type?
        get() = byTypeParameterOrNull?.type

    override fun toString(): String {
        // avoid color
        return toStringImpl(10)
    }

    override fun toStringImpl(depth: Int): String {
        val superBounds = superBoundsOrNull
        val withoutBounds = "$scope." + style(name, GREEN)
        return if (superBounds == Types.NullableAny || superBounds == null) withoutBounds else {
            "($withoutBounds: ${superBounds.toString(depth)})"
        }
    }

    override fun equals(other: Any?): Boolean {
        return other is GenericType &&
                other.scope == scope &&
                other.name == name
    }

    override fun hashCode(): Int {
        return scope.hashCode() * 31 + name.hashCode()
    }
}