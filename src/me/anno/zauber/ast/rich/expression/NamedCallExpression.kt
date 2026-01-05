package me.anno.zauber.ast.rich.expression

import me.anno.zauber.ast.rich.NamedParameter
import me.anno.zauber.ast.rich.TokenListIndex.resolveOrigin
import me.anno.zauber.typeresolution.ResolutionContext
import me.anno.zauber.typeresolution.TypeResolution
import me.anno.zauber.typeresolution.TypeResolution.resolveValueParameters
import me.anno.zauber.typeresolution.members.FieldResolver.resolveFieldType
import me.anno.zauber.typeresolution.members.MethodResolver.resolveCallType
import me.anno.zauber.types.Scope
import me.anno.zauber.types.Type

class NamedCallExpression(
    val base: Expression,
    val name: String,
    val typeParameters: List<Type>?,
    val valueParameters: List<NamedParameter>,
    scope: Scope, origin: Int
) : Expression(scope, origin) {

    init {
        check(name != ".")
        check(name != "?.")
    }

    override fun forEachExpr(callback: (Expression) -> Unit) {
        callback(base)
        for (i in valueParameters.indices) {
            callback(valueParameters[i].value)
        }
    }

    override fun clone(scope: Scope) = NamedCallExpression(
        base.clone(scope), name, typeParameters,
        valueParameters.map { NamedParameter(it.name, it.value.clone(scope)) },
        scope, origin
    )

    override fun hasLambdaOrUnknownGenericsType(): Boolean {
        return typeParameters == null ||
                base.hasLambdaOrUnknownGenericsType() ||
                valueParameters.any { it.value.hasLambdaOrUnknownGenericsType() }
    }

    override fun toStringImpl(depth: Int): String {
        val base = base.toString(depth)
        val valueParameters = valueParameters.joinToString(", ", "(", ")") { it.toString(depth) }
        return if (typeParameters != null && typeParameters.isEmpty()) {
            "($base).$name$valueParameters"
        } else {
            "($base).$name<${typeParameters?.joinToString() ?: "?"}>$valueParameters"
        }
    }

    override fun resolveType(context: ResolutionContext): Type {
        val calleeType = TypeResolution.resolveType(
            /* target lambda type seems not deductible */
            context.withTargetType(null),
            base,
        )
        // todo type-args may be needed for type resolution
        val valueParameters = resolveValueParameters(context, valueParameters)

        val constructor = null
        return resolveCallType(
            context.withSelfType(calleeType),
            this, name, constructor,
            typeParameters, valueParameters
        )
    }

}