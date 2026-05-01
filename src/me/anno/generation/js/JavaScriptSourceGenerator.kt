package me.anno.generation.js

import me.anno.generation.Generator
import me.anno.zauber.types.impl.ClassType
import me.anno.zauber.types.specialization.MethodSpecialization
import java.io.File

// todo this is just like Java source code, except that
//  a) we don't need to specify what each type is
//  b) we must generate unique method names from their signature, if overloads exist
object JavaScriptSourceGenerator : Generator()