package me.anno.zauber.ast.rich

import me.anno.zauber.ast.FlagSet
import me.anno.zauber.ast.rich.Flags.hasFlag
import me.anno.zauber.ast.rich.controlflow.ReturnExpression
import me.anno.zauber.ast.rich.expression.Expression
import me.anno.zauber.scope.Scope
import me.anno.zauber.typeresolution.ResolutionContext
import me.anno.zauber.typeresolution.TypeResolution
import me.anno.zauber.types.Type

class Method(
    selfType: Type?,
    explicitSelfType: Boolean,
    name: String?,
    typeParameters: List<Parameter>,
    valueParameters: List<Parameter>,
    // todo defined constructors need this extra scope, too
    scope: Scope,
    returnType: Type?,
    val extraConditions: List<TypeCondition>,
    body: Expression?,
    flags: FlagSet,
    origin: Long
) : MethodLike(
    selfType, explicitSelfType,
    typeParameters, valueParameters, returnType,
    scope, name ?: "?", body, flags, origin
) {

    /**
     * for getters/setters
     * */
    var backingField: Field? = null

    /**
     * for getters/setters
     * */
    var backedField: Field? = null

    // can inline methods be open? explicit inheritance-tree would need to be inlined...
    fun isInline(): Boolean = flags.hasFlag(Flags.INLINE) && overriddenBy.isEmpty() && body != null

    fun resolveReturnType(context: ResolutionContext): Type {
        val returnType = returnType
        if (returnType != null) return returnType

        var body = body ?: throw IllegalStateException("Expected method to have either returnType or body $this")
        if (body is ReturnExpression) body = body.value
        return TypeResolution.resolveType(context, body)
    }

    override fun toString(): String {
        val builder = flags()
        builder.append("fun ")
        typeParams(builder)
        selfType(builder)
        builder.append(name)
        valueParams(builder)
        return builder.toString()
    }
}