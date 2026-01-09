package me.anno.zauber.generation.java

import me.anno.zauber.ast.rich.Constructor
import me.anno.zauber.ast.rich.Field
import me.anno.zauber.ast.rich.Method
import me.anno.zauber.ast.rich.Parameter
import me.anno.zauber.ast.rich.expression.Expression
import me.anno.zauber.ast.simple.ASTSimplifier
import me.anno.zauber.generation.DeltaWriter
import me.anno.zauber.generation.Generator
import me.anno.zauber.generation.java.JavaExpressionWriter.appendSuperCall
import me.anno.zauber.generation.java.JavaSimplifiedASTWriter.appendSimplifiedAST
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
import java.io.File

// todo before generating JVM bytecode, create source code to be compiled with a normal Java compiler
//  big difference: stack-based
object JavaSourceGenerator : Generator() {

    private val blacklistedPaths = listOf(listOf("java"))

    override fun generateCode(dst: File, root: Scope) {
        val writer = DeltaWriter(dst)
        try {
            writer.write(
                File(dst, "org/jetbrains/annotations/Nullable.java"),
                "package org.jetbrains.annotations;\n\n" +
                        "import java.lang.annotation.*;\n\n" +
                        "@Retention(RetentionPolicy.RUNTIME)\n" +
                        "@Target(ElementType.TYPE)\n" +
                        "public @interface Nullable {}\n"
            )
            generate(root, dst, writer)
        } finally {
            writer.finish()
        }
    }

    // todo add line to imports where needed
    // todo if a type is defined twice, we still need the full path
    // todo if the parent is the same as scope.parent, we can skip the import

    fun appendType(type: Type, scope: Scope, isGeneric: Boolean) {
        when (type) {
            NullType -> builder.append("Object /* null */")
            NothingType -> builder.append("Object /* Nothing */")
            is ClassType -> appendClassType(type, scope, isGeneric)
            is UnionType if type.types.size == 2 && NullType in type.types -> {
                // builder.append("@org.jetbrains.annotations.Nullable ")
                appendType(type.types.first { it != NullType }, scope, isGeneric)
                builder.append("/* or null */")
            }
            is SelfType if (type.scope == scope) -> {
                builder.append(scope.name)
            }
            is GenericType if (type.scope == scope) -> {
                builder.append(type.name)
            }
            UnknownType -> builder.append('?')
            is LambdaType -> {
                // todo define all these classes...
                // todo add respective import...
                builder.append("zauber.Function").append(type.parameters.size)
                    .append('<')
                for (param in type.parameters) {
                    appendType(param.type, scope, true)
                    builder.append(", ")
                }
                appendType(type.returnType, scope, true)
                builder.append('>')
            }
            is GenericType -> builder.append(type.name)
            else -> {
                builder.append("Object /* $type */")
            }
        }
    }

    fun appendClassType(type: ClassType, scope: Scope, isGeneric: Boolean) {

        when (type.clazz) {
            StringType.clazz -> builder.append("String")
            IntType.clazz -> builder.append(if (isGeneric) "Integer" else "int")
            LongType.clazz -> builder.append(if (isGeneric) "Long" else "long")
            FloatType.clazz -> builder.append(if (isGeneric) "Float" else "float")
            DoubleType.clazz -> builder.append(if (isGeneric) "Double" else "double")
            BooleanType.clazz -> builder.append(if (isGeneric) "Boolean" else "boolean")
            ByteType.clazz -> builder.append(if (isGeneric) "Byte" else "byte")
            ShortType.clazz -> builder.append(if (isGeneric) "Short" else "short")
            CharType.clazz -> builder.append(if (isGeneric) "Character" else "char")
            AnyType.clazz -> builder.append("Object")
            else -> builder.append(type.clazz.pathStr)
        }

        val params = type.typeParameters
        if (!params.isNullOrEmpty()) {
            builder.append('<')
            for (i in params.indices) {
                if (i > 0) builder.append(", ")
                val param = params.getOrNull(i) ?: NullableAnyType
                appendType(param, scope, true)
            }
            builder.append('>')
        }
    }

    private fun appendPackage(path: List<String>) {
        builder.append("package ")
            .append(path.joinToString("."))
            .append(";\n\n")
    }

    fun generate(scope: Scope, dst: File, writer: DeltaWriter) {
        val scopeType = scope.scopeType
        if (scopeType != null && scopeType.isClassType()) {
            val file = File(dst, scope.path.joinToString("/") + ".java")

            appendPackage(scope.path.dropLast(1))
            generateInside(scope.name, scope)

            writer.write(file, finish())
        } else if ((scopeType == ScopeType.PACKAGE || scopeType == null) && scope.path !in blacklistedPaths) {
            if (scopeType == ScopeType.PACKAGE &&
                (scope.fields.isNotEmpty() || scope.methods.isNotEmpty())
            ) {
                val name = scope.name.capitalize() + "Kt"
                val file = File(dst, scope.path.joinToString("/") + "/$name.java")

                appendPackage(scope.path)
                generateInside(name, scope)

                writer.write(file, finish())
            }

            for (child in scope.children) {
                generate(child, dst, writer)
            }
        }
    }

    fun writeBlock(run: () -> Unit) {
        builder.append(" {")

        depth++
        nextLine()

        run()

        if (builder.endsWith("  ")) {
            builder.setLength(builder.length - 2)
        }
        depth--
        builder.append("}\n")
        indent()
    }

    fun generateInside(name: String, scope: Scope) {

        // todo imports ->
        //  collect them in a dry-run :)


        builder.append("public ")
        val type = when (scope.scopeType) {
            ScopeType.ENUM_CLASS -> "final class"
            ScopeType.NORMAL_CLASS -> "class"
            ScopeType.INTERFACE -> "interface"
            ScopeType.OBJECT -> "final class"
            ScopeType.ENUM_ENTRY_CLASS -> "final class"
            ScopeType.PACKAGE -> "final class"
            else -> scope.scopeType.toString()
        }
        if ("abstract" in scope.keywords) builder.append("abstract ")
        builder.append(type).append(' ')
        builder.append(name)

        appendTypeParams(scope)
        appendSuperTypes(scope)

        writeBlock {

            appendFields(scope)

            val primaryConstructorScope = scope.primaryConstructorScope
            if (primaryConstructorScope != null) appendInitBlocks(primaryConstructorScope)

            appendConstructors(scope)
            appendMethods(scope)

            // inner classes
            if (scope.scopeType != ScopeType.PACKAGE) {
                for (child in scope.children) {
                    val childType = child.scopeType ?: continue
                    if (childType.isClassType()) {
                        generateInside(child.name, child)
                    }
                }
            }
        }
    }

    private fun appendInitBlocks(scope: Scope) {
        for (body in scope.code) {
            writeBlock {
                scope.hasTypeParameters = true // just to prevent crashing
                val context = ResolutionContext(scope, scope.typeWithArgs, true, null)
                appendCode(context, body)
            }
        }
    }

    private fun appendFields(classScope: Scope) {
        if (classScope.scopeType == ScopeType.INTERFACE) return // no backing fields
        val fields = classScope.fields
        for (field in fields) {

            // todo decide whether this fields needs a backing field

            if (field.selfType != classScope.typeWithArgs) continue
            appendBackingField(classScope, field)
        }
    }

    private fun appendBackingField(scope: Scope, field: Field) {
        if (field.byParameter == null) {
            builder.append("public ")
            if (!field.isMutable) builder.append("final ")
            appendType(field.valueType ?: NullableAnyType, scope, false)
            builder.append(' ').append(field.name).append(';')
            nextLine()
        }
    }

    private fun appendMethods(classScope: Scope) {
        for (method in classScope.methods) {
            appendMethod(classScope, method)
        }
    }

    private fun appendConstructors(classScope: Scope) {
        for (constructor in classScope.constructors) {
            appendConstructor(classScope, constructor)
        }
    }

    private fun appendMethod(classScope: Scope, method: Method) {

        val selfType = method.selfType
        val isBySelf = selfType == classScope.typeWithArgs ||
                "override" in method.keywords ||
                "abstract" in method.keywords

        if ("override" in method.keywords) {
            builder.append("@Override")
            nextLine()
        }

        if ("abstract" in method.keywords && classScope.scopeType != ScopeType.INTERFACE) {
            builder.append("abstract ")
        }

        if (classScope.scopeType != ScopeType.INTERFACE) builder.append("public ")
        if ("external" in method.keywords) builder.append("native ")
        if (classScope.scopeType == ScopeType.INTERFACE && method.body != null) builder.append("default ")
        if (!isBySelf) builder.append("static ")

        appendTypeParameterDeclaration(method.typeParameters, classScope)
        appendType(method.returnType ?: NullableAnyType, classScope, false)
        builder.append(' ').append(method.name)
        appendValueParameterDeclaration(if (!isBySelf) selfType else null, method.valueParameters, classScope)
        val body = method.body
        if (body != null) {
            val context = ResolutionContext(classScope, method.selfType, true, null)
            appendCode(context, body)
        } else {
            builder.append(";")
            nextLine()
        }
    }

    private fun appendConstructor(classScope: Scope, constructor: Constructor) {
        builder.append("public ").append(classScope.name)
        appendValueParameterDeclaration(null, constructor.valueParameters, classScope)
        // todo append extra body-block for super-call
        val body = constructor.body

        val isPrimaryConstructor = constructor == classScope.primaryConstructorScope?.selfAsConstructor

        val context = ResolutionContext(classScope, constructor.selfType, true, null)
        val superCall = constructor.superCall

        writeBlock {
            // todo I think this must be in one line... needs different writing, and cannot handle errors the traditional way...
            if (superCall != null) {
                appendSuperCall(context, superCall)
            } else {
                builder.append("// no super call")
                nextLine()
            }
            if (isPrimaryConstructor) {
                for (parameter in constructor.valueParameters) {
                    if (!parameter.isVar && !parameter.isVal) continue
                    val name = parameter.name
                    builder.append("this.").append(name).append(" = ")
                        .append(name).append(';')
                    nextLine()
                }
            }
            if (body != null) {
                appendCode(context, body)
            }
        }
    }

    private fun appendTypeParameterDeclaration(
        valueParameters: List<Parameter>,
        scope: Scope
    ) {
        if (valueParameters.isEmpty()) return
        builder.append('<')
        for (param in valueParameters) {
            if (!builder.endsWith("<")) builder.append(", ")
            builder.append(param.name)
            if (param.type != AnyType && param.type != NullableAnyType) {
                builder.append(": ")
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

    private fun appendCode(context: ResolutionContext, body: Expression) {
        writeBlock {
            appendCodeWithoutBlock(context, body)
        }
    }

    private fun appendCodeWithoutBlock(context: ResolutionContext, body: Expression) {
        try {
            val simplified = ASTSimplifier.simplify(context, body)
            appendSimplifiedAST(simplified.startBlock)
        } catch (e: Throwable) {
            e.printStackTrace()
            builder.append("/* [${e.javaClass.simpleName}: ${e.message}] $body */")
        }
        nextLine()
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
                    appendType(param.type, scope, false)
                }
            }
            builder.append('>')
        }
    }

    private fun appendSuperTypes(scope: Scope) {
        val superCall0 = scope.superCalls.firstOrNull { it.valueParameters != null }
        if (superCall0 != null && superCall0.type != AnyType) {
            builder.append(" extends ")
            appendClassType(superCall0.type, scope, false)
        }
        val implementsKeyword = if (scope.scopeType == ScopeType.INTERFACE) " extends " else " implements "
        for (superCall in scope.superCalls) {
            if (superCall.valueParameters != null) continue
            builder.append(implementsKeyword)
            appendClassType(superCall.type, scope, false)
        }
    }
}