package me.anno.zauber.expansion

import me.anno.zauber.ast.rich.member.Constructor
import me.anno.zauber.ast.rich.member.Field
import me.anno.zauber.ast.rich.member.Method
import me.anno.zauber.ast.rich.member.MethodLike
import me.anno.zauber.ast.rich.parameter.Parameter
import me.anno.zauber.scope.Scope
import me.anno.zauber.scope.ScopeInit
import me.anno.zauber.scope.ScopeInitType
import me.anno.zauber.typeresolution.ResolutionContext
import me.anno.zauber.types.Specialization

/**
 * Resolved/replaces method/field/class types, so we don't have to do it later on again
 * */
object EarlyTypeResolution {

    val typeResolutionCreator = ScopeInit(ScopeInitType.RESOLVE_TYPES) { scope: Scope ->
        replaceUnresolvedTypes(scope)
    }

    private fun replaceUnresolvedTypes(scope: Scope) {
        val selfScope = findSelfScope(scope)

        val isOpen = selfScope != null && selfScope.isOpen()
        val selfScope1 = if (isOpen) null else selfScope

        val asMethod = scope.selfAsMethod
        if (asMethod != null) replaceUnresolvedTypesForMethod(asMethod, selfScope1)

        val asConstructor = scope.selfAsConstructor
        if (asConstructor != null) replaceUnresolvedTypesForConstructor(asConstructor, selfScope1)

        for (field in scope.fields) {
            replaceUnresolvedTypesForField(field, selfScope1)
        }
    }

    private fun replaceUnresolvedTypesForMethod(method: Method, selfScope: Scope?) {
        method.selfType = method.selfType?.resolve(selfScope)
        replaceUnresolvedTypesForMethodLike(method, selfScope)
    }

    private fun replaceUnresolvedTypesForConstructor(method: Constructor, selfScope: Scope?) {
        replaceUnresolvedTypesForMethodLike(method, selfScope)
    }

    private fun replaceUnresolvedTypesForMethodLike(method: MethodLike, selfScope: Scope?) {
        replaceUnresolvedTypesForParameters(method.typeParameters, selfScope)
        replaceUnresolvedTypesForParameters(method.valueParameters, selfScope)
        method.returnType = method.returnType?.resolve(selfScope)
    }

    private fun replaceUnresolvedTypesForParameters(parameters: List<Parameter>, selfScope: Scope?) {
        for (pi in parameters.indices) {
            val param = parameters[pi]
            param.type = param.type.resolve(selfScope)
        }
    }

    private fun findSelfScope(scope: Scope): Scope? {
        var scope = scope
        while (true) {
            if (scope.isClassLike()) return scope
            scope = scope.parentIfSameFile ?: return null
        }
    }

    private fun replaceUnresolvedTypesForField(field: Field, selfScope: Scope?) {
        field.selfType = field.selfType?.resolve(selfScope)
        field.valueType = field.valueType?.resolve(selfScope) ?: run {
            // todo define proper resolution context
            // todo we need knownLambdas and extensionThis -> this should already happen in the method
            val selfType = null
            field.resolveValueType(
                ResolutionContext(
                    selfType, Specialization.noSpecialization,
                    false, null, emptyMap(), emptyList()
                )
            )
        }
    }

}
