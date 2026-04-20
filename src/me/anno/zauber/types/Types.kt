package me.anno.zauber.types

import me.anno.utils.ResetThreadLocal.Companion.threadLocal
import me.anno.zauber.Compile.root
import me.anno.zauber.ast.rich.Parameter
import me.anno.zauber.scope.Scope
import me.anno.zauber.typeresolution.TypeResolution.langScope
import me.anno.zauber.types.impl.*

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

@Suppress("PropertyName")
class TypesImpl {

    private fun getType(i: String): ClassType {
        val scope = getScope(i, "", UnknownType)
        return ClassType(scope, emptyList(), -1, true)
    }

    private fun getType(i: String, genericNames: String, nat: Type): ClassType {
        val scope = getScope(i, genericNames, nat)
        return ClassType(
            scope,
            genericNames.indices.map {
                val param = scope.typeParameters[it]
                GenericType(scope, param.name)
            }, -1, true
        )
    }

    val Any = getType("Any")
    val NullableAny = UnionType(listOf(Any, NullType))
    val Unit = getType("Unit")
    val Boolean = getType("Boolean")

    val Char = getType("Char")
    val Number = getType("Number")
    val Byte = getType("Byte")
    val Short = getType("Short")
    val Int = getType("Int")
    val Long = getType("Long")
    val Float = getType("Float")
    val Double = getType("Double")
    val UByte = getType("UByte")
    val UShort = getType("UShort")
    val UInt = getType("UInt")
    val ULong = getType("ULong")
    val Half = getType("Half")

    val String = getType("String")
    val Throwable = getType("Throwable")
    val Nothing = getType("Nothing")

    val Array = getType("Array", "V", NullableAny)
    val List = getType("List", "V", NullableAny)
    val ArrayList = getType("ArrayList", "V", NullableAny)
    val LinkedList = getType("LinkedList", "V", NullableAny)
    val Map = getType("Map", "KV", NullableAny)

    val Yielded = getType("Yielded", "RTY", NullableAny)
    val Yieldable = getType("Yieldable", "RTY", NullableAny)

    val MacroContext = getType("MacroContext")

    val TypeT = getType("Type")
    val UnionType = getType("UnionType")
    val ClassType = getType("ClassType", "V", NullableAny)
    val GenericType = getType("GenericType")
    val Field = getType("Field")

}