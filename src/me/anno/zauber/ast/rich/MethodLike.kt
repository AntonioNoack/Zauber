package me.anno.zauber.ast.rich

import me.anno.zauber.ast.FlagSet
import me.anno.zauber.ast.rich.Flags.hasFlag
import me.anno.zauber.ast.rich.expression.Expression
import me.anno.zauber.expansion.IsMethodRecursive
import me.anno.zauber.expansion.IsMethodThrowing
import me.anno.generation.Specializations
import me.anno.zauber.logging.LogManager
import me.anno.zauber.scope.Scope
import me.anno.zauber.scope.ScopeType
import me.anno.zauber.typeresolution.ResolutionContext
import me.anno.zauber.types.Type
import me.anno.zauber.types.impl.GenericType
import me.anno.zauber.types.specialization.MethodSpecialization
import me.anno.zauber.types.specialization.Specialization

open class MethodLike(
    selfType: Type?,
    explicitSelfType: Boolean,

    val typeParameters: List<Parameter>,
    val valueParameters: List<Parameter>,
    var returnType: Type?,
    scope: Scope, name: String,
    var body: Expression?,
    flags: FlagSet,
    origin: Int
) : Member(selfType, explicitSelfType, name, scope, flags, origin) {

    companion object {
        private val LOGGER = LogManager.getLogger(MethodLike::class)
    }

    fun isRecursive(specialization: Specialization): Boolean {
        return IsMethodRecursive[MethodSpecialization(this, specialization)]
    }

    fun getThrownType(specialization: Specialization): Type {
        return IsMethodThrowing[MethodSpecialization(this, specialization)]
    }

    fun getYieldedType(specialization: Specialization): Type {
        return IsMethodThrowing[MethodSpecialization(this, specialization)]
    }

    fun collectGenerics(): List<Parameter> {
        var scope = scope
        val result = ArrayList<Parameter>()
        while (true) {
            result.addAll(scope.typeParameters)

            if (scope.isObjectLike()) break
            if (scope.isClassType() &&
                scope.scopeType != ScopeType.INNER_CLASS &&
                scope.scopeType != ScopeType.INLINE_CLASS
            ) break

            // todo only accept parent-parameters on some conditions
            scope = scope.parent ?: break
        }
        return result
    }

    fun validateSpecialization(specialization: Specialization) {
        // todo check that the specialization contains exactly what we require
        val actualGenerics = specialization.typeParameters.generics
        val expectedGenerics = collectGenerics()
        val matchesGenerics = actualGenerics.toSet() == expectedGenerics.toSet()
        if (!matchesGenerics) {
            LOGGER.warn("Mismatched generics for $this: got $specialization, expected $expectedGenerics")
        }
    }

    fun getSpecializedBody(specialization: Specialization): Expression? {
        val body = body ?: return null

        validateSpecialization(specialization)

        return specializations.getOrPut(specialization) {
            Specializations.push(specialization) {
                val context = ResolutionContext(null, specialization, true, null)
                Specializations.foundMethodSpecialization(this, specialization)
                body.resolve(context)
            }
        }
    }

    val specializations = HashMap<Specialization, Expression>()

    /**
     * Whether the storage location (field/argument) is needed to resolve this's return type
     * todo use this property in CallExpression.hasLambdaOrUnderdefined
     * */
    val hasUnderdefinedGenerics by lazy {
        typeParameters.any { typeParam ->
            if (typeParam.scope == scope) {
                val genericType = GenericType(typeParam.scope, typeParam.name)
                returnType?.contains(genericType)
                    ?: ((selfType?.contains(genericType) == true) ||
                            valueParameters.none { it.type.contains(genericType) })
            } else false // else resolved by parent
        }
    }

    fun isPrivate(): Boolean = flags.hasFlag(Flags.PRIVATE)
    fun isProtected(): Boolean = flags.hasFlag(Flags.PROTECTED)
    fun isExternal(): Boolean = flags.hasFlag(Flags.EXTERNAL)

    var selfTypeIfNecessary: Type? = null

}