package me.anno.zauber.ast.rich.controlflow

import me.anno.zauber.ast.rich.expression.Expression
import me.anno.zauber.typeresolution.ResolutionContext
import me.anno.zauber.typeresolution.TypeResolution
import me.anno.zauber.types.Scope
import me.anno.zauber.types.Type
import me.anno.zauber.types.impl.UnionType.Companion.unionTypes

class TryCatchBlock(val tryBody: Expression, val catches: List<Catch>, val finallyExpression: Expression?) :
    Expression(tryBody.scope, tryBody.origin) {

    override fun resolveType(context: ResolutionContext): Type {
        val bodyType = TypeResolution.resolveType(context, tryBody)
        val catchTypes = catches.map {
            TypeResolution.resolveType(context, it.handler)
        }.reduceOrNull { a, b -> unionTypes(a, b) }
        return if (catchTypes == null) bodyType
        else unionTypes(bodyType, catchTypes)
    }

    override fun hasLambdaOrUnknownGenericsType(): Boolean {
        return tryBody.hasLambdaOrUnknownGenericsType() ||
                catches.any { it.handler.hasLambdaOrUnknownGenericsType() }
    }

    override fun clone(scope: Scope) = TryCatchBlock(tryBody.clone(scope), catches.map {
        Catch(it.param.clone(it.param.scope /* I don't think we should override this */), it.handler.clone(scope))
    }, finallyExpression?.clone(scope))

    override fun toStringImpl(depth: Int): String {
        return "try { $tryBody } ${
            catches.joinToString(" ") {
                "catch(${it.param} { ${it.handler}})"
            }
        } finally { $finallyExpression }"
    }

}