package me.anno.zauber.expansion

import me.anno.zauber.ast.rich.*
import me.anno.zauber.ast.rich.expression.CallExpression
import me.anno.zauber.ast.rich.expression.ExpressionList
import me.anno.zauber.ast.rich.expression.MemberNameExpression
import me.anno.zauber.ast.rich.expression.NamedCallExpression
import me.anno.zauber.ast.rich.expression.constants.SpecialValue
import me.anno.zauber.ast.rich.expression.constants.SpecialValueExpression
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
                    createDefaultParameterFunction(scopeI)
                ScopeType.PRIMARY_CONSTRUCTOR, ScopeType.CONSTRUCTOR ->
                    createDefaultParameterConstructor(scopeI)
                else -> {}
            }
        }
    }

    private fun createDefaultParameterFunction(scope: Scope) {
        val scopeParent = scope.parent ?: return
        val self = scope.selfAsMethod ?: return
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

            val scopeName = scopeParent.generateName("f:${self.name}")
            val scope = scopeParent.getOrPut(scopeName, ScopeType.METHOD)
            scope.typeParameters = self.typeParameters

            val newTypeParameters = self.typeParameters.map { GenericType(scope, it.name) }
            val newValueParameters = self.valueParameters.mapIndexed { index, parameter ->
                val value =
                    if (index < i) MemberNameExpression(parameter.name, scope, origin)
                    else parameter.defaultValue!!
                NamedParameter(parameter.name, value)
            }

            val newBody = if (self.selfType == null) {
                CallExpression(
                    MemberNameExpression(self.name!!, scope, origin),
                    newTypeParameters, newValueParameters, origin
                )
            } else {
                NamedCallExpression(
                    SpecialValueExpression(SpecialValue.THIS, scope, origin),
                    self.name!!, newTypeParameters,
                    newValueParameters, scope, origin
                )
            }
            val newMethod = Method(
                self.selfType, self.name,
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


    private fun createDefaultParameterConstructor(scope: Scope) {
        val scopeParent = scope.parent ?: return
        val self = scope.selfAsConstructor ?: return
        val valueParameters = self.valueParameters
        for (i in valueParameters.lastIndex downTo 0) {
            val param = valueParameters[i]
            if (param.defaultValue == null) return

            // check if class has another function with that parameter defined
            val expectedParamsForMatch = self.valueParameters.subList(0, i).map { param -> param.type }
            val match = scopeParent.children.firstOrNull {
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

            val origin = self.origin

            val scopeName = scopeParent.generateName("constructor")
            val scope = scopeParent.getOrPut(scopeName, ScopeType.CONSTRUCTOR)
            scope.typeParameters = self.typeParameters

            val newValueParameters = self.valueParameters.mapIndexed { index, parameter ->
                val value =
                    if (index < i) MemberNameExpression(parameter.name, scope, origin)
                    else parameter.defaultValue!!
                NamedParameter(parameter.name, value)
            }

            val superCall = InnerSuperCall(InnerSuperCallTarget.THIS, newValueParameters)
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