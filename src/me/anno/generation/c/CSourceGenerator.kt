package me.anno.generation.c

import me.anno.generation.cpp.CppSourceGenerator
import me.anno.zauber.types.Specialization

/**
 * this is more custom than C++:
 * todo we need to implement inheritance explicitly
 * todo we also need to deduplicate methods with same name, but different parameters
 * */
open class CSourceGenerator : CppSourceGenerator() {

    companion object {
        fun hashMethodParameters(method: Specialization): String {
            check(method.isMethodLike())
            if (method.method.valueParameters.isEmpty()) {
                // we rely on this special behaviour -> make it explicit
                return "0"
            }
            return method.use {
                val hash = method.method.valueParameters.joinToString {
                    "${it.name}: ${resolveType(it.type)}"
                }.hashCode()
                hash.toUInt().toString(36)
            }
        }
    }

    override fun getMethodName(method: Specialization): String {
        val base = super.getMethodName0(method)
        return "${base}_${hashMethodParameters(method)}"
    }

    override fun getExtension(headerOnly: Boolean): String {
        return if (headerOnly) "h" else "c"
    }
}