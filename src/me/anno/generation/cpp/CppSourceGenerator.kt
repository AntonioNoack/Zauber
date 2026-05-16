package me.anno.generation.cpp

import me.anno.generation.BoxedType
import me.anno.generation.FileEntry
import me.anno.generation.FileWithImportsWriter
import me.anno.generation.ImportSorter
import me.anno.generation.Specializations.specialization
import me.anno.generation.java.JavaSourceGenerator
import me.anno.generation.java.JavaSuperCallWriter.appendSuperCallParams
import me.anno.support.jvm.FirstJVMClassReader.Companion.isPrivate
import me.anno.utils.ResetThreadLocal.Companion.threadLocal
import me.anno.zauber.SpecialFieldNames.OBJECT_FIELD_NAME
import me.anno.zauber.ast.rich.*
import me.anno.zauber.ast.rich.expression.constants.NumberExpression
import me.anno.zauber.ast.simple.SimpleBlock.Companion.isNullable
import me.anno.zauber.ast.simple.SimpleBlock.Companion.isValue
import me.anno.zauber.ast.simple.SimpleDeclaration
import me.anno.zauber.ast.simple.SimpleField
import me.anno.zauber.ast.simple.SimpleGraph
import me.anno.zauber.ast.simple.SimpleInstruction
import me.anno.zauber.ast.simple.expression.SimpleAllocateInstance
import me.anno.zauber.ast.simple.expression.SimpleAssignment
import me.anno.zauber.ast.simple.expression.SimpleCall
import me.anno.zauber.scope.Scope
import me.anno.zauber.typeresolution.ResolutionContext
import me.anno.zauber.types.Specialization
import me.anno.zauber.types.Type
import me.anno.zauber.types.Types
import me.anno.zauber.types.impl.ClassType
import me.anno.zauber.types.impl.GenericType
import java.io.File

/**
 * structs are directly supported, inheritance is still supported
 * todo needs custom GC
 * */
open class CppSourceGenerator(val cppVersion: Int = 11) : JavaSourceGenerator() {

    companion object {

        val protectedCppTypes by threadLocal {
            Types.run {
                mapOf(
                    Boolean to BoxedType("Boolean", "boolean"),
                    Byte to BoxedType("Byte", "byte"),
                    Short to BoxedType("Short", "short"),
                    Int to BoxedType("Int", "int"),
                    Long to BoxedType("Long", "long"),
                    Char to BoxedType("Char", "char"),
                    Float to BoxedType("Float", "float"),
                    Double to BoxedType("Double", "double"),
                )
            }
        }

        val nativeCppTypes by threadLocal { protectedCppTypes.filter { (_, it) -> it.boxed != it.native } }
        val nativeCppNumbers by threadLocal { nativeCppTypes - Types.Boolean }

        fun StringBuilder.appendRelativePath(packagePath: List<String>, import: List<String>) {
            var i = 0
            while (i + 1 < import.size && i < packagePath.size && packagePath[i] == import[i]) {
                // nothing to do
                i++
            }
            val numBackwards = packagePath.size - i
            if (numBackwards > 0) {
                repeat(numBackwards) {
                    append("../")
                }
            } else {
                append("./")
            }
            var needsSlash = false
            while (i < import.size) {
                if (needsSlash) append("/")
                append(import[i])
                needsSlash = true
                i++
            }
        }
    }

    override val protectedTypes: Map<ClassType, BoxedType> get() = protectedCppTypes
    override val nativeTypes: Map<ClassType, BoxedType> get() = nativeCppTypes
    override val nativeNumbers: Map<ClassType, BoxedType> get() = nativeCppNumbers

    val cppFiles = HashSet<File>()

    open fun needsHeaders() = true

    override fun getExtension(headerOnly: Boolean): String {
        return if (headerOnly) "hpp" else "cpp"
    }

    override fun defineNullableAnnotation(dst: File, writer: FileWithImportsWriter) {
        // nothing to do here
    }

    override fun generateClassForScope(
        scope: Scope, dst: File, writer: FileWithImportsWriter, specialization: Specialization,
        methods: Collection<Specialization>, fields: Collection<Specialization>
    ) {
        if (needsHeaders()) {
            val (name, packageScope) = getNameAndScope(scope, specialization)
            appendClass(name, scope, specialization, methods, fields, true)
            writeInto(packageScope, name, dst, writer, true)

            appendClass(name, scope, specialization, methods, fields, false)
            cppFiles += writeInto(packageScope, name, dst, writer, false)
        } else {
            // just generate one class
            super.generateClassForScope(scope, dst, writer, specialization, methods, fields)
        }
    }

    override fun appendClass(
        className: String, classScope: Scope, specialization: Specialization,
        methods: Collection<Specialization>, fields: Collection<Specialization>,
        headerOnly: Boolean
    ) {
        declareImport(classScope, specialization)
        specialization.use {

            appendSpecializationInfoComment()

            if (headerOnly) {
                appendClassFlags(classScope)
                appendClassPrefix(classScope, className)

                // we specialize only the generics we need
                /*if (specialization.containsGenerics()) {
                    appendTypeParams(classScope)
                }*/

                appendSuperTypes(classScope)
                appendClassBody(classScope, className, methods, fields, true)
                trimWhitespaceAtEnd()
                builder.append(";")
                nextLine()

            } else {
                appendConstructors(classScope, className, methods, false)
                appendMethods(classScope, className, methods, false)
            }
        }
    }

    override fun appendSuperTypes(scope: Scope) {
        var hasSuper = false
        val superCall0 = scope.superCalls.firstOrNull()
        if (superCall0 != null &&
            superCall0.isClassCall &&
            superCall0.type != Types.Any
        ) {
            val type = superCall0.type
            builder.append(" : ")
            appendType(type, scope, true)
            hasSuper = true
        }

        var implementsKeyword = if (hasSuper) ", " else " : "
        for (superCall in scope.superCalls) {
            if (superCall.isInterfaceCall) {
                val type = superCall.type
                builder.append(implementsKeyword)
                appendType(type, scope, true)
                implementsKeyword = ", "
            }
        }
    }

    override fun appendConstructors(
        classScope: Scope, className: String,
        methods: Collection<Specialization>, headerOnly: Boolean
    ) {
        super.appendConstructors(classScope, className, methods, headerOnly)

        // if this is a value class & we have no empty constructor, append one
        if (headerOnly && classScope.typeWithArgs.isValue() &&
            methods.none { spec ->
                val method = spec.method
                method is Constructor && method.valueParameters.isEmpty()
            }
        ) {
            builder.append("public: ")
            builder.append(className).append("(){}")
            nextLine()
        }
    }

    override fun appendBackingField(classScope: Scope, field: Field, allowFinal: Boolean, headerOnly: Boolean) {
        appendFieldFlags(classScope, field, allowFinal)

        var valueType = (field.valueType ?: Types.NullableAny)
        valueType = valueType.resolve(classScope)
        valueType = resolveType(valueType)

        appendType(valueType, classScope, false)
        builder.append(' ')
        appendFieldName(field)
        val isNumber = valueType in nativeCppNumbers
        builder.append(if (isNumber) " = 0;" else " = {};")
        nextLine()
    }

    override fun appendStaticInstance(classScope: Scope, className: String) {
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
        if (packagePath.isEmpty()) return

        if (cppVersion >= 17) {
            builder.append("namespace ")
            for (i in packagePath.indices) {
                if (i > 0) builder.append('_')
                builder.append(packagePath[i])
            }
            builder.append("{")
            nextLine()
        } else {
            for (part in packagePath) {
                builder.append("namespace ").append(part).append(" {")
                nextLine()
            }
        }
        depth++
        nextLine()
    }

    override fun endPackageDeclaration(packagePath: List<String>, file: File) {
        if (packagePath.isEmpty()) return
        val packageDepth = if (cppVersion >= 17) 1 else packagePath.size
        depth--
        nextLine()
        repeat(packageDepth) {
            builder.append("}")
        }
        nextLine()
    }

    override fun beginPackageDeclaration(
        packagePath: List<String>, file: File,
        imports: Map<String, List<String>>,
        nativeImports: Set<String>
    ) {
        if (file.name.endsWith(".hpp")) builder.append("#pragma once\n")
        if (nativeImports.isNotEmpty()) {
            for (import in nativeImports) {
                builder.append(import)
                nextLine()
            }
            nextLine()
        }

        // only really needed, if we have allocations...
        builder.append("#include \"${"../".repeat(packagePath.size)}CppStandardLib.hpp\"\n")
        nextLine()

        appendImports(packagePath, imports)
        writeUsingNamespace(imports)

        nextLine()
        appendPackageDeclaration(packagePath, file)
    }

    fun writeUsingNamespace(imports: Map<String, List<String>>) {
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
        builder.appendRelativePath(packagePath, import)
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
        if (headerOnly) {
            builder.append(";")
            nextLine()
        } else {
            val body = constructor.body ?: return
            val context = ResolutionContext(constructor.selfType, true, null, emptyMap())
            writeBlock {
                val methodSpec = specialization
                check(methodSpec.method === constructor)
                appendCode(context, methodSpec, body, true)
            }
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

            // interfaces don't need super calls :)
            val superCall = constructor.superCall
            val superType = classScope.superCalls
                .firstOrNull { it.isClassCall }?.typeI
            if (superCall != null) {
                builder.append(" : ")
                if (superCall.target == InnerSuperCallTarget.THIS) {
                    builder.append(className) // is this supported? yes
                } else {
                    appendType(superType!!, constructor.scope, false)
                }

                val context = ResolutionContext(null, specialization, true, null)
                appendSuperCallParams(context, superCall)
            } else {
                comment { builder.append("superCall is null") }
            }
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
        method0: Specialization, headerOnly: Boolean
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
        method0: Specialization, headerOnly: Boolean
    ) {
        val method = method0.method as Method
        appendMethodFlags(classScope, method, headerOnly)

        appendTypeParameterDeclaration(method.typeParameters, classScope)

        val returnType = resolveType(method.returnType ?: Types.NullableAny)
        appendType(returnType, classScope, false)
        appendOwnershipSuffix(returnType)

        builder.append(' ')
        if (!headerOnly) {
            builder.append(className).append("::")
        }
        builder.append(getMethodName(method0))

        assignSelfType(classScope, method)
        appendValueParameterDeclaration(method.selfTypeIfNecessary, method.valueParameters, classScope)
    }


    fun appendNativeImports(method: Method) {
        val nativeImpl = getNativeImplementation(method) ?: return
        val imports = nativeImpl.lines()
            .filter { it.startsWith("#include") }
        if (imports.isNotEmpty()) {
            nativeImports.addAll(imports)
        }
    }

    override fun appendMethodBody(methodSpec: Specialization, headerOnly: Boolean) {
        if (headerOnly) {
            builder.append(";")
            nextLine()
        } else {
            super.appendMethodBody(methodSpec, false)
        }
    }

    override fun appendNativeImplementation(nativeImpl: String, method: MethodLike) {
        val implWithoutImports = nativeImpl.lines()
            .filter { !it.startsWith("#include") }
            .joinToString("\n")
        super.appendNativeImplementation(implWithoutImports, method)
    }

    override fun appendGetObjectInstance(objectScope: Scope, exprScope: Scope) {
        appendType(objectScope.typeWithArgs, objectScope, false)
        builder.append("::get").append(OBJECT_FIELD_NAME).append("()")
    }

    override fun appendDeclare(graph: SimpleGraph, expression: SimpleAssignment) {
        val dst = expression.dst
        // without final
        appendType(dst.type, expression.scope, false)
        appendOwnershipSuffix(dst.type)
        builder.append(' ')
        appendFieldName(graph, dst)
        builder.append(" = ")
    }

    override fun appendObjectInstance(field: Field, exprScope: Scope, forFieldAccess: String) {
        check(forFieldAccess == ".")
        if (field.ownerScope == outsideClassLike(exprScope)) {
            builder.append("this->")
        } else {
            appendGetObjectInstance(field.ownerScope, exprScope)
            builder.append(forFieldAccess)
        }
    }

    // add "virtual" fun getClassId() (?)
    // check if is native type, value type -> true instance
    // check if is object type -> reference
    // check if is nullable -> pointer

    fun appendOwnershipSuffix(type: Type) {
        val type = resolveType(type)
        val symbol = when {
            type.isNullable() -> "*"
            type.isValue() -> ""
            else -> "&"
        }
        builder.append(symbol)
    }

    override fun filterImports(name: String, packageScope: Scope, headerOnly: Boolean) {
        if (headerOnly) {
            // remove self-include
            imports.remove(name)
        }
    }

    override fun appendFieldName(graph: SimpleGraph, field: SimpleField, forFieldAccess: String) {
        val needsArrow = if (field.isOwnerThis(graph)) {
            builder.append("this")
            true
        } else if (field.isObjectLike()) {
            val objectType = (field.type as ClassType).clazz
            appendGetObjectInstance(objectType, graph.method.scope)
            false
        } else {
            var field = field
            while (true) {
                field = field.mergeInfo?.dst ?: break
            }
            when (val expr = field.constantRef) {
                is NumberExpression -> appendNumber(field.type, expr)
                null -> builder.append("tmp").append(field.id)
                else -> throw NotImplementedError("Append constant field $expr")
            }
            // todo depending on type, we need . or ->
            false
        }
        if (forFieldAccess.isNotEmpty()) {
            val symbol = if (needsArrow) {
                when (forFieldAccess) {
                    "." -> "->"
                    ")." -> ")->"
                    else -> forFieldAccess.replace(".", "->")
                }
            } else forFieldAccess
            builder.append(symbol)
        }
    }

    override fun appendType(type: Type, scope: Scope, needsBoxedType: Boolean) {
        val type = resolveType(type)

        if (!needsBoxedType) {
            val protected = protectedTypes[type]
            if (protected != null) {
                builder.append(protected.native)
                return
            }
        }

        if (type is GenericType) {
            return appendTypeImpl(type.superBounds, scope, needsBoxedType)
        }

        appendTypeImpl(type, scope, needsBoxedType)
    }

    override fun appendCallForPrimitive(needsCastForFirstValue: BoxedType, expr: SimpleCall, graph: SimpleGraph) {
        // ensure import
        val selfType = expr.self.type
        val position = builder.length
        appendType(selfType, expr.scope, true)
        builder.setLength(position)

        builder.append(needsCastForFirstValue.boxed).append("(")
        appendFieldName(graph, expr.self)
        builder.append(").")
        builder.append(expr.methodName)
        appendValueParams(graph, expr.valueParameters)
    }

    override fun appendInstrImpl(graph: SimpleGraph, expr: SimpleInstruction) {
        when (expr) {
            is SimpleAllocateInstance -> {
                // todo test nullable variables
                // this allocation is a ClassType, so it cannot be null ever
                if (!expr.allocatedType.isValue()) {
                    // call GC-aware alloc instead
                    builder.append("gcNew<")
                    appendType(expr.allocatedType, expr.scope, true)
                    builder.append(">")
                    appendValueParams(graph, expr.paramsForLater)
                } else {
                    appendType(expr.allocatedType, expr.scope, true)
                    appendValueParams(graph, expr.paramsForLater)
                }
            }
            is SimpleDeclaration -> {
                val type = expr.type
                appendType(type, expr.scope, false)
                appendOwnershipSuffix(type)
                builder.append(' ').append(expr.name)
                when { // avoid undefined variables, where possible
                    type.isNullable() -> builder.append(" = nullptr")
                    type.isValue() -> builder.append(" = {}")
                    type in nativeNumbers -> builder.append(" = 0")
                    else -> {} // default value is unknown...
                }
            }
            else -> super.appendInstrImpl(graph, expr)
        }
    }

}