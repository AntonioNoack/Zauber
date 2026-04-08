package me.anno.support.jvm

import me.anno.support.jvm.JVMClassReader.Companion.getScope
import me.anno.zauber.ast.rich.Parameter
import me.anno.zauber.logging.LogManager
import me.anno.zauber.scope.Scope
import me.anno.zauber.typeresolution.ParameterList
import me.anno.zauber.types.Type
import me.anno.zauber.types.Types
import me.anno.zauber.types.impl.ClassType
import me.anno.zauber.types.impl.GenericType
import me.anno.zauber.types.impl.UnknownType

class SignatureReader(val signature: String, val scope: Scope) {

    companion object {
        private val LOGGER = LogManager.getLogger(SignatureReader::class)
    }

    var i = 0
    val origin = -1

    fun consume(c: Char) {
        check(signature[i] == c) {
            "Expected '$c' at $signature@$i, got '${signature[i]}'"
        }
        i++
    }

    fun readClassType(): Type {
        val i0 = i
        while (signature[i] !in "<;") {
            i++
        } // 'i' is now on '>' or ';'
        var name = signature.substring(i0, i)
        var generics = readGenerics1()
        while (signature[i] == '.') {
            // todo an inner class with generics in the parent...
            //  yes, we need that, but we have no way to represent it at the moment...
            val i0 = ++i
            while (signature[i] !in "<;") {
                i++
            }
            name += "/" + signature.substring(i0, i)
            generics = readGenerics1()
        }
        consume(';')
        val clazz = getScope(name, null)
        return if (clazz.hasTypeParameters && generics.size == clazz.typeParameters.size) {
            ClassType(clazz, generics, origin)
        } else {
            // UnresolvedType(name, generics, clazz, emptyList())
            if (generics.isNotEmpty()) {
                // todo we're missing out :(
                LOGGER.warn("Skipping typeParameters for $clazz<$generics>, because type is yet to be read")
            }
            ClassType(clazz, null, origin)
        }
    }

    fun readGenerics1(): List<Type> {
        if (signature[i] == '<') {
            val generics = ArrayList<Type>()
            consume('<')
            while (signature[i] != '>') {
                generics.add(readType())
            }
            consume('>')
            return generics
        } else return emptyList()
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
            '+', '-' -> readType() // todo add super-type flag somehow...
            '*' -> UnknownType
            'C' -> Types.Char
            'B' -> Types.Byte
            'S' -> Types.Short
            'I' -> Types.Int
            'J' -> Types.Long
            'F' -> Types.Float
            'D' -> Types.Double
            'Z' -> Types.Boolean
            '[' -> {
                val subType = readType()
                Types.Array.withTypeParameter(subType)
            }
            'V' -> Types.Unit
            else -> throw IllegalStateException("Read unknown type '${signature[i - 1]}': $signature@${i - 1}")
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
                // the first colon thingy is an object (extends), the second and so forth are interfaces...
                if (signature[i] == ':') i++ // hack
                val type = readType()
                generics.add(Parameter(generics.size, name, type, scope, origin))
            }
            i++ // skip '>'
            return generics
        } else return emptyList()
    }
}