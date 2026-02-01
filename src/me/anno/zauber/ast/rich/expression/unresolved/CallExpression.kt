package me.anno.zauber.ast.rich.expression.unresolved

import me.anno.zauber.ast.rich.NamedParameter
import me.anno.zauber.ast.rich.TokenListIndex
import me.anno.zauber.ast.rich.expression.CallExpressionBase
import me.anno.zauber.ast.rich.expression.Expression
import me.anno.zauber.ast.rich.expression.TypeExpression
import me.anno.zauber.logging.LogManager
import me.anno.zauber.typeresolution.ResolutionContext
import me.anno.zauber.typeresolution.TypeResolution
import me.anno.zauber.typeresolution.TypeResolution.langScope
import me.anno.zauber.typeresolution.TypeResolution.resolveValueParameters
import me.anno.zauber.typeresolution.ValueParameter
import me.anno.zauber.typeresolution.members.ConstructorResolver
import me.anno.zauber.typeresolution.members.MethodResolver
import me.anno.zauber.typeresolution.members.ResolvedMember
import me.anno.zauber.types.Scope
import me.anno.zauber.types.Type

/**
 * Calls base<typeParams>(valueParams), without anything on the left
 * */
class CallExpression(
    base: Expression,
    typeParameters: List<Type>?,
    valueParameters: List<NamedParameter>,
    origin: Int
) : CallExpressionBase(
    base, typeParameters,
    valueParameters, base.scope, origin
) {

    companion object {
        private val LOGGER = LogManager.getLogger(CallExpression::class)
    }

    override fun toStringImpl(depth: Int): String {
        val valueParameters = valueParameters.joinToString(", ", "(", ")") { it.toString(depth) }
        val base = self.toString(depth)
        return if (typeParameters != null && typeParameters.isEmpty()) {
            "($base)$valueParameters"
        } else "($base)<${typeParameters ?: "?"}>$valueParameters"
    }

    override fun needsBackingField(methodScope: Scope): Boolean {
        return valueParameters.any { it.value.needsBackingField(methodScope) }
    }

    override fun clone(scope: Scope) = CallExpression(
        self.clone(scope), typeParameters,
        valueParameters.map { NamedParameter(it.name, it.value.clone(scope)) }, origin
    )

    override fun splitsScope(): Boolean {
        return self.splitsScope() ||
                valueParameters.any { it.value.splitsScope() } ||
                // these check is a little too loose
                (self is UnresolvedFieldExpression && (self.name == "check" || self.name == "require")) ||
                (self is FieldExpression && (self.field.name == "check" || self.field.name == "require")) ||
                (self is MemberNameExpression && (self.name == "check" || self.name == "require"))
    }

    override fun resolveCallable(context: ResolutionContext): ResolvedMember<*> {
        val typeParameters = typeParameters
        val valueParameters = resolveValueParameters(context, valueParameters)
        if (LOGGER.isInfoEnabled) LOGGER.info("Resolving call: ${self}<${typeParameters ?: "?"}>($valueParameters)")

        // base can be a constructor, field or a method
        // find the best matching candidate...
        return when (self) {
            is MemberNameExpression ->
                throw IllegalStateException("CallExpression with MemberNameExpression must be converted into NamedCallExpression")
            is UnresolvedFieldExpression -> resolveCallableByName(self, context, valueParameters)
            is FieldExpression -> self.resolveField(context)
            is TypeExpression -> {

                val baseType = self.type
                val baseScope = TypeResolution.typeToScope(baseType)
                    ?: throw NotImplementedError("Instantiating a $baseType is not yet implemented")
                check(baseScope.hasTypeParameters)

                val constructor = ConstructorResolver
                    .findMemberInScopeImpl(
                        baseScope, baseScope.name, context.targetType, context.selfType,
                        typeParameters, valueParameters
                    )

                constructor ?: throw IllegalStateException("Missing constructor for $baseType")
            }
            else -> throw IllegalStateException(
                "Resolve field/method in Callable for ${self.javaClass} ($self) " +
                        "in ${TokenListIndex.resolveOrigin(origin)}"
            )
        }
    }

    private fun resolveCallableByName(
        base: UnresolvedFieldExpression, context: ResolutionContext,
        valueParameters: List<ValueParameter>
    ): ResolvedMember<*> {
        val name = base.name
        val nameAsImport = base.nameAsImport
        if (LOGGER.isInfoEnabled) LOGGER.info("Find call[UFE] '$name' with nameAsImport=null, tp: $typeParameters, vp: $valueParameters")
        // findConstructor(selfScope, false, name, typeParameters, valueParameters)
        val c = ConstructorResolver

        // todo surely, Constructors should consider imports, too, right?
        //  or are they immediately covered by being detected as constructors?
        val returnType = context.targetType
        val constructor = MethodResolver.null1() // do we need this constructor-stuff? yes, we do, why ever
            ?: c.findMemberInFile(scope, origin, name, returnType, null, typeParameters, valueParameters)
            ?: c.findMemberInFile(langScope, origin, name, returnType, null, typeParameters, valueParameters)

        LOGGER.info("TypeParameters for call: $typeParameters")
        val byMethodCall = MethodResolver.resolveCallable(
            context, scope,
            name, nameAsImport,
            constructor, typeParameters, valueParameters, origin
        )
        if (byMethodCall != null) return byMethodCall

        MethodResolver.printScopeForMissingMethod(context, this, name, typeParameters, valueParameters)
    }

    override fun forEachExpression(callback: (Expression) -> Unit) {
        callback(self)
        for (param in valueParameters) callback(param.value)
    }
}