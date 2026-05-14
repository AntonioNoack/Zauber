package me.anno.generation.java

import me.anno.generation.*
import me.anno.generation.Specializations.specialization
import me.anno.generation.java.JavaSuperCallWriter.appendSuperCall
import me.anno.utils.ResetThreadLocal.Companion.threadLocal
import me.anno.zauber.Compile.root
import me.anno.zauber.SpecialFieldNames.OBJECT_FIELD_NAME
import me.anno.zauber.ast.FlagSet
import me.anno.zauber.ast.reverse.CodeReconstruction
import me.anno.zauber.ast.reverse.SimpleBranch
import me.anno.zauber.ast.reverse.SimpleLoop
import me.anno.zauber.ast.rich.*
import me.anno.zauber.ast.rich.Flags.hasFlag
import me.anno.zauber.ast.rich.expression.Expression
import me.anno.zauber.ast.rich.expression.constants.SpecialValue
import me.anno.zauber.ast.simple.*
import me.anno.zauber.ast.simple.ASTSimplifier.needsFieldByParameter
import me.anno.zauber.ast.simple.SimpleNode.Companion.isNullable
import me.anno.zauber.ast.simple.SimpleNode.Companion.needsCopy
import me.anno.zauber.ast.simple.controlflow.SimpleExit
import me.anno.zauber.ast.simple.controlflow.SimpleReturn
import me.anno.zauber.ast.simple.controlflow.SimpleThrow
import me.anno.zauber.ast.simple.expression.*
import me.anno.zauber.expansion.DependencyData
import me.anno.zauber.interpreting.ExternalKey
import me.anno.zauber.scope.Scope
import me.anno.zauber.scope.ScopeInitType
import me.anno.zauber.scope.ScopeType
import me.anno.zauber.typeresolution.ParameterList.Companion.resolveGenerics
import me.anno.zauber.typeresolution.ResolutionContext
import me.anno.zauber.types.Type
import me.anno.zauber.types.Types
import me.anno.zauber.types.impl.*
import me.anno.zauber.types.impl.arithmetic.NullType
import me.anno.zauber.types.impl.arithmetic.UnionType
import me.anno.zauber.types.impl.arithmetic.UnknownType
import me.anno.zauber.types.specialization.ClassSpecialization
import me.anno.zauber.types.specialization.FieldSpecialization
import me.anno.zauber.types.specialization.MethodSpecialization
import me.anno.zauber.types.specialization.Specialization
import java.io.File

/**
 * before generating JVM bytecode, create source code to be compiled with a normal Java compiler
 *  big difference: stack-based,
 *  small differences:
 *   - Java only supports one interface of each kind [-> can be solved by specialization :3]
 *   - Generics are always boxed, and overrides must be boxed, too [-> we just must be careful with declarations and reflections]
 *   - Java forbids duplicate field names in cascading method scopes [-> we must rename them]
 *   - fields into lambdas must be effectively final [-> must create wrapper objects of all local variables in these cases just like in C (unless inlined)]
 * */
open class JavaSourceGenerator : Generator() {

    companion object {

        val protectedJavaTypes by threadLocal {
            Types.run {
                mapOf(
                    String to BoxedType("java.lang.String", "java.lang.String"),
                    Boolean to BoxedType("Boolean", "boolean"),
                    Byte to BoxedType("Byte", "byte"),
                    Short to BoxedType("Short", "short"),
                    Int to BoxedType("Integer", "int"),
                    Long to BoxedType("Long", "long"),
                    Char to BoxedType("Character", "char"),
                    Float to BoxedType("Float", "float"),
                    Double to BoxedType("Double", "double"),
                    Any to BoxedType("Object", "Object"),

                    // what about these native types???
                    Array.withTypeParameter(Boolean) to BoxedType("Array_zauberBoolean", "boolean[]"),
                    Array.withTypeParameter(Byte) to BoxedType("Array_zauberByte", "byte[]"),
                    Array.withTypeParameter(Short) to BoxedType("Array_zauberShort", "short[]"),
                    Array.withTypeParameter(Int) to BoxedType("Array_zauberInt", "int[]"),
                    Array.withTypeParameter(Long) to BoxedType("Array_zauberLong", "long[]"),
                    Array.withTypeParameter(Char) to BoxedType("Array_zauberChar", "char[]"),
                    Array.withTypeParameter(Float) to BoxedType("Array_zauberFloat", "float[]"),
                    Array.withTypeParameter(Double) to BoxedType("Array_zauberDouble", "double[]"),
                )
            }
        }

        val nativeJavaTypes by threadLocal { protectedJavaTypes.filter { (_, it) -> it.boxed != it.native } }
        val nativeJavaNumbers by threadLocal { nativeJavaTypes - Types.Boolean }

        val registeredMethods by threadLocal { HashMap<ExternalKey, String>() }
        fun register(key: ExternalKey, implementation: String) {
            registeredMethods[key] = implementation
        }

        fun register(scope: Scope, name: String, valueParameterTypes: List<Type>, implementation: String) {
            register(ExternalKey(scope, name, valueParameterTypes), implementation)
        }

        fun resolveType(type: Type): Type {
            var type = type
            while (true) {
                try {
                    val resolved = type.resolve().specialize()
                    if (resolved == type) break
                    type = resolved
                } catch (e: Exception) {
                    e.printStackTrace()
                    break
                }
            }
            return type
        }

        fun isStoredField(field: Field): Boolean {
            return if (field.isObjectInstance()) false
            else needsFieldByParameter(field.byParameter) && needsBackingField(field)
        }

        fun needsBackingField(field: Field): Boolean {
            val getter = field.getter
            val setter = field.setter
            if (getter?.body == null || getter.body!!.needsBackingField(getter.scope)) return true
            if (setter?.body == null) return field.isMutable
            return setter.body!!.needsBackingField(setter.scope)
        }

    }

    open val protectedTypes: Map<ClassType, BoxedType> get() = protectedJavaTypes
    open val nativeTypes: Map<ClassType, BoxedType> get() = nativeJavaTypes
    open val nativeNumbers: Map<ClassType, BoxedType> get() = nativeJavaNumbers

    override fun generateCode(dst: File, data: DependencyData, mainMethod: Method) {
        val writer = FileWithImportsWriter(this, dst)
        try {

            defineNullableAnnotation(dst, writer)
            defineMainMethodCall(dst, writer, mainMethod)

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
        } finally {
            writer.finish()
        }
    }

    open fun defineNullableAnnotation(dst: File, writer: FileWithImportsWriter) {
        val file = File(dst, "org/jetbrains/annotations/Nullable.java")
        writer[file] = FileEntry("org.jetbrains.annotations".split('.'), this)
            .apply {
                content.append(
                    """
                import java.lang.annotation.*;
                        
                @Retention(RetentionPolicy.RUNTIME)
                @Target(ElementType.TYPE)
                public @interface Nullable {}
            """.trimIndent()
                )
            }
    }

    open fun getMainMethodFile(dst: File): File {
        return File(dst, "zauber/LaunchZauber.${getExtension(false)}")
    }

    open fun defineMainMethodCall(dst: File, writer: FileWithImportsWriter, mainMethod: Method) {
        appendGetObjectInstance(mainMethod.ownerScope, root)
        val className = builder.toString()
        builder.clear()

        writer[getMainMethodFile(dst)] = defineMainMethodCallEntry(dst, writer, mainMethod, className)
    }

    open fun defineMainMethodCallEntry(
        dst: File, writer: FileWithImportsWriter,
        mainMethod: Method, className: String
    ): FileEntry {
        val needsArgs = mainMethod.valueParameters.isNotEmpty()
        return FileEntry(listOf("zauber"), this)
            .apply {
                content.append(
                    """
                public class LaunchZauber {
                    public static void main(String[] args) {
                        $className.${mainMethod.name}(${if (needsArgs) "args" else ""});
                    }
                }
            """.trimIndent()
                )
            }
    }

    open fun createSpecializationSuffix(specialization: Specialization): String {
        if (specialization.isEmpty()) return ""
        // todo ensure the name is original, but keep it readable
        return "_${specialization.createUniqueName()}"
    }

    fun createFile(packageScope: Scope, name: String, dst: File, extension: String): File {
        return File(dst, packageScope.path.joinToString("/") + "/$name.$extension")
    }

    fun createClassName(scope: Scope, specialization: Specialization): String {
        return scope.name + createSpecializationSuffix(specialization)
    }

    fun createPackageName(scope: Scope, specialization: Specialization): String {
        return scope.name.capitalize() + "Kt" + createSpecializationSuffix(specialization)
    }

    open fun generateClassForScope(
        scope: Scope, dst: File, writer: FileWithImportsWriter, specialization: Specialization,
        methods: Collection<MethodSpecialization>, fields: Collection<FieldSpecialization>
    ) {
        val (name, packageScope) = getNameAndScope(scope, specialization)
        appendClass(name, scope, specialization, methods, fields, false)
        writeInto(packageScope, name, dst, writer, false)
    }

    fun getNameAndScope(
        scope: Scope, specialization: Specialization
    ): Pair<String, Scope> {
        val name: String
        val packageScope: Scope
        if (scope.scopeType == ScopeType.PACKAGE) {
            // we need some package helper
            name = createPackageName(scope, specialization)
            packageScope = scope
        } else {
            name = createClassName(scope, specialization)
            packageScope = scope.parent!!
        }
        return name to packageScope
    }

    open fun getExtension(headerOnly: Boolean): String {
        return "java"
    }

    /**
     * whether classes in this package are already included
     * */
    open fun filterImports(name: String, packageScope: Scope, headerOnly: Boolean) {
        imports.entries.removeIf { (name1, path) ->
            // these are automatically imported:
            name == name1 || path.subList(0, path.size - 1) == packageScope.path
        }
    }

    fun writeInto(
        packageScope: Scope,
        name: String,
        dst: File,
        writer: FileWithImportsWriter,
        headerOnly: Boolean
    ): File {
        val file = createFile(packageScope, name, dst, getExtension(headerOnly))
        filterImports(name, packageScope, headerOnly)
        val entry = writer[file]
        if (entry != null) {
            entry.content.append(builder); builder.clear()
            entry.imports.putAll(imports)
            entry.nativeImports.addAll(nativeImports)
            imports.clear()
            nativeImports.clear()
            writer[file] = entry
        } else {
            writer[file] = FileEntry(packageScope.path, this)
        }
        return file
    }

    open fun getClassType(scope: Scope): String {
        return when (scope.scopeType) {
            ScopeType.ENUM_CLASS -> "final class"
            ScopeType.NORMAL_CLASS -> {
                val isFinal = isFinal(scope.flags)
                if (isFinal) "final class" else "class"
            }
            ScopeType.INTERFACE -> "interface"
            ScopeType.OBJECT, ScopeType.COMPANION_OBJECT -> "final class"
            ScopeType.ENUM_ENTRY_CLASS -> "final class"
            ScopeType.PACKAGE -> "final class"
            else -> scope.scopeType.toString()
        }
    }

    fun appendSpecializationInfoComment() {
        if (specialization.isNotEmpty()) {
            comment {
                builder.append("Specialization: ")
                val params = specialization.typeParameters
                for (i in params.indices) {
                    if (!builder.endsWith(": ")) builder.append(", ")
                    builder.append(params.generics[i].name).append('=')
                    builder.append(params.getOrNull(i))
                }
                nextLine()
            }
        }
    }

    open fun appendClassFlags(scope: Scope) {
        builder.append("public ")

        if (scope.scopeType != ScopeType.INNER_CLASS &&
            scope.scopeType != ScopeType.PACKAGE &&
            scope.parent?.scopeType != ScopeType.PACKAGE
        ) {
            builder.append("static ")
        }

        if (scope.flags.hasFlag(Flags.ABSTRACT)) builder.append("abstract ")
    }

    fun appendClassPrefix(scope: Scope, className: String) {
        builder.append(getClassType(scope))
            .append(' ').append(className)
    }

    open fun appendStaticInstance(classScope: Scope, className: String) {
        builder.append("public static final ")
        builder.append(className).append(' ').append(OBJECT_FIELD_NAME)
            .append(" = new ").append(className).append("();")
        nextLine()
    }

    open fun appendClass(
        className: String, classScope: Scope, specialization: Specialization,
        methods: Collection<MethodSpecialization>, fields: Collection<FieldSpecialization>,
        headerOnly: Boolean
    ) {
        declareImport(classScope, specialization)
        specialization.use {
            appendSpecializationInfoComment()

            appendClassFlags(classScope)
            appendClassPrefix(classScope, className)

            if (specialization.containsGenerics()) {
                appendTypeParams(classScope)
            }
            appendSuperTypes(classScope)

            appendClassBody(classScope, className, methods, fields, headerOnly)
        }
    }

    open fun appendClassBody(
        classScope: Scope, className: String,
        methods: Collection<MethodSpecialization>,
        fields: Collection<FieldSpecialization>,
        headerOnly: Boolean
    ) {
        writeBlock {

            if (classScope.isObjectLike()) {
                appendStaticInstance(classScope, className)
            }

            val allowFinalFields = !classScope.isValueType() &&
                    methods.any { it.method is Constructor }

            appendFields(classScope, fields, allowFinalFields, headerOnly)
            appendConstructors(classScope, className, methods, headerOnly)
            appendMethods(classScope, className, methods, headerOnly)
        }
    }

    fun appendFields(
        classScope: Scope, fields: Collection<FieldSpecialization>, allowFinal: Boolean,
        headerOnly: Boolean
    ) {
        if (classScope.scopeType == ScopeType.INTERFACE) return // no backing fields
        for ((field) in fields) {
            if (isStoredField(field)) {
                appendBackingField(classScope, field, allowFinal, headerOnly)
            }
        }
    }

    open fun appendFieldFlags(classScope: Scope, field: Field, allowFinal: Boolean) {
        builder.append("public ")
        if (field == classScope.objectField) builder.append("static ")
        if (!field.isMutable && allowFinal) builder.append("final ")
    }

    open fun appendBackingField(classScope: Scope, field: Field, allowFinal: Boolean, headerOnly: Boolean) {
        appendFieldFlags(classScope, field, allowFinal)

        var valueType = (field.valueType ?: Types.NullableAny)
        valueType = valueType.resolve(classScope)
        valueType = resolveType(valueType)

        appendType(valueType, classScope, false)
        builder.append(' ')
        appendFieldName(field)
        builder.append(';')
        nextLine()
    }

    open fun appendMethods(
        classScope: Scope, className: String,
        methods: Collection<MethodSpecialization>,
        headerOnly: Boolean
    ) {
        for (method0 in methods) {
            val method = method0.method
            if (method !is Method) continue
            if (method.scope.parent != classScope) {
                // an inherited method -> skip, because it's already defined in the parent
                continue
            }

            try {
                appendMethod(classScope, className, method0, headerOnly)
            } catch (e: Exception) {
                comment { builder.append(e) }
                nextLine()
            }
        }
    }

    open fun appendConstructors(
        classScope: Scope, className: String,
        methods: Collection<MethodSpecialization>,
        headerOnly: Boolean
    ) {
        for ((constructor) in methods) {
            if (constructor !is Constructor) continue
            appendConstructor(classScope, className, constructor, headerOnly)
        }
    }

    fun isFinal(keywords: FlagSet): Boolean {
        return keywords.hasFlag(Flags.FINAL) || (
                !keywords.hasFlag(Flags.OPEN) &&
                        !keywords.hasFlag(Flags.OVERRIDE) &&
                        !keywords.hasFlag(Flags.ABSTRACT)
                )
    }

    open fun appendMethod(classScope: Scope, className: String, method0: MethodSpecialization, headerOnly: Boolean) {
        val (method, spec) = method0
        method as Method

        // some spacing
        nextLine()

        appendMethodHeader(classScope, className, method0, headerOnly)
        appendMethodBody(method, spec, headerOnly)
    }

    fun assignSelfType(classScope: Scope, method: Method) {
        val selfType = method.selfType
        val isBySelf = selfType == classScope.typeWithArgs ||
                method.flags.hasFlag(Flags.OVERRIDE) ||
                method.flags.hasFlag(Flags.ABSTRACT)

        val selfTypeIfNecessary = if (!isBySelf) selfType else null
        method.selfTypeIfNecessary = selfTypeIfNecessary
    }

    open fun appendMethodHeader(
        classScope: Scope,
        className: String,
        method0: MethodSpecialization,
        headerOnly: Boolean
    ) {
        val method = method0.method as Method
        appendMethodFlags(classScope, method, headerOnly)

        appendTypeParameterDeclaration(method.typeParameters, classScope)
        appendType(method.returnType ?: Types.NullableAny, classScope, false)

        builder.append(' ').append(getMethodName(method0))

        assignSelfType(classScope, method)
        appendValueParameterDeclaration(method.selfTypeIfNecessary, method.valueParameters, classScope)
    }

    open fun appendMethodBody(method: Method, spec: Specialization, headerOnly: Boolean) {
        val nativeImpl = getNativeImplementation(method)
        val body = method.body

        if (body != null) {
            val context = ResolutionContext(method.selfType, spec, true, null)
            appendCode(context, method, body, false)
        } else if (nativeImpl != null) {
            appendNativeImplementation(nativeImpl, method)
        } else {
            builder.append(";")
            nextLine()
        }
    }

    open fun appendNativeImplementation(nativeImpl: String, method: MethodLike) {
        writeBlock {
            builder.append(nativeImpl)
            builder.append(";")
            nextLine()
            appendReturnIfMissing(method)
        }
    }

    fun appendReturnIfMissing(method: MethodLike) {
        if ("return " !in builder) {
            builder.append("return ")
            appendGetObjectInstance(Types.Unit.clazz, method.scope)
            builder.append(";")
            nextLine()
        }
    }

    open fun getMethodName(method: MethodSpecialization): String {
        return getMethodName0(method)
    }

    fun getMethodName0(method: MethodSpecialization): String {
        val (method, specForName) = method
        return if (specForName.isNotEmpty()) {
            "${method.name}_${specForName.hash}"
        } else method.name
    }

    open fun appendMethodFlags(classScope: Scope, method: Method, headerOnly: Boolean) {

        if (method.flags.hasFlag(Flags.OVERRIDE)) {
            builder.append("@Override")
            nextLine()
        }

        if (method.flags.hasFlag(Flags.ABSTRACT) && classScope.scopeType != ScopeType.INTERFACE) {
            builder.append("abstract ")
        }

        val isPublic = classScope.scopeType != ScopeType.INTERFACE
        val isNative = method.flags.hasFlag(Flags.EXTERNAL)
        val isFinal = (classScope.scopeType != ScopeType.INTERFACE && isFinal(method.flags)) ||
                isNative || classScope.isObjectLike()
        val isDefault = classScope.scopeType == ScopeType.INTERFACE && method.body != null

        val nativeImpl = getNativeImplementation(method)

        if (isPublic) builder.append("public ")
        if (isNative && nativeImpl == null) builder.append("native ")
        if (isFinal) builder.append("final ")
        if (isDefault) builder.append("default ")

    }

    fun getNativeImplementation(method: Method): String? {
        val classScope = method.ownerScope
        return if (method.flags.hasFlag(Flags.EXTERNAL)) {
            val valueParameterTypes = method.valueParameters.map { it.type.resolve(classScope) }
            registeredMethods[ExternalKey(classScope, method.name, valueParameterTypes)]
        } else null
    }

    open fun appendConstructorFlags(classScope: Scope, constructor: Constructor, headerOnly: Boolean) {
        val visibility = if (classScope.isObject()) "private " else "public "
        builder.append(visibility)
    }

    open fun appendConstructor(classScope: Scope, className: String, constructor: Constructor, headerOnly: Boolean) {

        // some spacing
        nextLine()

        appendConstructorHeader(classScope, className, constructor, headerOnly)
        appendConstructorBody(classScope, className, constructor, headerOnly)
    }

    open fun appendConstructorBody(
        classScope: Scope,
        className: String,
        constructor: Constructor,
        headerOnly: Boolean
    ) {
        val body = constructor.body
        val context = ResolutionContext(constructor.selfType, true, null, emptyMap())

        writeBlock {
            val superCall = constructor.superCall
            if (superCall != null) {
                appendSuperCall(context, superCall)
            }

            if (body != null) {
                appendCode(context, constructor, body, true)
            }
        }
    }

    open fun appendConstructorHeader(
        classScope: Scope, className: String,
        constructor: Constructor, headerOnly: Boolean
    ) {
        appendConstructorFlags(classScope, constructor, headerOnly)
        builder.append(className)
        appendValueParameterDeclaration(null, constructor.valueParameters, classScope)
    }

    fun appendTypeParameterDeclaration(valueParameters: List<Parameter>, scope: Scope) {
        if (valueParameters.isEmpty()) return
        builder.append('<')
        for (param in valueParameters) {
            if (!builder.endsWith("<")) builder.append(", ")
            builder.append(param.name)
            if (param.type != Types.Any && param.type != Types.NullableAny) {
                builder.append(" extends ")
                appendType(param.type, scope, false)
            }
        }
        builder.append("> ")
    }

    open fun appendValueParameterDeclaration(
        selfTypeIfNecessary: Type?,
        valueParameters: List<Parameter>,
        scope: Scope
    ) {
        builder.append('(')
        if (selfTypeIfNecessary != null) {
            appendType(selfTypeIfNecessary, scope, false)
            builder.append(" __self")
        }
        for (param in valueParameters) {
            if (!builder.endsWith("(")) builder.append(", ")
            appendType(param.type, scope, false)
            builder.append(' ')
            appendFieldName(param)
        }
        builder.append(')')
    }

    open fun appendCode(context: ResolutionContext, method: MethodLike, body: Expression, skipSuperCall: Boolean) {
        writeBlock {
            try {
                val method1 = MethodSpecialization(method, context.specialization)
                val graph = ASTSimplifier.simplify(method1)
                if (skipSuperCall) graph.removeSuperCalls()

                CodeReconstruction.createCodeFromGraph(graph)

                for ((self, dst) in graph.thisFields) {
                    val (scope, explicit) = self
                    if (explicit) {
                        TODO("Somehow get explicit self...")
                    } else {
                        if (!scope.isObjectLike() && scope.isClassLike() && scope != method.scope && scope != method.ownerScope) {
                            TODO("Declare $self in $dst for $method")
                        } // else not needed
                    }
                }

                // todo simplify all entry points as methods...
                appendSimplifiedAST(graph, graph.startBlock)
            } catch (e: Throwable) {
                e.printStackTrace()
                comment {
                    builder.append(
                        "[${e.javaClass.simpleName}: ${e.stackTrace ?: "no-st"}] $body"
                            .replace("/*", "[")
                            .replace("*/", "]")
                    )
                }
                nextLine()
            }
        }
    }

    fun appendTypeParams(scope: Scope) {
        val typeParams = scope.typeParameters
        if (typeParams.isNotEmpty()) {
            builder.append('<')
            for ((i, param) in typeParams.withIndex()) {
                if (i > 0) builder.append(", ")
                builder.append(param.name)
                if (param.type != Types.NullableAny) {
                    builder.append(" extends ")
                    appendType(param.type, scope, true)
                }
            }
            builder.append('>')
        }
    }

    fun appendSuperTypes(scope: Scope) {
        try {
            val superCall0 = scope.superCalls.firstOrNull()
            if (superCall0 != null &&
                superCall0.isClassCall &&
                superCall0.type != Types.Any
            ) {
                val type = superCall0.type
                builder.append(" extends ")
                appendType(type, scope, true)
            }

            var implementsKeyword = if (scope.scopeType == ScopeType.INTERFACE) " extends " else " implements "
            for (superCall in scope.superCalls) {
                if (superCall.isInterfaceCall) {
                    val type = superCall.type
                    builder.append(implementsKeyword)
                    appendType(type, scope, true)
                    implementsKeyword = ", "
                }
            }
        } catch (e: Exception) {
            comment { builder.append(e) }
        }
    }

    fun declareImport(classScope: Scope, specialization: Specialization) {
        val length = builder.length
        appendClassType(classScope, specialization)
        builder.setLength(length)
    }

    val imports = HashMap<String, List<String>>()
    val nativeImports = LinkedHashSet<String>()

    open fun appendAssign(graph: SimpleGraph, expression: SimpleAssignment) {
        val dst = expression.dst
        if (dst.mergeInfo != null) {
            appendFieldName(graph, dst)
            builder.append(" = ")
        } else {
            appendDeclare(graph, expression)
        }
    }

    open fun appendDeclare(graph: SimpleGraph, expression: SimpleAssignment) {
        val dst = expression.dst
        builder.append("final ")
        appendType(dst.type, expression.scope, false)
        builder.append(' ')
        appendFieldName(graph, dst)
        builder.append(" = ")
    }

    fun SimpleField.isObjectLike() = type is ClassType && type.clazz.isObjectLike()

    fun SimpleField.isOwnerThis(graph: SimpleGraph): Boolean {
        return type is ClassType && type.clazz == graph.method.ownerScope &&
                graph.thisFields.any { !it.key.isExplicitSelf && it.value === this }
    }

    open fun appendGetObjectInstance(objectScope: Scope, exprScope: Scope) {
        appendType(objectScope.typeWithArgs, objectScope, false)
        builder.append('.').append(OBJECT_FIELD_NAME)
    }

    open fun appendFieldName(
        graph: SimpleGraph, field: SimpleField,
        forFieldAccess: String = ""
    ) {
        if (field.isObjectLike()) {
            val objectScope = (field.type as ClassType).clazz
            appendGetObjectInstance(objectScope, graph.method.scope)
        } else if (field.isOwnerThis(graph)) {
            builder.append("this")
        } else {
            var field = field
            while (true) {
                field = field.mergeInfo?.dst ?: break
            }
            builder.append("tmp").append(field.id)
        }
        builder.append(forFieldAccess)
    }

    open fun appendValueParams(graph: SimpleGraph, valueParameters: List<SimpleField>, withBrackets: Boolean = true) {
        if (withBrackets) builder.append('(')
        for (i in valueParameters.indices) {
            if (i > 0) builder.append(", ")
            val parameter = valueParameters[i]
            appendFieldName(graph, parameter)
        }
        if (withBrackets) builder.append(')')
    }

    // todo we have converted SimpleBlock into a complex graph,
    //  before we can use it, we must convert it back
    open fun appendSimplifiedAST(
        graph: SimpleGraph, expr: SimpleNode,
        // loop: SimpleLoop? = null
    ) {
        val instructions = expr.instructions
        for (i in instructions.indices) {
            val instr = instructions[i]
            appendSimplifiedAST(graph, instr /*loop*/)
            if (instr is SimpleAssignment &&
                instr.dst.type == Types.Nothing
            ) break
        }
        if (expr.branchCondition == null) {
            val next = expr.nextBranch
            if (next != null) {
                appendSimplifiedAST(graph, next)
            }
        } else {
            // todo this may or may not be simply be possible...
            // todo this may be a loop, branch, or similar...
            TODO("Jump to either branch")
        }
    }

    open fun appendInstrPrefix(graph: SimpleGraph, expr: SimpleInstruction) {
        if (expr is SimpleAssignment && expr.dst.type != Types.Nothing && !expr.dst.isObjectLike()) {
            val notNeeded = expr.dst.numReads == 0
            if (notNeeded) comment { appendAssign(graph, expr) }
            else appendAssign(graph, expr)
        }
    }

    open fun appendInstrSuffix(graph: SimpleGraph, expr: SimpleInstruction) {
        when (expr) {
            is SimpleCall -> {
                if (expr.sample !is Constructor) {
                    builder.append(";")
                }// else we only placed a comment
            }
            is SimpleAssignment,
            is SimpleSetField,
            is SimpleExit,
            is SimpleDeclaration -> builder.append(';')
            else -> {}
        }
        if (/*expr !is SimpleBlock &&*/ expr !is SimpleBranch) nextLine()
        if (expr is SimpleAssignment && expr.dst.type == Types.Nothing) {
            builder.append("throw new AssertionError(\"Unreachable\");")
            nextLine()
        }
    }

    open fun appendSimplifiedAST(
        graph: SimpleGraph, expr: SimpleInstruction,
        // loop: SimpleLoop? = null
    ) {
        if (expr is SimpleGetObject) return
        appendInstrPrefix(graph, expr)
        appendInstrImpl(graph, expr)
        appendInstrSuffix(graph, expr)
    }

    open fun appendInstrImpl(graph: SimpleGraph, expr: SimpleInstruction) {
        when (expr) {
            is SimpleBranch -> {
                builder.append("if (")
                appendFieldName(graph, expr.condition)
                builder.append(')')
                writeBlock {
                    appendSimplifiedAST(graph, expr.ifTrue)
                }
                trimWhitespaceAtEnd()
                builder.append(" else ")
                writeBlock {
                    appendSimplifiedAST(graph, expr.ifFalse)
                }
            }
            is SimpleLoop -> {
                builder.append("b").append(expr.body.blockId)
                builder.append(": while (true)")
                writeBlock {
                    appendSimplifiedAST(graph, expr.body)
                }
            }
            /*is SimpleGoto -> {
                if (expr.condition != null) {
                    builder.append("if (").append1(expr.condition).append(") ")
                }
                builder.append(if (expr.isBreak) "break" else "continue")
                if (expr.bodyBlock != loop?.body) {
                    builder.append(" b").append(expr.bodyBlock.blockId)
                }
                builder.append(';')
            }*/
            is SimpleDeclaration -> {
                appendType(expr.type, expr.scope, false)
                builder.append(' ').append(expr.name)
            }
            is SimpleString -> {
                builder.append('"').append(expr.base.value).append('"')
            }
            is SimpleNumber -> {
                // todo remove suffixes, that are not supported by Java
                //  and instead cast the value to the target
                builder.append(expr.base.value)
            }
            is SimpleGetField -> {
                appendSelfForFieldAccess(graph, expr.self, expr.field, expr.scope)
                appendFieldName(expr.field)
            }
            is SimpleSetField -> {
                appendSelfForFieldAccess(graph, expr.self, expr.field, expr.scope)
                appendFieldName(expr.field)
                builder.append(" = ")

                appendFieldName(graph, expr.value)

                val needsCopy = expr.value.type.needsCopy()
                if (needsCopy) appendCopy()
            }
            is SimpleCompare -> {
                appendFieldName(graph, expr.left)
                builder.append(' ')
                builder.append(expr.type.symbol).append(" 0")
            }
            is SimpleInstanceOf -> {
                // todo if type is ClassType, this is easy, else we need to build an expression...
                appendFieldName(graph, expr.value)
                builder.append(" instanceof ")
                appendType(expr.type, expr.scope, false)
            }
            is SimpleCheckEquals -> {
                // todo this could be converted into a SimpleBranch + SimpleCall
                // todo simple types use ==, while complex types use .equals()

                // todo if left cannot be null, skip null check
                // todo if left side is a native field, use the static class for comparison

                val leftCanBeNull = expr.left.type.isNullable()
                val rightCanBeNull = expr.right.type.isNullable()

                val leftNative = nativeTypes[expr.left.type]
                val rightNative = nativeTypes[expr.right.type]
                when {
                    leftNative != null && rightNative != null -> {
                        appendFieldName(graph, expr.left)
                        builder.append(" == ")
                        appendFieldName(graph, expr.right)
                    }
                    leftCanBeNull && rightCanBeNull -> {
                        appendFieldName(graph, expr.left)
                        builder.append(" == null ? ")
                        appendFieldName(graph, expr.right)
                        builder.append(" == null : ")
                        appendFieldName(graph, expr.left, ".")
                        builder.append("equals(")
                        appendFieldName(graph, expr.right)
                        builder.append(")")
                    }
                    leftCanBeNull -> {
                        appendFieldName(graph, expr.left)
                        builder.append(" != null && ")
                        appendFieldName(graph, expr.left, ".")
                        builder.append("equals(")
                        appendFieldName(graph, expr.right)
                        builder.append(")")
                    }
                    rightCanBeNull -> {
                        appendFieldName(graph, expr.right)
                        builder.append(" != null && ")
                        appendFieldName(graph, expr.left, ".")
                        builder.append("equals(")
                        appendFieldName(graph, expr.right)
                        builder.append(")")
                    }
                    else -> {
                        appendFieldName(graph, expr.left, ".")
                        builder.append("equals(")
                        appendFieldName(graph, expr.right)
                        builder.append(")")
                    }
                }
            }
            is SimpleCheckIdentical -> {
                appendFieldName(graph, expr.left)
                builder.append(" == ")
                appendFieldName(graph, expr.right)
            }
            is SimpleSpecialValue -> {
                when (expr.type) {
                    SpecialValue.TRUE -> builder.append("true")
                    SpecialValue.FALSE -> builder.append("false")
                    SpecialValue.NULL -> builder.append("null")
                }
            }
            is SimpleCall -> {
                if (expr.sample is Constructor) {
                    comment {
                        builder.append("new ")
                        // appendType(expr.dst.type, expr.scope, true)
                        appendValueParams(graph, expr.valueParameters)
                    }
                } else {
                    // Number.toX() needs to be converted to a cast
                    val methodName = expr.methodName
                    val done = when (expr.valueParameters.size) {
                        0 -> {
                            val castSymbol = when (methodName) {
                                "toInt" -> "(int) "
                                "toLong" -> "(long) "
                                "toFloat" -> "(float) "
                                "toDouble" -> "(double) "
                                "toByte" -> "(byte) "
                                "toShort" -> "(short) "
                                "toChar" -> "(char) "
                                else -> null
                            }
                            if (castSymbol != null && expr.self.type in nativeNumbers) {
                                builder.append(castSymbol)
                                appendFieldName(graph, expr.self)
                                true
                            } else if (expr.self.type == Types.Boolean && methodName == "not") {
                                builder.append('!')
                                appendFieldName(graph, expr.self)
                                true
                            } else false
                        }
                        1 -> {
                            val supportsType = when (expr.self.type) {
                                Types.String, in nativeTypes -> true
                                else -> false
                            }
                            val symbol = when (methodName) {
                                "plus" -> " + "
                                "minus" -> " - "
                                "times" -> " * "
                                "div" -> " / "
                                "rem" -> " % "
                                // compareTo is a problem for numbers:
                                //  we must call their static compare() function
                                "compareTo" -> "compare"
                                else -> null
                            }
                            if (supportsType && symbol != null) {
                                appendFieldName(graph, expr.self)
                                builder.append(symbol)
                                appendFieldName(graph, expr.valueParameters[0])
                                true
                            } else false
                        }
                        else -> false
                    }
                    if (!done) {
                        appendCallImpl(graph, expr)
                    }
                }
            }
            is SimpleAllocateInstance -> {
                // handled in SimpleCall, because only there do we have the value parameters
                builder.append("new ")
                appendType(expr.allocatedType, expr.scope, true)
                appendValueParams(graph, expr.paramsForLater)
            }
            is SimpleSelfConstructor -> {
                when (expr.isThis) {
                    true -> builder.append("this")
                    false -> builder.append("super")
                }
                appendValueParams(graph, expr.valueParameters)
            }
            is SimpleReturn -> {
                // todo cast if necessary
                builder.append("return ")
                appendFieldName(graph, expr.field)
            }
            is SimpleThrow -> {
                // todo cast if necessary
                builder.append("throw ")
                appendFieldName(graph, expr.field)
            }
            else -> {
                comment {
                    builder.append(expr.javaClass.simpleName).append(": ")
                        .append(expr)
                }
            }
        }
    }

    open fun appendCopy() {
        builder.append(".copy()")
    }

    open fun appendCallImpl(graph: SimpleGraph, expr: SimpleCall) {
        val needsCastForFirstValue = nativeTypes[expr.self.type]
        if (needsCastForFirstValue != null) {
            appendCallForPrimitive(needsCastForFirstValue, expr, graph)
        } else {
            appendFieldName(graph, expr.self, ".")
            val methodName = getMethodName(expr.methodSpec)
            builder.append(methodName)
            appendValueParams(graph, expr.valueParameters)
        }
    }

    open fun appendCallForPrimitive(
        needsCastForFirstValue: BoxedType, expr: SimpleCall,
        graph: SimpleGraph,
    ) {
        builder.append(needsCastForFirstValue.boxed).append('.')
        val methodName = getMethodName(expr.methodSpec)
        builder.append(methodName).append('(')
        appendFieldName(graph, expr.self)
        if (expr.valueParameters.isNotEmpty()) {
            builder.append(", ")
            appendValueParams(graph, expr.valueParameters, false)
        }
        builder.append(')')
    }

    fun outsideClassLike(scope: Scope): Scope? {
        var scope = scope
        while (true) {
            if (scope.isClassLike()) return scope
            scope = scope.parentIfSameFile ?: return null
        }
    }

    open fun appendObjectInstance(field: Field, exprScope: Scope, forFieldAccess: String) {
        if (field.ownerScope == outsideClassLike(exprScope)) {
            // if there is nothing dangerous in-between, we could use this.
            builder.append("this")
        } else {
            appendGetObjectInstance(field.ownerScope, exprScope)
        }
        builder.append(forFieldAccess)
    }

    open fun appendSelfForFieldAccess(graph: SimpleGraph, self: SimpleField, field: Field, exprScope: Scope) {
        if (self.type is ClassType && self.type.clazz.isObjectLike()) {
            appendObjectInstance(field, exprScope, ".")
        } else if (self.type is ClassType && !self.type.clazz.isClassLike()) {
            builder.append("/* ${field.ownerScope.pathStr} */ ")
        } else {
            val fieldSelfType = field.selfType
            val needsCast = self.type != fieldSelfType
            if (needsCast && fieldSelfType != null) {
                builder.append("((")
                appendType(fieldSelfType, exprScope, true)
                builder.append(')')
                appendFieldName(graph, self, ").")
            } else {
                appendFieldName(graph, self, ".")
            }
        }
    }

    open fun appendType(type: Type, scope: Scope, needsBoxedType: Boolean) {
        val type = resolveType(type)

        val protected = protectedTypes[type]
        // println("appending $type -> $protected, $needsBoxedType")
        if (protected != null) {
            builder.append(if (needsBoxedType) protected.boxed else protected.native)
            return
        }

        appendTypeImpl(type, scope, needsBoxedType)
    }

    open fun appendTypeImpl(type: Type, scope: Scope, needsBoxedType: Boolean) {
        when (type) {
            NullType -> {
                builder.append("Object ")
                comment { builder.append("null") }
            }
            Types.Nothing -> {
                builder.append("Object ")
                comment { builder.append("Nothing") }
            }
            is ClassType -> appendClassType(type, scope, needsBoxedType)
            is UnionType if type.types.size == 2 && NullType in type.types -> {
                // builder.append("@org.jetbrains.annotations.Nullable ")
                appendType(
                    type.types.first { it != NullType }, scope,
                    true /* native types cannot be null */
                )
                comment { builder.append("or null") }
            }
            is SelfType if (type.scope == scope) -> builder.append(scope.name)
            is ThisType -> appendType(type.type, scope, needsBoxedType)
            UnknownType -> builder.append('?')
            is LambdaType -> {
                val selfType = type.selfType
                builder.append("zauber.Function")
                    .append(type.parameters.size + if (selfType != null) 1 else 0)
                    .append('<')
                if (selfType != null) {
                    appendType(selfType, scope, true)
                    builder.append(", ")
                }
                for (param in type.parameters) {
                    appendType(param.type, scope, true)
                    builder.append(", ")
                }
                appendType(type.returnType, scope, true)
                builder.append('>')
            }
            is GenericType -> {
                val lookup = specialization[type]
                if (lookup != null) appendType(lookup, scope, needsBoxedType)
                else {
                    comment { builder.append(type.scope.pathStr) }
                    builder.append(type.name)
                }
            }
            is TypeOfField -> {
                val valueType = type.resolve()
                appendType(valueType, scope, needsBoxedType)
            }
            else -> {
                builder.append("Object ")
                comment {
                    builder.append(type)
                        .append(" (")
                        .append(type.javaClass.simpleName)
                        .append(')')
                }
            }
        }
    }

    fun appendClassType(type: ClassType, scope: Scope, needsBoxedType: Boolean) {

        if (type.clazz.scopeType == ScopeType.TYPE_ALIAS) {
            val newType0 = type.clazz.selfAsTypeAlias!!
            val newType = type.typeParameters.resolveGenerics(
                null, /* used for 'This'/'Self' */ newType0
            )
            appendType(newType, scope, needsBoxedType)
            return
        }

        val specialization = Specialization(type)
        appendClassType(type.clazz, specialization)
    }

    fun appendClassType(type: Scope, specialization: Specialization) {

        check(type.scopeType != ScopeType.TYPE_ALIAS)

        val className = createClassName(type, specialization)
        val path0 = type.parent!!.path + className

        val path1 = if (type.scopeType == ScopeType.PACKAGE) {
            val extraName = createPackageName(type, specialization)
            path0 + extraName
        } else path0

        appendClassName(path1)
    }

    fun appendClassName(path: List<String>) {
        val name = path.last()
        val existingImport = imports.getOrPut(name) { path }
        if (existingImport == path) {
            // good :)
            builder.append(name)
        } else {
            // duplicate path -> full path needed
            appendPath(path)
        }
    }

    fun appendFieldName(field: Field) {
        if (!field.ownerScope.isClassLike()) {
            // append("__").append(field.ownerScope.depth).append('_')
        }
        builder.append(field.name)
    }

    fun appendFieldName(field: Parameter) {
        // append("__").append(field.scope.depth).append('_')
        builder.append(field.name)
    }

    open fun appendPackageDeclaration(packagePath: List<String>, file: File) {
        builder.append("package ")
        appendPath(packagePath)
        builder.append(";\n\n")
    }

    open fun beginPackageDeclaration(
        packagePath: List<String>, file: File, imports: Map<String, List<String>>,
        nativeImports: Set<String>
    ) {
        appendPackageDeclaration(packagePath, file)
        appendImports(packagePath, imports)
    }

    open fun endPackageDeclaration(packagePath: List<String>, file: File) {
        // nothing to do in Java
    }

    open fun appendImports(packagePath: List<String>, imports: Map<String, List<String>>) {
        val importList = imports.values.sortedWith(ImportSorter)
        val position = builder.length
        for (import in importList) {
            appendImport(packagePath, import)
        }
        if (builder.length > position) nextLine()
    }

    fun appendPath(path: List<String>, separator: String = ".") {
        for (i in path.indices) {
            if (i > 0) builder.append(separator)
            builder.append(path[i])
        }
    }

    open fun appendImport(packagePath: List<String>, import: List<String>) {
        builder.append("import ")
        appendPath(import)
        builder.append(";\n")
    }

}