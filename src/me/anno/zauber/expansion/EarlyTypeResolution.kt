package me.anno.zauber.expansion

import me.anno.zauber.Zauber.root
import me.anno.zauber.ast.rich.expression.unresolved.LambdaExpression
import me.anno.zauber.ast.rich.expression.unresolved.LambdaVariable
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
        val selfScope0 = findSelfScope(scope)

        val isOpen = selfScope0 != null && selfScope0.isOpen()
        val selfScope = if (isOpen) null else selfScope0

        replaceAnnotationTypes(scope, selfScope)

        val asMethod = scope.selfAsMethod
        if (asMethod != null) replaceUnresolvedTypesForMethod(asMethod, selfScope)

        val asConstructor = scope.selfAsConstructor
        if (asConstructor != null) replaceUnresolvedTypesForConstructor(asConstructor, selfScope)

        val asLambda = scope.selfAsLambda
        if (asLambda != null) replaceUnresolvedTypesForLambda(asLambda, selfScope)

        val fields = scope.fields
        for (i in fields.indices) {
            replaceUnresolvedTypesForField(fields[i], selfScope)
        }
    }

    private fun replaceUnresolvedTypesForMethod(method: Method, selfScope: Scope?) {
        method.selfType = method.selfType?.resolve(selfScope)
        replaceUnresolvedTypesForMethodLike(method, selfScope)
    }

    private fun replaceUnresolvedTypesForConstructor(method: Constructor, selfScope: Scope?) {
        replaceUnresolvedTypesForMethodLike(method, selfScope)
    }

    private fun replaceUnresolvedTypesForLambda(method: LambdaExpression, selfScope: Scope?) {
        val lambdaVariables = method.variables ?: return
        for (variable in lambdaVariables) {
            variable.type = variable.type?.resolve(selfScope)
        }
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

            // todo we need knownLambdas and extensionThis -> this should already happen in the method

            val specForSelfScope = Specialization.allUnknown(getMethodOrClassScope(field))

            val ctx = ResolutionContext(
                selfType = null, specForSelfScope,
                false, null, emptyMap(), emptyList()
            )
            if (field.initialValue == null && field.byParameter is LambdaVariable) null
            else field.resolveValueType(ctx)
        }
    }

    private fun getMethodOrClassScope(field: Field): Scope {
        var scope = field.ownerScope
        while (true) {
            if (scope.isClassLike() || scope.isMethodLike()) return scope
            scope = scope.parentIfSameFile ?: return root
        }
    }

    private fun replaceAnnotationTypes(scope: Scope, selfScope: Scope?) {
        val annotations = scope.annotations
        for (i in annotations.indices) {
            val annotation = annotations[i]
            annotation.type = annotation.type.resolve(selfScope)
        }
    }

}
