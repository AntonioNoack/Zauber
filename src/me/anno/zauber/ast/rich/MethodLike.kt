package me.anno.zauber.ast.rich

import me.anno.generation.Specializations
import me.anno.zauber.ast.FlagSet
import me.anno.zauber.ast.rich.Flags.hasFlag
import me.anno.zauber.ast.rich.expression.Expression
import me.anno.zauber.expansion.IsMethodRecursive
import me.anno.zauber.expansion.IsMethodThrowing
import me.anno.zauber.logging.LogManager
import me.anno.zauber.scope.Scope
import me.anno.zauber.scope.ScopeInitType
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

    init {
        check(scope.isMethodLike()) {
            "Method scope must be method-like, got $scope (${scope.scopeType})"
        }
    }

    val capturedFields = HashSet<Field>()

    val ownerScope get() = scope.parent!!
    val methodScope get() = scope

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
            specialization.push {
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

    fun isAbstract(): Boolean {
        scope[ScopeInitType.AFTER_DISCOVERY]
        return body == null
    }

    var selfTypeIfNecessary: Type? = null

    fun flags(builder: StringBuilder = StringBuilder()): StringBuilder {
        if (isPrivate()) builder.append("private ")
        if (isProtected()) builder.append("protected ")
        if (isExternal()) builder.append("external ")
        return builder
    }

    fun selfType(builder: StringBuilder = StringBuilder()): StringBuilder {
        if (selfType != null) {
            builder.append(selfType.toString()).append('.')
        }
        return builder
    }

    fun typeParams(builder: StringBuilder = StringBuilder()): StringBuilder {
        if (typeParameters.isNotEmpty()) {
            builder.append('<')
            builder.append(typeParameters.joinToString(", ") {
                "${it.name}: ${it.type}"
            })
            builder.append("> ")
        }
        return builder
    }

    fun valueParams(builder: StringBuilder = StringBuilder()): StringBuilder {
        builder.append(valueParameters.joinToString(", ", "(", ")") {
            "${it.name}: ${it.type.resolvedName}"
        })
        return builder
    }

}