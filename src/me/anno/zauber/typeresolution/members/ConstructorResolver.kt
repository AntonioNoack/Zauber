package me.anno.zauber.typeresolution.members

import me.anno.zauber.ast.rich.Constructor
import me.anno.zauber.ast.rich.Parameter
import me.anno.zauber.logging.LogManager
import me.anno.zauber.typeresolution.ParameterList
import me.anno.zauber.typeresolution.ParameterList.Companion.emptyParameterList
import me.anno.zauber.typeresolution.ResolutionContext
import me.anno.zauber.typeresolution.TypeResolution.typeToScope
import me.anno.zauber.typeresolution.ValueParameter
import me.anno.zauber.typeresolution.members.ResolvedMember.Companion.resolveGenerics
import me.anno.zauber.types.Scope
import me.anno.zauber.types.ScopeType
import me.anno.zauber.types.Type

object ConstructorResolver : MemberResolver<Constructor, ResolvedConstructor>() {

    private val LOGGER = LogManager.getLogger(ConstructorResolver::class)

    override fun findMemberInScope(
        scope: Scope?, origin: Int, name: String,

        returnType: Type?, // sometimes, we know what to expect from the return type
        selfType: Type?, // if inside Companion/Object/Class/Interface, this is defined; else null

        typeParameters: List<Type>?,
        valueParameters: List<ValueParameter>
    ): ResolvedConstructor? {
        LOGGER.info("Checking $scope for constructor $name")
        scope ?: return null
        if (scope.name == name) {
            val constructor = findMemberInScopeImpl(scope, name, returnType, selfType, typeParameters, valueParameters)
            if (constructor != null) return constructor
        }
        // LOGGER.info("  children: ${scope.children.map { it.name }}")
        for (child in scope.children) {
            if (child.name == name/* && child.scopeType?.isClassType() == true*/) {
                val constructor =
                    findMemberInScopeImpl(child, name, returnType, selfType, typeParameters, valueParameters)
                if (constructor != null) return constructor
            }
        }
        return null
    }

    fun findMemberInScopeImpl(
        scope: Scope, name: String,

        returnType: Type?, // sometimes, we know what to expect from the return type
        selfType: Type?, // if inside Companion/Object/Class/Interface, this is defined; else null

        typeParameters: List<Type>?,
        valueParameters: List<ValueParameter>,
    ): ResolvedConstructor? {
        LOGGER.info("Checking $scope for constructors")
        check(scope.name == name)
        val children = scope.children
        for (i in children.indices) {
            val constructor = children[i].selfAsConstructor ?: continue
            // if (method.name != name) continue
            if (constructor.typeParameters.isNotEmpty()) {
                LOGGER.info("Given $constructor on $selfType, with target $returnType, can we deduct any generics from that?")
            }
            val match = findMemberMatch(
                constructor, constructor.selfType,
                returnType,
                typeParameters, valueParameters,
                /* todo is this ok?? */scope,
            )
            LOGGER.info("Match($constructor): $match")
            if (match != null) return match
        }
        if (scope.scopeType == ScopeType.TYPE_ALIAS) {
            return getByTypeAlias(scope, returnType, selfType, typeParameters, valueParameters)
        }
        return null
    }

    private fun getByTypeAlias(
        scope: Scope,

        returnType: Type?, // sometimes, we know what to expect from the return type
        selfType: Type?, // if inside Companion/Object/Class/Interface, this is defined; else null

        typeParameters: List<Type>?,
        valueParameters: List<ValueParameter>,
    ): ResolvedConstructor? {

        // this can still happen during type-resolution,
        //  because we don't know yet whether a method call is a constructor with 100% accuracy

        val newType = scope.selfAsTypeAlias!!
        val typeParameters0 = typeParameters
            ?.toParameterList(scope.typeParameters)

        val newType2 = if (typeParameters0 != null) resolveGenerics(
            selfType, newType,
            scope.typeParameters,
            typeParameters0
        ) else newType

        val typeParameters2 = typeParameters?.map { typeParam ->
            resolveGenerics(
                selfType, typeParam,
                scope.typeParameters,
                typeParameters0!!
            )
        }

        val scope = typeToScope(newType2)!!
        return findMemberInScopeImpl(
            scope, scope.name, returnType, selfType,
            typeParameters2, valueParameters
        )
    }

    private fun findMemberMatch(
        constructor: Constructor,
        memberReturnType: Type?,

        returnType: Type?, // sometimes, we know what to expect from the return type

        typeParameters: List<Type>?,
        valueParameters: List<ValueParameter>,
        codeScope: Scope,
    ): ResolvedConstructor? {

        val typeParameters = typeParameters
            ?.toParameterList(constructor.selfType.clazz.typeParameters)

        LOGGER.info("Resolving generics for constructor $constructor")
        val generics = findGenericsForMatch(
            null, null,
            memberReturnType, returnType,
            constructor.selfType.clazz.typeParameters, typeParameters,
            constructor.valueParameters, valueParameters
        ) ?: return null
        val context = ResolutionContext(constructor.selfType, false, returnType, emptyMap())
        return ResolvedConstructor(generics, constructor, context, codeScope)
    }

    fun List<Type>.toParameterList(generics: List<Parameter>): ParameterList {
        if (isEmpty()) return emptyParameterList()
        return ParameterList(generics, this)
    }
}