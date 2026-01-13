package me.anno.zauber.typeresolution.members

import me.anno.zauber.ast.rich.Field
import me.anno.zauber.ast.rich.controlflow.ReturnExpression
import me.anno.zauber.logging.LogManager
import me.anno.zauber.typeresolution.ResolutionContext
import me.anno.zauber.typeresolution.TypeResolution.getSelfType
import me.anno.zauber.typeresolution.TypeResolution.resolveType
import me.anno.zauber.typeresolution.ValueParameter
import me.anno.zauber.typeresolution.members.MergeTypeParams.mergeTypeParameters
import me.anno.zauber.typeresolution.members.ResolvedMethod.Companion.selfTypeToTypeParams
import me.anno.zauber.types.Import
import me.anno.zauber.types.Scope
import me.anno.zauber.types.ScopeType
import me.anno.zauber.types.Type
import me.anno.zauber.types.impl.ClassType

object FieldResolver : MemberResolver<Field, ResolvedField>() {

    private val LOGGER = LogManager.getLogger(FieldResolver::class)

    override fun findMemberInScope(
        scope: Scope?, origin: Int, name: String,

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
            val valueType = getFieldReturnType(scopeSelfType, field, returnType)
            val match = findMemberMatch(
                field, valueType,
                returnType, selfType,
                typeParameters, valueParameters,
                origin
            )
            if (match != null) return match
        }
        for (child in scope.children) {
            if (child.name != name ||
                (child.scopeType != ScopeType.OBJECT &&
                        child.scopeType != ScopeType.COMPANION_OBJECT)
            ) continue

            val field = child.fields.first { it.name == "__instance__" }
            val valueType = getFieldReturnType(scopeSelfType, field, returnType)
            val match = findMemberMatch(
                field, valueType,
                returnType, selfType,
                typeParameters, valueParameters,
                origin
            )
            if (match != null) return match
        }
        return null
    }

    fun getFieldReturnType(scopeSelfType: Type?, field: Field, returnType: Type?): Type? {
        return if (returnType != null) {
            getFieldReturnType(scopeSelfType, field)
        } else field.valueType // no resolution invoked (fast-path)
    }

    private fun getFieldReturnType(scopeSelfType: Type?, field: Field): Type? {
        if (field.valueType == null) {
            var expr = (field.initialValue ?: field.getterExpr)!!
            while (expr is ReturnExpression) expr = expr.value
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
        valueParameters: List<ValueParameter>,
        origin: Int
    ): ResolvedField? {
        check(valueParameters.isEmpty())

        var fieldSelfParams = selfTypeToTypeParams(field.selfType, selfType)
        var fieldSelfType = field.selfType // todo we should clear these garbage types before type resolution
        if (fieldSelfType is ClassType && fieldSelfType.clazz.scopeType?.isClassType() != true) {
            LOGGER.info("Field had invalid selfType: $fieldSelfType")
            fieldSelfType = null
            fieldSelfParams = emptyList()
        }

        val actualTypeParams = mergeTypeParameters(
            fieldSelfParams, fieldSelfType,
            field.typeParameters, typeParameters,
            origin
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
        context: ResolutionContext,
        name: String, nameAsImport: List<Import>,
        typeParameters: List<Type>?, // if provided, typically not the case (I've never seen it)
        origin: Int,
    ): ResolvedField? {
        val selfType = context.selfType
        LOGGER.info("TypeParams for field '$name': $typeParameters, scope: ${context.codeScope}, selfType: $selfType, targetType: ${context.targetType}")

        return resolveInCodeScope(context) { candidateScope, selfType ->
            findMemberInHierarchy(
                candidateScope, origin, name, context.targetType,
                selfType, typeParameters, emptyList()
            )
        } ?: resolveFieldByImportImpl(context, name, nameAsImport, typeParameters, origin)
    }

    fun resolveFieldByImportImpl(
        context: ResolutionContext,
        name: String, nameAsImport: List<Import>,
        typeParameters: List<Type>?, // if provided, typically not the case (I've never seen it)
        origin: Int,
    ): ResolvedField? {
        val returnType = context.targetType
        val selfTypes = ArrayList<Type>()
        resolveInCodeScope(context) { _, selfType ->
            if (selfType !in selfTypes) selfTypes.add(selfType)
        }

        for (import in nameAsImport) {
            if (import.name != name) continue

            val scope = import.path
            val scopeSelfType = getSelfType(scope)
            for (selfType in selfTypes) {
                val field = resolveFieldByImport(scope) ?: continue
                val valueType = getFieldReturnType(scopeSelfType, field, returnType)
                val match = findMemberMatch(
                    field, valueType,
                    returnType, selfType,
                    typeParameters, emptyList(),
                    origin
                )
                if (match != null) return match
            }
        }
        return null
    }

    fun resolveFieldByImport(nameAsImport: Scope): Field? {
        when (nameAsImport.scopeType) {
            ScopeType.OBJECT, ScopeType.COMPANION_OBJECT -> {
                nameAsImport.objectField!!
            }
            ScopeType.NORMAL_CLASS, ScopeType.INTERFACE, ScopeType.ENUM_CLASS -> {
                val parentCompanion = nameAsImport.companionObject
                if (parentCompanion != null) return parentCompanion.objectField!!

                // throw IllegalStateException("Could not resolve type for $nameAsImport in normal class")
            }
            null -> {

                // todo "Companion" could appear at all levels of the import :(
                val fieldName = nameAsImport.name
                val parent = nameAsImport.parent!!
                val matchingField = parent.fields.firstOrNull { it.name == fieldName }
                if (matchingField != null) return matchingField

                val parentCompanion = parent.companionObject
                val matchingField1 = parentCompanion?.fields?.firstOrNull { it.name == fieldName }
                if (matchingField1 != null) return matchingField1

                // throw IllegalStateException("Could not resolve field '$fieldName' in $parent")
            }
            else -> {
                TODO("what is the type of imported ${nameAsImport.pathStr} -> ${nameAsImport.scopeType}?")
            }
        }
        return null
    }

    fun resolveField(
        context: ResolutionContext, field: Field,
        typeParameters: List<Type>?, // if provided, typically not the case (I've never seen it)
        origin: Int
    ): ResolvedField? {
        val selfType = context.selfType
        LOGGER.info("TypeParams for field '$field': $typeParameters, selfType: $selfType")

        val valueType = getFieldReturnType(context.selfType, field, context.targetType)
        return findMemberMatch(
            field, valueType,
            context.targetType, selfType,
            typeParameters, emptyList(),
            origin
        )
    }

}