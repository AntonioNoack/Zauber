package me.anno.generation.llvm

import me.anno.generation.BoxedType
import me.anno.generation.c.CSourceGenerator
import me.anno.generation.java.JavaSourceGenerator
import me.anno.utils.ResetThreadLocal.Companion.threadLocal
import me.anno.zauber.ast.reverse.SimpleBranch
import me.anno.zauber.ast.reverse.SimpleLoop
import me.anno.zauber.ast.rich.member.Constructor
import me.anno.zauber.ast.rich.member.Field
import me.anno.zauber.ast.rich.member.Method
import me.anno.zauber.ast.rich.expression.constants.SpecialValue
import me.anno.zauber.ast.simple.*
import me.anno.zauber.ast.simple.SimpleBlock.Companion.isNullable
import me.anno.zauber.ast.simple.controlflow.SimpleReturn
import me.anno.zauber.ast.simple.controlflow.SimpleThrow
import me.anno.zauber.ast.simple.expression.*
import me.anno.zauber.expansion.DependencyData
import me.anno.zauber.scope.Scope
import me.anno.zauber.types.Type
import me.anno.zauber.types.Types
import me.anno.zauber.types.impl.ClassType
import me.anno.zauber.types.Specialization
import java.io.File

// todo this is like C (end game difficulty), just different commands?
class LLVMSourceGenerator : CSourceGenerator() {

    companion object {

        // taken from Java
        val protectedLLVMTypes by threadLocal {
            Types.run {
                mapOf(
                    Boolean to BoxedType("Boolean", "boolean"),
                    Byte to BoxedType("Byte", "byte"),
                    Short to BoxedType("Short", "short"),
                    Int to BoxedType("Integer", "int"),
                    Long to BoxedType("Long", "long"),
                    Char to BoxedType("Character", "char"),
                    Float to BoxedType("Float", "float"),
                    Double to BoxedType("Double", "double"),
                )
            }
        }

        val nativeLLVMTypes by threadLocal { protectedLLVMTypes.filter { (_, it) -> it.boxed != it.native } }
        val nativeLLVMNumbers by threadLocal { nativeLLVMTypes - Types.Boolean }

    }

    override val protectedTypes: Map<ClassType, BoxedType> get() = protectedLLVMTypes
    override val nativeTypes: Map<ClassType, BoxedType> get() = nativeLLVMTypes
    override val nativeNumbers: Map<ClassType, BoxedType> get() = nativeLLVMNumbers

    override fun generateCode(dst: File, data: DependencyData, mainMethod: Method) {
        for (method in data.calledMethods) {
            generateCode(method)
        }

        dst.writeText(builder.toString())
        builder.clear()
    }

    override fun getMethodName(method: Specialization): String {
        return method.method.memberScope.pathStr +
                JavaSourceGenerator().createSpecializationSuffix(method)
    }

    override fun comment(body: () -> Unit) {
        commentDepth++
        try {
            builder.append(if (commentDepth == 1) "; " else "(")
            body()
            if (commentDepth == 1) nextLine()
            else builder.append(")")
        } finally {
            commentDepth--
        }
    }

    fun generateCode(method0: Specialization) {

        val method = method0.method
        method.body ?: return

        nextLine()
        builder.append("define ")
        appendType(method.resolveReturnType(method0), method.scope, false)

        builder.append(" @")
        builder.append(getMethodName(method0))
        builder.append("(")

        appendType(method.ownerScope.typeWithArgs.specialize(method0), method.scope, false)
        builder.append(" %this")
        for (parameter in method.valueParameters) {
            builder.append(", ")
            appendType(parameter.type, method.scope, false)
            builder.append(" %").append(parameter.name)
        }

        builder.append(")")

        val graph = ASTSimplifier.simplify(method0)
        prepareGraph(graph)

        writeBlock {
            appendSimpleBlock(graph, graph.startBlock)
        }
    }

    override fun appendType(type: Type, scope: Scope, needsBoxedType: Boolean) {
        when (val type = resolveType(type)) {
            Types.Int, Types.UInt -> builder.append("i32")
            Types.Long, Types.ULong -> builder.append("i64")
            Types.Half -> builder.append("f16")
            Types.Float -> builder.append("f32")
            Types.Double -> builder.append("f64")
            else -> {
                comment { builder.append("$type") }
                builder.append(" i64")
            }
        }
    }

    override fun appendFieldName(
        graph: SimpleGraph,
        field: SimpleField,
        forFieldAccess: String
    ) {
        if (field.isObjectLike()) {
            val objectScope = (field.type as ClassType).clazz
            appendGetObjectInstance(objectScope, graph.method.scope)
        } else if (field.isOwnerThis(graph)) {
            builder.append("%this")
        } else {
            var field = field
            while (true) {
                field = field.mergeInfo?.dst ?: break
            }
            builder.append("%").append(field.id)
        }
        builder.append(forFieldAccess)
    }

    override fun appendSimpleBlock(graph: SimpleGraph, expr: SimpleBlock) {
        val instructions = expr.instructions
        for (i in instructions.indices) {
            val instr = instructions[i]
            appendSimpleInstruction(graph, instr /*loop*/)
            if (instr is SimpleAssignment &&
                instr.dst.type == Types.Nothing
            ) break
        }
        if (expr.branchCondition == null) {
            val next = expr.nextBranch
            if (next != null) {
                appendSimpleBlock(graph, next)
            }
        } else {
            // todo this may or may not be simply be possible...
            // todo this may be a loop, branch, or similar...
            TODO("Jump to either branch")
        }
    }

    override fun appendAssign(graph: SimpleGraph, expression: SimpleAssignment) {
        val dst = expression.dst
        if (dst.mergeInfo != null) {
            appendFieldName(graph, dst)
            builder.append(" = ")
        } else {
            comment { appendType(dst.type, expression.scope, false) }
            builder.append(' ')
            appendFieldName(graph, dst)
            builder.append(" = ")
        }
    }

    override fun appendSimpleInstruction(graph: SimpleGraph, expr: SimpleInstruction) {
        if (expr is SimpleGetObject) return
        if (expr is SimpleAssignment && expr.dst.type != Types.Nothing && !expr.dst.isObjectLike()) {
            val notNeeded = expr.dst.numReads == 0
            if (notNeeded) comment { appendAssign(graph, expr) }
            else appendAssign(graph, expr)
        }
        when (expr) {
            is SimpleBranch -> {
                builder.append("if (")
                appendFieldName(graph, expr.condition)
                builder.append(')')
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
                builder.append("b").append(expr.body.blockId)
                builder.append(": while (true)")
                writeBlock {
                    appendSimpleBlock(graph, expr.body)
                }
            }
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
                        appendFieldName(graph, expr.left)
                        builder.append(".equals(")
                        appendFieldName(graph, expr.right)
                        builder.append(")")
                    }
                    leftCanBeNull -> {
                        appendFieldName(graph, expr.left)
                        builder.append(" != null && ")
                        appendFieldName(graph, expr.left)
                        builder.append(".equals(")
                        appendFieldName(graph, expr.right)
                        builder.append(")")
                    }
                    rightCanBeNull -> {
                        appendFieldName(graph, expr.right)
                        builder.append(" != null && ")
                        appendFieldName(graph, expr.left)
                        builder.append(".equals(")
                        appendFieldName(graph, expr.right)
                        builder.append(")")
                    }
                    else -> {
                        appendFieldName(graph, expr.left)
                        builder.append(".equals(")
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
                                "plus" -> "add"
                                "minus" -> "sub"
                                "times" -> "mul"
                                "div" -> "div"
                                "rem" -> "mod"
                                // compareTo is a problem for numbers:
                                //  we must call their static compare() function
                                "compareTo" -> "compare"
                                else -> null
                            }
                            if (supportsType && symbol != null) {
                                builder.append(symbol).append(' ')
                                appendType(expr.dst.type, expr.scope, false)
                                builder.append(' ')
                                appendFieldName(graph, expr.self)
                                builder.append(", ")
                                appendFieldName(graph, expr.valueParameters[0])
                                true
                            } else false
                        }
                        else -> false
                    }
                    if (!done) {
                        val needsCastForFirstValue = nativeTypes[expr.self.type]
                        if (needsCastForFirstValue != null) {
                            builder.append(needsCastForFirstValue.boxed).append('.')
                            builder.append(expr.methodName).append('(')
                            appendFieldName(graph, expr.self)
                            if (expr.valueParameters.isNotEmpty()) {
                                builder.append(", ")
                                appendValueParams(graph, expr.valueParameters, false)
                            }
                            builder.append(')')
                        } else {
                            appendFieldName(graph, expr.self, ".")
                            builder.append(expr.methodName)
                            appendValueParams(graph, expr.valueParameters)
                        }
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
                builder.append("ret ")
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
        if (/*expr !is SimpleBlock &&*/ expr !is SimpleBranch) nextLine()
        if (expr is SimpleAssignment && expr.dst.type == Types.Nothing) {
            builder.append("throw new AssertionError(\"Unreachable\")")
            nextLine()
        }
    }

    override fun appendObjectInstance(field: Field, exprScope: Scope, forFieldAccess: String) {
        // todo if there is nothing dangerous in-between, we could use this.
        if (field.ownerScope == outsideClassLike(exprScope)) {
            builder.append("%this")
        } else {
            appendGetObjectInstance(field.ownerScope, exprScope)
        }
        builder.append(forFieldAccess)
    }

    override fun appendSelfForFieldAccess(graph: SimpleGraph, self: SimpleField, field: Field, exprScope: Scope) {
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


}