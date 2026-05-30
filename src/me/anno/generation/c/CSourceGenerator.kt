package me.anno.generation.c

import me.anno.generation.BoxedType
import me.anno.generation.FileEntry
import me.anno.generation.FileWithImportsWriter
import me.anno.generation.Specializations.specialization
import me.anno.generation.cpp.CppSourceGenerator
import me.anno.generation.java.Import2
import me.anno.zauber.ast.rich.member.Constructor
import me.anno.zauber.ast.rich.member.Field
import me.anno.zauber.ast.rich.member.Method
import me.anno.zauber.ast.rich.parameter.Parameter
import me.anno.zauber.ast.simple.SimpleBlock.Companion.isValue
import me.anno.zauber.ast.simple.SimpleGraph
import me.anno.zauber.ast.simple.expression.SimpleAllocateInstance
import me.anno.zauber.ast.simple.expression.SimpleCall
import me.anno.zauber.ast.simple.expression.SimpleConstructorCall
import me.anno.zauber.ast.simple.fields.SimpleInstruction
import me.anno.zauber.scope.Scope
import me.anno.zauber.types.Specialization
import me.anno.zauber.types.Type
import me.anno.zauber.types.Types
import java.io.File
import java.util.*

/**
 * this is more custom than C++:
 * todo we need to implement inheritance explicitly
 * todo we also need to deduplicate methods with same name, but different parameters
 * */
class CSourceGenerator : CppSourceGenerator() {

    companion object {
        fun hashMethodParameters(method: Specialization): String {
            check(method.isMethodLike())
            if (method.method.valueParameters.isEmpty()) {
                // we rely on this special behavior -> make it explicit
                return "0"
            }
            return method.use {
                val hash = method.method.valueParameters.joinToString {
                    "${it.name}: ${resolveType(it.type)}"
                }.hashCode()
                hash.toUInt().toString(manglingBasis)
            }
        }
    }

    private fun getDefineName(packagePath: List<String>, file: File): String {
        return (packagePath + file.name.replace('.', '_'))
            .joinToString("_").uppercase(Locale.ENGLISH)
    }

    override fun beginPackageDeclaration(
        packagePath: List<String>, file: File,
        imports: Map<String, Import2>, nativeImports: Set<String>
    ) {
        if (file.name.endsWith(".h")) {
            val defineName = getDefineName(packagePath, file)
            builder.append("#ifndef ").append(defineName); nextLine()
            builder.append("#define ").append(defineName); nextLine()
            nextLine()
        }
        appendNativeImports(nativeImports)
        appendStdlibImport(packagePath)

        appendImports(packagePath, imports)
        nextLine()
    }

    override fun appendVisibility(isPrivate: Boolean) {
        // no visibility for C
    }

    override fun endPackageDeclaration(packagePath: List<String>, file: File) {
        if (file.name.endsWith(".h")) {
            nextLine()
            val defineName = getDefineName(packagePath, file)
            builder.append("#endif // ").append(defineName); nextLine()
        }
    }

    override fun appendStdlibImport(packagePath: List<String>) {
        // only really needed, if we have allocations...
        builder.append("#include \"${"../".repeat(packagePath.size)}CStandardLib.h\"\n")
        nextLine()
    }

    override fun appendImport(packagePath: List<String>, import: List<String>, importedScope: Scope?) {
        builder.append("#include \"")
        builder.appendRelativePath(packagePath, import)
        builder.append(".h\"")
        nextLine()
    }

    override fun appendMethodFlags(classScope: Scope, method0: Specialization, headerOnly: Boolean) {
        // nothing yet
    }

    override fun needsEmptyConstructor(classScope: Scope, methods: Collection<Specialization>): Boolean {
        return false
    }

    override fun getMethodName(method0: Specialization): String {
        val ownerScope = method0.method.ownerScope
        val ownerSpec = method0.withScope(ownerScope)
        val clazzName = getClassName(ownerSpec.clazz, ownerSpec)
        val packagePrefix = (ownerScope.parent?.path ?: emptyList())
            .joinToString("") { name -> name + "_" }
        val base = if (method0.method is Constructor) "_init_" else {
            super.getMethodName0(method0)
                .replace('-', '_')
                .replace('.', '_')
        }
        return "${packagePrefix}${clazzName}_${base}_${hashMethodParameters(method0)}"
    }

    override fun getExtension(headerOnly: Boolean): String {
        return if (headerOnly) "h" else "c"
    }

    override fun appendClassPrefix(scope: Scope, className: String) {
        builder.append("typedef struct ")
    }

    override fun appendClass(
        className: String, classScope: Scope,
        specialization: Specialization,
        methods: Collection<Specialization>,
        fields: Collection<Specialization>,
        headerOnly: Boolean
    ) {
        declareImport(classScope, specialization)
        specialization.use {

            val packagePrefix = getPackagePrefix(classScope)

            appendSpecializationInfoComment()

            if (headerOnly) {
                appendClassFlags(classScope)
                appendClassPrefix(classScope, className)

                writeBlock {
                    // todo append truly all fields...
                    appendFields(classScope, fields, true, headerOnly)
                }
                removeTrailingWhitespace()

                builder.append(' ')
                builder.append(packagePrefix)
                builder.append(className)
                builder.append(";")
                nextLine()
            }

            appendConstructors(classScope, className, methods, headerOnly)
            appendMethods(classScope, className, methods, headerOnly)

            if (classScope.isObjectLike()) {

                nextLine()
                builder.append(packagePrefix)
                builder.append(className)
                builder.append("* ")
                builder.append(packagePrefix)
                builder.append(className)
                builder.append("__getObject()")

                if (headerOnly) {
                    builder.append(';')
                    nextLine()
                } else {
                    writeBlock {
                        builder.append("static ")
                            .append(packagePrefix).append(className)
                            .append(" instance;"); nextLine()
                        builder.append("static char isInitialized = 0;"); nextLine()
                        builder.append("if (!isInitialized) ")
                        writeBlock {
                            builder.append("isInitialized = 1;"); nextLine()
                            val method = classScope.getOrCreatePrimaryConstructorScope().selfAsConstructor!!
                            val methodSpec = Specialization.fromSimple(method.memberScope)
                            builder.append(getMethodName(methodSpec))
                                .append("(&instance);")
                            nextLine()
                        }
                        builder.append("return &instance;")
                        nextLine()
                    }
                }

            }
        }
    }

    override fun appendGetObjectInstance(objectScope: Scope, exprScope: Scope) {

        imports[objectScope.pathStr] = Import2(
            if (objectScope.isPackage()) objectScope.path + getPackageName(objectScope)
            else objectScope.path, objectScope
        )

        val className = getClassName(objectScope, Specialization.fromSimple(objectScope))
        val packagePrefix = getPackagePrefix(objectScope)
        builder.append(packagePrefix)
        builder.append(className)
        builder.append("__getObject()")
    }

    fun getPackagePrefix(classScope: Scope): String {
        val scope = if (classScope.isPackage()) classScope else classScope.parent!!
        return scope.path.joinToString("") { name -> name + "_" }
    }

    override fun appendConstructorHeader(
        classScope: Scope, className: String,
        constructor: Constructor, headerOnly: Boolean
    ) {
        appendType(Types.Unit, classScope, false)
        builder.append("* ").append(getMethodName(specialization))
        appendValueParameterDeclaration(null, constructor.valueParameters, classScope)
    }

    override fun appendValueParameterDeclaration(
        selfTypeIfNecessary: Type?,
        valueParameters: List<Parameter>, scope: Scope
    ) {
        builder.append('(')
        if (true) {
            appendType(scope.typeWithArgs.specialize(), scope, true)
            builder.append("* this")
        }
        if (selfTypeIfNecessary != null) {
            builder.append(", ")
            appendType(selfTypeIfNecessary, scope, false)
            builder.append(" __self")
        }
        for (param in valueParameters) {
            builder.append(", ")
            appendType(param.type, scope, false)
            appendOwnershipSuffix(param.type)
            builder.append(' ')
            appendFieldName(param)
        }
        builder.append(')')
    }

    override fun appendBackingField(classScope: Scope, field: Field, allowFinal: Boolean, headerOnly: Boolean) {
        appendFieldFlags(classScope, field, allowFinal)

        var valueType = (field.valueType ?: Types.NullableAny)
        valueType = valueType.resolve(classScope)
        valueType = resolveType(valueType)

        appendType(valueType, classScope, false)
        builder.append(' ')
        appendFieldName(field)
        builder.append(";")
        nextLine()
    }

    override fun appendMethodHeader(
        classScope: Scope, className: String,
        method0: Specialization, headerOnly: Boolean
    ) {
        // kinda-hack to not output the this-type
        super.appendMethodHeader(classScope, className, method0, headerOnly = true)
    }

    override fun appendClassName(path: List<String>, scope: Scope) {
        val name = path.joinToString(".") // everything needs to be imported
        imports.getOrPut(name) { Import2(path, scope) }

        builder.append(path.joinToString("_"))
    }

    override fun appendNonNativeCall(expr: SimpleCall, graph: SimpleGraph) {
        val methodName = getMethodName(expr.specialization)
        builder.append(methodName).append('(')
        appendFieldName(graph, expr.thisInstance, "")
        appendValueParams(graph, expr.valueParameters, withBrackets = false)
        builder.append(')')
    }

    override fun getMainMethodFile(dst: File): File {
        return File(dst, "main.c")
    }

    override fun defineMainMethodCallEntry(
        dst: File, writer: FileWithImportsWriter,
        mainMethod: Method, className: String
    ): FileEntry {
        val needsArgs = mainMethod.valueParameters.isNotEmpty()
        cppFiles += getMainMethodFile(dst)
        val methodName = getMethodName(Specialization.fromSimple(mainMethod.memberScope))

        val l0 = builder.length
        appendGetObjectInstance(mainMethod.ownerScope, mainMethod.scope)
        val objInstance = builder.substring(l0)
        builder.setLength(l0)

        return FileEntry(emptyList(), this)
            .apply {
                // todo convert argc/argv to String-array, if needed
                content.append(
                    """
                int main(int argc, char** argv) {
                    $methodName($objInstance${if (needsArgs) ", argv" else ""});
                    return 0;
                }
            """.trimIndent()
                )
            }
    }

    override fun appendDefaultValue(valueType: Type) {
        when (valueType) {
            Types.Boolean -> builder.append("false")
            in nativeTypes -> builder.append("0")
            else -> {
                if (valueType.isValue()) builder.append("{}")
                else builder.append("NULL")
            }
        }
    }

    override fun appendInstrImpl(graph: SimpleGraph, expr: SimpleInstruction) {
        when (expr) {
            is SimpleAllocateInstance -> {
                // this allocation is a ClassType, so it cannot be null ever
                if (!expr.allocatedType.isValue()) {
                    // call GC-aware alloc instead
                    builder.append("gcNew(")
                    appendType(expr.allocatedType, expr.scope, true)
                    builder.append(")")
                } else {
                    builder.append("{}")
                }
            }
            is SimpleConstructorCall -> {
                val methodName = getMethodName(expr.specialization)
                builder.append(methodName).append('(')
                appendFieldName(graph, expr.thisInstance, "")
                appendValueParams(graph, expr.valueParameters, withBrackets = false)
                builder.append(");")
            }
            else -> super.appendInstrImpl(graph, expr)
        }
    }

    override fun appendNativeCall(needsCastForFirstValue: BoxedType, expr: SimpleCall, graph: SimpleGraph) {
        check(expr.methodName == "inc")
        appendFieldName(graph, expr.thisInstance, "")
        builder.append(" + 1")
    }

}