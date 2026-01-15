package me.anno.zauber.ast.rich.expression

import me.anno.zauber.ast.rich.TokenListIndex.resolveOrigin
import me.anno.zauber.typeresolution.ResolutionContext
import me.anno.zauber.types.Scope
import me.anno.zauber.types.Type
import me.anno.zauber.types.Types.UnitType

abstract class Expression(val scope: Scope, val origin: Int) {

    /**
     * cached for faster future resolution and for checking in from later stages
     * */
    var resolvedType: Type? = null

    abstract fun resolveType(context: ResolutionContext): Type

    fun resolve(context: ResolutionContext): Expression {
        val resolved = resolveImpl(context)
        check(resolved.isResolved())
        if (resolved.resolvedType == null) {
            resolved.resolvedType = resolved.resolveType(context)
        }
        return resolved
    }

    // @Deprecated("Only the Expression-class and nothing else should call this")
    open fun resolveImpl(context: ResolutionContext): Expression {
        if (isResolved()) return this
        throw NotImplementedError("Resolve ${javaClass.simpleName}, $this")
    }

    fun exprHasNoType(context: ResolutionContext): Type {
        if (!context.allowTypeless) throw IllegalStateException(
            "Expected type, but found $this (${javaClass.simpleName}) in ${resolveOrigin(origin)}"
        )
        return UnitType
    }

    init {
        numExpressionsCreated++
    }

    /**
     * clone to get rid of resolvedType,
     * or to change the scope
     * */
    abstract fun clone(scope: Scope): Expression

    override fun toString(): String = toStringImpl(10)
    fun toString(depth: Int): String {
        return if (depth >= 0) toStringImpl(depth - 1) else "${javaClass.simpleName}..."
    }

    abstract fun toStringImpl(depth: Int): String

    /**
     * returns whether the type of this has a lambda, or some other unknown generics inside;
     * for lambdas, we need to know, because usually no other type information is available;
     * for unknown generics, we need them for the return type to be fully known
     * */
    abstract fun hasLambdaOrUnknownGenericsType(context: ResolutionContext): Boolean

    /**
     * whether the expression contains a FieldExpression with name == 'field' and scope == methodScope
     * */
    abstract fun needsBackingField(methodScope: Scope): Boolean

    /**
     * whether this expression changes information about types
     * */
    abstract fun splitsScope(): Boolean

    /**
     * after type resolution, all expressions should be resolved
     * */
    abstract fun isResolved(): Boolean

    companion object {
        var numExpressionsCreated = 0
            private set
    }
}