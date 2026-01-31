package me.anno.zauber.generation.java

import me.anno.zauber.ast.KeywordSet
import me.anno.zauber.ast.reverse.CodeReconstruction
import me.anno.zauber.ast.rich.*
import me.anno.zauber.ast.rich.Keywords.hasFlag
import me.anno.zauber.ast.rich.expression.Expression
import me.anno.zauber.ast.simple.ASTSimplifier
import me.anno.zauber.ast.simple.ASTSimplifier.needsFieldByParameter
import me.anno.zauber.generation.DeltaWriter
import me.anno.zauber.generation.Generator
import me.anno.zauber.generation.Specializations
import me.anno.zauber.generation.Specializations.foundTypeSpecialization
import me.anno.zauber.generation.Specializations.specialization
import me.anno.zauber.generation.Specializations.specializations
import me.anno.zauber.generation.java.JavaExpressionWriter.appendSuperCall
import me.anno.zauber.generation.java.JavaSimplifiedASTWriter.appendSimplifiedAST
import me.anno.zauber.typeresolution.ParameterList.Companion.resolveGenerics
import me.anno.zauber.typeresolution.ResolutionContext
import me.anno.zauber.types.Scope
import me.anno.zauber.types.ScopeType
import me.anno.zauber.types.Type
import me.anno.zauber.types.Types.AnyType
import me.anno.zauber.types.Types.BooleanType
import me.anno.zauber.types.Types.ByteType
import me.anno.zauber.types.Types.CharType
import me.anno.zauber.types.Types.DoubleType
import me.anno.zauber.types.Types.FloatType
import me.anno.zauber.types.Types.IntType
import me.anno.zauber.types.Types.LongType
import me.anno.zauber.types.Types.NothingType
import me.anno.zauber.types.Types.NullableAnyType
import me.anno.zauber.types.Types.ShortType
import me.anno.zauber.types.Types.StringType
import me.anno.zauber.types.impl.*
import me.anno.zauber.types.specialization.MethodSpecialization
import me.anno.zauber.types.specialization.Specialization
import me.anno.zauber.types.specialization.Specialization.Companion.noSpecialization
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

    @Suppress("MayBeConstant")
    val enforceSpecialization = true

    val protectedTypes = mapOf(
        StringType to BoxedType("java.lang.String", "java.lang.String"),
        BooleanType to BoxedType("Boolean", "boolean"),
        ByteType to BoxedType("Byte", "byte"),
        ShortType to BoxedType("Short", "short"),
        IntType to BoxedType("Integer", "int"),
        LongType to BoxedType("Long", "long"),
        CharType to BoxedType("Character", "char"),
        FloatType to BoxedType("Float", "float"),
        DoubleType to BoxedType("Double", "double"),
        AnyType to BoxedType("Object", "Object")
    )

    val nativeTypes = protectedTypes.filter { (_, it) -> it.boxed != it.native }
    val nativeNumbers = nativeTypes - BooleanType

    // todo we need to add these for every constructor and super call,
    //  we need specialized methods for every generic method (incl. getters and setters)

    private val blacklistedPaths = listOf(listOf("java"))

    override fun generateCode(dst: File, root: Scope) {
        val writer = DeltaWriter(dst)
        try {
            defineNullableAnnotation(dst, writer)
            generate(root, dst, writer, noSpecialization)
            Specializations.generate(
                { generate(it.scope, dst, writer, it.specialization) },
                { extendScope(it, dst, writer) })
        } finally {
            writer.finish()
        }
    }

    private fun defineNullableAnnotation(dst: File, writer: DeltaWriter) {
        writer[File(dst, "org/jetbrains/annotations/Nullable.java")] =
            """
                package org.jetbrains.annotations;
                
                import java.lang.annotation.*;
                
                @Retention(RetentionPolicy.RUNTIME)
                @Target(ElementType.TYPE)
                public @interface Nullable {}
            """.trimIndent()
    }

    fun extendScope(spec: MethodSpecialization, dst: File, writer: DeltaWriter) {
        val method = spec.method
        val classScope = method.scope.parent!!
        appendMethod(classScope, method, spec.specialization)

        writer[dst] += finish()
    }

    // todo add line to imports where needed
    // todo if a type is defined twice, we still need the full path
    // todo if the parent is the same as scope.parent, we can skip the import

    fun appendType(type: Type, scope: Scope, needsBoxedType: Boolean) {
        val protected = protectedTypes[type]
        if (protected != null) {
            builder.append(if (needsBoxedType) protected.boxed else protected.native)
            return
        }

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

        when (type) {
            NullType -> {
                builder.append("Object ")
                comment { builder.append("null") }
            }
            NothingType -> {
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
                    if (enforceSpecialization) {
                        throw IllegalStateException(
                            "All generics must be resolved, " +
                                    "$type is unknown in $specialization, $scope"
                        )
                    }
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

        val params = type.typeParameters
        if (!params.isNullOrEmpty()) {
            val spec = Specialization(type)
            val className = createClassName(type.clazz, spec)
            builder.append(type.clazz.parent!!.pathStr).append('.')
                .append(className)
            foundTypeSpecialization(type.clazz, spec)
        } else {
            builder.append(type.clazz.pathStr)
        }
    }

    private fun appendPackage(path: List<String>) {
        builder.append("package ")
            .append(path.joinToString("."))
            .append(";\n\n")
    }

    fun createSpecializationSuffix(specialization: Specialization): String {
        if (specialization.isEmpty()) return ""
        // todo ensure the name is original, but keep it readable
        return "_${specialization.createUniqueName()}"
    }

    fun createFile(scope: Scope, name: String, dst: File): File {
        return File(dst, scope.path.joinToString("/") + "/$name.java")
    }

    fun createClassName(scope: Scope, specialization: Specialization): String {
        return scope.name + createSpecializationSuffix(specialization)
    }

    fun createPackageName(scope: Scope, specialization: Specialization): String {
        return scope.name.capitalize() + "Kt" + createSpecializationSuffix(specialization)
    }

    fun generate(scope: Scope, dst: File, writer: DeltaWriter, specialization: Specialization) {
        val scopeType = scope.scopeType
        if (scope.isClassType()) {
            val name = createClassName(scope, specialization)

            appendPackage(scope.path.dropLast(1))
            generateInside(name, scope, specialization)

            writer[createFile(scope.parent!!, name, dst)] = finish()
        } else if ((scopeType == ScopeType.PACKAGE || scopeType == null) && scope.path !in blacklistedPaths) {
            if (scopeType == ScopeType.PACKAGE && (scope.fields.isNotEmpty() || scope.methods.isNotEmpty())) {
                // we need some package helper
                val name = createPackageName(scope, specialization)

                appendPackage(scope.path)
                generateInside(name, scope, specialization)

                writer[createFile(scope, name, dst)] = finish()
            }

            // todo child specialization may be needed in some cases
            //  objects, normal classes, enum classes? no
            //  inner classes: yes
            if (specialization.isEmpty()) {
                for (child in scope.children) {
                    generate(child, dst, writer, noSpecialization)
                }
            }
        }
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

    fun generateInside(className: String, scope: Scope, specialization: Specialization) {

        if (enforceSpecialization) {
            for (type in scope.typeParameters) {
                if (specialization[type] == null) return
            }
        }

        // todo imports ->
        //  collect them in a dry-run :)

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
            scope.parent?.scopeType != ScopeType.PACKAGE
        ) {
            builder.append("static ")
        }
        val javaType = when (scope.scopeType) {
            ScopeType.ENUM_CLASS -> "final class"
            ScopeType.NORMAL_CLASS -> {
                val isFinal = isFinal(scope.keywords)
                if (isFinal) "final class" else "class"
            }
            ScopeType.INTERFACE -> "interface"
            ScopeType.OBJECT, ScopeType.COMPANION_OBJECT -> "final class"
            ScopeType.ENUM_ENTRY_CLASS -> "final class"
            ScopeType.PACKAGE -> "final class"
            else -> scope.scopeType.toString()
        }
        if (scope.keywords.hasFlag(Keywords.ABSTRACT)) builder.append("abstract ")
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
            appendConstructors(scope, className)
            appendMethods(scope)

            @Suppress("Since15")
            specializations.removeLast()

            // inner classes
            if (specialization.isEmpty() && scope.scopeType != ScopeType.PACKAGE) {
                // todo we may need sub-specializations...
                for (child in scope.children) {
                    val childType = child.scopeType ?: continue
                    if (childType.isClassType()) {
                        // some spacing
                        nextLine()
                        generateInside(child.name, child, specialization)
                    }
                }
            }
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
        val valueType = (field.valueType ?: NullableAnyType).resolve(classScope)
        appendType(valueType, classScope, false)
        builder.append(' ').append(field.name).append(';')
        nextLine()
    }

    private fun appendMethods(classScope: Scope) {
        for (method in classScope.methods) {
            if (method.scope.parent != classScope) {
                // an inherited method -> skip, because it's already defined in the parent
                continue
            }
            if (method.typeParameters.isNotEmpty() && enforceSpecialization) {
                // we don't know the required specializations yet
                continue
            }
            try {
                appendMethod(classScope, method, noSpecialization)
            } catch (e: Exception) {
                builder.append("// $e")
                nextLine()
            }
        }
    }

    private fun appendConstructors(classScope: Scope, name: String) {
        for (constructor in classScope.constructors) {
            appendConstructor(classScope, constructor, name)
        }
    }

    private fun isFinal(keywords: KeywordSet): Boolean {
        return keywords.hasFlag(Keywords.FINAL) || (
                !keywords.hasFlag(Keywords.OPEN) &&
                        !keywords.hasFlag(Keywords.OVERRIDE) &&
                        !keywords.hasFlag(Keywords.ABSTRACT)
                )
    }

    private fun appendMethod(classScope: Scope, method: Method, specForName: Specialization) {

        // some spacing
        nextLine()

        val selfType = method.selfType
        val isBySelf = selfType == classScope.typeWithArgs ||
                method.keywords.hasFlag(Keywords.OVERRIDE) ||
                method.keywords.hasFlag(Keywords.ABSTRACT)

        if (method.keywords.hasFlag(Keywords.OVERRIDE)) {
            builder.append("@Override")
            nextLine()
        }

        if (method.keywords.hasFlag(Keywords.ABSTRACT) && classScope.scopeType != ScopeType.INTERFACE) {
            builder.append("abstract ")
        }

        if (classScope.scopeType != ScopeType.INTERFACE) builder.append("public ")
        if (method.keywords.hasFlag(Keywords.EXTERNAL)) builder.append("native ")
        if (classScope.scopeType != ScopeType.INTERFACE && isFinal(method.keywords)) builder.append("final ")
        if (classScope.scopeType == ScopeType.INTERFACE && method.body != null) builder.append("default ")
        // if (!isBySelf || classScope.scopeType?.isObject() == true) builder.append("static ")

        appendTypeParameterDeclaration(method.typeParameters, classScope)
        appendType(method.returnType ?: NullableAnyType, classScope, false)
        builder.append(' ').append(method.name)

        if (specForName.isNotEmpty()) {
            builder.append('_').append(specForName.hash)
        }

        val selfTypeIfNecessary = if (!isBySelf) selfType else null
        method.selfTypeIfNecessary = selfTypeIfNecessary
        appendValueParameterDeclaration(selfTypeIfNecessary, method.valueParameters, classScope)
        val body = method.body
        if (body != null) {
            val context = ResolutionContext(method.selfType, true, null, emptyMap())
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

        val isPrimaryConstructor = constructor == classScope.primaryConstructorScope?.selfAsConstructor

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
                appendCode(context, constructor, body)
            }
            if (isPrimaryConstructor) {
                for (body in constructor.scope.code) {
                    appendCode(context, constructor, body)
                }
            }
        }
    }

    private fun appendTypeParameterDeclaration(valueParameters: List<Parameter>, scope: Scope) {
        if (valueParameters.isEmpty()) return
        builder.append('<')
        for (param in valueParameters) {
            if (!builder.endsWith("<")) builder.append(", ")
            builder.append(param.name)
            if (param.type != AnyType && param.type != NullableAnyType) {
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
                val simplified = ASTSimplifier.simplify(context, body)
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
                if (param.type != NullableAnyType) {
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
                superCall0.valueParameters != null &&
                superCall0.type != AnyType
            ) {
                val type = superCall0.type
                builder.append(" extends ")
                appendType(type, scope, true)
            }

            var implementsKeyword = if (scope.scopeType == ScopeType.INTERFACE) " extends " else " implements "
            for (superCall in scope.superCalls) {
                if (superCall.valueParameters != null) continue
                val type = superCall.type
                builder.append(implementsKeyword)
                appendType(type, scope, true)
                implementsKeyword = ", "
            }
        } catch (e: Exception) {
            comment { builder.append(e) }
        }
    }
}