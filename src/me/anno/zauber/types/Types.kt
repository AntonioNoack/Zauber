package me.anno.zauber.types

import me.anno.zauber.Compile.root
import me.anno.zauber.ast.rich.Parameter
import me.anno.zauber.typeresolution.TypeResolution.langScope
import me.anno.zauber.types.impl.ClassType
import me.anno.zauber.types.impl.NullType.typeOrNull

object Types {

    private val types = ArrayList<ClassType>()

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

    fun getScope(i: String, genericNames: String): Scope {
        val scope = getScope0(i)
        ensureTypeParameters(scope, genericNames)
        return scope
    }

    private fun ensureTypeParameters(scope: Scope, genericNames: String) {
        val numGenerics = genericNames.length
        if (scope.hasTypeParameters) {
            check(scope.typeParameters.size == numGenerics) {
                "Expected Types.getScope to have correct number of generics, ${scope.typeParameters.size} vs $numGenerics"
            }
        } else {
            scope.typeParameters =
                if (numGenerics == 0) emptyList()
                else List(numGenerics) { Parameter(it, genericNames[it].toString(), NullableAnyType, scope, -1) }
            scope.hasTypeParameters = true
        }
    }

    fun getType(i: String, genericNames: String): ClassType {
        val type = ClassType(
            getScope(i, genericNames),
            if (genericNames.isEmpty()) emptyList() else null,
            -1
        )
        types += type
        return type
    }

    val AnyType = getType("Any", "")
    val NullableAnyType = typeOrNull(AnyType)
    val UnitType = getType("Unit", "")
    val CharType = getType("Char", "")
    val ByteType = getType("Byte", "")
    val ShortType = getType("Short", "")
    val IntType = getType("Int", "")
    val LongType = getType("Long", "")
    val FloatType = getType("Float", "")
    val DoubleType = getType("Double", "")
    val UByteType = getType("UByte", "")
    val UShortType = getType("UShort", "")
    val UIntType = getType("UInt", "")
    val ULongType = getType("ULong", "")
    val HalfType = getType("Half", "")
    val StringType = getType("String", "")
    val NumberType = getType("Number", "")
    val ThrowableType = getType("Throwable", "")
    val YieldedType = getType("Yielded", "RTY")
    val NothingType = getType("Nothing", "")
    val BooleanType = getType("Boolean", "")
    val ArrayType = getType("Array", "V")
    val ListType = getType("List", "V")
    val ArrayListType = getType("ArrayList", "V")
    val MapType = getType("Map", "KV")
    val PairType = getType("Pair", "FS")
    val YieldableType = getType("Yieldable", "RTY")
    val NullPointerExceptionType = getType("NullPointerException", "")

    fun register() {
        for (type in types) {
            var scope = type.clazz
            while (true) {
                val parent = scope.parent ?: break
                if (scope !in parent.children) parent.children.add(scope)
                scope = parent
            }
        }
    }
}