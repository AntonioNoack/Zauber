package me.anno.zauber.astbuilder.expression

import me.anno.zauber.astbuilder.NamedParameter
import me.anno.zauber.astbuilder.TokenListIndex.resolveOrigin
import me.anno.zauber.typeresolution.ResolutionContext
import me.anno.zauber.typeresolution.TypeResolution.langScope
import me.anno.zauber.typeresolution.TypeResolution.resolveValueParameters
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

    init {
        check(base !is NameExpression || base.name != "?.")
    }

    override fun forEachExpr(callback: (Expression) -> Unit) {
        callback(base)
        for (i in valueParameters.indices) {
            callback(valueParameters[i].value)
        }
    }

    override fun toString(): String {
        return if (typeParameters != null && typeParameters.isEmpty()) {
            "($base)($valueParameters)"
        } else "($base)<${typeParameters ?: "?"}>($valueParameters)"
    }

    override fun clone(scope: Scope) = CallExpression(
        base.clone(scope), typeParameters,
        valueParameters.map { NamedParameter(it.name, it.value.clone(scope)) }, origin
    )

    override fun hasLambdaOrUnknownGenericsType(): Boolean {
        return typeParameters == null ||
                base.hasLambdaOrUnknownGenericsType() ||
                valueParameters.any { it.value.hasLambdaOrUnknownGenericsType() }
    }

    override fun resolveType(context: ResolutionContext): Type {
        val typeParameters = typeParameters
        val valueParameters = resolveValueParameters(context, valueParameters)
        println("Resolving call: ${base}<${typeParameters ?: "?"}>($valueParameters)")
        // todo base can be a constructor, field or a method
        // todo find the best matching candidate...
        val returnType = context.targetType
        when (base) {
            is NamedCallExpression if base.name == "." -> {
                TODO("Find method/field ${base}($valueParameters)")
            }
            is NameExpression -> {
                val name = base.name
                println("Find call '$name' with nameAsImport=null, tp: $typeParameters, vp: $valueParameters")
                // findConstructor(selfScope, false, name, typeParameters, valueParameters)
                val c = ConstructorResolver
                val constructor = null1()
                    ?: c.findMemberInFile(context.codeScope, name, returnType, null, typeParameters, valueParameters)
                    ?: c.findMemberInFile(langScope, name, returnType, null, typeParameters, valueParameters)
                return resolveCallType(
                    context, this, name, constructor,
                    typeParameters, valueParameters
                )
            }
            is ImportedExpression -> {
                val name = base.nameAsImport.name
                println("Find call '$name' with nameAsImport=${base.nameAsImport}")
                // findConstructor(selfScope, false, name, typeParameters, valueParameters)
                val c = ConstructorResolver
                val constructor =
                    c.findMemberInFile(base.nameAsImport, name, returnType, null, typeParameters, valueParameters)
                        ?: findMemberInFile(
                            base.nameAsImport.parent, name,
                            returnType,
                            base.nameAsImport.parent.ifIsClassScope()?.typeWithoutArgs,
                            typeParameters, valueParameters
                        )
                return resolveCallType(
                    context, this, name, constructor,
                    typeParameters, valueParameters
                )
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