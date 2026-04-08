package me.anno.zauber.types

import me.anno.zauber.Compile.STDLIB_NAME
import me.anno.zauber.Compile.root
import me.anno.zauber.scope.Scope
import me.anno.zauber.scope.ScopeType
import me.anno.zauber.typeresolution.TypeResolution.langScope
import me.anno.utils.ResetThreadLocal.Companion.threadLocal

/**
 * types, that are automatically imported into every file
 * */
object StandardTypes {

    val standardClasses: Map<String, Scope>
            by threadLocal { createStandardTypeScopes() }

    private fun createStandardTypeScopes(): Map<String, Scope> {

        // stdlib must be a package to be searched for children automatically
        root.getOrPut(STDLIB_NAME, ScopeType.PACKAGE)

        val zauber = langScope
        val standardClasses0 = listOf(
            // strings
            "String",
            "StringBuilder",
            "CharSequence",

            // special types
            "Any",
            "Nothing",
            "Unit",
            "Array",

            // util
            "Class",
            "Enum",
            "IntRange",
            "ClosedFloatingPointRange",
            "Lazy",
            "TokenInfo",

            "Comparable",
            "Comparator",

            "Iterator",
            "ListIterator",
            "MutableIterator",
            "MutableListIterator",
            "Iterable",
            "Collection",
            "MutableCollection",

            "List",
            "ArrayList",
            "MutableList",

            "IndexedValue",

            "Set",
            "HashSet",
            "MutableSet",

            "Map",
            "HashMap",
            "MutableMap",

            "Annotation",
            "Suppress",
            "Deprecated",

            "Throwable",
            "Exception",
            "RuntimeException",
            "InterruptedException",
            "InstantiationException",
            "NoSuchMethodException",
            "IllegalArgumentException",
            "IllegalStateException",
            "ClassCastException",
            "Error",
            "NoClassDefFoundError",
            "ClassNotFoundException",
            "NoSuchFieldException",
            "NoSuchMethodException",
            "OutOfMemoryError",
            "IndexOutOfBoundsException",

            "Pair",
            "Triple",
            "Number",

            // natives
            "Boolean",
            "Byte",
            "Short",
            "Char",
            "Int",
            "Long",
            "Float",
            "Double",

            // native arrays
            "BooleanArray",
            "ByteArray",
            "ShortArray",
            "CharArray",
            "IntArray",
            "LongArray",
            "FloatArray",
            "DoubleArray",
        ).associateWith { name ->
            zauber.getOrPut(name, null)
        }

        val standardClasses1 = mapOf(

            // util²
            "JvmField" to "kotlin.jvm",
            "JvmStatic" to "kotlin.jvm",
            "JvmOverloads" to "kotlin.jvm",
            "Throws" to "kotlin.jvm",
            "Thread" to "java.lang",
            "ThreadLocal" to "java.lang",
            "Process" to "java.lang",
            "ClassLoader" to "java.lang",
            "AbstractList" to "java.util",
            "RandomAccess" to "java.util"

        ).mapValues { (name, packageName) ->
            val parts = packageName.split('.')
            var currPackage = root
            for (i in parts.indices) {
                currPackage = currPackage.getOrPut(parts[i], null)
            }
            currPackage.getOrPut(name, null)
        }

        val standardClasses = standardClasses0 + standardClasses1

        // mark these types as not having a generic parameter
        val nonGenericTypes = listOf(
            // strings
            "String",
            "StringBuilder",
            "CharSequence",

            // special types
            "Any",
            "Nothing",
            "Unit",

            // util
            "IntRange",
            "ClosedFloatingPointRange",
            "TokenInfo",

            "Annotation",
            "Suppress",
            "Deprecated",

            "Throwable",
            "Exception",
            "RuntimeException",
            "InterruptedException",
            "InstantiationException",
            "NoSuchMethodException",
            "IllegalArgumentException",
            "IllegalStateException",
            "ClassCastException",
            "Error",
            "NoClassDefFoundError",
            "ClassNotFoundException",
            "NoSuchFieldException",
            "NoSuchMethodException",
            "OutOfMemoryError",
            "IndexOutOfBoundsException",

            "Number",

            // util²
            "JvmField",
            "JvmStatic",
            "JvmOverloads",
            "Throws",
            "Thread",
            "Process",
            "ClassLoader",

            // natives
            "Boolean",
            "Byte",
            "Short",
            "Char",
            "Int",
            "Long",
            "Float",
            "Double",

            // native arrays
            "BooleanArray",
            "ByteArray",
            "ShortArray",
            "CharArray",
            "IntArray",
            "LongArray",
            "FloatArray",
            "DoubleArray",
        )

        for (name in nonGenericTypes) {
            val type = standardClasses[name]!!
            type.hasTypeParameters = true
        }

        return standardClasses
    }
}
