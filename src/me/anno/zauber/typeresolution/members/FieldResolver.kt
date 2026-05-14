package me.anno.zauber.typeresolution.members

import me.anno.zauber.Compile.root
import me.anno.zauber.ast.rich.Field
import me.anno.zauber.ast.rich.controlflow.ReturnExpression
import me.anno.zauber.logging.LogManager
import me.anno.zauber.scope.Scope
import me.anno.zauber.scope.ScopeInitType
import me.anno.zauber.typeresolution.ParameterList
import me.anno.zauber.typeresolution.ResolutionContext
import me.anno.zauber.typeresolution.TypeResolution.getSelfType
import me.anno.zauber.typeresolution.TypeResolution.resolveType
import me.anno.zauber.typeresolution.ValueParameter
import me.anno.zauber.typeresolution.members.MergeTypeParams.mergeCallPart
import me.anno.zauber.typeresolution.members.MergeTypeParams.mergeTypeParameters
import me.anno.zauber.typeresolution.members.ResolvedMethod.Companion.selfTypeToTypeParams
import me.anno.zauber.types.Import
import me.anno.zauber.types.Type
import me.anno.zauber.types.impl.ClassType

object FieldResolver : MemberResolver<Field, ResolvedField>() {

    private val LOGGER = LogManager.getLogger(FieldResolver::class)

    override fun findMemberInScope(
        scope: Scope?, origin: Int, name: String,
        typeParameters: List<Type>?,
        valueParameters: List<ValueParameter>,
        context: ResolutionContext
    ): ResolvedField? {
        scope ?: return null

        // println("Searching for '$name' in $scope, from ${resolveOrigin(origin)}")

        val selfType = context.selfType
        val returnType = context.targetType

        val scopeSelfType = getSelfType(scope)
        var bestMatch: ResolvedField? = null
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
                scope, origin
            )
            bestMatch = joinMatches(bestMatch, match)
        }

        val companion = scope.companionObject
        if (companion != null) {
            if (scope.name == name) {
                val field = companion.getOrCreateObjectField(-1)
                val valueType = getFieldReturnType(scopeSelfType, field, returnType)
                val match = findMemberMatch(
                    field, valueType,
                    returnType, selfType,
                    typeParameters, valueParameters,
                    scope, origin
                )
                bestMatch = joinMatches(bestMatch, match)
            }
            val match = findMemberInScope(
                companion, origin, name, returnType, selfType,
                typeParameters, valueParameters, context,
            )
            bestMatch = joinMatches(bestMatch, match)
        }

        for (child in scope[ScopeInitType.AFTER_DISCOVERY].children) {
            if (child.name != name || !child.isObjectLike()) continue

            child[ScopeInitType.AFTER_DISCOVERY]

            val field = child.getOrCreateObjectField(-1)
            val valueType = getFieldReturnType(scopeSelfType, field, returnType)
            val match = findMemberMatch(
                field, valueType,
                returnType, selfType,
                typeParameters, valueParameters,
                scope, origin
            )
            bestMatch = joinMatches(bestMatch, match)
        }
        return bestMatch
    }

    fun joinMatches(bestMatch: ResolvedField?, match: ResolvedField?): ResolvedField? {
        return if (match != null && (bestMatch == null || match.matchScore < bestMatch.matchScore)) match
        else bestMatch
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
            val contextSelfType = field.selfType ?: scopeSelfType
            val context = ResolutionContext(contextSelfType, false, null, emptyMap())
            field.valueType = resolveType(context, expr)
        }
        return field.valueType
    }

    fun findMemberMatch(
        field0: Field,
        byFieldExpectedType: Type?,

        byExprExpectedType: Type?, // sometimes, we know what to expect from the return type
        selfType: Type?, //  this is non-null, iff the access is explicit (this.x, not just x)

        typeParameters: List<Type>?,
        valueParameters: List<ValueParameter>,
        codeScope: Scope, origin: Int
    ): ResolvedField? {
        check(valueParameters.isEmpty())

        val field = field0.getBackedField() ?: field0
        val isBackingField = field0 != field

        if (field.selfType == null) {

            val actualTypeParams = mergeCallPart(field.typeParameters, typeParameters, origin)

            LOGGER.info("Resolving generics for field $field")
            val matchScore = MatchScore(2)
            val generics = findGenericsForMatch(
                null, null,
                byFieldExpectedType, byExprExpectedType,
                field.typeParameters, actualTypeParams,
                emptyList(), valueParameters, matchScore
            ) ?: return null

            val ownerTypes = filterTypeParams((selfType as? ClassType)?.typeParameters, field.ownerScope)

            val context = ResolutionContext(null, false, byFieldExpectedType, emptyMap())
            // println("Generics for resolved field/2: $field, $selfType -> $ownerTypes, $generics, ctx: ${context.specialization}")
            return ResolvedField(
                ownerTypes, field, generics,
                context, codeScope, isBackingField, matchScore, origin
            )
        } else {

            var fieldSelfType = field.selfType?.resolve(null)
            val selfTypeI = selfType
                ?: if (fieldSelfType is ClassType && fieldSelfType.clazz.isObject())
                    field.selfType else null

            var fieldSelfParams = selfTypeToTypeParams(field.selfType, selfTypeI)
            if (fieldSelfType is ClassType && !fieldSelfType.clazz.isClassLike()) {
                LOGGER.info("Field '$field' had invalid selfType: $fieldSelfType")
                fieldSelfType = null
                fieldSelfParams = emptyList()
            }

            val actualTypeParams = mergeTypeParameters(
                fieldSelfParams, fieldSelfType,
                field.typeParameters, typeParameters,
                origin
            )

            LOGGER.info("Resolving generics for field $field")
            val matchScore = MatchScore(2)
            val generics = findGenericsForMatch(
                fieldSelfType, selfTypeI,
                byFieldExpectedType, byExprExpectedType,
                fieldSelfParams + field.typeParameters, actualTypeParams,
                emptyList(), valueParameters, matchScore
            ) ?: return null

            val selfType = selfTypeI ?: fieldSelfType
            val context = ResolutionContext(selfType, false, byFieldExpectedType, emptyMap())
            // println("Generics for resolved field: $field -> $generics")
            return ResolvedField(
                generics.subList(0, fieldSelfParams.size), field,
                generics.subList(fieldSelfParams.size, generics.size),
                context, codeScope, isBackingField, matchScore, origin
            )
        }
    }

    fun filterTypeParams(typeParams: ParameterList?, scope: Scope): ParameterList {
        return typeParams?.filterByGenerics { it.scope == scope }
            ?: ParameterList.emptyParameterList()
    }

    fun resolveField(
        context: ResolutionContext, codeScope: Scope,
        name: String, nameAsImport: List<Import>,
        typeParameters: List<Type>?, // if provided, typically not the case (I've never seen it)
        origin: Int,
    ): ResolvedField? {
        val selfType = context.selfType
        LOGGER.info("TypeParams for field '$name': $typeParameters, scope: $codeScope, selfType: $selfType, targetType: ${context.targetType}")

        return resolveInCodeScope(context, codeScope) { candidateScope, selfType ->
            findMemberInScope(
                candidateScope, origin, name,
                typeParameters, emptyList(),
                context.withSelfType(selfType)
            )
        } ?: resolveFieldByImports(
            context, codeScope,
            name, nameAsImport,
            typeParameters, origin
        ) ?: resolveFieldByPackages(
            context, name, origin
        )
    }

    fun resolveFieldByImports(
        context: ResolutionContext, codeScope: Scope,
        name: String, nameAsImport: List<Import>,
        typeParameters: List<Type>?, // if provided, typically not the case (I've never seen it)
        origin: Int,
    ): ResolvedField? {
        if (nameAsImport.isEmpty()) return null

        val selfTypes = ArrayList<Type?>()
        resolveInCodeScope(context, codeScope) { _, selfType ->
            if (selfType !in selfTypes) selfTypes.add(selfType)
        }
        if (null !in selfTypes) selfTypes.add(null)

        // println("Checking imports $nameAsImport, selfType: ${context.selfType}, selfTypes: $selfTypes")
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
                    typeParameters, emptyList(), context
                )
                if (field != null) return field
            }
        }
        return null
    }

    fun resolveFieldByPackages(context: ResolutionContext, name: String, origin: Int): ResolvedField? {
        if (context.selfType != null) return null
        val root = root[ScopeInitType.AFTER_DISCOVERY]
        return findMemberInScope(root, origin, name, null, emptyList(), context)
    }

    fun resolveField(
        context: ResolutionContext, field: Field,
        typeParameters: List<Type>?, // if provided, typically not the case (I've never seen it)
        scope: Scope, origin: Int,
    ): ResolvedField? {
        val selfType = context.selfType
        LOGGER.info("TypeParams for field '$field': $typeParameters, selfType: $selfType")

        val valueType = getFieldReturnType(context.selfType, field, context.targetType)
        return findMemberMatch(
            field, valueType,
            context.targetType, selfType,
            typeParameters, emptyList(),
            scope, origin
        )
    }

}