package me.anno.zauber.ast.rich.member

import me.anno.zauber.ast.FlagSet
import me.anno.zauber.ast.rich.Flags
import me.anno.zauber.ast.rich.Flags.hasFlag
import me.anno.zauber.ast.rich.parameter.Parameter
import me.anno.zauber.ast.rich.expression.Expression
import me.anno.zauber.expansion.IsMethodRecursive
import me.anno.zauber.expansion.IsMethodThrowing
import me.anno.zauber.scope.Scope
import me.anno.zauber.scope.ScopeInitType
import me.anno.zauber.typeresolution.ResolutionContext
import me.anno.zauber.types.Specialization
import me.anno.zauber.types.Type
import me.anno.zauber.types.Types
import me.anno.zauber.types.impl.GenericType

open class MethodLike(
    selfType: Type?,
    explicitSelfType: Boolean,

    typeParameters: List<Parameter>,
    valueParameters: List<Parameter>,
    returnType: Type?,

    scope: Scope, name: String,
    var body: Expression?,
    flags: FlagSet,
    origin: Long
) : Member(
    selfType, explicitSelfType, name, scope, flags,
    typeParameters, valueParameters, returnType, origin
) {

    init {
        check(scope.isMethodLike()) {
            "Method scope must be method-like, got $scope (${scope.scopeType})"
        }
    }

    val capturedFields = HashSet<Field>()

    override val ownerScope get() = scope.parent!!
    override val memberScope get() = scope

    fun isRecursive(specialization: Specialization): Boolean {
        check(specialization.scope == memberScope)
        return IsMethodRecursive[specialization]
    }

    fun getThrownType(specialization: Specialization): Type {
        check(specialization.scope == memberScope)
        return IsMethodThrowing[specialization]
    }

    fun getYieldedType(specialization: Specialization): Type {
        check(specialization.scope == memberScope)
        return IsMethodThrowing[specialization]
    }

    fun getSpecializedBody(specialization: Specialization): Expression? {
        val body = body ?: return null
        val specialization = specialization.withScope(scope)
        // println("applying $specialization to $this")

        return specializations.getOrPut(specialization) {
            specialization.use {
                val context = ResolutionContext(null, specialization, true, null)
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
                if (it.type == Types.NullableAny) it.name
                else "${it.name}: ${it.type}"
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