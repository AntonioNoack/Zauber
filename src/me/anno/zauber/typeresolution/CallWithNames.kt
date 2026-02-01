package me.anno.zauber.typeresolution

import me.anno.zauber.ast.rich.NamedParameter
import me.anno.zauber.ast.rich.Parameter
import me.anno.zauber.ast.rich.TokenListIndex.resolveOrigin
import me.anno.zauber.ast.rich.expression.Expression
import me.anno.zauber.ast.rich.expression.ExpressionList
import me.anno.zauber.ast.rich.expression.constants.NumberExpression
import me.anno.zauber.ast.rich.expression.unresolved.*
import me.anno.zauber.types.Scope
import me.anno.zauber.types.Type
import me.anno.zauber.types.Types.ArrayType
import me.anno.zauber.types.impl.ClassType
import me.anno.zauber.types.impl.UnionType.Companion.unionTypes

object CallWithNames {

    private fun hasTooFewParameters(
        anyIsVararg: Boolean,
        expectedParameters: Int,
        actualParameters: Int
    ): Boolean {
        if (anyIsVararg) {
            // empty entry is valid, too
            if (expectedParameters > actualParameters + 1) {
                return true
            }
        } else {
            if (expectedParameters > actualParameters) {
                return true
            }
        }
        return false
    }

    /**
     * Change the order of value parameters if needed.
     * execution order must remain unchanged!
     * */
    fun resolveNamedParameters(
        expectedParameters: List<Parameter>,
        actualParameters: List<ValueParameter>
    ): List<ValueParameter>? {
        val anyIsVararg = expectedParameters.any { it.isVararg }
        if (hasTooFewParameters(anyIsVararg, expectedParameters.size, actualParameters.size)) return null

        return if (actualParameters.any { it.name != null || it.hasVarargStar } ||
            actualParameters.size != expectedParameters.size ||
            anyIsVararg
        ) {

            val result = arrayOfNulls<ValueParameter>(expectedParameters.size)

            // first assign all names slots
            for (valueParam in actualParameters) {
                val name = valueParam.name ?: continue
                val index = expectedParameters.indexOfFirst { it.name == name }
                if (index < 0) return null
                check(result[index] == null)
                result[index] = valueParam
            }

            // then all unnamed
            // todo if vararg
            //  if is Prefix with ARRAY_TO_VARARGS, then assign directly
            //  else create an array, step by step...
            var index = 0
            for (j in actualParameters.indices) {
                val valueParam = actualParameters[j]
                if (valueParam.name != null) continue
                while (result[index] != null) index++

                val ev = expectedParameters[index]
                if (!ev.isVararg && valueParam.hasVarargStar) return null // incompatible
                val isNormal = !ev.isVararg || valueParam.hasVarargStar
                if (isNormal) {
                    result[index++] = valueParam
                } else {
                    check(index == result.lastIndex) { "vararg must be in last place, $index vs ${result.lastIndex}" }
                    // collect all varargs
                    val type0 = ev.type as ClassType
                    val targetType = type0.typeParameters!![0]
                    val values = actualParameters.subList(j, actualParameters.size)
                        .filter { it.name == null }
                        .map { it.getType(targetType) }
                    val types = unionTypes(values)
                    val arrayType = ClassType(type0.clazz, listOf(types), ev.origin)
                    result[index++] = ValueParameterImpl(null, arrayType, true)
                    break
                }
            }

            if (index == result.lastIndex) {
                val ev = expectedParameters[index]
                if (!ev.isVararg) return null
                // check(ev.isVararg) { "Expected vararg in last place" }
                val arrayOfUnknown = ClassType(ArrayType.clazz, null)
                result[index] = ValueParameterImpl(null, arrayOfUnknown, true)
            }

            check(result.none { it == null })

            @Suppress("UNCHECKED_CAST")
            result.toList() as List<ValueParameter>
        } else actualParameters
    }

    /**
     * Change the order of value parameters if needed.
     * execution order must remain unchanged!
     * */
    fun resolveNamedParameters(
        expectedParameters: List<Parameter>,
        actualParameters: List<NamedParameter>,
        scope: Scope, origin: Int,
    ): List<Expression>? {
        val anyIsVararg = expectedParameters.any { it.isVararg }
        if (hasTooFewParameters(anyIsVararg, expectedParameters.size, actualParameters.size)) return null

        return if (actualParameters.any { it.name != null || it.value is ArrayToVarargsStar } ||
            actualParameters.size != expectedParameters.size ||
            anyIsVararg
        ) {

            val result = arrayOfNulls<Expression>(expectedParameters.size)

            // first assign all names slots
            for (valueParam in actualParameters) {
                val name = valueParam.name ?: continue
                val index = expectedParameters.indexOfFirst { it.name == name }
                if (index < 0) return null
                check(result[index] == null)
                result[index] = valueParam.value
            }

            // then all unnamed
            // todo if vararg
            //  if is Prefix with ARRAY_TO_VARARGS, then assign directly
            //  else create an array, step by step...
            var index = 0
            for (j in actualParameters.indices) {
                val valueParam = actualParameters[j]
                if (valueParam.name != null) continue
                while (result[index] != null) index++

                val ev = expectedParameters[index]
                val vpHasVarargStar = false
                if (!ev.isVararg && vpHasVarargStar) return null // incompatible
                val isNormal = !ev.isVararg || vpHasVarargStar
                if (isNormal) {
                    result[index++] = valueParam.value
                } else {
                    check(index == result.lastIndex) {
                        "vararg must be in last place, $index vs ${result.lastIndex}, " +
                                "$scope, ${resolveOrigin(origin)}"
                    }
                    // collect all varargs
                    val values = actualParameters.subList(j, actualParameters.size).filter { it.name == null }
                    result[index++] = createArrayOfExpr(ev, values, scope, origin)
                    break
                }
            }

            if (index == result.lastIndex) {
                val ev = expectedParameters[index]
                if (!ev.isVararg) return null
                // check(ev.isVararg) { "Expected vararg in last place" }
                // println("Using instanceType $instanceType for empty varargs")
                result[index] = createArrayOfExpr(ev, emptyList(), scope, origin)
            }

            check(result.none { it == null })

            @Suppress("UNCHECKED_CAST")
            result.toList() as List<Expression>
        } else actualParameters.map { it.value }
    }

    fun createArrayOfExpr(
        ev: Parameter, values: List<NamedParameter>,
        scope: Scope, origin: Int,
    ): Expression {
        val arrayType = ev.type
        check(arrayType is ClassType && arrayType.clazz.name == "Array")
        val instanceType = ev.type.typeParameters?.first()
        return createArrayOfExpr(instanceType, values.map { it.value }, scope, origin)
    }

    fun createArrayOfExpr(
        instanceType: Type?, values: List<Expression>,
        scope: Scope, origin: Int,
    ): Expression {

        val typeParameters = if (instanceType != null) listOf(instanceType) else null

        // this shall be the implementation of ArrayOf():
        //  create an Array-instance,
        //  fill all members
        //  'return' it
        val createArrayInstructions = ArrayList<Expression>(values.size + 2)
        val sizeExpr = NumberExpression("${values.size}", scope, origin)
        val arrayInitExpr = ConstructorExpression(
            ArrayType.clazz, typeParameters,
            listOf(NamedParameter(null, sizeExpr)), null, scope, origin
        )

        val tmpField = scope.createImmutableField(arrayInitExpr)
        val tmpFieldExpr = FieldExpression(tmpField, scope, origin)
        createArrayInstructions.add(AssignmentExpression(tmpFieldExpr, arrayInitExpr))
        for (i in values.indices) {
            val index = NumberExpression("$i", scope, origin)
            createArrayInstructions.add(
                NamedCallExpression(
                    tmpFieldExpr, "set", emptyList(), emptyList(),
                    listOf(
                        NamedParameter(null, index),
                        NamedParameter(null, values[i])
                    ), scope, origin
                )
            )
        }
        createArrayInstructions.add(tmpFieldExpr) // 'return' value
        return ExpressionList(createArrayInstructions, scope, origin)
    }

}