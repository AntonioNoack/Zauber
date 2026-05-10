package me.anno.generation.c

import me.anno.generation.cpp.CppSourceGenerator

/**
 * this is more custom than C++:
 * todo we need to implement inheritance explicitly
 * todo we also need to deduplicate methods with same name, but different parameters
 * */
open class CSourceGenerator : CppSourceGenerator() {
    override fun getExtension(headerOnly: Boolean): String {
        return if (headerOnly) "h" else "c"
    }
}