package me.anno.zauber.ast.rich

import me.anno.zauber.ast.KeywordSet
import me.anno.zauber.ast.rich.Keywords.hasFlag
import me.anno.zauber.ast.rich.expression.Expression
import me.anno.zauber.ast.simple.SimpleBlock
import me.anno.zauber.expansion.IsMethodRecursive
import me.anno.zauber.expansion.IsMethodThrowing
import me.anno.zauber.expansion.IsMethodYielding
import me.anno.zauber.typeresolution.ParameterList
import me.anno.zauber.types.Scope
import me.anno.zauber.types.Type
import me.anno.zauber.types.impl.*

open class MethodLike(

    open val selfType: Type?,
    val explicitSelfType: Boolean,

    val typeParameters: List<Parameter>,
    val valueParameters: List<Parameter>,
    var returnType: Type?,
    val scope: Scope,
    var body: Expression?,
    val keywords: KeywordSet,
    val origin: Int
) {
    var simpleBody: SimpleBlock? = null

    val isRecursive: Boolean
        get() = IsMethodRecursive[this]

    val thrownType: Type
        get() = IsMethodThrowing[this]

    val yieldedType: Type
        get() = IsMethodYielding[this]

    val resolvedBodies = HashMap<ParameterList, Expression>()

    /**
     * Whether the storage location (field/argument) is needed to resolve this's return type
     * todo use this property in CallExpression.hasLambdaOrUnderdefined
     * */
    val hasUnderdefinedGenerics = typeParameters.any { typeParam ->
        if (typeParam.scope == scope) {
            val genericType = GenericType(typeParam.scope, typeParam.name)
            returnType?.contains(genericType)
                ?: ((selfType?.contains(genericType) == true) ||
                        valueParameters.none { it.type.contains(genericType) })
        } else false // else resolved by parent
    }

    fun isPrivate(): Boolean = keywords.hasFlag(Keywords.PRIVATE)
    fun isExternal(): Boolean = keywords.hasFlag(Keywords.EXTERNAL)

    var selfTypeIfNecessary: Type? = null

    companion object {
        fun Type.contains(type: GenericType): Boolean {
            if (this == type) return true
            return when (this) {
                is UnionType -> types.any { member -> member.contains(type) }
                NullType -> false
                is SelfType, is ThisType -> throw NotImplementedError("Does $this contain $type?")
                is ClassType -> typeParameters?.any { it.contains(type) } == true
                is LambdaType -> false // todo does it??? kind of known...
                is GenericType -> false // not the same; todo we might need to check super/redirects
                else -> throw NotImplementedError("Does $this contain $type?")
            }
        }
    }
}