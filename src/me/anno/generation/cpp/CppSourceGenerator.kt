package me.anno.generation.cpp

import me.anno.generation.FileEntry
import me.anno.generation.FileWithImportsWriter
import me.anno.generation.java.JavaSourceGenerator
import me.anno.support.jvm.FirstJVMClassReader.Companion.isPrivate
import me.anno.zauber.ast.rich.Method
import me.anno.zauber.scope.Scope
import me.anno.zauber.types.specialization.FieldSpecialization
import me.anno.zauber.types.specialization.MethodSpecialization
import me.anno.zauber.types.specialization.Specialization
import java.io.File

// todo compared to C, this has inheritance built-in, which
//  we can directly use; and it has ready-made shared references
class CppSourceGenerator : JavaSourceGenerator() {

    val cppVersion = 11

    // todo generate runnable C++ code from what we parsed
    // todo just produce all code for now as-is

    // todo we need .hpp and .cpp files...

    val cppFiles = HashSet<File>()

    override fun getExtension(headerOnly: Boolean): String {
        return if (headerOnly) "hpp" else "cpp"
    }

    override fun generateClassForScope(
        scope: Scope, dst: File, writer: FileWithImportsWriter, specialization: Specialization,
        methods: Collection<MethodSpecialization>, fields: Collection<FieldSpecialization>
    ) {
        val (name, packageScope) = getNameAndScope(scope, specialization)
        generateClassBody(name, scope, specialization, methods, fields, true)
        writeInto(packageScope, name, dst, writer, true)

        generateClassBody(name, scope, specialization, methods, fields, false)
        cppFiles += writeInto(packageScope, name, dst, writer, false)
    }

    override fun appendStaticInstance(className: String) {
        // https://stackoverflow.com/a/1008289/4979303
        builder.append(
            """
        static $className& getInstance() {
            static $className instance;
            return instance;
        }
    private:
        $className() {}
        """.trimIndent()
        )
        if (cppVersion >= 3 && cppVersion < 11) {
            builder.append(
                """
    // in private section
        $className($className const&); // Don't Implement
        void operator=($className const&); // Don't implement 
    public:
        """.trimIndent()
            )
        } else if (cppVersion >= 11) {
            builder.append(
                """
    public:
        $className($className const&) = delete;
        void operator=($className const&) = delete;
            """.trimIndent()
            )
        } else {
            builder.append("public:")
            nextLine()
        }
    }

    override fun defineMainMethodCallEntry(
        dst: File, writer: FileWithImportsWriter,
        mainMethod: Method, className: String
    ): FileEntry {
        val needsArgs = mainMethod.valueParameters.isNotEmpty()
        val imports0 = imports
        return FileEntry("zauber")
            .apply {
                imports.putAll(imports0)
                content.append(
                    """
                void main(char** args) {
                    $className.${mainMethod.name}(${if (needsArgs) "args" else ""});
                }
            """.trimIndent()
                )
            }
    }

    override fun appendPackageDeclaration(packagePath: String) {
        builder.append("// package $packagePath\n\n")
    }

    override fun appendImport(import: List<String>) {
        builder.append("#include \"").append(import.joinToString("/")).append(".hpp\"\n")
    }

    override fun appendClassFlags(scope: Scope) {
        // no flags yet
    }

    override fun appendMethodFlags(classScope: Scope, method: Method, headerOnly: Boolean) {
        if (headerOnly) {
            if (method.flags.isPrivate()) {
                builder.append("private:")
                nextLine()
            } else {
                builder.append("public:")
                nextLine()
            }
        }
        // no flags yet
    }

    override fun getClassType(scope: Scope): String {
        return "struct"
    }

    override fun appendMethodBody(method: Method, spec: Specialization, headerOnly: Boolean) {
        if (headerOnly) {
            builder.append(";")
            nextLine()
        } else {
            super.appendMethodBody(method, spec, false)
        }
    }

    override fun hasAutoPackageImports(): Boolean = false

}