package me.anno.generation.jvm

import me.anno.generation.java.JavaSourceGenerator

/**
 * todo mix between JavaCodeGen and WASMCodeGen:
 *
 * first create all .class files in the FileWriter,
 * then join them into a .jar file with metadata to create a runnable jar
 * */
class JVMBytecodeGenerator : JavaSourceGenerator() {
    override fun getExtension(headerOnly: Boolean): String = "class"

    val binary = JVMBytecodeWriter()

}
