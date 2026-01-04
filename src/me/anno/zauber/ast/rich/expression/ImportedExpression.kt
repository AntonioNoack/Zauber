package me.anno.zauber.ast.rich.expression

import me.anno.zauber.typeresolution.ResolutionContext
import me.anno.zauber.types.Scope
import me.anno.zauber.types.ScopeType
import me.anno.zauber.types.Type
import me.anno.zauber.types.impl.ClassType

class ImportedExpression(
    val nameAsImport: Scope,
    scope: Scope, origin: Int
) : Expression(scope, origin) {

    override fun forEachExpr(callback: (Expression) -> Unit) {}
    override fun toStringImpl(depth: Int): String = nameAsImport.name

    override fun clone(scope: Scope) = ImportedExpression(nameAsImport, scope, origin)

    override fun hasLambdaOrUnknownGenericsType(): Boolean = false

    override fun resolveType(context: ResolutionContext): Type {
        val typeParams = if (nameAsImport.scopeType == ScopeType.OBJECT) emptyList<Type>() else null
        return ClassType(nameAsImport, typeParams)
    }
}