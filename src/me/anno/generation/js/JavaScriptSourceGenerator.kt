package me.anno.generation.js

import me.anno.generation.java.JavaSourceGenerator

// todo this is just like Java source code, except that
//  a) we don't need to specify what each type is
//  b) we must generate unique method names from their signature, if overloads exist
open class JavaScriptSourceGenerator : JavaSourceGenerator()