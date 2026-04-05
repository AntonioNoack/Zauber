package me.anno.zauber.types

import me.anno.zauber.Compile.root
import me.anno.zauber.ast.rich.Parameter
import me.anno.zauber.scope.Scope
import me.anno.zauber.typeresolution.TypeResolution.langScope
import me.anno.zauber.types.impl.ClassType
import me.anno.zauber.types.impl.NullType
import me.anno.zauber.types.impl.UnionType
import me.anno.zauber.types.impl.UnknownType
import me.anno.zauber.utils.ResetThreadLocal.Companion.threadLocal

val Types by threadLocal { TypesImpl() }

private fun getScope0(i: String): Scope {
    if ('.' !in i) {
        return langScope.getOrPut(i, null)
    }

    val parts = i.split('.')
    var scope = root
    for (part in parts) {
        scope = scope.getOrPut(part, null)
    }
    println("Created ${scope.pathStr} from root #${root.atomic}")
    return scope
}

fun getScope(i: String, genericNames: String, nat: Type): Scope {
    val scope = getScope0(i)
    ensureTypeParameters(scope, genericNames, nat)
    return scope
}

private fun ensureTypeParameters(scope: Scope, genericNames: String, nat: Type) {
    val numGenerics = genericNames.length
    if (scope.hasTypeParameters) {
        check(scope.typeParameters.size == numGenerics) {
            "Expected Types.getScope to have correct number of generics, ${scope.typeParameters.size} vs $numGenerics"
        }
    } else {
        scope.typeParameters =
            if (numGenerics == 0) emptyList()
            else List(numGenerics) { Parameter(it, genericNames[it].toString(), nat, scope, -1) }
        scope.hasTypeParameters = true
    }
}

class TypesImpl {

    private fun getType(i: String): ClassType {
        return ClassType(getScope(i, "", UnknownType), emptyList(), -1)
    }

    private fun getType(i: String, genericNames: String, nat: Type): ClassType {
        val type = ClassType(
            getScope(i, genericNames, nat),
            if (genericNames.isEmpty()) emptyList() else null,
            -1
        )
        return type
    }

    val AnyType = getType("Any")
    val NullableAnyType = UnionType(listOf(AnyType, NullType))
    val UnitType = getType("Unit")
    val CharType = getType("Char")
    val ByteType = getType("Byte")
    val ShortType = getType("Short")
    val IntType = getType("Int")
    val LongType = getType("Long")
    val FloatType = getType("Float")
    val DoubleType = getType("Double")
    val UByteType = getType("UByte")
    val UShortType = getType("UShort")
    val UIntType = getType("UInt")
    val ULongType = getType("ULong")
    val HalfType = getType("Half")
    val StringType = getType("String")
    val NumberType = getType("Number")
    val ThrowableType = getType("Throwable")
    val YieldedType = getType("Yielded", "RTY", NullableAnyType)
    val NothingType = getType("Nothing")
    val BooleanType = getType("Boolean")
    val ArrayType = getType("Array", "V", NullableAnyType)
    val ListType = getType("List", "V", NullableAnyType)
    val ArrayListType = getType("ArrayList", "V", NullableAnyType)
    val MapType = getType("Map", "KV", NullableAnyType)
    val YieldableType = getType("Yieldable", "RTY", NullableAnyType)
    val LinkedList = getType("LinkedList", "V", NullableAnyType)

}