package me.anno.generation.rust

import me.anno.generation.BoxedType
import me.anno.generation.FileEntry
import me.anno.generation.FileWithImportsWriter
import me.anno.generation.c.CSourceGenerator
import me.anno.utils.ResetThreadLocal.Companion.threadLocal
import me.anno.zauber.SpecialFieldNames.OBJECT_FIELD_NAME
import me.anno.zauber.ast.rich.Constructor
import me.anno.zauber.ast.rich.Field
import me.anno.zauber.ast.rich.Method
import me.anno.zauber.ast.rich.Parameter
import me.anno.zauber.ast.simple.ASTSimplifier.needsFieldByParameter
import me.anno.zauber.ast.simple.SimpleField
import me.anno.zauber.ast.simple.SimpleGraph
import me.anno.zauber.ast.simple.SimpleInstruction
import me.anno.zauber.ast.simple.SimpleNode.Companion.isValue
import me.anno.zauber.ast.simple.expression.SimpleAllocateInstance
import me.anno.zauber.ast.simple.expression.SimpleAssignment
import me.anno.zauber.expansion.DependencyData
import me.anno.zauber.scope.Scope
import me.anno.zauber.scope.ScopeInitType
import me.anno.zauber.types.Type
import me.anno.zauber.types.Types
import me.anno.zauber.types.impl.ClassType
import me.anno.zauber.types.specialization.ClassSpecialization
import me.anno.zauber.types.specialization.FieldSpecialization
import me.anno.zauber.types.specialization.MethodSpecialization
import me.anno.zauber.types.specialization.Specialization
import java.io.File

/**
 * this has the same complexity as C, plus we must define ownership somehow...
 *  wrapping everything into GC is kind of lame...
 * */
class RustSourceGenerator : CSourceGenerator() {

    companion object {

        val protectedRustTypes by threadLocal {
            Types.run {
                mapOf(
                    Boolean to BoxedType("Boolean", "boo"),
                    Byte to BoxedType("Byte", "i8"),
                    Short to BoxedType("Short", "i16"),
                    Int to BoxedType("Int", "i32"),
                    Long to BoxedType("Long", "i64"),
                    Char to BoxedType("Char", "char"),
                    Float to BoxedType("Float", "f32"),
                    Double to BoxedType("Double", "f64"),
                )
            }
        }

        val nativeRustTypes by threadLocal { protectedRustTypes.filter { (_, it) -> it.boxed != it.native } }
        val nativeRustNumbers by threadLocal { nativeRustTypes - Types.Boolean }

    }

    override val protectedTypes: Map<ClassType, BoxedType> get() = protectedRustTypes
    override val nativeTypes: Map<ClassType, BoxedType> get() = nativeRustTypes
    override val nativeNumbers: Map<ClassType, BoxedType> get() = nativeRustNumbers

    override fun needsHeaders(): Boolean = false
    override fun getExtension(headerOnly: Boolean): String = "rs"

    override fun generateCode(dst: File, data: DependencyData, mainMethod: Method) {
        val writer = FileWithImportsWriter(this, dst)
        try {

            val methodsByClass = data.calledMethods.groupBy {
                ClassSpecialization(it.method.ownerScope, it.specialization)
            }

            val fieldsByClass = (data.getFields + data.setFields).groupBy {
                ClassSpecialization(it.field.ownerScope, it.specialization)
            }

            val classes = (methodsByClass.keys + fieldsByClass.keys + data.createdClasses)
                .filter { it.clazz.isClassLike() }

            for (clazz in classes) {
                val methods = methodsByClass[clazz] ?: emptyList()
                val fields = fieldsByClass[clazz] ?: emptyList()
                val classSpec = clazz.specialization
                clazz.clazz[ScopeInitType.CODE_GENERATION]
                generateClassForScope(clazz.clazz, dst, writer, classSpec, methods, fields)
            }

            generateModuleHierarchy(dst, writer)
            defineMainMethodCall(dst, writer, mainMethod)

        } finally {
            writer.finish()
        }
    }

    fun generateModuleHierarchy(dst: File, writer: FileWithImportsWriter) {
        val folders = writer.newContent
            // .filter { it.value == null }
            .keys.groupBy { it.parentFile }
            .entries
            .sortedBy { it.key.absolutePath.length }
        for ((folder, children) in folders) {
            if (children.first() == dst) continue

            val entry = FileEntry(emptyList(), this)
            for (child in children) {
                entry.content.append("pub mod ").append(child.nameWithoutExtension).append(";\n")
            }
            writer[File(folder, "mod.rs")] = entry
        }
    }

    override fun getMainMethodFile(dst: File): File {
        return File(dst, "main.rs")
    }

    override fun defineMainMethodCallEntry(
        dst: File, writer: FileWithImportsWriter,
        mainMethod: Method, className: String
    ): FileEntry {
        val needsArgs = mainMethod.valueParameters.isNotEmpty()
        val methodName = getMethodName(MethodSpecialization(mainMethod, Specialization.noSpecialization))
        return FileEntry(emptyList(), this)
            .apply {
                // todo convert argc/argv to String-array, if needed
                // include all imports
                content.append(writer[File(dst, "mod.rs")]!!.content)
                content.append(
                    """
                fn main() {
                    $className.$methodName(${if (needsArgs) "std::env::args()" else ""});
                }
            """.trimIndent()
                )
            }
    }

    override fun getClassType(scope: Scope): String {
        return "pub struct"
    }

    override fun appendClass(
        className: String, classScope: Scope, specialization: Specialization,
        methods: Collection<MethodSpecialization>, fields: Collection<FieldSpecialization>,
        headerOnly: Boolean
    ) {
        declareImport(classScope, specialization)
        specialization.push {
            appendSpecializationInfoComment()

            if (classScope.isObjectLike()) {
                appendStaticInstance(classScope, className)
            }

            appendClassFlags(classScope)
            appendClassPrefix(classScope, className)

            /*if (specialization.containsGenerics()) {
                appendTypeParams(classScope)
            }*/
            appendSuperTypes(classScope)

            appendClassBody(classScope, className, methods, fields, headerOnly)
        }
    }

    override fun appendStaticInstance(classScope: Scope, className: String) {
        builder.append("use lazy_static::lazy_static;\n")
        builder.append("use std::sync::{Mutex, MutexGuard};\n\n")
        builder.append("lazy_static! {\n")
        builder.append("  static ref ").append(OBJECT_FIELD_NAME)
            .append(": Mutex<").append(className)
            .append("> = Mutex::new(").append(className)
            .append("::new());\n")
        builder.append("}")
        nextLine()
        nextLine()
    }

    fun appendCreateEmptyInstance(classScope: Scope, className: String) {
        builder.append(className).append(" {")
        for (field in classScope.fields) {
            if (field.name == OBJECT_FIELD_NAME) continue
            if (!needsFieldByParameter(field.byParameter)) continue
            if (!needsBackingField(field)) continue

            builder.append(field.name).append(": ")
            appendDefaultValue(field.valueType!!)
            builder.append(", ")
        }
        builder.append("}")
    }

    override fun appendClassBody(
        classScope: Scope, className: String,
        methods: Collection<MethodSpecialization>, fields: Collection<FieldSpecialization>,
        headerOnly: Boolean
    ) {
        writeBlock {
            val allowFinalFields = !classScope.isValueType() &&
                    methods.any { it.method is Constructor }
            appendFields(classScope, fields, allowFinalFields, headerOnly)
        }
        nextLine()

        builder.append("impl ").append(className)
        writeBlock {
            if (classScope.isObjectLike()) {
                builder.append("pub fn get").append(OBJECT_FIELD_NAME)
                    .append("() -> MutexGuard<'static, ").append(className).append(">")
                writeBlock {
                    builder.append(OBJECT_FIELD_NAME)
                        .append(".lock().unwrap()")
                    nextLine()
                }
            }

            appendConstructors(classScope, className, methods, headerOnly)
            appendMethods(classScope, className, methods, headerOnly)
        }
    }

    override fun appendMethod(
        classScope: Scope, className: String,
        method0: MethodSpecialization, headerOnly: Boolean
    ) {
        super.appendMethod(classScope, className, method0, headerOnly)
        nextLine()
    }

    override fun appendMethodFlags(classScope: Scope, method: Method, headerOnly: Boolean) {
        // nothing to do yet
        builder.append("pub fn ")
    }

    override fun appendMethodHeader(
        classScope: Scope, className: String,
        method0: MethodSpecialization, headerOnly: Boolean
    ) {
        val method = method0.method as Method
        appendMethodFlags(classScope, method, headerOnly)

        appendTypeParameterDeclaration(method.typeParameters, classScope)

        builder.append(getMethodName(method0))

        assignSelfType(classScope, method)
        appendValueParameterDeclaration(method.selfTypeIfNecessary, method.valueParameters, classScope)

        builder.append(" -> ")
        val returnType = resolveType(method.returnType ?: Types.NullableAny)
        val isObject = returnType is ClassType && returnType.clazz.isObjectLike()
        if (isObject) builder.append("MutexGuard<'static, ")
        appendType(returnType, classScope, false)
        if (isObject) builder.append(">")
    }

    override fun appendBackingField(classScope: Scope, field: Field, allowFinal: Boolean, headerOnly: Boolean) {
        appendFieldFlags(classScope, field, allowFinal)

        var valueType = (field.valueType ?: Types.NullableAny)
        valueType = valueType.resolve(classScope)
        valueType = resolveType(valueType)

        appendFieldName(field)
        builder.append(": ")
        appendType(valueType, classScope, false)
        builder.append(',')
        nextLine()
    }

    override fun appendConstructorHeader(
        classScope: Scope, className: String,
        constructor: Constructor, headerOnly: Boolean
    ) {
        builder.append("fn new")
        appendValueParameterDeclaration1(constructor.valueParameters, classScope)
        builder.append(" -> Self")
        writeBlock {
            // todo assign all fields...
            builder.append("let mut obj = ")
            appendCreateEmptyInstance(classScope, "Self")
            builder.append(";")
            nextLine()
            builder.append("obj.__init__(")
            for (param in constructor.valueParameters) {
                if (!builder.endsWith("(")) builder.append(", ")
                builder.append(param.name)
            }
            builder.append(");")
            nextLine()
            builder.append("obj")
            nextLine()
        }
        nextLine()

        builder.append("fn __init__")
        appendValueParameterDeclaration(null, constructor.valueParameters, classScope)
    }

    fun appendDefaultValue(valueType: Type) {
        when (valueType) {
            Types.Boolean -> builder.append("false")
            in nativeTypes -> builder.append("0")
            else -> builder.append("null")
        }
    }

    fun appendValueParameterDeclaration1(
        valueParameters: List<Parameter>, scope: Scope
    ) {
        builder.append("(")
        for (param in valueParameters) {
            if (!builder.endsWith("(")) builder.append(", ")
            appendFieldName(param)
            builder.append(": ")
            appendType(param.type, scope, false)
        }
        builder.append(')')
    }

    override fun appendValueParameterDeclaration(
        selfTypeIfNecessary: Type?,
        valueParameters: List<Parameter>, scope: Scope
    ) {
        builder.append("(&mut self")
        if (selfTypeIfNecessary != null) {
            builder.append(", ")
            builder.append(" __self: ")
            appendType(selfTypeIfNecessary, scope, false)
        }
        for (param in valueParameters) {
            builder.append(", ")
            appendFieldName(param)
            builder.append(": ")
            appendType(param.type, scope, false)
        }
        builder.append(')')
    }

    override fun beginPackageDeclaration(
        packagePath: List<String>, file: File,
        imports: Map<String, List<String>>, nativeImports: Set<String>
    ) {
        appendPackageDeclaration(packagePath, file)
        appendImports(packagePath, imports)
    }

    override fun endPackageDeclaration(packagePath: List<String>, file: File) {
        /*if (packagePath.isNotEmpty()) {
            trimWhitespaceAtEnd()
            depth--
            nextLine()

            repeat(packagePath.size) { builder.append("}") }
            nextLine()
        }*/
    }

    override fun appendPackageDeclaration(packagePath: List<String>, file: File) {
        /*if (packagePath.isNotEmpty()) {
            for (part in packagePath) {
                builder.append("pub mod ").append(part).append(" {")
            }
            depth++
            nextLine()
        }*/
    }

    override fun appendImport(packagePath: List<String>, import: List<String>) {
        builder.append("use crate::") // internal imports start with "crate::"
        appendPath(import, "::")
        builder.append("::").append(import.last())
        builder.append(";")
        nextLine()
    }

    override fun filterImports(name: String, packageScope: Scope, headerOnly: Boolean) {
        // remove self-include
        imports.remove(name)
    }

    override fun appendDeclare(graph: SimpleGraph, expression: SimpleAssignment) {
        val dst = expression.dst
        builder.append("let ")
        appendFieldName(graph, dst)
        builder.append(": ")
        appendType(dst.type, expression.scope, false)
        builder.append(" = ")
    }

    override fun appendFieldName(
        graph: SimpleGraph,
        field: SimpleField,
        forFieldAccess: String
    ) {
        // avoid special C++ logic
        super.appendFieldName(graph, field, "")
        builder.append(forFieldAccess)
    }

    override fun appendGetObjectInstance(objectScope: Scope, exprScope: Scope) {
        if (objectScope == outsideClassLike(exprScope)) {
            builder.append("self")
        } else {
            appendType(objectScope.typeWithArgs, objectScope, false)
            builder.append("::get").append(OBJECT_FIELD_NAME).append("()")
        }
    }

    override fun appendObjectInstance(field: Field, exprScope: Scope, forFieldAccess: String) {
        appendGetObjectInstance(field.ownerScope, exprScope)
        builder.append(forFieldAccess)
    }

    override fun appendInstrImpl(graph: SimpleGraph, expr: SimpleInstruction) {
        when (expr) {
            is SimpleAllocateInstance -> {
                val needsReference = !expr.allocatedType.isValue()
                if (needsReference) {
                    // todo depending on container type, define allocation...
                    builder.append("gcNew<")
                    appendType(expr.allocatedType, expr.scope, true)
                    builder.append(">")
                    appendValueParams(graph, expr.paramsForLater)
                } else {
                    appendType(expr.allocatedType, expr.scope, true)
                    appendValueParams(graph, expr.paramsForLater)
                }
            }
            else -> super.appendInstrImpl(graph, expr)
        }
    }

    // todo container types:
    //  type with GC inside | potentially self inside -> GcCell<Option<Gc<X>>>
    //  value type -> X
    //  else -> RefCell<X>

    // extern crate libc;
    //
    // use libc::c_char;
    // use libc::c_int;
    //
    // use std::ffi::CString;

}