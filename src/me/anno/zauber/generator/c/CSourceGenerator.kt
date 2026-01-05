package me.anno.zauber.generator.c

import me.anno.zauber.ast.rich.controlflow.*
import me.anno.zauber.ast.rich.expression.*
import me.anno.zauber.ast.rich.expression.constants.NumberExpression
import me.anno.zauber.ast.rich.expression.constants.SpecialValueExpression
import me.anno.zauber.ast.rich.expression.constants.StringExpression
import me.anno.zauber.generator.Generator
import me.anno.zauber.logging.LogManager
import me.anno.zauber.types.Scope
import me.anno.zauber.types.ScopeType
import me.anno.zauber.types.Type
import me.anno.zauber.types.impl.ClassType
import java.io.File

// todo this is the final boss:
//  all allocations, shared references, GC, inheritance etc must be implemented by us
object CSourceGenerator : Generator() {

    private val LOGGER = LogManager.getLogger(CSourceGenerator::class)

    // todo generate runnable C code from what we parsed
    // todo just produce all code for now as-is

    // todo we need .h and .c files...

    fun getName(scope: Scope?): String {
        scope ?: return "void /*???*/"
        return scope.path.joinToString("_")
            .replace("\$f:", "M_")
    }

    fun getName(scope: Type?): String {
        return when (scope) {
            is ClassType -> getName(scope.clazz)
            null -> "void /*???*/"
            else -> "void /* $scope */"
        }
    }

    override fun generateCode(dst: File, root: Scope) {
        builder.clear()
        dst.deleteRecursively()

        fun generateCode(scope: Scope) {
            when (val scopeType = scope.scopeType) {
                ScopeType.PACKAGE, null -> {
                    if (scope.name != "*") {
                        indent()
                        builder.append("// package ").append(scope.name).append('\n')
                        depth++
                    }
                    if (scope.name != "*") depth--
                }
                ScopeType.TYPE_ALIAS -> {} // nothing to do
                ScopeType.METHOD -> {
                    writeMethod(scope)
                }
                else -> if (scopeType.isClassType()) {
                    writeClassReflectionStruct(scope)
                    writeClassInstanceStruct(scope)
                }
            }
            for (child in scope.children) {
                generateCode(child)
            }
        }

        generateCode(root)

        val dstFile = File(dst, "Root.c")
        dstFile.parentFile.mkdirs()
        dstFile.writeText(builder.toString())
    }

    fun writeMethod(scope: Scope) {
        block {
            val self = scope.selfAsMethod!!
            val returnType = self.returnType

            builder.append("struct ").append(getName(returnType))
            builder.append(if (returnType.isValueType()) " " else "* ")
            builder.append(getName(scope)).append("(")

            // todo append context parameters, e.g. self

            depth++
            builder.append("\n")
            indent()
            builder.append("struct ")
                .append(getName(scope.parent))
                .append("* ")
                .append("__this")

            for (param in self.valueParameters) {
                if (!builder.endsWith("(")) builder.append(", ")
                builder.append("\n")
                indent()
                builder.append("struct ")
                    .append(getName(param.type))
                    .append(if (param.type.isValueType()) " " else "* ")
                    .append(param.name)
            }
            depth--

            builder.append(") {\n")

            fun writeExpr(expr: Expression, needsValue: Boolean, source: Expression? = null) {
                // todo depending on source, decide whether we need brackets
                val needsBrackets = needsValue
                if (needsBrackets) builder.append('(')
                when (expr) {
                    is IfElseBranch -> {
                        block {
                            builder.append(if (needsValue) "(" else "if (")
                            writeExpr(expr.condition, true)
                            builder.append(if (needsValue) ")" else ") {\n")
                            indent()
                            writeExpr(expr.ifBranch, needsValue)
                            if (expr.elseBranch != null) {
                                builder.append(if (needsValue) ") : (" else "} else {\n")
                                indent()
                                writeExpr(expr.elseBranch, needsValue)
                                if (needsValue) builder.append(")")
                            }
                        }
                    }
                    is WhileLoop -> {
                        block {
                            builder.append("while (")
                            writeExpr(expr.condition, true)
                            builder.append(") {\n")
                            indent()
                            writeExpr(expr.body, false)
                        }
                    }
                    is ExpressionList -> {
                        // check(!needsValue) // todo if needs value, we somehow need to extract the last one...
                        for (entry in expr.list) {
                            builder.append("\n")
                            indent()
                            writeExpr(entry, needsValue)
                            if (entry !is ExpressionList) {
                                builder.append(';')
                            }
                        }
                    }
                    is ReturnExpression -> {
                        val value = expr.value
                        if (value != null) {
                            builder.append("return ")
                            writeExpr(value, true)
                            builder.append(";\n")
                        } else {
                            builder.append("return;\n")
                        }
                    }
                    is MemberNameExpression -> {
                        builder.append(expr.name)
                    }
                    is StringExpression -> {
                        // todo escape? is it already escaped?
                        builder.append('"').append(expr.value).append('"')
                    }
                    is NumberExpression -> {
                        // todo remove f/d suffix
                        // todo additional lld suffix for longs...
                        builder.append(expr.value)
                    }
                    is ContinueExpression -> {
                        // todo if no label is assigned, define our own
                        // todo goto end-of-block label?
                        builder.append("continue /* @${expr.label} */")
                    }
                    is BreakExpression -> {
                        // todo if no label is assigned, define our own
                        // todo goto end-after-block label
                        builder.append("break /* @${expr.label} */")
                    }
                    is NamedCallExpression -> {
                        // todo append default values
                        // todo write them in the correct order (without naming)
                        // todo place return type argument
                        writeExpr(expr.base, true)
                        builder.append('.').append(expr.name).append('(')
                        for ((i, param) in expr.valueParameters.withIndex()) {
                            if (i > 0) builder.append(", ")
                            writeExpr(param.value, true)
                        }
                        builder.append(')')
                    }
                    is CallExpression -> {
                        // todo append default values
                        // todo write them in the correct order (without naming)
                        // todo place return type argument
                        writeExpr(expr.base, true)
                        builder.append('(')
                        for ((i, param) in expr.valueParameters.withIndex()) {
                            if (i > 0) builder.append(", ")
                            writeExpr(param.value, true)
                        }
                        builder.append(')')
                    }
                    is IsInstanceOfExpr -> {
                        builder.append("__isInstanceOf(")
                        writeExpr(expr.instance, true)
                        builder.append(", ")
                        builder.append(getName(expr.type))
                        builder.append(")")
                    }
                    is CompareOp -> {
                        builder.append('(')
                        writeExpr(expr.value, true)
                        builder.append(expr.type.symbol)
                        builder.append("0)")
                    }
                    is CheckEqualsOp -> {
                        writeExpr(expr.left, true)
                        builder.append(expr.symbol)
                        writeExpr(expr.right, true)
                    }
                    is NamedTypeExpression -> {
                        builder.append(getName(expr.type))
                    }
                    is AssignmentExpression -> {
                        writeExpr(expr.variableName, true)
                        builder.append("=")
                        writeExpr(expr.newValue, true)
                    }
                    is FieldExpression -> {
                        builder.append(expr.field.name)
                    }
                    is SpecialValueExpression -> {
                        builder.append(expr.value.symbol)
                    }
                    is UnresolvedFieldExpression -> {
                        builder.append(expr.name)
                    }
                    else -> {
                        LOGGER.warn("Implement writing ${expr.javaClass.simpleName}")
                        builder.append("/* $expr */")
                    }
                }
                if (needsBrackets) builder.append(')')
            }

            val body = self.body
            if (body != null) {
                writeExpr(body, false)
            }

        }
    }

    fun writeClassReflectionStruct(scope: Scope) {
        block {
            builder.append("struct ").append(getName(scope)).append("_Class {\n")

            // super class as a field
            for (parent in scope.superCalls) {
                // todo make them structs??? can already have resolved methods
                // todo include all to-be-resolved (open/interface) methods
                val pt = parent.type
                indent()
                builder.append("struct ").append(getName(pt.clazz)).append("_Class super").append(pt.clazz.name)
                    .append(";\n")
            }
        }
    }

    fun writeClassInstanceStruct(scope: Scope) {
        block {
            builder.append("struct ").append(getName(scope)).append(" {\n")

            // super class as a field
            for (parent in scope.superCalls) {
                // only primary super instance is needed, the rest should be put into Class
                if (parent.valueParams == null) continue
                val pt = parent.type as ClassType
                indent()
                builder.append("struct ").append(getName(pt.clazz)).append(" superStruct;\n")
            }

            // todo append proper type...
            for (field in scope.fields) {
                // if (field.selfType != null) continue

                indent()
                builder.append("struct ")
                val type = field.valueType
                when (type) {
                    is ClassType -> builder.append(getName(type.clazz))
                    null -> builder.append("void /* null */")
                    else -> builder.append("void /* $type */")
                }
                builder.append(if (type.isValueType()) " " else "* ")
                builder.append(field.name).append(";\n")
            }

            // todo interfaces as fields
        }
    }

    // todo "value" should also be a property for fields to embed them into the class

    fun Type?.isValueType(): Boolean {
        if (this == null) return false
        if (this is ClassType) {
            return "value" in clazz.keywords
        }
        return false
    }
}