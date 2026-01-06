package me.anno.zauber.ast.rich.expression

import me.anno.zauber.typeresolution.ResolutionContext
import me.anno.zauber.types.Scope
import me.anno.zauber.types.ScopeType
import me.anno.zauber.types.Type

class ImportedExpression(
    val nameAsImport: Scope,
    scope: Scope, origin: Int
) : Expression(scope, origin) {

    override fun toStringImpl(depth: Int): String = nameAsImport.name

    override fun clone(scope: Scope) = ImportedExpression(nameAsImport, scope, origin)

    override fun hasLambdaOrUnknownGenericsType(): Boolean = false

    override fun resolveType(context: ResolutionContext): Type {
        // todo what can this be?
        //  a type...
        //  an object
        //  a field -> should return fieldType
        return when (nameAsImport.scopeType) {
            ScopeType.OBJECT -> nameAsImport.typeWithoutArgs
            null -> {
                TODO("look for field ${nameAsImport.pathStr}")
            }
            else -> {
                TODO("what is the type of imported ${nameAsImport.pathStr}?")
            }
        }
    }
}