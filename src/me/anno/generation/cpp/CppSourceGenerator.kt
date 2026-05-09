package me.anno.generation.cpp

import me.anno.generation.BoxedType
import me.anno.generation.FileEntry
import me.anno.generation.FileWithImportsWriter
import me.anno.generation.ImportSorter
import me.anno.generation.Specializations.specializations
import me.anno.generation.java.JavaSourceGenerator
import me.anno.support.jvm.FirstJVMClassReader.Companion.isPrivate
import me.anno.utils.ResetThreadLocal.Companion.threadLocal
import me.anno.zauber.SpecialFieldNames.OBJECT_FIELD_NAME
import me.anno.zauber.ast.rich.*
import me.anno.zauber.ast.rich.Flags.hasFlag
import me.anno.zauber.ast.simple.SimpleGraph
import me.anno.zauber.ast.simple.expression.SimpleAssignment
import me.anno.zauber.interpreting.ExternalKey
import me.anno.zauber.scope.Scope
import me.anno.zauber.types.Type
import me.anno.zauber.types.Types
import me.anno.zauber.types.impl.ClassType
import me.anno.zauber.types.impl.arithmetic.NullType
import me.anno.zauber.types.impl.arithmetic.UnionType
import me.anno.zauber.types.impl.memory.ValueType
import me.anno.zauber.types.specialization.FieldSpecialization
import me.anno.zauber.types.specialization.MethodSpecialization
import me.anno.zauber.types.specialization.Specialization
import java.io.File

// todo compared to C, this has inheritance built-in, which
//  we can directly use; and it has ready-made shared references
class CppSourceGenerator(val cppVersion: Int = 11) : JavaSourceGenerator() {


    companion object {

        val protectedCppTypes by threadLocal {
            Types.run {
                mapOf(
                    Boolean to BoxedType("Boolean", "boolean"),
                    Byte to BoxedType("Byte", "byte"),
                    Short to BoxedType("Short", "short"),
                    Int to BoxedType("Integer", "int"),
                    Long to BoxedType("Long", "long"),
                    Char to BoxedType("Character", "char"),
                    Float to BoxedType("Float", "float"),
                    Double to BoxedType("Double", "double"),
                )
            }
        }

        val nativeCppTypes by threadLocal { protectedCppTypes.filter { (_, it) -> it.boxed != it.native } }
        val nativeCppNumbers by threadLocal { nativeCppTypes - Types.Boolean }

        val registeredMethods by threadLocal { HashMap<ExternalKey, String>() }
        fun register(key: ExternalKey, implementation: String) {
            registeredMethods[key] = implementation
        }

        fun register(scope: Scope, name: String, valueParameterTypes: List<Type>, implementation: String) {
            register(ExternalKey(scope, name, valueParameterTypes), implementation)
        }
    }

    override val protectedTypes: Map<ClassType, BoxedType> get() = protectedCppTypes
    override val nativeTypes: Map<ClassType, BoxedType> get() = nativeCppTypes
    override val nativeNumbers: Map<ClassType, BoxedType> get() = nativeCppNumbers

    val cppFiles = HashSet<File>()

    override fun getExtension(headerOnly: Boolean): String {
        return if (headerOnly) "hpp" else "cpp"
    }

    override fun defineNullableAnnotation(dst: File, writer: FileWithImportsWriter) {
        // nothing to do here
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

    override fun generateClassBody(
        className: String, classScope: Scope, specialization: Specialization,
        methods: Collection<MethodSpecialization>, fields: Collection<FieldSpecialization>,
        headerOnly: Boolean
    ) {
        declareImport(classScope, specialization)
        specializations.add(specialization)

        appendSpecializationInfoComment()

        if (headerOnly) {
            appendClassFlags(classScope)
            appendClassPrefix(classScope, className)

            if (specialization.containsGenerics()) {
                appendTypeParams(classScope)
            }

            appendSuperTypes(classScope)
            appendClassBody(classScope, className, methods, fields, true)
            trimWhitespaceAtEnd()
            builder.append(";")
            nextLine()

        } else {
            appendConstructors(classScope, className, methods, false)
            appendMethods(classScope, className, methods, false)
        }

        @Suppress("Since15")
        specializations.removeLast()
    }

    override fun appendStaticInstance(className: String) {
        // https://stackoverflow.com/a/1008289/4979303
        builder.append(
            """
        static $className& get$OBJECT_FIELD_NAME() {
            static $className $OBJECT_FIELD_NAME;
            return $OBJECT_FIELD_NAME;
        }
        """.trimIndent() + "\n"
        )
        if (cppVersion in 3 until 11) {
            builder.append(
                """
    private:
        $className($className const&); // Don't Implement
        void operator=($className const&); // Don't implement 
    public:
        """.trimIndent()
            )
        } else if (cppVersion >= 11) {
            builder.append(
                """
        $className($className const&) = delete;
        void operator=($className const&) = delete;
            """.trimIndent()
            )
        } else {
            builder.append("public:")
        }
        nextLine()
        nextLine()
    }

    override fun getMainMethodFile(dst: File): File {
        return File(dst, "__main.cpp")
    }

    override fun defineMainMethodCallEntry(
        dst: File, writer: FileWithImportsWriter,
        mainMethod: Method, className: String
    ): FileEntry {
        val needsArgs = mainMethod.valueParameters.isNotEmpty()
        cppFiles += getMainMethodFile(dst)
        return FileEntry(emptyList(), this)
            .apply {
                // todo convert argc/argv to String-array, if needed
                content.append(
                    """
                int main(int argc, char** argv) {
                    $className.${mainMethod.name}(${if (needsArgs) "argv" else ""});
                    return 0;
                }
            """.trimIndent()
                )
            }
    }

    override fun appendPackageDeclaration(packagePath: List<String>, file: File) {
        if (file.name.endsWith(".hpp")) builder.append("#pragma once\n")
        if (packagePath.isEmpty()) return
        if (cppVersion >= 17) {
            for (part in packagePath) {
                builder.append("namespace ").append(part).append(" {")
                nextLine()
            }
        } else {
            builder.append("namespace ")
            for (part in packagePath) {
                builder.append(part).append(' ')
            }
            builder.append("{")
            nextLine()
        }
        depth++
        nextLine()
    }

    override fun endPackageDeclaration(packagePath: List<String>, file: File) {
        if (packagePath.isEmpty()) return
        val packageDepth = if (cppVersion >= 17) 1 else packagePath.size
        repeat(packageDepth) {
            builder.append("}")
        }
        depth--
        nextLine()
    }

    override fun beginPackageDeclaration(
        packagePath: List<String>, file: File,
        imports: Map<String, List<String>>,
        nativeImports: Set<String>
    ) {
        if (nativeImports.isNotEmpty()) {
            for (import in nativeImports) {
                builder.append(import)
                nextLine()
            }
            nextLine()
        }

        writeImports(packagePath, imports)
        writeUsingNamespace(packagePath, imports)

        nextLine()
        appendPackageDeclaration(packagePath, file)
    }

    fun writeUsingNamespace(packagePath: List<String>, imports: Map<String, List<String>>) {
        if (imports.none { it.value.size > 1 }) return

        val usingNamespace = imports.values
            .filter { it.size > 1 }
            .map { it.subList(0, it.size - 1) }
            .distinct()
            .sortedWith(ImportSorter)

        for (import in usingNamespace) {
            builder.append("using namespace ")
            for (i in import.indices) {
                if (i > 0) builder.append("::")
                builder.append(import[i])
            }
            builder.append(";")
            nextLine()
        }
    }

    override fun appendImport(packagePath: List<String>, import: List<String>) {
        builder.append("#include \"")
        var i = 0
        while (i + 1 < import.size && i < packagePath.size && packagePath[i] == import[i]) {
            // nothing to do
            i++
        }
        repeat(packagePath.size - i) {
            builder.append("../")
        }
        var needsSlash = false
        while (i < import.size) {
            if (needsSlash) builder.append("/")
            builder.append(import[i])
            needsSlash = true
            i++
        }
        builder.append(".hpp\"")
        nextLine()
    }

    override fun appendClassFlags(scope: Scope) {
        // no flags yet
    }

    override fun appendConstructorBody(
        classScope: Scope, className: String,
        constructor: Constructor, headerOnly: Boolean
    ) {
        if (!headerOnly) super.appendConstructorBody(classScope, className, constructor, false)
        else {
            builder.append(";")
            nextLine()
        }
    }

    override fun appendConstructorFlags(classScope: Scope, constructor: Constructor, headerOnly: Boolean) {
        if (headerOnly) {
            if (classScope.isObjectLike() || constructor.flags.isPrivate()) {
                builder.append("private:")
                nextLine()
            } else {
                builder.append("public:")
                nextLine()
            }
        }
        // no flags yet
    }

    override fun appendConstructorHeader(
        classScope: Scope, className: String,
        constructor: Constructor, headerOnly: Boolean
    ) {
        if (headerOnly) super.appendConstructorHeader(classScope, className, constructor, true)
        else {
            appendConstructorFlags(classScope, constructor, false)
            builder.append(className).append("::").append(className)
            appendValueParameterDeclaration(null, constructor.valueParameters, classScope)
        }
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

    override fun appendFieldFlags(classScope: Scope, field: Field, allowFinal: Boolean) {
        // no flags yet
    }

    override fun getClassType(scope: Scope): String {
        return "struct"
    }

    override fun appendMethod(
        classScope: Scope, className: String,
        method0: MethodSpecialization, headerOnly: Boolean
    ) {
        val method = method0.method as Method
        if (!headerOnly && method0.method.isExternal() && getNativeImplementation(method) == null) {
            // missing implementation
            comment {
                appendNativeImports(method)
                super.appendMethod(classScope, className, method0, headerOnly)
            }
        } else {
            if (!headerOnly) appendNativeImports(method)
            super.appendMethod(classScope, className, method0, headerOnly)
        }
    }

    override fun appendMethodHeader(
        classScope: Scope, className: String,
        method0: MethodSpecialization, headerOnly: Boolean
    ) {
        val method = method0.method as Method
        appendMethodFlags(classScope, method, headerOnly)

        val selfType = method.selfType
        val isBySelf = selfType == classScope.typeWithArgs ||
                method.flags.hasFlag(Flags.OVERRIDE) ||
                method.flags.hasFlag(Flags.ABSTRACT)

        appendTypeParameterDeclaration(method.typeParameters, classScope)

        val returnType = resolveType(method.returnType ?: Types.NullableAny)
        appendType(returnType, classScope, false)
        appendOwnershipSuffix(returnType)

        builder.append(' ')
        if (!headerOnly) {
            builder.append(className).append("::")
        }
        builder.append(getMethodName(method0))

        val selfTypeIfNecessary = if (!isBySelf) selfType else null
        method.selfTypeIfNecessary = selfTypeIfNecessary
        appendValueParameterDeclaration(selfTypeIfNecessary, method.valueParameters, classScope)
    }


    fun appendNativeImports(method: Method) {
        val nativeImpl = getNativeImplementation(method) ?: return
        val imports = nativeImpl.lines()
            .filter { it.startsWith("#include") }
        if (imports.isNotEmpty()) {
            nativeImports.addAll(imports)
        }
    }

    override fun appendMethodBody(method: Method, spec: Specialization, headerOnly: Boolean) {
        if (headerOnly) {
            builder.append(";")
            nextLine()
        } else {
            super.appendMethodBody(method, spec, false)
        }
    }

    override fun appendNativeImplementation(nativeImpl: String, method: MethodLike) {
        val implWithoutImports = nativeImpl.lines()
            .filter { !it.startsWith("#include") }
            .joinToString("\n")
        super.appendNativeImplementation(implWithoutImports, method)
    }

    override fun appendGetObjectInstance() {
        builder.append("::get").append(OBJECT_FIELD_NAME).append("()")
    }

    override fun appendDeclare(graph: SimpleGraph, expression: SimpleAssignment) {
        val dst = expression.dst
        // without final
        appendType(dst.type, expression.scope, false)
        builder.append(' ').append1(graph, dst).append(" = ")
    }

    override fun appendObjectInstance(field: Field, exprScope: Scope) {
        if (field.ownerScope == outsideClassLike(exprScope)) {
            builder.append("this->")
        } else {
            appendType(field.ownerScope.typeWithArgs, exprScope, true)
            appendGetObjectInstance()
            builder.append('.')
        }
    }

    // add "virtual" fun getClassId() (?)
    // check if is native type, value type -> true instance
    // check if is object type -> reference
    // check if is nullable -> pointer

    fun appendOwnershipSuffix(type: Type) {
        val type = resolveType(type)
        val symbol = when {
            isNullable(type) -> "*"
            isValue(type) -> ""
            else -> "&"
        }
        builder.append(symbol)
    }

    fun isNullable(type: Type): Boolean {
        return (type == NullType) || (type is UnionType && NullType in type.types)
    }

    fun isValue(type: Type): Boolean {
        return (type is ValueType) || (type is ClassType && type.clazz.isValueType())
    }

    override fun filterImports(name: String, packageScope: Scope, headerOnly: Boolean) {
        if (headerOnly) {
            // remove self-include
            imports.remove(name)
        }
    }

}