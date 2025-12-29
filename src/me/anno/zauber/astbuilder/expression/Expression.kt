package me.anno.zauber.astbuilder.expression

import me.anno.zauber.astbuilder.TokenListIndex.resolveOrigin
import me.anno.zauber.logging.LogManager
import me.anno.zauber.typeresolution.ResolutionContext
import me.anno.zauber.types.Scope
import me.anno.zauber.types.Type
import me.anno.zauber.types.Types.UnitType

abstract class Expression(val scope: Scope, val origin: Int) {

    /**
     * cached for faster future resolution and for checking in from later stages
     * */
    var resolvedType: Type? = null

    abstract fun forEachExpr(callback: (Expression) -> Unit)
    abstract fun resolveType(context: ResolutionContext): Type

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

    override fun toString(): String = toString(10)
    abstract fun toString(depth: Int): String

    /**
     * returns whether the type of this has a lambda, or some other unknown generics inside;
     * for lambdas, we need to know, because usually no other type information is available;
     * for unknown generics, we need them for the return type to be fully known
     * */
    open fun hasLambdaOrUnknownGenericsType(): Boolean {
        // todo what about listOf("1,2,3").map{it.split(',').map{it.toInt()}}?
        //  can we somehow hide lambdas? I don't think so...
        LOGGER.warn("Does (${javaClass.simpleName}) $this contain a lambda? Assuming no for now...")
        return false
    }

    companion object {

        private val LOGGER = LogManager.getLogger(Expression::class)

        var numExpressionsCreated = 0
            private set
    }
}