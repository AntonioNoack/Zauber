package me.anno.zauber.types

import me.anno.zauber.Compile.root
import me.anno.zauber.astbuilder.Parameter
import me.anno.zauber.typeresolution.TypeResolution.langScope
import me.anno.zauber.types.impl.ClassType
import me.anno.zauber.types.impl.NullType.typeOrNull

object Types {

    private fun getScope0(i: String): Scope {
        if ('.' !in i) {
            return langScope.getOrPut(i, null)
        }

        val parts = i.split('.')
        var scope = root
        for (part in parts) {
            scope = scope.getOrPut(part, null)
        }
        return scope
    }

    fun getScope(i: String, numGenerics: Int): Scope {
        val scope = getScope0(i)
        if (scope.hasTypeParameters) {
            check(scope.typeParameters.size == numGenerics)
            return scope
        } else {
            scope.typeParameters =
                if (numGenerics == 0) emptyList()
                else List(numGenerics) { Parameter(('A' + it).toString(), NullableAnyType, scope, -1) }
            scope.hasTypeParameters = true
            return scope
        }
    }

    fun getType(i: String, numGenerics: Int): ClassType {
        return ClassType(
            getScope(i, numGenerics),
            if (numGenerics == 0) emptyList() else null
        )
    }

    val AnyType = getType("Any", 0)
    val NullableAnyType = typeOrNull(AnyType)
    val UnitType = getType("Unit", 0)
    val CharType = getType("Char", 0)
    val ByteType = getType("Byte", 0)
    val ShortType = getType("Short", 0)
    val IntType = getType("Int", 0)
    val LongType = getType("Long", 0)
    val FloatType = getType("Float", 0)
    val DoubleType = getType("Double", 0)
    val UIntType = getType("UInt", 0)
    val ULongType = getType("ULong", 0)
    val HalfType = getType("Half", 0)
    val StringType = getType("String", 0)
    val ThrowableType = getType("Throwable", 0)
    val NothingType = getType("Nothing", 0)
    val BooleanType = getType("Boolean", 0)
    val ArrayType = getType("Array", 1)

    // todo yes, it is Iterable<*>, but * = Nothing still feels wrong :/
    val AnyIterableType = ClassType(getScope("Iterable", 1), listOf(NothingType))
    val AnyClassType = ClassType(getScope("Class", 1), listOf(NothingType))
}