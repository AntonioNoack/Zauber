package me.anno.zauber.types

import me.anno.zauber.Compile.STDLIB_NAME
import me.anno.zauber.Compile.root
import me.anno.zauber.scope.Scope
import me.anno.zauber.scope.ScopeType
import me.anno.zauber.utils.ResetThreadLocal.Companion.threadLocal

/**
 * types, that are automatically imported into every file
 * */
object StandardTypes {

    val standardClasses: Map<String, Scope>
            by threadLocal { createStandardTypeScopes() }

    private fun createStandardTypeScopes(): Map<String, Scope> {

        // stdlib must be a package to be searched for children automatically
        root.getOrPut(STDLIB_NAME, ScopeType.PACKAGE)

        val standardClasses = mapOf(
            // strings
            "String" to STDLIB_NAME,
            "StringBuilder" to STDLIB_NAME,
            "CharSequence" to STDLIB_NAME,

            // special types
            "Any" to STDLIB_NAME,
            "Nothing" to STDLIB_NAME,
            "Unit" to STDLIB_NAME,
            "Array" to STDLIB_NAME,

            // util
            "Class" to STDLIB_NAME,
            "Enum" to STDLIB_NAME,
            "IntRange" to STDLIB_NAME,
            "ClosedFloatingPointRange" to STDLIB_NAME,
            "Lazy" to STDLIB_NAME,

            "Comparable" to STDLIB_NAME,
            "Comparator" to STDLIB_NAME,

            "Iterator" to STDLIB_NAME,
            "ListIterator" to STDLIB_NAME,
            "MutableIterator" to STDLIB_NAME,
            "MutableListIterator" to STDLIB_NAME,
            "Iterable" to STDLIB_NAME,
            "Collection" to STDLIB_NAME,
            "MutableCollection" to STDLIB_NAME,

            "List" to STDLIB_NAME,
            "ArrayList" to STDLIB_NAME,
            "MutableList" to STDLIB_NAME,

            "IndexedValue" to STDLIB_NAME,

            "Set" to STDLIB_NAME,
            "HashSet" to STDLIB_NAME,
            "MutableSet" to STDLIB_NAME,

            "Map" to STDLIB_NAME,
            "HashMap" to STDLIB_NAME,
            "MutableMap" to STDLIB_NAME,

            "Annotation" to STDLIB_NAME,
            "Suppress" to STDLIB_NAME,
            "Deprecated" to STDLIB_NAME,

            "Throwable" to STDLIB_NAME,
            "Exception" to STDLIB_NAME,
            "RuntimeException" to STDLIB_NAME,
            "InterruptedException" to STDLIB_NAME,
            "InstantiationException" to STDLIB_NAME,
            "NoSuchMethodException" to STDLIB_NAME,
            "IllegalArgumentException" to STDLIB_NAME,
            "IllegalStateException" to STDLIB_NAME,
            "ClassCastException" to STDLIB_NAME,
            "Error" to STDLIB_NAME,
            "NoClassDefFoundError" to STDLIB_NAME,
            "ClassNotFoundException" to STDLIB_NAME,
            "NoSuchFieldException" to STDLIB_NAME,
            "NoSuchMethodException" to STDLIB_NAME,
            "OutOfMemoryError" to STDLIB_NAME,
            "IndexOutOfBoundsException" to STDLIB_NAME,

            "Pair" to STDLIB_NAME,
            "Triple" to STDLIB_NAME,
            "Number" to STDLIB_NAME,

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
            "RandomAccess" to "java.util",

            // natives
            "Boolean" to STDLIB_NAME,
            "Byte" to STDLIB_NAME,
            "Short" to STDLIB_NAME,
            "Char" to STDLIB_NAME,
            "Int" to STDLIB_NAME,
            "Long" to STDLIB_NAME,
            "Float" to STDLIB_NAME,
            "Double" to STDLIB_NAME,

            // native arrays
            "BooleanArray" to STDLIB_NAME,
            "ByteArray" to STDLIB_NAME,
            "ShortArray" to STDLIB_NAME,
            "CharArray" to STDLIB_NAME,
            "IntArray" to STDLIB_NAME,
            "LongArray" to STDLIB_NAME,
            "FloatArray" to STDLIB_NAME,
            "DoubleArray" to STDLIB_NAME,
        ).mapValues { (name, packageName) ->
            val parts = packageName.split('.')
            var currPackage = root
            for (i in parts.indices) {
                currPackage = currPackage.getOrPut(parts[i], null)
            }
            currPackage.getOrPut(name, null)
        }

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
