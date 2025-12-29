package me.anno.zauber.typeresolution.members

import me.anno.zauber.astbuilder.Constructor
import me.anno.zauber.logging.LogManager
import me.anno.zauber.typeresolution.ResolutionContext
import me.anno.zauber.typeresolution.ValueParameter
import me.anno.zauber.types.Scope
import me.anno.zauber.types.Type

object ConstructorResolver : MemberResolver<Constructor, ResolvedConstructor>() {

    private val LOGGER = LogManager.getLogger(ConstructorResolver::class)

    override fun findMemberInScope(
        scope: Scope?, name: String,

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

    private fun findMemberInScopeImpl(
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
            )
            LOGGER.info("Match($constructor): $match")
            if (match != null) return match
        }
        return null
    }

    private fun findMemberMatch(
        constructor: Constructor,
        memberReturnType: Type?,

        returnType: Type?, // sometimes, we know what to expect from the return type

        typeParameters: List<Type>?,
        valueParameters: List<ValueParameter>,
    ): ResolvedConstructor? {
        val generics = findGenericsForMatch(
            null, null,
            memberReturnType, returnType,
            constructor.selfType.clazz.typeParameters, typeParameters,
            constructor.valueParameters, valueParameters
        ) ?: return null
        val context = ResolutionContext(
            constructor.selfType.clazz, constructor.selfType,
            false, returnType
        )
        return ResolvedConstructor(generics, constructor, context)
    }
}