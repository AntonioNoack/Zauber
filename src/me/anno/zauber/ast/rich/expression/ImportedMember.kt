package me.anno.zauber.ast.rich.expression

import me.anno.zauber.typeresolution.ResolutionContext
import me.anno.zauber.types.Scope
import me.anno.zauber.types.ScopeType
import me.anno.zauber.types.Type

/**
 * can be a field, method or object
 * */
class ImportedMember(
    val nameAsImport: Scope,
    scope: Scope, origin: Int
) : Expression(scope, origin) {

    override fun toStringImpl(depth: Int): String = nameAsImport.name

    override fun clone(scope: Scope) = ImportedMember(nameAsImport, scope, origin)

    // todo it might have...
    override fun hasLambdaOrUnknownGenericsType(): Boolean = false

    override fun resolveType(context: ResolutionContext): Type {
        return when (nameAsImport.scopeType) {
            ScopeType.OBJECT, ScopeType.COMPANION_OBJECT -> nameAsImport.typeWithArgs
            ScopeType.NORMAL_CLASS, ScopeType.INTERFACE, ScopeType.ENUM_CLASS -> {
                val parentCompanion = nameAsImport.companionObject
                if (parentCompanion != null) return parentCompanion.typeWithArgs

                throw IllegalStateException("Could not resolve type for $nameAsImport in normal class")
            }
            null -> {

                // todo "Companion" could appear at all levels of the import :(
                val fieldName = nameAsImport.name
                val parent = nameAsImport.parent!!
                val matchingField = parent.fields.firstOrNull { it.name == fieldName }
                if (matchingField != null) return matchingField.resolveValueType(context)

                val parentCompanion = parent.companionObject
                val matchingField1 = parentCompanion?.fields?.firstOrNull { it.name == fieldName }
                if (matchingField1 != null) return matchingField1.resolveValueType(context)

                throw IllegalStateException("Could not resolve field '$fieldName' in $parent")
            }
            else -> {
                TODO("what is the type of imported ${nameAsImport.pathStr} -> ${nameAsImport.scopeType}?")
            }
        }
    }
}