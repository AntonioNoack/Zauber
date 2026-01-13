package me.anno.zauber.ast.rich.expression

import me.anno.zauber.ast.rich.NamedParameter
import me.anno.zauber.typeresolution.ResolutionContext
import me.anno.zauber.typeresolution.TypeResolution
import me.anno.zauber.typeresolution.TypeResolution.resolveValueParameters
import me.anno.zauber.typeresolution.members.MethodResolver
import me.anno.zauber.typeresolution.members.ResolvedMember
import me.anno.zauber.types.Import
import me.anno.zauber.types.Scope
import me.anno.zauber.types.Type

class NamedCallExpression(
    base: Expression,
    val name: String, val nameAsImport: List<Import>,
    typeParameters: List<Type>?, valueParameters: List<NamedParameter>,
    scope: Scope, origin: Int
) : CallExpressionBase(
    base, typeParameters,
    valueParameters, scope, origin
) {

    constructor(base: Expression, name: String, scope: Scope, origin: Int) :
            this(base, name, emptyList(), null, emptyList(), scope, origin)

    constructor(base: Expression, name: String, nameAsImport: List<Import>, scope: Scope, origin: Int) :
            this(base, name, nameAsImport, null, emptyList(), scope, origin)

    constructor(base: Expression, name: String, other: Expression, scope: Scope, origin: Int) :
            this(base, name, emptyList(), null, listOf(NamedParameter(null, other)), scope, origin)

    init {
        check(name != ".")
        check(name != "?.")
    }

    override fun clone(scope: Scope) = NamedCallExpression(
        base.clone(scope), name, nameAsImport, typeParameters,
        valueParameters.map { NamedParameter(it.name, it.value.clone(scope)) },
        scope, origin
    )

    override fun needsBackingField(methodScope: Scope): Boolean {
        return base.needsBackingField(methodScope) ||
                valueParameters.any { it.value.needsBackingField(methodScope) }
    }

    override fun splitsScope(): Boolean {
        return base.splitsScope() ||
                valueParameters.any { it.value.splitsScope() }
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

    fun calculateBaseType(context: ResolutionContext): Type {
        return TypeResolution.resolveType(
            /* base type seems not deductible -> todo we could help it, if all candidates have the same base type */
            context.withTargetType(null),
            base,
        )
    }

    override fun resolveCallable(context: ResolutionContext): ResolvedMember<*> {
        val baseType = calculateBaseType(context)
        val valueParameters = resolveValueParameters(context, valueParameters)
        val constructor = null
        val contextI = context.withSelfType(baseType)
        return MethodResolver.resolveCallable(
            contextI, name, nameAsImport, constructor,
            typeParameters, valueParameters, origin
        ) ?: MethodResolver.printScopeForMissingMethod(
            contextI, this, name,
            typeParameters, valueParameters,
        )
    }
}