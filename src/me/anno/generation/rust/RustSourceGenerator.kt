package me.anno.generation.rust

import me.anno.generation.Generator

// todo this has the same complexity as C, plus we must define ownership somehow...
//  wrapping everything into GC is kind of lame...
object RustSourceGenerator : Generator()