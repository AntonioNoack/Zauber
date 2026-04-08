package me.anno.support.jvm

import me.anno.support.jvm.JVMBytecodeReader.Companion.getScope
import me.anno.zauber.ast.rich.Parameter
import me.anno.zauber.scope.Scope
import me.anno.zauber.types.Type
import me.anno.zauber.types.impl.ClassType
import me.anno.zauber.types.impl.GenericType

class SignatureReader(val signature: String, val scope: Scope) {

    var i = 0
    val origin = -1

    fun consume(c: Char) {
        check(signature[i] == c) {
            "Expected '$c' at $signature@$i, got '${signature[i]}'"
        }
        i++
    }

    fun readClassType(): ClassType {
        val i0 = i
        while (signature[i] !in "<;") {
            i++
        } // 'i' is now on '>' or ';'
        val name = signature.substring(i0, i)
        val generics = ArrayList<Type>()
        if (signature[i] == '<') {
            consume('<')
            while (signature[i] != '>') {
                generics.add(readType())
            }
            consume('>')
        }
        consume(';')
        val clazz = getScope(name, null)
        return ClassType(clazz, generics, origin)
    }

    fun readGenericType(): GenericType {
        val i0 = i
        val i1 = signature.indexOf(';', i)
        check(i1 > i0)
        val name = signature.substring(i0, i1)
        i = i1 + 1
        return GenericType(scope, name)
    }

    fun readType(): Type {
        return when (signature[i++]) {
            'L' -> readClassType()
            'T' -> readGenericType()
            else -> throw IllegalStateException("Read unknown type '${signature[i - 1]}': $signature@$i")
        }
    }

    fun readGenerics(): List<Parameter> {
        if (i < signature.length && signature[i] == '<') {
            val generics = ArrayList<Parameter>()
            i++
            while (signature[i] != '>') {
                val colon = signature.indexOf(':', i)
                check(colon >= 0) { "Missing colon for generics at ${signature.substring(i)}" }
                val name = signature.substring(i, colon)
                i = colon + 1
                val type = readType()
                generics.add(Parameter(generics.size, name, type, scope, origin))
            }
            i++ // skip '>'
            return generics
        } else return emptyList()
    }
}