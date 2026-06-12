package me.anno.zauber.types

import me.anno.utils.ResetThreadLocal.Companion.threadLocal
import me.anno.zauber.Zauber.root
import me.anno.zauber.ast.rich.parameter.Parameter
import me.anno.zauber.ast.rich.parameter.ParameterMutability
import me.anno.zauber.ast.rich.parameter.ParameterType
import me.anno.zauber.scope.Scope
import me.anno.zauber.scope.ScopeType
import me.anno.zauber.typeresolution.TypeResolution.langScope
import me.anno.zauber.types.impl.ClassType
import me.anno.zauber.types.impl.GenericType
import me.anno.zauber.types.impl.arithmetic.NullType
import me.anno.zauber.types.impl.arithmetic.UnionType
import me.anno.zauber.types.impl.arithmetic.UnknownType

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

private fun getScope0(i: String, scopeType: ScopeType): Scope {
    if ('.' !in i) {
        return langScope.getOrPut(i, scopeType)
    }

    val parts = i.split('.')
    var scope = root
    for (i in parts.indices) {
        val part = parts[i]
        val scopeTypeI = if (i == parts.lastIndex) scopeType else null
        scope = scope.getOrPut(part, scopeTypeI)
    }
    println("Created ${scope.pathStr} from root #${root.atomic}")
    return scope
}

fun getScope(i: String, genericNames: String, nat: Type): Scope {
    val scope = getScope0(i)
    ensureTypeParameters(scope, genericNames, nat)
    return scope
}

fun getScope(i: String, genericNames: String, nat: Type, scopeType: ScopeType): Scope {
    val scope = getScope0(i, scopeType)
    ensureTypeParameters(scope, genericNames, nat)
    return scope
}

private fun ensureTypeParameters(scope: Scope, genericNames: String, nat: Type) {
    val numGenerics = genericNames.length
    if (scope.hasTypeParameters) {
        check(scope.declaredTypeParameters.size == numGenerics) {
            "Expected Types.getScope to have correct number of generics, ${scope.declaredTypeParameters.size} vs $numGenerics"
        }
    } else {
        val typeParams =
            if (numGenerics == 0) emptyList()
            else List(numGenerics) { index ->
                val name = genericNames[index].toString()
                Parameter(
                    index, name, ParameterType.TYPE_PARAMETER,
                    ParameterMutability.DEFAULT,
                    nat, scope, -1
                )
            }
        scope.setTypeParams(typeParams)
    }
}

@Suppress("PropertyName")
class TypesImpl {

    fun getType(name: String): ClassType {
        val scope = getScope(name, "", UnknownType)
        return ClassType(scope, emptyList(), -1, true)
    }

    fun getType(name: String, scopeType: ScopeType): ClassType {
        val scope = getScope(name, "", UnknownType, scopeType)
        return ClassType(scope, emptyList(), -1, true)
    }

    private fun getType(name: String, genericNames: String, nat: Type): ClassType {
        val scope = getScope(name, genericNames, nat)
        return ClassType(
            scope,
            genericNames.indices.map {
                val param = scope.typeParameters[it]
                GenericType(scope, param.name)
            }, -1, true
        )
    }

    // basics
    val Any = getType("Any", ScopeType.NORMAL_CLASS)
    val NullableAny = UnionType(listOf(Any, NullType))
    val Unit = getType("Unit", ScopeType.OBJECT)
    val Boolean = getType("Boolean", ScopeType.ENUM_CLASS)
    val String = getType("String", ScopeType.NORMAL_CLASS)

    // numbers:
    val Char = getType("Char", ScopeType.NORMAL_CLASS)
    val Number = getType("Number")
    val Byte = getType("Byte", ScopeType.NORMAL_CLASS)
    val Short = getType("Short", ScopeType.NORMAL_CLASS)
    val Int = getType("Int", ScopeType.NORMAL_CLASS)
    val Long = getType("Long", ScopeType.NORMAL_CLASS)
    val Float = getType("Float", ScopeType.NORMAL_CLASS)
    val Double = getType("Double", ScopeType.NORMAL_CLASS)
    val UByte = getType("UByte", ScopeType.NORMAL_CLASS)
    val UShort = getType("UShort", ScopeType.NORMAL_CLASS)
    val UInt = getType("UInt", ScopeType.NORMAL_CLASS)
    val ULong = getType("ULong", ScopeType.NORMAL_CLASS)

    // todo define other ML types like FP8, BF16?
    val Half = getType("Half", ScopeType.NORMAL_CLASS)

    // collections:
    val Array = getType("Array", "V", NullableAny)
    val List = getType("List", "V", NullableAny)
    val ArrayList = getType("ArrayList", "V", NullableAny)
    val LinkedList = getType("LinkedList", "V", NullableAny)
    val Map = getType("Map", "KV", NullableAny)

    // control-flow:
    val Throwable = getType("Throwable", ScopeType.NORMAL_CLASS)
    val NullPointerException = getType("NullPointerException")
    val Yielded = getType("Yielded", "RTY", NullableAny)
    val Yieldable = getType("Yieldable", "RTY", NullableAny)
    val Nothing = getType("Nothing", ScopeType.ENUM_CLASS)

    // macros:
    val MacroContext = getType("MacroContext")

    // reflections:
    val TypeT = getType("Type")
    val UnionType = getType("UnionType")
    val ClassType = getType("ClassType", "V", NullableAny)
    val GenericType = getType("GenericType")
    val Field = getType("Field")

    // unit testing:
    val Test = getType("Test")
    val ParameterizedTest = getType("ParameterizedTest")

    // C/C++ interop:
    val Pointer = getType("Pointer", "T", NullableAny)
    val Ref = getType("Ref", "T", NullableAny)

}
