package me.anno.zauber.typeresolution

import me.anno.zauber.ast.rich.NamedParameter
import me.anno.zauber.ast.rich.Parameter
import me.anno.zauber.ast.rich.expression.unresolved.ArrayToVarargsStar
import me.anno.zauber.ast.rich.expression.unresolved.CallExpression
import me.anno.zauber.ast.rich.expression.Expression
import me.anno.zauber.ast.rich.expression.unresolved.UnresolvedFieldExpression
import me.anno.zauber.types.Scope
import me.anno.zauber.types.Types.ArrayType
import me.anno.zauber.types.impl.ClassType
import me.anno.zauber.types.impl.UnionType.Companion.unionTypes

object CallWithNames {

    /**
     * Change the order of value parameters if needed.
     * execution order must remain unchanged!
     * */
    fun resolveNamedParameters(
        expectedParameters: List<Parameter>,
        actualParameters: List<ValueParameter>
    ): List<ValueParameter>? {
        val anyIsVararg = expectedParameters.any { it.isVararg }
        if (anyIsVararg) {
            // empty entry is valid, too
            if (expectedParameters.size > actualParameters.size + 1) {
                return null
            }
        } else {
            if (expectedParameters.size > actualParameters.size) {
                return null
            }
        }

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
                    val arrayType = ClassType(type0.clazz, listOf(types))
                    result[index++] = ValueParameterImpl(null, arrayType, true)
                    break
                }
            }

            if (index == result.lastIndex) {
                val ev = expectedParameters[index]
                check(ev.isVararg) { "Expected vararg in last place" }
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
        if (anyIsVararg) {
            // empty entry is valid, too
            if (expectedParameters.size > actualParameters.size + 1) {
                return null
            }
        } else {
            if (expectedParameters.size > actualParameters.size) {
                return null
            }
        }

        return if (actualParameters.any { it.name != null || it.value is ArrayToVarargsStar } ||
            actualParameters.size != expectedParameters.size ||
            anyIsVararg
        ) {

            val result = arrayOfNulls<Expression>(actualParameters.size)

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
                    check(index == result.lastIndex) { "vararg must be in last place, $index vs ${result.lastIndex}" }
                    // collect all varargs
                    val values = actualParameters.subList(j, actualParameters.size)
                        .filter { it.name == null }
                    result[index++] = CallExpression(
                        UnresolvedFieldExpression("arrayOf", emptyList(), scope, origin),
                        emptyList(), values, origin
                    )
                    break
                }
            }

            if (index == result.lastIndex) {
                val ev = expectedParameters[index]
                val arrayType = ev.type as ClassType
                val instanceType = arrayType.typeParameters!![0]
                check(ev.isVararg) { "Expected vararg in last place" }
                // println("Using instanceType $instanceType for empty varargs")
                result[index] = CallExpression(
                    UnresolvedFieldExpression("arrayOf", emptyList(), scope, origin),
                    listOf(instanceType), emptyList(), origin
                )
            }

            check(result.none { it == null })

            @Suppress("UNCHECKED_CAST")
            result.toList() as List<Expression>
        } else actualParameters.map { it.value }
    }

}