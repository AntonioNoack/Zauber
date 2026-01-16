package me.anno.zauber.expansion

import me.anno.zauber.ast.rich.*
import me.anno.zauber.ast.rich.expression.ExpressionList
import me.anno.zauber.ast.rich.expression.resolved.ThisExpression
import me.anno.zauber.ast.rich.expression.unresolved.CallExpression
import me.anno.zauber.ast.rich.expression.unresolved.NamedCallExpression
import me.anno.zauber.ast.rich.expression.unresolved.UnresolvedFieldExpression
import me.anno.zauber.logging.LogManager
import me.anno.zauber.typeresolution.TypeResolution.forEachScope
import me.anno.zauber.types.Scope
import me.anno.zauber.types.ScopeType
import me.anno.zauber.types.impl.GenericType

object DefaultParameterExpansion {

    private val LOGGER = LogManager.getLogger(DefaultParameterExpansion::class)

    fun createDefaultParameterFunctions(scope: Scope) {
        forEachScope(scope) { scopeI ->
            when (scopeI.scopeType) {
                ScopeType.METHOD ->
                    createDefaultParameterMethod(scopeI)
                ScopeType.CONSTRUCTOR ->
                    createDefaultParameterConstructor(scopeI)
                else -> {}
            }
        }
    }

    private fun createDefaultParameterMethod(methodScope: Scope) {
        val scopeParent = methodScope.parent ?: return
        val self = methodScope.selfAsMethod ?: return
        val valueParameters = self.valueParameters
        for (i in valueParameters.lastIndex downTo 0) {
            val param = valueParameters[i]
            if (param.defaultValue == null) return

            // check if class has another function with that parameter defined
            val expectedParamsForMatch = self.valueParameters.subList(0, i).map { param -> param.type }
            val match = scopeParent.children.firstOrNull {
                val method = it.selfAsMethod
                method != null && method.name == self.name &&
                        method.selfType == self.selfType &&
                        method.valueParameters.map { param -> param.type } ==
                        expectedParamsForMatch
            }
            if (match != null) {
                LOGGER.info("Unused default-parameter: '$self'.${param.name} is already defined by $match")
                continue
            }

            val origin = self.origin

            val scope = scopeParent.generate("f:${self.name}", ScopeType.METHOD)
            scope.typeParameters = self.typeParameters

            val newTypeParameters = self.typeParameters.map { GenericType(scope, it.name) }
            val newValueParameters = self.valueParameters.mapIndexed { index, parameter ->
                val value =
                    if (index < i) UnresolvedFieldExpression(parameter.name, emptyList(), scope, origin)
                    else parameter.defaultValue!!
                NamedParameter(parameter.name, value)
            }

            val newBody = if (self.selfType == null) {
                CallExpression(
                    UnresolvedFieldExpression(self.name!!, emptyList(), scope, origin),
                    newTypeParameters, newValueParameters, origin
                )
            } else {
                NamedCallExpression(
                    ThisExpression(scopeParent, scope, origin),
                    self.name!!, emptyList(),
                    newTypeParameters, newValueParameters, scope, origin
                )
            }
            val newMethod = Method(
                self.selfType, self.explicitSelfType, self.name,
                self.typeParameters, self.valueParameters.subList(0, i),
                scope, self.returnType, self.extraConditions,
                newBody,
                self.keywords,
                origin
            )
            scope.selfAsMethod = newMethod
            LOGGER.info("Created $newMethod")
        }
    }

    private fun createDefaultParameterConstructor(constructorScope: Scope) {
        val classScope = constructorScope.parent ?: return
        val self = constructorScope.selfAsConstructor ?: return
        val valueParameters = self.valueParameters
        for (i in valueParameters.lastIndex downTo 0) {
            val param = valueParameters[i]
            if (param.defaultValue == null) return

            // check if class has another function with that parameter defined
            val expectedParamsForMatch = self.valueParameters.subList(0, i).map { param -> param.type }
            val match = classScope.children.firstOrNull {
                val method = it.selfAsConstructor
                method != null &&
                        method.selfType == self.selfType &&
                        method.valueParameters.map { param -> param.type } ==
                        expectedParamsForMatch
            }
            if (match != null) {
                LOGGER.info("Unused default-parameter: '$self'.${param.name} is already defined by $match")
                continue
            }


            val scopeName = classScope.generateName("synthetic:constructor")
            val scope = classScope.getOrPut(scopeName, ScopeType.CONSTRUCTOR)
            scope.typeParameters = self.typeParameters

            val origin = self.origin
            val newValueParameters = self.valueParameters.mapIndexed { index, parameter ->
                val value =
                    if (index < i) UnresolvedFieldExpression(parameter.name, emptyList(), scope, origin)
                    else parameter.defaultValue!!
                NamedParameter(parameter.name, value)
            }

            val superCall = InnerSuperCall(
                InnerSuperCallTarget.THIS, newValueParameters,
                self.origin // is this fine?
            )
            val newConstructor = Constructor(
                self.valueParameters.subList(0, i),
                scope, superCall, ExpressionList(emptyList(), scope, origin),
                self.keywords, origin
            )
            scope.selfAsConstructor = newConstructor
            LOGGER.info("Created $newConstructor")
        }
    }
}