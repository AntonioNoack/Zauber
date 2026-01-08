package me.anno.zauber.typeresolution.members

import me.anno.zauber.ast.rich.Field
import me.anno.zauber.logging.LogManager
import me.anno.zauber.typeresolution.ParameterList
import me.anno.zauber.typeresolution.ResolutionContext
import me.anno.zauber.typeresolution.TypeResolution.getSelfType
import me.anno.zauber.typeresolution.TypeResolution.langScope
import me.anno.zauber.typeresolution.TypeResolution.resolveType
import me.anno.zauber.typeresolution.ValueParameter
import me.anno.zauber.typeresolution.members.MergeTypeParams.mergeTypeParameters
import me.anno.zauber.typeresolution.members.ResolvedMethod.Companion.selfTypeToTypeParams
import me.anno.zauber.types.Scope
import me.anno.zauber.types.ScopeType
import me.anno.zauber.types.Type
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

    private fun getOuterClassDepth(scope: Scope?): Int {
        var scope = scope
        while (scope != null) {
            if (scope.scopeType?.isClassType() == true) {
                return scope.path.size
            }

            scope = scope.parentIfSameFile
        }
        return -1
    }

    private fun isScopeAvailable(maybeSelfScope: Scope, originalSelfScope: Int): Boolean {
        return when (maybeSelfScope.scopeType) {
            ScopeType.INLINE_CLASS, ScopeType.PACKAGE, ScopeType.OBJECT -> true // only one instance
            ScopeType.INTERFACE, ScopeType.NORMAL_CLASS, ScopeType.ENUM_CLASS ->
                maybeSelfScope.path.size >= originalSelfScope
            else -> true // idk
        }
    }

    fun resolveField(
        context: ResolutionContext, name: String,
        typeParameters: ParameterList?, // if provided, typically not the case (I've never seen it)
    ): ResolvedField? {
        val returnType = context.targetType
        val selfType = context.selfType
        LOGGER.info("TypeParams for field '$name': $typeParameters, selfType: $selfType")
        val valueParameters = emptyList<ValueParameter>()

        var field: ResolvedField? = null
        var maybeSelfScope = context.selfScope
        val outerClassDepth = getOuterClassDepth(maybeSelfScope)
        while (maybeSelfScope != null && field == null) {
            if (isScopeAvailable(maybeSelfScope, outerClassDepth)) {
                println("Checking for field '$name' in $maybeSelfScope")
                field = findMemberInHierarchy(
                    maybeSelfScope, name, returnType,
                    if (maybeSelfScope == context.selfType) selfType
                    else maybeSelfScope.typeWithoutArgs, typeParameters, valueParameters
                )
            } else println("Skipping scope '$maybeSelfScope'")

            maybeSelfScope = maybeSelfScope.parentIfSameFile
        }

        field =
            field ?: findMemberInFile(context.codeScope, name, returnType, selfType, typeParameters, valueParameters)
        field = field ?: findMemberInFile(langScope, name, returnType, selfType, typeParameters, valueParameters)
        return field
    }

}