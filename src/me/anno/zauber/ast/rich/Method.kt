package me.anno.zauber.ast.rich

import me.anno.zauber.ast.rich.controlflow.ReturnExpression
import me.anno.zauber.ast.rich.expression.Expression
import me.anno.zauber.typeresolution.ResolutionContext
import me.anno.zauber.typeresolution.TypeResolution
import me.anno.zauber.types.Scope
import me.anno.zauber.types.Type

class Method(
    val selfType: Type?,
    var name: String?,
    val typeParameters: List<Parameter>,
    val valueParameters: List<Parameter>,
    // todo defined constructors need this extra scope, too
    val scope: Scope,
    var returnType: Type?,
    val extraConditions: List<TypeCondition>,
    val body: Expression?,
    val keywords: List<String>,
    val origin: Int
) {

    var backingField: Field? = null

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