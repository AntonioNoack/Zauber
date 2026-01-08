package me.anno.zauber.generation.cpp

import me.anno.zauber.generation.Generator
import me.anno.zauber.types.Scope
import java.io.File

// todo compared to C, this has inheritance built-in, which
//  we can directly use; and it has ready-made shared references
object CppSourceGenerator : Generator() {

    // todo generate runnable C++ code from what we parsed
    // todo just produce all code for now as-is

    // todo we need .h and .c files...

    override fun generateCode(dst: File, root: Scope) {

    }
}