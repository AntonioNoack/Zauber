package me.anno.zauber.generator.java

import me.anno.zauber.ast.rich.Field
import me.anno.zauber.ast.rich.Method
import me.anno.zauber.generator.DeltaWriter
import me.anno.zauber.generator.Generator
import me.anno.zauber.types.Scope
import me.anno.zauber.types.ScopeType
import me.anno.zauber.types.Type
import me.anno.zauber.types.Types.NullableAnyType
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

    fun appendType(type: Type, scope: Scope) {
        when (type) {
            is ClassType -> appendClassType(type, scope)
            is UnionType if type.types.size == 2 && NullType in type.types -> {
                builder.append("@org.jetbrains.annotations.Nullable ")
                appendType(type.types.first { it != NullType }, scope)
            }
            is SelfType if (type.scope == scope) -> {
                builder.append(scope.name)
            }
            is GenericType if (type.scope == scope) -> {
                builder.append(type.name)
            }
            is LambdaType -> {
                // todo define all these classes...
                // todo add respective import...
                builder.append("zauber.Function").append(type.parameters.size)
                    .append('<')
                for (param in type.parameters) {
                    appendType(param.type, scope)
                    builder.append(", ")
                }
                appendType(type.returnType, scope)
                builder.append('>')
            }
            else -> builder.append(type.toString())
        }
    }

    fun appendClassType(type: ClassType, scope: Scope) {
        builder.append(type.clazz.pathStr)
        val params = type.typeParameters
        if (!params.isNullOrEmpty()) {
            builder.append('<')
            for ((i, param) in params.withIndex()) {
                if (i > 0) builder.append(", ")
                appendType(param, scope)
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

            writer.write(file, builder.toString())
            builder.clear()
        } else if ((scopeType == ScopeType.PACKAGE || scopeType == null) && scope.path !in blacklistedPaths) {
            if (scopeType == ScopeType.PACKAGE &&
                (scope.fields.isNotEmpty() || scope.methods.isNotEmpty())
            ) {
                val name = scope.name.capitalize() + "Kt"
                val file = File(dst, scope.path.joinToString("/") + "/$name.java")

                appendPackage(scope.path)
                generateInside(name, scope)

                writer.write(file, builder.toString())
                builder.clear()
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

        if (scope.scopeType != ScopeType.ENUM_ENTRY_CLASS) {
            builder.append("public ")
            val type = when (scope.scopeType) {
                ScopeType.ENUM_CLASS -> "enum"
                ScopeType.NORMAL_CLASS -> "class"
                ScopeType.INTERFACE -> "interface"
                ScopeType.OBJECT -> "final class"
                ScopeType.PACKAGE -> "final class"
                else -> scope.scopeType.toString()
            }
            if ("abstract" in scope.keywords) builder.append("abstract ")
            builder.append(type).append(' ')
        }
        builder.append(name)

        appendTypeParams(scope)

        if (scope.scopeType != ScopeType.ENUM_CLASS &&
            scope.scopeType != ScopeType.ENUM_ENTRY_CLASS
        ) {
            // todo not supported in Java :(
            //  -> we can solve that issue by defining our own enum class:
            //  just create our own static instances
            appendSuperTypes(scope)
        }

        writeBlock {

            if (scope.scopeType == ScopeType.ENUM_CLASS) {
                val entries = scope.children.filter { it.scopeType == ScopeType.ENUM_ENTRY_CLASS }
                for ((index, child) in entries.withIndex()) {
                    if (index > 0) {
                        while (builder.last() in " \n") builder.setLength(builder.length - 1)
                        builder.append(", ")
                        nextLine()
                    }
                    generateInside(child.name, child)
                }
                if (entries.isNotEmpty()) {
                    while (builder.last() in " \n") builder.setLength(builder.length - 1)
                }
                builder.append(';')
                nextLine()
            }

            appendFields(scope)

            // todo define static instance
            // todo do we make companions a static instance or extra classes???

            // todo define constructors

            appendMethods(scope)

            // inner classes
            if (scope.scopeType != ScopeType.PACKAGE) {
                for (child in scope.children) {
                    val childType = child.scopeType ?: continue
                    if (childType.isClassType() && childType != ScopeType.ENUM_ENTRY_CLASS) {
                        generateInside(child.name, child)
                    }
                }
            }
        }
    }

    private fun appendFields(scope: Scope) {
        val fields = scope.fields
        for (field in fields) {

            // todo write getter and setter
            // todo check whether this fields needs a backing field

            if (field.selfType != scope) continue
            appendBackingField(scope, field)
        }
    }

    private fun appendBackingField(scope: Scope, field: Field) {
        if (field.isVar || field.isVal) {
            appendType(field.valueType ?: NullableAnyType, scope)
            builder.append(' ').append(field.name).append(';')
            nextLine()
        }
    }

    private fun appendMethods(scope: Scope) {
        for (method in scope.methods) {
            appendMethod(scope, method)
        }
    }

    private fun appendMethod(scope: Scope, method: Method) {
        val selfType = method.selfType
        val isBySelf = selfType == scope.typeWithoutArgs ||
                "override" in method.keywords ||
                "abstract" in method.keywords
        if ("override" in method.keywords) builder.append("@Override ")
        if ("abstract" in method.keywords && scope.scopeType != ScopeType.INTERFACE) {
            builder.append("abstract ")
        }
        builder.append("public ")
        if (!isBySelf) builder.append("static ")
        appendType(method.returnType ?: NullableAnyType, scope)
        builder.append(' ').append(method.name)
        builder.append('(')
        if (!isBySelf && selfType != null) {
            appendType(selfType, scope)
            builder.append(" __self")
        }
        for (param in method.valueParameters) {
            if (!builder.endsWith("(")) builder.append(", ")
            appendType(param.type, scope)
            builder.append(' ').append(param.name)
        }
        builder.append(')')
        val body = method.body
        if (body != null) {
            writeBlock {

            }
        } else {
            builder.append(";")
            nextLine()
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
                    appendType(param.type, scope)
                }
            }
            builder.append('>')
        }
    }

    private fun appendSuperTypes(scope: Scope) {
        val superCall0 = scope.superCalls.firstOrNull { it.valueParams != null }
        if (superCall0 != null) {
            builder.append(" extends ")
            appendClassType(superCall0.type, scope)
        }
        val implementsKeyword = if (scope.scopeType == ScopeType.INTERFACE) " extends " else " implements "
        for (superCall in scope.superCalls) {
            if (superCall.valueParams != null) continue
            builder.append(implementsKeyword)
            appendClassType(superCall.type, scope)
        }
    }
}