package me.anno.zauber.typeresolution.members

import me.anno.zauber.ast.rich.Field
import me.anno.zauber.logging.LogManager
import me.anno.zauber.typeresolution.ResolutionContext
import me.anno.zauber.typeresolution.TypeResolution.getSelfType
import me.anno.zauber.typeresolution.TypeResolution.resolveType
import me.anno.zauber.typeresolution.ValueParameter
import me.anno.zauber.typeresolution.members.MergeTypeParams.mergeTypeParameters
import me.anno.zauber.typeresolution.members.ResolvedMethod.Companion.selfTypeToTypeParams
import me.anno.zauber.types.Scope
import me.anno.zauber.types.ScopeType
import me.anno.zauber.types.Type
import me.anno.zauber.types.Types.NothingType
import me.anno.zauber.types.Types.UnitType
import me.anno.zauber.types.impl.ClassType

object FieldResolver : MemberResolver<Field, ResolvedField>() {

    private val LOGGER = LogManager.getLogger(FieldResolver::class)

    override fun findMemberInScope(
        scope: Scope?, name: String,

        returnType: Type?, // sometimes, we know what to expect from the return type
        selfType: Type?, // if inside Companion/Object/Class/Interface, this is defined; else null

        typeParameters: List<Type>?,
        valueParameters: List<ValueParameter>
    ): ResolvedField? {
        scope ?: return null
        val scopeSelfType = getSelfType(scope)
        for (field in scope.fields) {
            if (field.name != name) continue
            if (field.typeParameters.isNotEmpty()) {
                LOGGER.info("Given $field on $selfType, with target $returnType, can we deduct any generics from that?")
            }
            val valueType = if (returnType != null) {
                getFieldReturnType(scopeSelfType, field)
            } else field.valueType // no resolution invoked (fast-path)
            val match = findMemberMatch(
                field, valueType,
                returnType, selfType,
                typeParameters, valueParameters
            )
            if (match != null) return match
        }
        return null
    }

    private fun getFieldReturnType(scopeSelfType: Type?, field: Field): Type? {
        if (field.valueType == null) {
            val expr = (field.initialValue ?: field.getterExpr)!!
            LOGGER.info("Resolving valueType($field), initial/getter: $expr")
            val context = ResolutionContext(
                field.codeScope,//.innerScope,
                field.selfType ?: scopeSelfType,
                false, null
            )
            field.valueType = resolveType(context, expr)
        }
        return field.valueType
    }

    fun findMemberMatch(
        field: Field,
        fieldReturnType: Type?,

        // todo what is the difference between fieldReturnType and returnType here?

        returnType: Type?, // sometimes, we know what to expect from the return type
        selfType: Type?, // if inside Companion/Object/Class/Interface, this is defined; else null

        typeParameters: List<Type>?,
        valueParameters: List<ValueParameter>
    ): ResolvedField? {
        check(valueParameters.isEmpty())

        var fieldSelfParams = selfTypeToTypeParams(field.selfType)
        var fieldSelfType = field.selfType // todo we should clear these garbage types before type resolution
        if (fieldSelfType is ClassType && fieldSelfType.clazz.scopeType?.isClassType() != true) {
            LOGGER.info("Field had invalid selfType: $fieldSelfType")
            fieldSelfType = null
            fieldSelfParams = emptyList()
        }

        val actualTypeParams = mergeTypeParameters(
            fieldSelfParams, fieldSelfType,
            field.typeParameters, typeParameters,
        )

        LOGGER.info("Resolving generics for field $field")
        val generics = findGenericsForMatch(
            fieldSelfType, if (fieldSelfType == null) null else selfType,
            fieldReturnType, returnType,
            fieldSelfParams + field.typeParameters, actualTypeParams,
            emptyList(), valueParameters
        ) ?: return null

        val selfType = selfType ?: fieldSelfType
        val context = ResolutionContext(field.codeScope, selfType, false, fieldReturnType)
        return ResolvedField(
            generics.subList(0, fieldSelfParams.size), field,
            generics.subList(fieldSelfParams.size, generics.size), context
        )
    }

    fun resolveField(
        context: ResolutionContext, name: String,
        typeParameters: List<Type>?, // if provided, typically not the case (I've never seen it)
    ): ResolvedField? {
        val selfType = context.selfType
        LOGGER.info("TypeParams for field '$name': $typeParameters, selfType: $selfType")
        return resolveInCodeScope(context) { candidateScope, selfType ->
            findMemberInHierarchy(
                candidateScope, name, context.targetType,
                selfType, typeParameters, emptyList()
            )
        }
    }

}