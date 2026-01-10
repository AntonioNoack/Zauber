package me.anno.zauber.ast.rich

import me.anno.zauber.ast.KeywordSet
import me.anno.zauber.ast.rich.controlflow.ReturnExpression
import me.anno.zauber.ast.rich.expression.Expression
import me.anno.zauber.typeresolution.ResolutionContext
import me.anno.zauber.typeresolution.TypeResolution
import me.anno.zauber.types.Scope
import me.anno.zauber.types.Type

class Method(
    selfType: Type?,
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
    selfType, typeParameters, valueParameters, returnType,
    scope, body, keywords, origin
) {

    /**
     * for getters/setters
     * */
    var backingField: Field? = null

    // todo calculate whether a function is recursive
    //  a function is recursive, if any child-call is itself
    var isRecursive: Boolean? = null

    // todo calculate these
    var thrownTypes: List<Type>? = null

    // todo list which method was overridden
    // todo due to multi-interface, there may be many of them
    var overriddenMethods: List<Method>? = null

    // todo calculate this, too, for checking which methods actually
    //  need virtual calls
    var overriddenBy: List<Method>? = null

    fun resolveReturnType(context: ResolutionContext): Type {
        val returnType = returnType
        if (returnType != null) return returnType

        var body = body
        if (body is ReturnExpression) body = body.value
        return TypeResolution.resolveType(context, body!!)
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