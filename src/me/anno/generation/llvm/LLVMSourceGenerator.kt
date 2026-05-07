package me.anno.generation.llvm

import me.anno.generation.Generator
import me.anno.generation.java.BoxedType
import me.anno.generation.java.JavaBuilder.appendFieldName
import me.anno.generation.java.JavaBuilder.appendType
import me.anno.generation.java.JavaSimplifiedASTWriter.append1
import me.anno.generation.java.JavaSimplifiedASTWriter.appendAssign
import me.anno.generation.java.JavaSimplifiedASTWriter.appendSelfForFieldAccess
import me.anno.generation.java.JavaSimplifiedASTWriter.appendValueParams
import me.anno.generation.java.JavaSimplifiedASTWriter.canBeNull
import me.anno.generation.java.JavaSimplifiedASTWriter.isObjectLike
import me.anno.generation.java.JavaSourceGenerator
import me.anno.utils.ResetThreadLocal.Companion.threadLocal
import me.anno.zauber.ast.reverse.CodeReconstruction
import me.anno.zauber.ast.reverse.SimpleBranch
import me.anno.zauber.ast.reverse.SimpleLoop
import me.anno.zauber.ast.rich.Constructor
import me.anno.zauber.ast.rich.Method
import me.anno.zauber.ast.rich.expression.constants.SpecialValue
import me.anno.zauber.ast.simple.*
import me.anno.zauber.ast.simple.controlflow.SimpleExit
import me.anno.zauber.ast.simple.controlflow.SimpleReturn
import me.anno.zauber.ast.simple.controlflow.SimpleThrow
import me.anno.zauber.ast.simple.expression.*
import me.anno.zauber.expansion.DependencyData
import me.anno.zauber.typeresolution.ResolutionContext
import me.anno.zauber.types.Types
import me.anno.zauber.types.specialization.MethodSpecialization
import java.io.File

// todo this is like C, just different commands?
object LLVMSourceGenerator : Generator() {

    // taken from Java
    val protectedTypes by threadLocal {
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

    val nativeTypes by threadLocal { protectedTypes.filter { (_, it) -> it.boxed != it.native } }
    val nativeNumbers by threadLocal { nativeTypes - Types.Boolean }


    override fun generateCode(dst: File, data: DependencyData, mainMethod: Method) {
        for (method in data.calledMethods) {
            generateCode(method)
        }

        dst.writeText(builder.toString())
        builder.clear()
    }

    fun getMethodName(method: MethodSpecialization): String {
        return method.method.methodScope.pathStr +
                JavaSourceGenerator.createSpecializationSuffix(method.specialization)
    }

    fun generateCode(method0: MethodSpecialization) {

        val (method, specialization) = method0
        val body = method.body ?: return

        nextLine()
        builder.append("define void @")
            .append(getMethodName(method0))
        builder.append("()")

        val context = ResolutionContext(method.selfType, specialization, true, null)
        val graph = ASTSimplifier.simplify(method0)

        CodeReconstruction.createCodeFromGraph(graph)

        writeBlock {
            appendSimplifiedAST(graph, graph.startBlock)
        }
    }

    fun appendSimplifiedAST(graph: SimpleGraph, expr: SimpleNode) {
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

    fun appendSimplifiedAST(graph: SimpleGraph, expr: SimpleInstruction) {
        if (expr is SimpleGetObject) return
        if (expr is SimpleAssignment && expr.dst.type != Types.Nothing && !expr.dst.isObjectLike()) {
            val notNeeded = expr.dst.numReads == 0
            if (notNeeded) comment { appendAssign(graph, expr) }
            else appendAssign(graph, expr)
        }
        when (expr) {
            is SimpleBranch -> {
                builder.append("if (").append1(graph, expr.condition).append(')')
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
                builder.appendFieldName(expr.field)
            }
            is SimpleSetField -> {
                appendSelfForFieldAccess(graph, expr.self, expr.field, expr.scope)
                builder.appendFieldName(expr.field).append(" = ").append1(graph, expr.value)
            }
            is SimpleCompare -> {
                builder.append1(graph, expr.left).append(' ')
                builder.append(expr.type.symbol).append(" 0")
            }
            is SimpleInstanceOf -> {
                // todo if type is ClassType, this is easy, else we need to build an expression...
                builder.append1(graph, expr.value).append(" instanceof ")
                appendType(expr.type, expr.scope, false)
            }
            is SimpleCheckEquals -> {
                // todo this could be converted into a SimpleBranch + SimpleCall
                // todo simple types use ==, while complex types use .equals()

                // todo if left cannot be null, skip null check
                // todo if left side is a native field, use the static class for comparison

                val leftCanBeNull = canBeNull(expr.left.type)
                val rightCanBeNull = canBeNull(expr.right.type)

                val leftNative = nativeTypes[expr.left.type]
                val rightNative = nativeTypes[expr.right.type]
                when {
                    leftNative != null && rightNative != null -> {
                        builder.append1(graph, expr.left).append(" == ")
                            .append1(graph, expr.right)
                    }
                    leftCanBeNull && rightCanBeNull -> {
                        builder.append1(graph, expr.left).append(" == null ? ")
                            .append1(graph, expr.right).append(" == null : ")
                            .append1(graph, expr.left).append(".equals(")
                            .append1(graph, expr.right).append(")")
                    }
                    leftCanBeNull -> {
                        builder.append1(graph, expr.left).append(" != null && ")
                            .append1(graph, expr.left).append(".equals(")
                            .append1(graph, expr.right).append(")")
                    }
                    rightCanBeNull -> {
                        builder.append1(graph, expr.right).append(" != null && ")
                            .append1(graph, expr.left).append(".equals(")
                            .append1(graph, expr.right).append(")")
                    }
                    else -> {
                        builder.append1(graph, expr.left).append(".equals(")
                            .append1(graph, expr.right).append(")")
                    }
                }
            }
            is SimpleCheckIdentical -> {
                builder.append1(graph, expr.left).append(" == ")
                    .append1(graph, expr.right)
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
                                builder.append(castSymbol).append1(graph, expr.self)
                                true
                            } else if (expr.self.type == Types.Boolean && methodName == "not") {
                                builder.append('!').append1(graph, expr.self)
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
                                builder.append1(graph, expr.self).append(symbol)
                                builder.append1(graph, expr.valueParameters[0])
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
                            builder.append1(graph, expr.self)
                            if (expr.valueParameters.isNotEmpty()) {
                                builder.append(", ")
                                appendValueParams(graph, expr.valueParameters, false)
                            }
                            builder.append(')')
                        } else {
                            builder.append1(graph, expr.self).append('.')
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
                builder.append("return ").append1(graph, expr.field)
            }
            is SimpleThrow -> {
                // todo cast if necessary
                builder.append("throw ").append1(graph, expr.field)
            }
            else -> {
                comment {
                    builder.append(expr.javaClass.simpleName).append(": ")
                        .append(expr)
                }
            }
        }
        when (expr) {
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


}