package me.anno.zauber.ast.rich.member

import me.anno.utils.StringStyles.GREEN
import me.anno.utils.StringStyles.ORANGE
import me.anno.utils.StringStyles.TEXT
import me.anno.utils.StringStyles.style
import me.anno.zauber.ast.FlagSet
import me.anno.zauber.ast.rich.Flags
import me.anno.zauber.ast.rich.Flags.hasFlag
import me.anno.zauber.ast.rich.controlflow.ReturnExpression
import me.anno.zauber.ast.rich.expression.Expression
import me.anno.zauber.ast.rich.parameter.Parameter
import me.anno.zauber.ast.rich.parser.WhereTypeCondition
import me.anno.zauber.scope.Scope
import me.anno.zauber.typeresolution.ResolutionContext
import me.anno.zauber.typeresolution.TypeResolution
import me.anno.zauber.types.Type

class Method(
    selfType: Type?,
    explicitSelfType: Boolean,
    name: String,
    typeParameters: List<Parameter>,
    valueParameters: List<Parameter>,
    // todo defined constructors need this extra scope, too
    scope: Scope,
    returnType: Type?,
    val extraConditions: List<WhereTypeCondition>,
    body: Expression?,
    flags: FlagSet,
    origin: Long
) : MethodLike(
    selfType, explicitSelfType,
    typeParameters, valueParameters, returnType,
    scope, name, body, flags, origin
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
    fun isInline(): Boolean = flags.hasFlag(Flags.INLINE) && superMethods.isEmpty() && body != null

    fun resolveReturnType(context: ResolutionContext): Type {
        val returnType = returnType // todo validate it in all overrides & self
        if (returnType != null) return returnType

        var body = body ?: error("Expected method to have either returnType or body $this")
        if (body is ReturnExpression) body = body.value
        return TypeResolution.resolveType(context, body)
    }

    override fun toString(): String {
        val builder = flags()
        builder.append(style("fun ", ORANGE))
        appendTypeParams(builder)
        appendSelfType(builder)
        builder.append(style(name, GREEN))
        appendValueParams(builder)
        val returnType = returnType
        if (returnType != null) {
            builder.append(": ")
                .append(returnType)
        }
        return style(builder.toString(), TEXT)
    }

    fun isOpen(): Boolean {
        return flags.hasFlag(Flags.OPEN) ||
                (flags.hasFlag(Flags.OVERRIDE) && !flags.hasFlag(Flags.FINAL)) ||
                ownerScope.isInterface()
    }
}