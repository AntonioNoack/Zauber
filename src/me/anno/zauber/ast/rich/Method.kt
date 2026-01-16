package me.anno.zauber.ast.rich

import me.anno.zauber.ast.KeywordSet
import me.anno.zauber.ast.rich.Keywords.hasFlag
import me.anno.zauber.ast.rich.controlflow.ReturnExpression
import me.anno.zauber.ast.rich.expression.Expression
import me.anno.zauber.typeresolution.ResolutionContext
import me.anno.zauber.typeresolution.TypeResolution
import me.anno.zauber.types.Scope
import me.anno.zauber.types.Type

class Method(
    selfType: Type?,
    explicitSelfType: Boolean,
    var name: String?,
    typeParameters: List<Parameter>,
    valueParameters: List<Parameter>,
    // todo defined constructors need this extra scope, too
    scope: Scope,
    returnType: Type?,
    val extraConditions: List<TypeCondition>,
    body: Expression?,
    keywords: KeywordSet,
    origin: Int
) : MethodLike(
    selfType, explicitSelfType, typeParameters, valueParameters, returnType,
    scope, body, keywords, origin
) {

    /**
     * for getters/setters
     * */
    var backingField: Field? = null

    // due to multi-interface, there may be many of them
    var overriddenMethods: List<Method> = emptyList()
    var overriddenBy: List<Method> = emptyList()

    fun isInline(): Boolean = keywords.hasFlag(Keywords.INLINE) && overriddenBy.isEmpty() && body != null

    fun resolveReturnType(context: ResolutionContext): Type {
        val returnType = returnType
        if (returnType != null) return returnType

        var body = body ?: throw IllegalStateException("Expected method to have either returnType or body $this")
        if (body is ReturnExpression) body = body.value
        return TypeResolution.resolveType(context, body)
    }

    override fun toString(): String {
        val builder = StringBuilder()
        builder.append("fun ")
        if (typeParameters.isNotEmpty()) {
            builder.append('<')
            builder.append(typeParameters.joinToString(", ") {
                "${it.name}: ${it.type}"
            })
            builder.append("> ")
        }
        if (selfType != null) {
            builder.append(selfType.toString()).append('.')
        }
        builder.append(name)
        builder.append('(')
        builder.append(valueParameters.joinToString(", "))
        builder.append(')')
        return builder.toString()
    }
}