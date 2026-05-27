package me.anno.generation.c

import me.anno.generation.Specializations.specialization
import me.anno.generation.cpp.CppSourceGenerator
import me.anno.generation.java.Import2
import me.anno.zauber.ast.rich.member.Constructor
import me.anno.zauber.ast.rich.member.Field
import me.anno.zauber.ast.rich.parameter.Parameter
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
                hash.toUInt().toString(36)
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
        appendNativeImports()
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

    override fun needsEmptyConstructor(classScope: Scope, methods: Collection<Specialization>): Boolean {
        return false
    }

    override fun getMethodName(method: Specialization): String {
        val ownerScope = method.method.ownerScope
        val ownerSpec = method.withScope(ownerScope)
        val clazzName = if (ownerScope.isPackage()) {
            getPackageName(ownerSpec.clazz, ownerSpec)
        } else {
            getClassName(ownerSpec.clazz, ownerSpec)
        }
        val packagePrefix = (ownerScope.parent?.path ?: emptyList())
            .joinToString("") { name -> name + "_" }
        val base = if (method.method is Constructor) "_init_" else {
            super.getMethodName0(method)
                .replace('-', '_')
                .replace('.', '_')
        }
        return "${packagePrefix}${clazzName}_${base}_${hashMethodParameters(method)}"
    }

    override fun getExtension(headerOnly: Boolean): String {
        return if (headerOnly) "h" else "c"
    }

    override fun appendClassPrefix(scope: Scope, className: String) {
        builder.append("struct ")
        for (segment in scope.parent?.path ?: emptyList()) {
            builder.append(segment).append('_')
        }
        builder.append(className)
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

            appendSpecializationInfoComment()

            if (headerOnly) {
                appendClassFlags(classScope)
                appendClassPrefix(classScope, className)

                writeBlock {
                    // todo append truly all fields...
                    appendFields(classScope, fields, true, headerOnly)
                }
                removeTrailingWhitespace()
                builder.append(";")
                nextLine()
            }

            appendConstructors(classScope, className, methods, headerOnly)
            appendMethods(classScope, className, methods, headerOnly)

        }
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
        val name = path.joinToString("/") // everything needs to be imported
        imports.getOrPut(name) { Import2(path, scope) }

        builder.append("struct ")
            .append(path.joinToString("_"))
    }

}