package me.anno.generation.js

import me.anno.generation.BoxedType
import me.anno.generation.FileEntry
import me.anno.generation.FileWithImportsWriter
import me.anno.generation.Specializations
import me.anno.generation.c.CSourceGenerator.Companion.hashMethodParameters
import me.anno.generation.cpp.CppSourceGenerator.Companion.appendRelativePath
import me.anno.generation.java.JavaSourceGenerator
import me.anno.utils.ResetThreadLocal.Companion.threadLocal
import me.anno.zauber.SpecialFieldNames.OBJECT_FIELD_NAME
import me.anno.zauber.ast.rich.*
import me.anno.zauber.ast.rich.Flags.hasFlag
import me.anno.zauber.ast.simple.SimpleDeclaration
import me.anno.zauber.ast.simple.SimpleGraph
import me.anno.zauber.ast.simple.SimpleInstruction
import me.anno.zauber.ast.simple.expression.SimpleAllocateInstance
import me.anno.zauber.ast.simple.expression.SimpleAssignment
import me.anno.zauber.ast.simple.expression.SimpleCall
import me.anno.zauber.ast.simple.expression.SimpleSetField
import me.anno.zauber.scope.Scope
import me.anno.zauber.scope.ScopeType
import me.anno.zauber.typeresolution.ParameterList.Companion.emptyParameterList
import me.anno.zauber.typeresolution.ResolutionContext
import me.anno.zauber.types.Type
import me.anno.zauber.types.Types
import me.anno.zauber.types.impl.ClassType
import me.anno.zauber.types.impl.GenericType
import me.anno.zauber.types.specialization.FieldSpecialization
import me.anno.zauber.types.specialization.MethodSpecialization
import me.anno.zauber.types.specialization.Specialization
import java.io.File

/**
 * this is just like Java source code, except that
 *  a) we don't need to specify what each type is
 *  b) we must generate unique method names from their signature, if overloads exist
 * */
open class JavaScriptSourceGenerator : JavaSourceGenerator() {

    companion object {
        val protectedJSTypes by threadLocal {
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

        val nativeJSTypes by threadLocal { protectedJSTypes.filter { (_, it) -> it.boxed != it.native } }
        val nativeJSNumbers by threadLocal { nativeJSTypes - Types.Boolean }
    }

    override val protectedTypes: Map<ClassType, BoxedType> get() = protectedJSTypes
    override val nativeTypes: Map<ClassType, BoxedType> get() = nativeJSTypes
    override val nativeNumbers: Map<ClassType, BoxedType> get() = nativeJSNumbers

    override fun getExtension(headerOnly: Boolean): String = "js"

    override fun getMethodName(method: MethodSpecialization): String {
        val base = if (method.method is Constructor) "__init_" else super.getMethodName(method)
        return "${base}_${hashMethodParameters(method)}"
    }

    override fun getMainMethodFile(dst: File): File {
        return File(dst, "main.${getExtension(false)}")
    }

    override fun defineNullableAnnotation(dst: File, writer: FileWithImportsWriter) {
        // skip
    }

    override fun defineMainMethodCallEntry(
        dst: File, writer: FileWithImportsWriter,
        mainMethod: Method, className: String
    ): FileEntry {
        val needsArgs = mainMethod.valueParameters.isNotEmpty()
        val spec = Specialization(mainMethod.methodScope, emptyParameterList())
        val methodName = getMethodName(MethodSpecialization(mainMethod, spec))
        return FileEntry(emptyList(), this)
            .apply {
                content.append(
                    """
                $className.$methodName(${if (needsArgs) "args" else ""});
            """.trimIndent()
                )
            }
    }

    override fun appendPackageDeclaration(packagePath: List<String>, file: File) {
        builder.append("// $packagePath")
        nextLine()
        builder.append("'use strict';")
        nextLine()
        nextLine()
    }

    override fun appendImport(packagePath: List<String>, import: List<String>) {
        builder.append("import { ")
        builder.append(import.last())
        builder.append(" } from \"")
        builder.appendRelativePath(packagePath, import)
        builder.append(".js\";")
        nextLine()
    }

    override fun getClassType(scope: Scope): String {
        // todo what about enums?
        return "class"
    }

    override fun appendClassFlags(scope: Scope) {
        if (scope.flags.hasFlag(Flags.ABSTRACT)) builder.append("abstract ")
    }

    override fun appendClass(
        className: String, classScope: Scope, specialization: Specialization,
        methods: Collection<MethodSpecialization>, fields: Collection<FieldSpecialization>,
        headerOnly: Boolean
    ) {
        declareImport(classScope, specialization)
        specialization.use {
            appendSpecializationInfoComment()

            builder.append("export ")
            appendClassFlags(classScope)
            appendClassPrefix(classScope, className)

            // skipped type parameters
            appendSuperTypes(classScope)

            appendClassBody(classScope, className, methods, fields, headerOnly)
        }
    }

    override fun appendFieldFlags(classScope: Scope, field: Field, allowFinal: Boolean) {
        if (field == classScope.objectField) builder.append("static ")
    }

    override fun appendConstructorFlags(classScope: Scope, constructor: Constructor, headerOnly: Boolean) {
        // nothing
    }

    override fun appendConstructorHeader(
        classScope: Scope, className: String,
        constructor: Constructor, headerOnly: Boolean
    ) {
        appendConstructorFlags(classScope, constructor, headerOnly)
        if (classScope.isObjectLike()) builder.append("constructor")
        else builder.append(getMethodName(MethodSpecialization(constructor, Specializations.specialization)))
        appendValueParameterDeclaration(null, constructor.valueParameters, classScope)
    }

    override fun appendConstructor(
        classScope: Scope, className: String,
        constructor: Constructor, headerOnly: Boolean
    ) {
        // some spacing
        nextLine()

        appendConstructorHeader(classScope, className, constructor, headerOnly)
        appendConstructorBody(classScope, className, constructor, headerOnly)
    }

    override fun appendBackingField(classScope: Scope, field: Field, allowFinal: Boolean, headerOnly: Boolean) {
        appendFieldFlags(classScope, field, allowFinal)

        var valueType = (field.valueType ?: Types.NullableAny)
        valueType = valueType.resolve(classScope)
        valueType = resolveType(valueType)

        appendFieldName(field)
        builder.append(" = ")
        appendDefaultValue(valueType)
        builder.append(';')
        nextLine()
    }

    open fun appendDefaultValue(valueType: Type) {
        when (valueType) {
            Types.Boolean -> builder.append("false")
            in nativeTypes -> builder.append("0")
            else -> builder.append("null")
        }
    }

    override fun appendMethodFlags(classScope: Scope, method: Method, headerOnly: Boolean) {
        if (method.flags.hasFlag(Flags.ABSTRACT) && classScope.scopeType != ScopeType.INTERFACE) {
            builder.append("abstract ")
        }
    }

    override fun appendMethodHeader(
        classScope: Scope, className: String,
        method0: MethodSpecialization, headerOnly: Boolean
    ) {
        val method = method0.method as Method
        appendMethodFlags(classScope, method, headerOnly)

        builder.append(getMethodName(method0))

        assignSelfType(classScope, method)
        appendValueParameterDeclaration(method.selfTypeIfNecessary, method.valueParameters, classScope)
    }

    override fun appendMethodBody(method: MethodSpecialization, headerOnly: Boolean) {
        val nativeImpl = getNativeImplementation(method.method)
        val body = method.method.body

        if (body != null) {
            val context = ResolutionContext(method.method.selfType, method.specialization, true, null)
            appendCode(context, method, body, false)
        } else if (nativeImpl != null) {
            appendNativeImplementation(nativeImpl, method.method)
        } else {
            writeBlock {
                builder.append("throw 'Missing implementation for $method';")
                nextLine()
            }
        }
    }

    override fun appendStaticInstance(classScope: Scope, className: String) {
        builder.append("static ").append(OBJECT_FIELD_NAME)
            .append(" = new ").append(className).append("();")
        nextLine()
    }

    override fun appendDeclare(graph: SimpleGraph, expression: SimpleAssignment) {
        val dst = expression.dst
        builder.append("const ")
        appendFieldName(graph, dst)
        builder.append(" = ")
    }

    override fun appendValueParameterDeclaration(
        selfTypeIfNecessary: Type?,
        valueParameters: List<Parameter>, scope: Scope
    ) {
        builder.append('(')
        if (selfTypeIfNecessary != null) {
            builder.append(" __self")
        }
        for (param in valueParameters) {
            if (!builder.endsWith("(")) builder.append(", ")
            appendFieldName(param)
        }
        builder.append(')')
    }

    override fun appendInstrImpl(graph: SimpleGraph, expr: SimpleInstruction) {
        when (expr) {
            is SimpleAllocateInstance -> {
                builder.append("new ")
                appendType(expr.allocatedType, expr.scope, true)
                builder.append("()")
            }
            is SimpleCall if (expr.sample is Constructor) -> {
                appendFieldName(graph, expr.self, ".")
                builder.append(getMethodName(expr.methodSpec))
                appendValueParams(graph, expr.valueParameters)
            }
            is SimpleDeclaration -> {
                builder.append("let ").append(expr.name).append(" = ")
                appendDefaultValue(expr.type)
            }
            else -> super.appendInstrImpl(graph, expr)
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
        // todo bug: why is this not being imported???

        builder.append("Object.assign(new ")
        builder.append(needsCastForFirstValue.boxed).append("(), { content: ")
        appendFieldName(graph, expr.self)
        builder.append(" }).")
        builder.append(getMethodName(expr.methodSpec))
        appendValueParams(graph, expr.valueParameters)
    }

    override fun appendCopy(graph: SimpleGraph, expr: SimpleSetField) {
        builder.append(".copy_0()")
    }

    override fun filterImports(name: String, packageScope: Scope, headerOnly: Boolean) {
        // remove self-include
        imports.remove(name)
    }

}