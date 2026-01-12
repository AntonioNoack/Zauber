package me.anno.zauber.ast.rich.controlflow

import me.anno.zauber.ast.rich.expression.Expression
import me.anno.zauber.typeresolution.ResolutionContext
import me.anno.zauber.types.Scope
import me.anno.zauber.types.Type
import me.anno.zauber.types.Types.NothingType

/**
 * todo any method can yield:
 *   a yield can return extra instructions (like sleeping / waiting on resources),
 *   or return extra values (like intermediate state)
 * todo using the new keyword await,
 *   we get back any yielded thing,
 *   and can decide whether we accept it
 *   -> Yieldable: Iterable<Yielded>, Promise<ReturnType>?
 * todo by default, any yield-method
 *   - a) always stores all its state in a struct
 *   - b) contains a lambda for continuation
 *   - c) if normally called, yields are redirected to the method above (like throw/return),
 *        until the method "yields" a result
 * todo we could implement throws using these :3
 *   - and both return and throw are just special values that must call finish(),
 *   - dropping/GC-ing a Yieldable calls finish(), too
 * */
class YieldExpression(origin: Int, val value: Expression) : Expression(value.scope, origin) {
    override fun toStringImpl(depth: Int): String {
        return "yield ${value.toString(depth)}"
    }

    override fun resolveType(context: ResolutionContext): Type = NothingType
    override fun hasLambdaOrUnknownGenericsType(context: ResolutionContext): Boolean = false // always Nothing
    override fun needsBackingField(methodScope: Scope): Boolean = value.needsBackingField(methodScope)
    override fun clone(scope: Scope) = YieldExpression(origin, value.clone(scope))
}