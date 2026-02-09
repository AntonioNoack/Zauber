package me.anno.zauber.ast.rich

import me.anno.zauber.ast.KeywordSet
import me.anno.zauber.ast.rich.Keywords.hasFlag
import me.anno.zauber.ast.rich.expression.Expression
import me.anno.zauber.expansion.IsMethodRecursive
import me.anno.zauber.expansion.IsMethodThrowing
import me.anno.zauber.generation.Specializations
import me.anno.zauber.typeresolution.ResolutionContext
import me.anno.zauber.types.Scope
import me.anno.zauber.types.Type
import me.anno.zauber.types.impl.GenericType
import me.anno.zauber.types.specialization.MethodSpecialization
import me.anno.zauber.types.specialization.Specialization

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

    fun isRecursive(specialization: Specialization): Boolean {
        return IsMethodRecursive[MethodSpecialization(this, specialization)]
    }

    fun getThrownType(specialization: Specialization): Type {
        return IsMethodThrowing[MethodSpecialization(this, specialization)]
    }

    fun getYieldedType(specialization: Specialization): Type {
        return IsMethodThrowing[MethodSpecialization(this, specialization)]
    }

    fun getSpecializedBody(specialization: Specialization): Expression? {
        val body = body ?: return null
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

}