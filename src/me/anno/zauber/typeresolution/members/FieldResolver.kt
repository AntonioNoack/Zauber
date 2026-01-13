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

        // println("Searching for '$name' in $scope")

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

        val companion = scope.companionObject
        if (companion != null) {
            if (scope.name == name) {
                val field = companion.objectField!!
                val valueType = getFieldReturnType(scopeSelfType, field, returnType)
                val match = findMemberMatch(
                    field, valueType,
                    returnType, selfType,
                    typeParameters, valueParameters,
                    origin
                )
                if (match != null) return match
            }
            val field = findMemberInScope(
                companion, origin, name, returnType, selfType,
                typeParameters, valueParameters,
            )
            if (field != null) return field
        }

        for (child in scope.children) {
            if (child.name != name) continue
            val field = child.objectField ?: continue
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

        val selfTypeI = selfType
            ?: if (field.selfType is ClassType && field.selfType.clazz.isObject())
                field.selfType else null

        var fieldSelfParams = selfTypeToTypeParams(field.selfType, selfTypeI)
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
            fieldSelfType, if (fieldSelfType == null) null else selfTypeI,
            fieldReturnType, returnType,
            fieldSelfParams + field.typeParameters, actualTypeParams,
            emptyList(), valueParameters
        ) ?: return null

        val selfType = selfTypeI ?: fieldSelfType
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
        } ?: resolveFieldByImports(context, name, nameAsImport, typeParameters, origin)
    }

    fun resolveFieldByImports(
        context: ResolutionContext,
        name: String, nameAsImport: List<Import>,
        typeParameters: List<Type>?, // if provided, typically not the case (I've never seen it)
        origin: Int,
    ): ResolvedField? {
        if (nameAsImport.isEmpty()) return null

        val selfTypes = ArrayList<Type?>()
        resolveInCodeScope(context) { _, selfType ->
            if (selfType !in selfTypes) selfTypes.add(selfType)
        }
        if (null !in selfTypes) selfTypes.add(null)

        println("Checking imports $nameAsImport, selfType: ${context.selfType}, selfTypes: $selfTypes")
        val returnType = context.targetType
        for (import in nameAsImport) {
            if (import.name != name) continue
            val importPath = import.path
            val originalName = importPath.name
            val isUnknownScope = importPath.scopeType == null
            val scope = if (isUnknownScope) importPath.parent else importPath

            for (selfType in selfTypes) {
                val field = findMemberInScope(
                    scope, origin, originalName, returnType, selfType,
                    typeParameters, emptyList()
                )
                if (field != null) return field
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