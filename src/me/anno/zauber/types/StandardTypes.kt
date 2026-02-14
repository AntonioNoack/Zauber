package me.anno.zauber.types

import me.anno.zauber.Compile.root
import me.anno.zauber.Compile.stdlibName

/**
 * types, that are automatically imported into every file
 * */
object StandardTypes {

    init {
        // stdlib must be a package to be searched for children automatically
        root.getOrPut(stdlibName, ScopeType.PACKAGE)
    }

    val standardClasses = mapOf(
        // strings
        "String" to stdlibName,
        "StringBuilder" to stdlibName,
        "CharSequence" to stdlibName,

        // special types
        "Any" to stdlibName,
        "Nothing" to stdlibName,
        "Unit" to stdlibName,
        "Array" to stdlibName,

        // util
        "Class" to stdlibName,
        "Enum" to stdlibName,
        "IntRange" to stdlibName,
        "ClosedFloatingPointRange" to stdlibName,
        "Lazy" to stdlibName,

        "Comparable" to stdlibName,
        "Comparator" to stdlibName,

        "Iterator" to stdlibName,
        "ListIterator" to stdlibName,
        "MutableIterator" to stdlibName,
        "MutableListIterator" to stdlibName,
        "Iterable" to stdlibName,
        "Collection" to stdlibName,
        "MutableCollection" to stdlibName,

        "List" to stdlibName,
        "ArrayList" to stdlibName,
        "MutableList" to stdlibName,

        "IndexedValue" to stdlibName,

        "Set" to stdlibName,
        "HashSet" to stdlibName,
        "MutableSet" to stdlibName,

        "Map" to stdlibName,
        "HashMap" to stdlibName,
        "MutableMap" to stdlibName,

        "Annotation" to stdlibName,
        "Suppress" to stdlibName,
        "Deprecated" to stdlibName,

        "Throwable" to stdlibName,
        "Exception" to stdlibName,
        "RuntimeException" to stdlibName,
        "InterruptedException" to stdlibName,
        "InstantiationException" to stdlibName,
        "NoSuchMethodException" to stdlibName,
        "IllegalArgumentException" to stdlibName,
        "IllegalStateException" to stdlibName,
        "ClassCastException" to stdlibName,
        "Error" to stdlibName,
        "NoClassDefFoundError" to stdlibName,
        "ClassNotFoundException" to stdlibName,
        "NoSuchFieldException" to stdlibName,
        "NoSuchMethodException" to stdlibName,
        "OutOfMemoryError" to stdlibName,
        "IndexOutOfBoundsException" to stdlibName,

        "Pair" to stdlibName,
        "Triple" to stdlibName,
        "Number" to stdlibName,

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
        "Boolean" to stdlibName,
        "Byte" to stdlibName,
        "Short" to stdlibName,
        "Char" to stdlibName,
        "Int" to stdlibName,
        "Long" to stdlibName,
        "Float" to stdlibName,
        "Double" to stdlibName,

        // native arrays
        "BooleanArray" to stdlibName,
        "ByteArray" to stdlibName,
        "ShortArray" to stdlibName,
        "CharArray" to stdlibName,
        "IntArray" to stdlibName,
        "LongArray" to stdlibName,
        "FloatArray" to stdlibName,
        "DoubleArray" to stdlibName,
    ).mapValues { (name, packageName) ->
        val parts = packageName.split('.')
        var currPackage = root
        for (i in parts.indices) {
            currPackage = currPackage.getOrPut(parts[i], null)
        }
        currPackage.getOrPut(name, null)
    }

    init {
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
    }
}
