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
import me.anno.zauber.ast.simple.SimpleDeclaration
import me.anno.zauber.ast.simple.SimpleField
import me.anno.zauber.ast.simple.SimpleGraph
import me.anno.zauber.ast.simple.SimpleInstruction
import me.anno.zauber.ast.simple.expression.SimpleAllocateInstance
import me.anno.zauber.ast.simple.expression.SimpleAssignment
import me.anno.zauber.ast.simple.expression.SimpleCall
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

        val refCellImport = "std::cell::RefCell".split("::")
        val gcCellImport = "gc::GcCell".split("::")
        val gcImport = "gc::Gc".split("::")

        val libImports = listOf(
            refCellImport,
            gcCellImport,
            gcImport
        )

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

    override fun appendClassFlags(scope: Scope) {
        if (scope.isValueType() && RustOwnership[scope.typeWithArgs].isImmutable) {
            builder.append("#[derive(Copy,Clone)]") // simple mem-copy
            nextLine()
        } else {
            builder.append("#[derive(Clone)]") // maybe complicated copy
            nextLine()
        }
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
            if (field.name == OBJECT_FIELD_NAME || !isStoredField(field)) continue

            builder.append(field.name).append(": ")
            val type = field.valueType!!
            appendDefaultValue(type)
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
        method.scope[ScopeInitType.CODE_GENERATION]

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

    override fun appendFieldFlags(classScope: Scope, field: Field, allowFinal: Boolean) {
        builder.append("pub ")
    }

    override fun appendBackingField(classScope: Scope, field: Field, allowFinal: Boolean, headerOnly: Boolean) {
        appendFieldFlags(classScope, field, allowFinal)

        var valueType = (field.valueType ?: Types.NullableAny)
        valueType = valueType.resolve(classScope)
        valueType = resolveType(valueType)

        appendFieldName(field)
        builder.append(": ")
        appendTypeDecl(valueType, classScope)
        builder.append(',')
        nextLine()
    }

    fun appendTypeDecl(valueType: Type, classScope: Scope) {
        val ownership = getOwnership(valueType)
        builder.append(ownership.typePrefix)
        appendType(valueType, classScope, false)
        builder.append(ownership.typeSuffix)
    }

    override fun appendConstructorHeader(
        classScope: Scope, className: String,
        constructor: Constructor, headerOnly: Boolean
    ) {
        constructor.scope[ScopeInitType.CODE_GENERATION]

        builder.append("pub fn new")
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

    fun appendValueParameterDeclaration1(
        valueParameters: List<Parameter>, scope: Scope
    ) {
        builder.append("(")
        for (param in valueParameters) {
            if (!builder.endsWith("(")) builder.append(", ")
            appendFieldName(param)
            builder.append(": ")
            appendTypeDecl(resolveType(param.type), scope)
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
            val ownership = getOwnership(selfTypeIfNecessary)
            builder.append(ownership.typePrefix)
            appendType(selfTypeIfNecessary, scope, false)
            builder.append(ownership.typeSuffix)
        }
        for (param in valueParameters) {
            builder.append(", ")
            appendFieldName(param)
            builder.append(": ")
            appendTypeDecl(param.type, scope)
        }
        builder.append(')')
    }

    fun getOwnership(type: Type): RustOwnershipType {
        val ownership = RustOwnership[resolveType(type)]
        if (ownership.isFlatMutable) imports["RefCell"] = refCellImport
        if (ownership.isDeepMutable) {
            imports["GcCell"] = gcCellImport
            imports["Gc"] = gcImport
        }
        return ownership
    }

    override fun beginPackageDeclaration(
        packagePath: List<String>, file: File,
        imports: Map<String, List<String>>, nativeImports: Set<String>
    ) {
        appendImports(packagePath, imports)
    }

    override fun endPackageDeclaration(packagePath: List<String>, file: File) {
        // nothing to do
    }

    override fun appendImport(packagePath: List<String>, import: List<String>) {
        builder.append("use ")
        val fromZauber = import !in libImports
        if (fromZauber) {
            // internal imports start with "crate::"
            builder.append("crate::")
        }
        appendPath(import, "::")
        if (fromZauber) {
            // name must appear twice, once for the file, once for the class
            builder.append("::").append(import.last())
        }
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
        if (dst.type !in nativeTypes) builder.append("mut ")
        appendFieldName(graph, dst)
        builder.append(": ")
        appendTypeDecl(dst.type, expression.scope)
        builder.append(" = ")
    }

    override fun appendFieldName(
        graph: SimpleGraph,
        field: SimpleField,
        forFieldAccess: String
    ) {
        if (field.isObjectLike()) {
            val objectType = (field.type as ClassType).clazz
            appendGetObjectInstance(objectType, graph.method.scope)
            builder.append(forFieldAccess)
        } else if (field.isOwnerThis(graph)) {
            builder.append("self").append(forFieldAccess)
        } else {
            var field = field
            while (true) {
                field = field.mergeInfo?.dst ?: break
            }
            builder.append("tmp").append(field.id)
            when(forFieldAccess) {
                "" -> {}
                "." -> {
                    val ownership = getOwnership(field.type)
                    builder.append(ownership.callPrefix)
                    builder.append(forFieldAccess)
                }
            }
        }
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
                val ownership = getOwnership(expr.allocatedType)
                builder.append(ownership.allocPrefix)
                appendType(expr.allocatedType, expr.scope, true)
                builder.append("::new")
                appendValueParams(graph, expr.paramsForLater)
                builder.append(ownership.allocSuffix)
            }
            is SimpleDeclaration -> {
                val type = expr.type
                builder.append("let ")
                builder.append(expr.name).append(": ")
                appendTypeDecl(type, expr.scope)
            }
            else -> super.appendInstrImpl(graph, expr)
        }
    }

    fun appendDefaultValue(valueType: Type) {
        when (resolveType(valueType)) {
            Types.Boolean -> builder.append("false")
            in nativeTypes -> builder.append("0")
            else -> builder.append("()")
        }
    }

    override fun appendCopy() {
        // not necessary
    }

    override fun appendCallForPrimitive(needsCastForFirstValue: BoxedType, expr: SimpleCall, graph: SimpleGraph) {
        // ensure import
        val selfType = expr.self.type
        val position = builder.length
        appendType(selfType, expr.scope, true)
        builder.setLength(position)

        builder.append(needsCastForFirstValue.boxed).append("::new(")
        appendFieldName(graph, expr.self)
        builder.append(").")
        builder.append(getMethodName(expr.methodSpec))
        appendValueParams(graph, expr.valueParameters)
    }

    fun Type.isObjectLike() = this is ClassType && clazz.isObjectLike()

    override fun appendCallImpl(graph: SimpleGraph, expr: SimpleCall) {
        val needsCastForFirstValue = nativeTypes[expr.self.type]
        if (needsCastForFirstValue != null) {
            appendCallForPrimitive(needsCastForFirstValue, expr, graph)
        } else {
            appendFieldName(graph, expr.self, "")
            if (!expr.self.type.isObjectLike()) {
                val ownership = RustOwnership[expr.self.type]
                builder.append(ownership.callPrefix)
            }
            builder.append(".")
            val methodName = getMethodName(expr.methodSpec)
            builder.append(methodName)
            appendValueParams(graph, expr.valueParameters)
        }
    }

}