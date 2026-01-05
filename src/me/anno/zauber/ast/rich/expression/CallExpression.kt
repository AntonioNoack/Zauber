package me.anno.zauber.ast.rich.expression

import me.anno.zauber.ast.rich.NamedParameter
import me.anno.zauber.ast.rich.TokenListIndex.resolveOrigin
import me.anno.zauber.logging.LogManager
import me.anno.zauber.typeresolution.ResolutionContext
import me.anno.zauber.typeresolution.TypeResolution.langScope
import me.anno.zauber.typeresolution.TypeResolution.resolveValueParameters
import me.anno.zauber.typeresolution.TypeResolution.typeToScope
import me.anno.zauber.typeresolution.members.ConstructorResolver
import me.anno.zauber.typeresolution.members.MethodResolver.findMemberInFile
import me.anno.zauber.typeresolution.members.MethodResolver.null1
import me.anno.zauber.typeresolution.members.MethodResolver.resolveCallType
import me.anno.zauber.types.Scope
import me.anno.zauber.types.Type

/**
 * Calls base<typeParams>(valueParams)
 * */
class CallExpression(
    val base: Expression,
    val typeParameters: List<Type>?,
    val valueParameters: List<NamedParameter>,
    origin: Int
) : Expression(base.scope, origin) {

    companion object {
        private val LOGGER = LogManager.getLogger(CallExpression::class)
    }

    init {
        check(base !is MemberNameExpression || base.name != "?.")
    }

    override fun forEachExpr(callback: (Expression) -> Unit) {
        callback(base)
        for (i in valueParameters.indices) {
            callback(valueParameters[i].value)
        }
    }

    override fun toStringImpl(depth: Int): String {
        val valueParameters = valueParameters.joinToString(", ", "(", ")") { it.toString(depth) }
        val base = base.toString(depth)
        return if (typeParameters != null && typeParameters.isEmpty()) {
            "($base)$valueParameters"
        } else "($base)<${typeParameters ?: "?"}>$valueParameters"
    }

    override fun clone(scope: Scope) = CallExpression(
        base.clone(scope), typeParameters,
        valueParameters.map { NamedParameter(it.name, it.value.clone(scope)) }, origin
    )

    override fun hasLambdaOrUnknownGenericsType(): Boolean {
        // todo if(typeParameters == null), then only if a method/constructor/field is known, that has that issue...
        return typeParameters == null ||
                base.hasLambdaOrUnknownGenericsType() ||
                valueParameters.any { it.value.hasLambdaOrUnknownGenericsType() }
    }

    override fun resolveType(context: ResolutionContext): Type {
        val typeParameters = typeParameters
        val valueParameters = resolveValueParameters(context, valueParameters)
        if (LOGGER.enableInfo) LOGGER.info("Resolving call: ${base}<${typeParameters ?: "?"}>($valueParameters)")
        // base can be a constructor, field or a method
        // find the best matching candidate...
        val returnType = context.targetType
        when (base) {
            is NamedCallExpression if base.name == "." -> {
                TODO("Find method/field ${base}($valueParameters)")
            }
            is MemberNameExpression -> {
                val name = base.name
                if (LOGGER.enableInfo) LOGGER.info("Find call '$name' with nameAsImport=null, tp: $typeParameters, vp: $valueParameters")
                // findConstructor(selfScope, false, name, typeParameters, valueParameters)
                return resolveCallType(context, this, name, null, typeParameters, valueParameters)
            }
            is UnresolvedFieldExpression -> {
                val name = base.name
                if (LOGGER.enableInfo) LOGGER.info("Find call '$name' with nameAsImport=null, tp: $typeParameters, vp: $valueParameters")
                // findConstructor(selfScope, false, name, typeParameters, valueParameters)
                val c = ConstructorResolver
                val constructor = null1() // todo do we need this constructor-stuff??? I don't think so, it's not a type
                    ?: c.findMemberInFile(context.codeScope, name, returnType, null, typeParameters, valueParameters)
                    ?: c.findMemberInFile(langScope, name, returnType, null, typeParameters, valueParameters)
                return resolveCallType(context, this, name, constructor, typeParameters, valueParameters)
            }
            is NamedTypeExpression -> {
                val baseType = base.type
                val baseScope = typeToScope(baseType)
                    ?: throw NotImplementedError("Instantiating a $baseType is not yet implemented")
                check(baseScope.hasTypeParameters)
                if (baseScope.typeParameters.isEmpty()) {
                    // constructor is very clear
                    return baseScope.typeWithoutArgs
                } else {
                    val constructor = ConstructorResolver
                        .findMemberInScopeImpl(
                            baseScope, baseScope.name, context.targetType, context.selfType,
                            typeParameters, valueParameters
                        )
                    if (constructor == null) {
                        throw IllegalStateException("Missing constructor for $baseType")
                    }
                    return constructor.getTypeFromCall()
                }
            }
            is ImportedExpression -> {
                val name = base.nameAsImport.name
                if (LOGGER.enableInfo) LOGGER.info("Find call '$name' with nameAsImport=${base.nameAsImport}")
                // findConstructor(selfScope, false, name, typeParameters, valueParameters)
                val c = ConstructorResolver
                val constructor = null1()
                    ?: c.findMemberInFile(base.nameAsImport, name, returnType, null, typeParameters, valueParameters)
                    ?: findMemberInFile(
                        base.nameAsImport.parent, name,
                        returnType,
                        base.nameAsImport.parent.ifIsClassScope()?.typeWithoutArgs,
                        typeParameters, valueParameters
                    )
                return resolveCallType(context, this, name, constructor, typeParameters, valueParameters)
            }
            else -> throw IllegalStateException(
                "Resolve field/method for ${base.javaClass} ($base) " +
                        "in ${resolveOrigin(origin)}"
            )
        }
    }

    fun Scope?.ifIsClassScope(): Scope? {
        val scopeType = this?.scopeType ?: return null
        return if (scopeType.isClassType()) this else null
    }
}