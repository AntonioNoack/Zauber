package me.anno.generation.c

import me.anno.generation.cpp.CppSourceGenerator

// todo this is the final boss:
//  all allocations, shared references, GC, inheritance etc must be implemented by us
object CSourceGenerator : CppSourceGenerator() {

    // todo generate runnable C code from what we parsed
    // todo just produce all code for now as-is

    // todo we need .h and .c files...

    override fun getExtension(headerOnly: Boolean): String {
        return if (headerOnly) "h" else "c"
    }

}