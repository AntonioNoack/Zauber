package me.anno.generation.cpp

import me.anno.generation.java.JavaSourceGenerator
import me.anno.generation.java.FileWithImportsWriter
import me.anno.zauber.scope.Scope
import me.anno.zauber.types.specialization.FieldSpecialization
import me.anno.zauber.types.specialization.MethodSpecialization
import me.anno.zauber.types.specialization.Specialization
import java.io.File

// todo compared to C, this has inheritance built-in, which
//  we can directly use; and it has ready-made shared references
class CppSourceGenerator : JavaSourceGenerator() {

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

}