package me.anno.generation.rust

import me.anno.generation.BoxedType
import me.anno.generation.FileEntry
import me.anno.generation.FileWithImportsWriter
import me.anno.generation.Specializations.specialization
import me.anno.generation.c.CSourceGenerator
import me.anno.utils.ResetThreadLocal.Companion.threadLocal
import me.anno.zauber.SpecialFieldNames.OBJECT_FIELD_NAME
import me.anno.zauber.ast.reverse.SimpleBranch
import me.anno.zauber.ast.reverse.SimpleLoop
import me.anno.zauber.ast.rich.expression.Expression
import me.anno.zauber.ast.rich.expression.constants.NumberExpression
import me.anno.zauber.ast.rich.member.Constructor
import me.anno.zauber.ast.rich.member.Field
import me.anno.zauber.ast.rich.member.Method
import me.anno.zauber.ast.rich.parameter.Parameter
import me.anno.zauber.ast.simple.*
import me.anno.zauber.ast.simple.expression.SimpleAllocateInstance
import me.anno.zauber.ast.simple.expression.SimpleCall
import me.anno.zauber.ast.simple.expression.SimpleSetField
import me.anno.zauber.expansion.DependencyData
import me.anno.zauber.scope.Scope
import me.anno.zauber.scope.ScopeInitType
import me.anno.zauber.typeresolution.ParameterList.Companion.emptyParameterList
import me.anno.zauber.typeresolution.ResolutionContext
import me.anno.zauber.types.Specialization
import me.anno.zauber.types.Type
import me.anno.zauber.types.Types
import me.anno.zauber.types.impl.ClassType
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
                    Boolean to BoxedType("Boolean", "bool"),
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
        val gcTraceImport = "gc::Trace".split("::")
        val gcFinalizeImport = "gc::Finalize".split("::")
        val mutexGuardImport = "std::sync::MutexGuard".split("::")

        val libImports = listOf(
            refCellImport,
            gcCellImport,
            gcImport,
            gcTraceImport,
            gcFinalizeImport,// must be defined, when we have no own finalize implementation
            mutexGuardImport,
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

            generateCodeImpl(dst, data, writer)

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
        val spec = Specialization(mainMethod.scope, emptyParameterList())
        val methodName = getMethodName(spec)
        return FileEntry(emptyList(), this)
            .apply {
                // todo convert argc/argv to String-array, if needed
                // include all imports
                content.append(writer[File(dst, "mod.rs")]!!.content)
                content.append(
                    "\n" + """
                fn main() {
                    $className.$methodName(${if (needsArgs) "std::env::args()" else ""});
                }
            """.trimIndent()
                )
            }
    }

    override fun appendClassFlags(scope: Scope) {
        val ownership = RustOwnership[resolveType(scope.typeWithArgs)]
        if (scope.isValueType() && ownership.isImmutable) {
            builder.append("#[derive(Copy,Clone)]") // simple mem-copy
            nextLine()
        } else if (scope.isObjectLike() ||
            ownership.isDeepMutable ||
            scope.typeWithArgs in nativeTypes
        ) {
            builder.append("#[derive(Clone)]") // maybe complicated copy
            nextLine()
        } else {
            imports["Trace"] = gcTraceImport
            imports["Finalize"] = gcFinalizeImport
            builder.append("#[derive(Trace, Finalize, Clone)]") // maybe complicated copy
            nextLine()
        }
    }

    override fun appendClass(
        className: String, classScope: Scope, specialization: Specialization,
        methods: Collection<Specialization>, fields: Collection<Specialization>,
        headerOnly: Boolean
    ) {
        declareImport(classScope, specialization)
        specialization.use {
            // todo writing the class must be done in two phases like for C++:
            //  traits and structs
            //  traits describe open class or interfaces
            //  structs define classes

            appendSpecializationInfoComment()

            if (classScope.isObjectLike()) {
                appendStaticInstance(classScope, className)
            }

            appendClassFlags(classScope)
            builder.append("pub struct ").append(className)
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
            nextLine()

            if (classScope.isOpen() || classScope == Types.Any.clazz) {
                // todo only if a class actually overrides it?
                builder.append("pub trait ").append(className).append(traitSuffix)
                writeBlock {
                    // todo add all open methods
                }
                nextLine()
            }

            for (call in classScope.superCalls) {
                val superScope = call.type.clazz
                val superClassName = getClassName(superScope, specialization.withScope(superScope)) + traitSuffix
                // we have to import it...
                imports[superClassName] = superScope.path.dropLast(1) + superClassName
                builder.append("impl ")
                    .append(superClassName)
                    .append(" for ").append(className)
                writeBlock {
                    // todo add all methods
                }
                nextLine()
            }
        }
    }

    override fun appendArrayContentField(classScope: Scope, headerOnly: Boolean) {
        val elementType = specialization.typeParameters[0]
        builder.append("content: Vec<")
        appendType(elementType, classScope, false)
        builder.append(">,")
        nextLine()
    }

    override fun appendArrayGetter(method0: Specialization) {
        writeBlock {
            builder.append("return self.content[index as usize];")
            nextLine()
        }
    }

    override fun appendArraySetter(method0: Specialization) {
        writeBlock {
            builder.append("self.content[index as usize] = value;")
            nextLine()

            imports["MutexGuard"] = mutexGuardImport

            builder.append("return ")
            appendGetObjectInstance(Types.Unit.clazz, method0.method.memberScope)
            builder.append(';')
            nextLine()
        }
    }

    /*override fun getClassName(scope: Scope, specialization: Specialization): String {
        return toUpperCamelCase(super.getClassName(scope, specialization))
    }*/

    var cleanRustNames = true

    fun toUpperCamelCase(s: String): String {
        if (!cleanRustNames) return s

        val b = StringBuilder(s.length)
        for (i in s.indices) {
            val c = s[i]
            if (c == '_') continue
            val capitalize = i == 0 || s[i - 1] == '_'
            b.append(if (capitalize) c.uppercaseChar() else c)
        }
        if (b.isEmpty()) b.append('_')
        return b.toString()
    }

    val traitSuffix = "Trait"

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
        builder.append(className).append(" { ")
        for (field in classScope.fields) {
            if (field.name == OBJECT_FIELD_NAME || !isStoredField(field)) continue

            builder.append(field.newName).append(": ")
            val type = field.valueType!!
            appendDefaultValue(type)
            builder.append(", ")
        }
        if (classScope == Types.Array.clazz) {
            builder.append("content: Vec::with_capacity(size), ")
        }
        if (builder.endsWith(", ")) builder.setLength(builder.length - 2)
        builder.append(" }")
    }

    override fun appendMethod(
        classScope: Scope, className: String,
        method0: Specialization, headerOnly: Boolean
    ) {
        super.appendMethod(classScope, className, method0, headerOnly)
        nextLine()
    }

    override fun appendMethodFlags(classScope: Scope, method0: Specialization, headerOnly: Boolean) {
        // nothing to do yet
        builder.append("pub fn ")
    }

    override fun appendMethodHeader(
        classScope: Scope, className: String,
        method0: Specialization, headerOnly: Boolean
    ) {
        val method = method0.method as Method
        method.scope[ScopeInitType.CODE_GENERATION]

        appendMethodFlags(classScope, method0, headerOnly)

        appendTypeParameterDeclaration(method.typeParameters, classScope)

        builder.append(getMethodName(method0))

        assignSelfType(classScope, method)
        appendValueParameterDeclaration(method.selfTypeIfNecessary, method.valueParameters, classScope)

        builder.append(" -> ")
        val returnType = resolveType(method.resolveReturnType(method0))
        val isObject = returnType is ClassType && returnType.clazz.isObjectLike()
        if (isObject) builder.append("MutexGuard<'static, ")
        appendType(returnType, classScope, false)
        if (isObject) builder.append(">")
    }

    override fun appendCode(
        context: ResolutionContext,
        method1: Specialization,
        body: Expression,
        skipSuperCall: Boolean
    ) {
        writeBlock {
            val graph = ASTSimplifier.simplify(method1)
            if (skipSuperCall) graph.removeSuperCalls()
            prepareGraph(graph)

            scanSelves(graph, method1.method)

            // todo simplify all entry points as methods...

            val pos0 = builder.length
            appendSimpleBlock(graph, graph.startBlock)

            appendMissingDeclarations(graph, pos0)
        }
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
            //hack!
            if (builder.endsWith(traitSuffix)) {
                builder.setLength(builder.length - traitSuffix.length)
            }
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

    override fun appendDeclare(graph: SimpleGraph, dst: SimpleField, scope: Scope, withEquals: Boolean) {
        builder.append("let ")
        if (dst.type !in nativeTypes) builder.append("mut ")
        appendFieldName(graph, dst)
        builder.append(": ")
        appendTypeDecl(dst.type, scope)
        if (withEquals) builder.append(" = ")
    }

    override fun appendLocalDeclaration(graph: SimpleGraph, field: Field) {
        val valueType = field.valueType!!
        builder.append("let ")
        if (field.isMutableEx || field.name.startsWith('$') /* todo declare them everywhere (DeclareExpression)  */)
            builder.append("mut ")
        builder.append(field.newName)
        builder.append(": ")
        appendTypeDecl(valueType, graph.method.memberScope)
        //builder.append(" = ")
        //appendDefaultValue(valueType)
        builder.append(";")
        nextLine()
    }

    override fun appendFieldName(
        graph: SimpleGraph,
        field: SimpleField,
        forFieldAccess: String
    ) {
        if (field.isOwnerThis(graph)) {
            builder.append("self").append(forFieldAccess)
        } else if (field.isObjectLike()) {
            val objectType = (field.type as ClassType).clazz
            appendGetObjectInstance(objectType, graph.method.scope)
            builder.append(forFieldAccess)
        } else {
            val field = field.dst
            when (val expr = field.constantRef) {
                is NumberExpression -> appendNumber(field.type, expr)
                null -> {
                    check(field.id >= 0) { "Invalid field $field in $graph" }
                    builder.append("tmp").append(field.id)
                    usedFields.add(field)
                }
                else -> throw NotImplementedError("Append constant field $expr")
            }
            when (forFieldAccess) {
                "" -> {}
                "." -> {
                    val ownership = getOwnership(field.type)
                    builder.append(ownership.callPrefix)
                    builder.append(forFieldAccess)
                }
            }
        }
    }

    override fun appendArrayContentInitialization(constructor: Constructor) {
        // todo we need to somehow get a null/zero element...
        val sizeName = constructor.valueParameters[0].name
        builder.append("for i in 0..$sizeName { self.content.push(0); }")
        nextLine()
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
                if (expr.field.isMutableEx) builder.append("mut ")
                builder.append(expr.name).append(": ")
                appendTypeDecl(type, expr.scope)
                declaredLocalFields += expr.field
            }
            is SimpleBranch -> {
                builder.append("if ")
                appendFieldName(graph, expr.condition)
                builder.append(' ')
                writeBlock {
                    appendSimpleBlock(graph, expr.ifTrue)
                }
                trimWhitespaceAtEnd()
                builder.append(" else ")
                writeBlock {
                    appendSimpleBlock(graph, expr.ifFalse)
                }
            }
            is SimpleLoop -> {
                check(expr.condition == null) { "Loop with condition not yet implemented" }
                // builder.append("'b").append(expr.body.blockId).append(": ") // todo check if we use the label anywhere
                builder.append("loop ")
                writeBlock {
                    appendSimpleBlock(graph, expr.body)
                }
            }
            else -> super.appendInstrImpl(graph, expr)
        }
    }

    override fun appendDefaultValue(valueType: Type) {
        when (resolveType(valueType)) {
            Types.Boolean -> builder.append("false")
            in nativeTypes -> builder.append("0")
            else -> builder.append("()")
        }
    }

    override fun appendCopy(graph: SimpleGraph, expr: SimpleSetField) {
        // not necessary, this is done by the owner-ship types
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
        builder.append(getMethodName(expr.specialization))
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
            val methodName = getMethodName(expr.specialization)
            builder.append(methodName)
            appendValueParams(graph, expr.valueParameters)
        }
    }

}