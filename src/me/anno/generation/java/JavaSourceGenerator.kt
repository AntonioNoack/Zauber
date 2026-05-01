package me.anno.generation.java

import me.anno.generation.Generator
import me.anno.generation.Specializations.specialization
import me.anno.generation.Specializations.specializations
import me.anno.generation.java.JavaBuilder.appendType
import me.anno.generation.java.JavaExpressionWriter.appendSuperCall
import me.anno.generation.java.JavaSimplifiedASTWriter.appendSimplifiedAST
import me.anno.generation.java.JavaSimplifiedASTWriter.imports
import me.anno.utils.ResetThreadLocal.Companion.threadLocal
import me.anno.zauber.ast.FlagSet
import me.anno.zauber.ast.reverse.CodeReconstruction
import me.anno.zauber.ast.rich.*
import me.anno.zauber.ast.rich.Flags.hasFlag
import me.anno.zauber.ast.rich.expression.Expression
import me.anno.zauber.ast.simple.ASTSimplifier
import me.anno.zauber.ast.simple.ASTSimplifier.needsFieldByParameter
import me.anno.zauber.expansion.DependencyData
import me.anno.zauber.scope.Scope
import me.anno.zauber.scope.ScopeInitType
import me.anno.zauber.scope.ScopeType
import me.anno.zauber.typeresolution.ResolutionContext
import me.anno.zauber.types.Type
import me.anno.zauber.types.Types
import me.anno.zauber.types.impl.ClassType
import me.anno.zauber.types.specialization.ClassSpecialization
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
object JavaSourceGenerator : Generator() {

    val protectedTypes by threadLocal {
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
                Any to BoxedType("Object", "Object")
            )
        }
    }

    val nativeTypes by threadLocal { protectedTypes.filter { (_, it) -> it.boxed != it.native } }
    val nativeNumbers by threadLocal { nativeTypes - Types.Boolean }

    // todo we need to add these for every constructor and super call,
    //  we need specialized methods for every generic method (incl. getters and setters)

    private val blacklistedPaths = listOf(listOf("java"))

    override fun generateCode(dst: File, data: DependencyData) {
        val writer = JavaWriter(dst)
        try {
            val classes = data.createdClasses
            val methods = data.calledMethods

            defineNullableAnnotation(dst, writer)
            val methodsByClass = methods.groupBy {
                it.method.ownerScope.typeWithArgs
                    .specialize(it.specialization)
            }

            val remainingClasses = HashSet(classes)
            for ((clazz, methods) in methodsByClass.entries) {
                clazz as ClassType
                remainingClasses.remove(ClassSpecialization(clazz))
                val classSpec = methods.first().specialization
                generate(clazz.clazz, dst, writer, classSpec, methods)
            }
            for (clazz in remainingClasses) {
                generate(clazz.clazz, dst, writer, clazz.specialization, emptyList())
            }
        } finally {
            writer.finish()
        }
    }

    private fun defineNullableAnnotation(dst: File, writer: JavaWriter) {
        val file = File(dst, "org/jetbrains/annotations/Nullable.java")
        writer[file] = JavaEntry("org.jetbrains.annotations")
            .apply {
                content.append(
                    """
                @Retention(RetentionPolicy.RUNTIME)
                @Target(ElementType.TYPE)
                public @interface Nullable {}
            """.trimIndent()
                )
                imports.add("java.lang.annotation.*")
            }
    }

    fun createSpecializationSuffix(specialization: Specialization): String {
        if (specialization.isEmpty()) return ""
        // todo ensure the name is original, but keep it readable
        return "_${specialization.createUniqueName()}"
    }

    fun createFile(packageScope: Scope, name: String, dst: File): File {
        return File(dst, packageScope.path.joinToString("/") + "/$name.java")
    }

    fun createClassName(scope: Scope, specialization: Specialization): String {
        return scope.name + createSpecializationSuffix(specialization)
    }

    fun createPackageName(scope: Scope, specialization: Specialization): String {
        return scope.name.capitalize() + "Kt" + createSpecializationSuffix(specialization)
    }

    fun generate(
        scope: Scope, dst: File, writer: JavaWriter, specialization: Specialization,
        methods: Collection<MethodSpecialization>
    ) {
        scope[ScopeInitType.CODE_GENERATION]
        check(scope.isClassLike()) {
            "Expected $scope to be class-like, but got ${scope.scopeType}"
        }
        if (scope.scopeType == ScopeType.PACKAGE) {

            // we need some package helper
            val name = createPackageName(scope, specialization)
            generateInside(name, scope, specialization, methods)
            writeInto(scope, name, dst, writer)

        } else {

            val name = createClassName(scope, specialization)
            generateInside(name, scope, specialization, methods)
            writeInto(scope.parent!!, name, dst, writer)

        }
    }

    fun writeInto(packageScope: Scope, name: String, dst: File, writer: JavaWriter) {
        val file = createFile(packageScope, name, dst)
        val entry = writer[file] ?: JavaEntry(packageScope.pathStr)
        entry.content.append(builder); builder.clear()
        entry.imports.addAll(imports.map { it.pathStr })
        imports.clear()
        writer[file] = entry
    }

    fun writeBlock(run: () -> Unit) {
        if (builder.isNotEmpty() && builder.last() != ' ') builder.append(' ')
        builder.append("{")

        depth++
        nextLine()

        try {
            run()

            if (builder.endsWith("  ")) {
                builder.setLength(builder.length - 2)
            }

            depth--
            builder.append("}\n")
            indent()
            depth++

        } finally {
            depth--
        }
    }

    fun generateInside(
        className: String, scope: Scope, specialization: Specialization,
        methods: Collection<MethodSpecialization>
    ) {

        specializations.add(specialization)

        if (specialization.isNotEmpty()) {
            builder.append("// Specialization: ")
            val params = specialization.typeParameters
            for (i in params.indices) {
                if (!builder.endsWith(": ")) builder.append(", ")
                builder.append(params.generics[i].name).append('=')
                builder.append(params.getOrNull(i))
            }
            builder.append('\n')
        }

        builder.append("public ")

        if (scope.scopeType != ScopeType.INNER_CLASS &&
            scope.scopeType != ScopeType.PACKAGE &&
            scope.parent?.scopeType != ScopeType.PACKAGE
        ) {
            builder.append("static ")
        }

        val javaType = when (scope.scopeType) {
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
        if (scope.flags.hasFlag(Flags.ABSTRACT)) builder.append("abstract ")
        builder.append(javaType).append(' ').append(className)

        if (specialization.isEmpty()) {
            appendTypeParams(scope)
        }
        appendSuperTypes(scope)

        writeBlock {

            if (scope.isObject()) {
                builder.append("public static final ")
                builder.append(className).append(" __instance__ = new ")
                builder.append(className).append("();")
                nextLine()
                nextLine()
            }

            appendFields(scope)
            appendConstructors(scope, className, methods)
            appendMethods(scope, methods)

            @Suppress("Since15")
            specializations.removeLast()
        }
    }

    private fun appendFields(classScope: Scope) {
        if (classScope.scopeType == ScopeType.INTERFACE) return // no backing fields
        val fields = classScope.fields
        for (field in fields) {
            if (field.selfType != classScope.typeWithArgs) continue
            if (!needsFieldByParameter(field.byParameter)) continue
            if (!needsBackingField(field)) continue
            appendBackingField(classScope, field)
        }
    }

    private fun needsBackingField(field: Field): Boolean {
        val getter = field.getter
        val setter = field.setter
        if (getter?.body == null || getter.body!!.needsBackingField(getter.scope)) return true
        if (setter?.body == null) return field.isMutable
        return setter.body!!.needsBackingField(setter.scope)
    }

    private fun appendBackingField(classScope: Scope, field: Field) {
        builder.append("public ")
        if (field == classScope.objectField) builder.append("static ")
        if (!field.isMutable) builder.append("final ")
        val valueType = (field.valueType ?: Types.NullableAny).resolve(classScope)
        appendType(valueType, classScope, false)
        builder.append(' ').append(field.name).append(';')
        nextLine()
    }

    private fun appendMethods(classScope: Scope, methods: Collection<MethodSpecialization>) {
        for ((method, spec) in methods) {
            if (method !is Method) continue
            if (method.scope.parent != classScope) {
                // an inherited method -> skip, because it's already defined in the parent
                continue
            }

            try {
                appendMethod(classScope, method, spec)
            } catch (e: Exception) {
                builder.append("// $e")
                nextLine()
            }
        }
    }

    private fun appendConstructors(classScope: Scope, name: String, methods: Collection<MethodSpecialization>) {
        for ((constructor) in methods) {
            if (constructor !is Constructor) continue
            appendConstructor(classScope, constructor, name)
        }
    }

    private fun isFinal(keywords: FlagSet): Boolean {
        return keywords.hasFlag(Flags.FINAL) || (
                !keywords.hasFlag(Flags.OPEN) &&
                        !keywords.hasFlag(Flags.OVERRIDE) &&
                        !keywords.hasFlag(Flags.ABSTRACT)
                )
    }

    private fun appendMethod(classScope: Scope, method: Method, specForName: Specialization) {

        // some spacing
        nextLine()

        val selfType = method.selfType
        val isBySelf = selfType == classScope.typeWithArgs ||
                method.flags.hasFlag(Flags.OVERRIDE) ||
                method.flags.hasFlag(Flags.ABSTRACT)

        if (method.flags.hasFlag(Flags.OVERRIDE)) {
            builder.append("@Override")
            nextLine()
        }

        if (method.flags.hasFlag(Flags.ABSTRACT) && classScope.scopeType != ScopeType.INTERFACE) {
            builder.append("abstract ")
        }

        if (classScope.scopeType != ScopeType.INTERFACE) builder.append("public ")
        if (method.flags.hasFlag(Flags.EXTERNAL)) builder.append("native ")
        if (classScope.scopeType != ScopeType.INTERFACE && isFinal(method.flags)) builder.append("final ")
        if (classScope.scopeType == ScopeType.INTERFACE && method.body != null) builder.append("default ")
        // if (!isBySelf || classScope.scopeType?.isObject() == true) builder.append("static ")

        appendTypeParameterDeclaration(method.typeParameters, classScope)
        appendType(method.returnType ?: Types.NullableAny, classScope, false)
        builder.append(' ').append(method.name)

        if (specForName.isNotEmpty()) {
            builder.append('_').append(specForName.hash)
        }

        val selfTypeIfNecessary = if (!isBySelf) selfType else null
        method.selfTypeIfNecessary = selfTypeIfNecessary
        appendValueParameterDeclaration(selfTypeIfNecessary, method.valueParameters, classScope)
        val body = method.body
        if (body != null) {
            val context = ResolutionContext(method.selfType, specialization, true, null)
            appendCode(context, method, body)
        } else {
            builder.append(";")
            nextLine()
        }
    }

    private fun appendConstructor(classScope: Scope, constructor: Constructor, name: String) {

        // some spacing
        nextLine()

        val visibility = if (classScope.isObject()) "private " else "public "
        builder.append(visibility).append(name)
        appendValueParameterDeclaration(null, constructor.valueParameters, classScope)
        // todo append extra body-block for super-call
        val body = constructor.body

        val context = ResolutionContext(constructor.selfType, true, null, emptyMap())
        val superCall = constructor.superCall

        writeBlock {
            if (superCall != null) {
                appendSuperCall(context, superCall)
            } else {
                builder.append("// no super call")
                nextLine()
            }
            if (body != null) {
                builder.append("// body")
                nextLine()
                appendCode(context, constructor, body)
            }
        }
    }

    private fun appendTypeParameterDeclaration(valueParameters: List<Parameter>, scope: Scope) {
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

    private fun appendValueParameterDeclaration(
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
            builder.append(' ').append(param.name)
        }
        builder.append(')')
    }

    private fun appendCode(context: ResolutionContext, method: MethodLike, body: Expression) {
        writeBlock {
            try {
                val method1 = MethodSpecialization(method, context.specialization)
                val simplified = ASTSimplifier.simplify(method1)
                CodeReconstruction.createCodeFromGraph(simplified)
                // todo simplify all entry points as methods...
                appendSimplifiedAST(method, simplified.startBlock)
            } catch (e: Throwable) {
                e.printStackTrace()
                comment {
                    builder.append(
                        "[${e.javaClass.simpleName}: ${e.message}] $body"
                            .replace("/*", "[")
                            .replace("*/", "]")
                    )
                }
                nextLine()
            }
        }
    }

    private fun appendTypeParams(scope: Scope) {
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

    private fun appendSuperTypes(scope: Scope) {
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
}