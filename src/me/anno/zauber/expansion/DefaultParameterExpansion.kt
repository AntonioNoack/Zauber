package me.anno.zauber.expansion

import me.anno.zauber.astbuilder.Method
import me.anno.zauber.astbuilder.NamedParameter
import me.anno.zauber.astbuilder.expression.CallExpression
import me.anno.zauber.astbuilder.expression.NameExpression
import me.anno.zauber.astbuilder.expression.NamedCallExpression
import me.anno.zauber.astbuilder.expression.constants.SpecialValue
import me.anno.zauber.astbuilder.expression.constants.SpecialValueExpression
import me.anno.zauber.typeresolution.TypeResolution.forEachScope
import me.anno.zauber.types.Scope
import me.anno.zauber.types.ScopeType
import me.anno.zauber.types.impl.GenericType

object DefaultParameterExpansion {

    fun createDefaultParameterFunctions(scope: Scope) {
        forEachScope(scope, ::createDefaultParameterFunction)
    }

    private fun createDefaultParameterFunction(scope: Scope) {
        val scopeParent = scope.parent ?: return
        val self = scope.selfAsMethod ?: return
        val params = self.valueParameters
        for (i in params.lastIndex downTo 0) {
            val param = params[i]
            param.initialValue ?: return

            // check if class has another function with that parameter defined
            val match = scopeParent.children.firstOrNull {
                val method = it.selfAsMethod
                method != null && method.name == self.name &&
                        method.selfType == self.selfType &&
                        method.valueParameters.map { param -> param.type } ==
                        self.valueParameters.subList(0, i).map { param -> param.type }
            }
            if (match != null) {
                println("Useless type-param: $self is defined by $match")
                continue
            }


            val origin = self.origin

            val scopeName = scopeParent.generateName("f:${self.name}")
            val scope = scopeParent.getOrPut(scopeName, ScopeType.METHOD)

            val typeParams = self.typeParameters.map { GenericType(scope, it.name) }
            val valueParams = self.valueParameters.mapIndexed { index, parameter ->
                val value =
                    if (index < i) NameExpression(parameter.name, scope, origin)
                    else parameter.initialValue!!
                NamedParameter(parameter.name, value)
            }

            val method = Method(
                self.selfType,
                self.name,
                self.typeParameters,
                self.valueParameters.subList(0, i),
                scope,
                self.returnType,
                self.extraConditions,
                if (self.selfType == null) {
                    CallExpression(
                        NameExpression(self.name!!, scope, origin),
                        typeParams, valueParams, origin
                    )
                } else {
                    NamedCallExpression(
                        SpecialValueExpression(SpecialValue.THIS, scope, origin),
                        self.name!!, typeParams,
                        valueParams, scope, origin
                    )
                },
                self.keywords,
                origin
            )
            scope.selfAsMethod = method
            println("Created $method")
        }
    }
}