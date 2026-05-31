package me.anno.compilation

import me.anno.generation.cpp.CppSourceGenerator
import me.anno.generation.java.JavaSourceGenerator
import me.anno.generation.llvm.LLVMSourceGenerator
import me.anno.zauber.ast.rich.member.Method
import me.anno.zauber.expansion.DependencyData
import me.anno.zauber.types.Type
import java.io.File

class MinimalLLVMCompiler : MinimalCompiler() {

    private fun appendType(type: Type, builder: StringBuilder) {
        val numbers = CppSourceGenerator.nativeCppNumbers
        val number = numbers[type]
        if (number != null) {
            builder.append(number.native)
        } else {
            // todo define struct for value types...
            builder.append("void*")
        }
    }

    override fun compile(projectFolder: File, srcFolder: File, dependencies: DependencyData, mainMethod: Method) {
        val srcFile = File(projectFolder, "Source.ll")
        val gen = LLVMSourceGenerator()
        gen.generateCode(srcFile, dependencies, mainMethod)

        // compile it to assembly
        runProcess(projectFolder, "llc", srcFile.name)
        val assemblyFile = srcFile.nameWithoutExtension + ".s"

        val allImports = HashSet<String>()
        val builder = StringBuilder()
        builder.append("#include \"CStandardLib.h\"\n")

        for ((key, impl) in JavaSourceGenerator.registeredMethods) {
            val methodName = key.findMethodName(gen) ?: continue
            val method = key.findMethod()!!

            val lines = impl.split('\n')
            val imports = lines.filter { it.startsWith("#include ") }

            var hadImports = false
            for (import in imports) {
                if (allImports.add(import)) {
                    builder.append(import).append('\n')
                    hadImports = true
                }
            }
            if (hadImports) {
                builder.append('\n')
            }

            appendType(method.returnType!!, builder)
            builder.append(' ')
                .append(methodName, 1, methodName.length) // start at '1' to remove '@'-symbol
                .append("(")

            appendType(method.ownerScope.typeWithArgs, builder)
            builder.append(" this")

            for (i in key.valueParameterTypes.indices) {
                builder.append(", ")
                appendType(key.valueParameterTypes[i], builder)
                builder.append(" arg").append(i)
            }

            builder.append(") {\n")
            for (line in lines) {
                if (!line.startsWith("#include ")) {
                    builder.append("  ").append(line).append("\n")
                }
            }
            if ("return" !in impl) {
                builder.append("  return NULL;\n")
            }
            builder.append("}\n")
        }

        builder.append("\n" +
                "int zauber_inheritance_readFromClassTable_28khp45(void* self, int addr) { return 0; }\n" +
                "int zauber_inheritance_readFromInterfaceTable_28khp45(void* self, int addr) { return 0; }\n" +
                "")

        val cFile = "Zauber.c"
        File(projectFolder, cFile)
            .writeText(builder.toString())

        File(projectFolder, "CStandardLib.h")
            .writeBytes(MinimalCCompiler.cStandardLib)

        // compile and link it together
        runProcess(projectFolder, "clang", assemblyFile, cFile, "-o", "Zauber.a")

    }

    override fun execute(projectFolder: File): String {
        return runProcessGetPrinted(projectFolder, "./Zauber.a")
    }
}