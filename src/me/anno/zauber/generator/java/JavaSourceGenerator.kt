package me.anno.zauber.generator.java

import me.anno.zauber.ast.rich.Constructor
import me.anno.zauber.ast.rich.Field
import me.anno.zauber.ast.rich.Method
import me.anno.zauber.ast.rich.Parameter
import me.anno.zauber.ast.rich.expression.Expression
import me.anno.zauber.ast.simple.ASTSimplifier
import me.anno.zauber.ast.simple.SimpleBlock
import me.anno.zauber.ast.simple.SimpleExpression
import me.anno.zauber.ast.simple.SimpleField
import me.anno.zauber.ast.simple.controlflow.SimpleBranch
import me.anno.zauber.ast.simple.controlflow.SimpleReturn
import me.anno.zauber.ast.simple.expression.*
import me.anno.zauber.generator.DeltaWriter
import me.anno.zauber.generator.Generator
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
            CharType.clazz -> builder.append(if (isGeneric) "Char" else "char")
            AnyType.clazz -> builder.append("Object")
            else -> builder.append(type.clazz.pathStr)
        }

        val params = type.typeParameters
        if (!params.isNullOrEmpty()) {
            builder.append('<')
            for ((i, param) in params.withIndex()) {
                if (i > 0) builder.append(", ")
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
            appendInitBlocks(scope)
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
                val context = ResolutionContext(scope, scope.typeWithArgs, true, null)
                appendCode(context, body)
            }
        }
    }

    private fun appendFields(scope: Scope) {
        val fields = scope.fields
        for (field in fields) {

            // todo write getter and setter
            // todo check whether this fields needs a backing field

            if (field.selfType != scope.typeWithArgs) continue
            appendBackingField(scope, field)
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

    private fun appendMethods(scope: Scope) {
        for (method in scope.methods) {
            appendMethod(scope, method)
        }
    }

    private fun appendConstructors(scope: Scope) {
        for (constructor in scope.constructors) {
            appendConstructor(scope, constructor)
        }
    }

    private fun appendMethod(scope: Scope, method: Method) {

        val selfType = method.selfType
        val isBySelf = selfType == scope.typeWithArgs ||
                "override" in method.keywords ||
                "abstract" in method.keywords
        if ("override" in method.keywords) builder.append("@Override ")
        if ("abstract" in method.keywords && scope.scopeType != ScopeType.INTERFACE) {
            builder.append("abstract ")
        }

        builder.append("public ")
        if (!isBySelf) builder.append("static ")
        appendType(method.returnType ?: NullableAnyType, scope, false)
        builder.append(' ').append(method.name)
        appendParameterDeclaration(if (!isBySelf) selfType else null, method.valueParameters, scope)
        val body = method.body
        if (body != null) {
            val context = ResolutionContext(scope, method.selfType, true, null)
            appendCode(context, body)
        } else {
            builder.append(";")
            nextLine()
        }
    }

    private fun appendConstructor(scope: Scope, constructor: Constructor) {
        builder.append("public ").append(scope.name)
        appendParameterDeclaration(null, constructor.valueParameters, scope)
        // todo append extra body-block for super-call
        val body = constructor.body

        val context = ResolutionContext(scope, constructor.selfType, true, null)
        val superCall = constructor.superCall
        when {
            superCall != null -> {
                writeBlock {
                    // todo I think this must be in one line... complicated...
                    appendCodeWithoutBlock(context, superCall.toExpr())
                    if (body != null) appendCode(context, body)
                }
            }
            body != null -> appendCode(context, body)
            else -> {
                builder.append(" {}")
                nextLine()
            }
        }
    }

    private fun appendParameterDeclaration(selfTypeIfNecessary: Type?, valueParameters: List<Parameter>, scope: Scope) {
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

    private fun appendAssign(expression: SimpleAssignmentExpression) {
        val dst = expression.dst
        builder.append("final ")
        appendType(dst.type, expression.scope, false)
        builder.append(' ').append1(dst).append(" = ")
    }

    private fun StringBuilder.append1(field: SimpleField): StringBuilder {
        append("tmp").append(field.id)
        return this
    }

    private fun appendSimplifiedAST(expr: SimpleExpression) {
        if (expr is SimpleAssignmentExpression) appendAssign(expr)
        when (expr) {
            is SimpleBlock -> {
                val instr = expr.instructions
                for (i in instr.indices) {
                    appendSimplifiedAST(instr[i])
                }
            }
            is SimpleBranch -> {
                builder.append("if (").append1(expr.condition).append(')')
                writeBlock {
                    appendSimplifiedAST(expr.ifTrue)
                }
                builder.append(" else ")
                writeBlock {
                    appendSimplifiedAST(expr.ifFalse)
                }
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
                if (expr.self != null) {
                    builder.append1(expr.self).append('.')
                }
                val getter = expr.field.getter
                if (getter != null) {
                    builder.append(getter.name).append("()")
                } else {
                    builder.append(expr.field.name)
                }
            }
            is SimpleCall -> {

                val methodName = expr.method.name
                val done = when (expr.valueParameters.size) {
                    0 -> {
                        if (expr.base.type == BooleanType && methodName == "not") {
                            builder.append('!').append1(expr.base)
                            true
                        } else false
                    }
                    1 -> {
                        val supportsType = when (expr.base.type) {
                            StringType, ByteType, ShortType, CharType, IntType, LongType, FloatType, DoubleType -> true
                            else -> false
                        }
                        val symbol = when (methodName) {
                            "plus" -> " + "
                            "minus" -> " - "
                            "times" -> " * "
                            "div" -> " / "
                            "rem" -> " % "
                            else -> null
                        }
                        if (supportsType && symbol != null) {
                            builder.append1(expr.base).append(symbol)
                            builder.append1(expr.valueParameters[0])
                            true
                        } else false
                    }
                    else -> false
                }
                if (!done) {
                    builder.append1(expr.base).append('.')
                    builder.append(methodName).append('(')
                    for (i in expr.valueParameters.indices) {
                        if (i > 0) builder.append(", ")
                        val parameter = expr.valueParameters[i]
                        builder.append1(parameter)
                    }
                    builder.append(')')
                }
            }
            is SimpleReturn -> {
                builder.append("return ").append1(expr.field).append(';')
            }
            else -> {
                builder.append("/* ${expr.javaClass.simpleName}: $expr */")
            }
        }
        if (expr is SimpleAssignmentExpression) builder.append(';')
        if (expr !is SimpleBlock && expr !is SimpleBranch) nextLine()
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
        val superCall0 = scope.superCalls.firstOrNull { it.valueParams != null }
        if (superCall0 != null) {
            builder.append(" extends ")
            appendClassType(superCall0.type, scope, false)
        }
        val implementsKeyword = if (scope.scopeType == ScopeType.INTERFACE) " extends " else " implements "
        for (superCall in scope.superCalls) {
            if (superCall.valueParams != null) continue
            builder.append(implementsKeyword)
            appendClassType(superCall.type, scope, false)
        }
    }
}