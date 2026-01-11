package me.anno.zauber.generation.java

import me.anno.zauber.ast.rich.expression.constants.SpecialValue
import me.anno.zauber.ast.simple.SimpleBlock
import me.anno.zauber.ast.simple.SimpleDeclaration
import me.anno.zauber.ast.simple.SimpleExpression
import me.anno.zauber.ast.simple.SimpleField
import me.anno.zauber.ast.simple.controlflow.SimpleBranch
import me.anno.zauber.ast.simple.controlflow.SimpleGoto
import me.anno.zauber.ast.simple.controlflow.SimpleLoop
import me.anno.zauber.ast.simple.controlflow.SimpleReturn
import me.anno.zauber.ast.simple.expression.*
import me.anno.zauber.generation.java.JavaSourceGenerator.appendType
import me.anno.zauber.generation.java.JavaSourceGenerator.writeBlock
import me.anno.zauber.types.Types.BooleanType
import me.anno.zauber.types.Types.ByteType
import me.anno.zauber.types.Types.CharType
import me.anno.zauber.types.Types.DoubleType
import me.anno.zauber.types.Types.FloatType
import me.anno.zauber.types.Types.IntType
import me.anno.zauber.types.Types.LongType
import me.anno.zauber.types.Types.ShortType
import me.anno.zauber.types.Types.StringType

object JavaSimplifiedASTWriter {

    private val builder = JavaSourceGenerator.builder

    fun appendAssign(expression: SimpleAssignmentExpression) {
        val dst = expression.dst
        if (dst.mergeInfo != null) {
            builder.append1(dst).append(" = ")
            return
        }
        builder.append("final ")
        appendType(dst.type, expression.scope, false)
        builder.append(' ').append1(dst).append(" = ")
    }

    fun StringBuilder.append1(field: SimpleField): StringBuilder {
        var field = field
        while (true) {
            field = field.mergeInfo?.dst ?: break
        }
        append("tmp").append(field.id)
        return this
    }

    fun appendValueParams(valueParameters: List<SimpleField>) {
        builder.append('(')
        for (i in valueParameters.indices) {
            if (i > 0) builder.append(", ")
            val parameter = valueParameters[i]
            builder.append1(parameter)
        }
        builder.append(')')
    }

    fun appendSimplifiedAST(expr: SimpleExpression, loop: SimpleLoop? = null) {
        if (expr is SimpleMerge) return
        if (expr is SimpleAssignmentExpression) appendAssign(expr)
        when (expr) {
            is SimpleBlock -> {
                val instructions = expr.instructions
                for (i in instructions.indices) {
                    val instr = instructions[i]
                    if (instr is SimpleMerge) {
                        appendAssign(instr)
                        builder.setLength(builder.length - 3) // remove " = "
                        builder.append(';')
                        JavaSourceGenerator.nextLine()
                    }
                }
                for (i in instructions.indices) {
                    val instr = instructions[i]
                    appendSimplifiedAST(instr, loop)
                }
            }
            is SimpleBranch -> {
                builder.append("if (").append1(expr.condition).append(')')
                writeBlock {
                    appendSimplifiedAST(expr.ifTrue, loop)
                }
                builder.append(" else ")
                writeBlock {
                    appendSimplifiedAST(expr.ifFalse, loop)
                }
            }
            is SimpleLoop -> {
                builder.append("b").append(expr.body.blockId)
                builder.append(": while (true)")
                writeBlock {
                    appendSimplifiedAST(expr.body, expr)
                }
            }
            is SimpleGoto -> {
                if (expr.condition != null) {
                    builder.append("if (").append1(expr.condition).append(") ")
                }
                builder.append(if (expr.isBreak) "break" else "continue")
                if (expr.bodyBlock != loop?.body) {
                    builder.append(" b").append(expr.bodyBlock.blockId)
                }
                builder.append(';')
            }
            is SimpleDeclaration -> {
                appendType(expr.type, expr.scope, false)
                builder.append(' ').append(expr.name).append(" = ")
                when (expr.type) {
                    IntType, LongType,
                    FloatType, DoubleType,
                    ByteType, ShortType -> builder.append("0")
                    CharType -> builder.append("(char) 0")
                    BooleanType -> builder.append("false")
                    else -> builder.append("null")
                }
                builder.append(';')
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
            is SimpleSetField -> {
                if (expr.self != null) {
                    builder.append1(expr.self).append('.')
                }
                val setter = expr.field.setter
                if (setter != null) {
                    builder.append(setter.name).append('(')
                    builder.append1(expr.src)
                    builder.append(')')
                } else {
                    builder.append(expr.field.name)
                        .append(" = ").append1(expr.src)
                }
                builder.append(';')
            }
            is SimpleCompare -> {
                builder.append1(expr.base).append(' ')
                builder.append(expr.type.symbol).append(" 0")
            }
            is SimpleInstanceOf -> {
                // todo if type is ClassType, this is easy, else we need to build an expression...
                builder.append1(expr.value).append(" instanceof ")
                appendType(expr.type, expr.scope, false)
            }
            is SimpleCheckEquals -> {
                // todo this could be converted into a SimpleBranch + SimpleCall
                // todo simple types use ==, while complex types use .equals()
                builder.append1(expr.left).append(" == null ? ")
                    .append1(expr.right).append(" == null : ")
                    .append1(expr.left).append(".equals(")
                    .append1(expr.right).append(")")
            }
            is SimpleCheckIdentical -> {
                builder.append1(expr.left).append(" == ")
                    .append1(expr.right)
            }
            is SimpleSpecialValue -> {
                when (expr.base.type) {
                    SpecialValue.TRUE -> builder.append("true")
                    SpecialValue.FALSE -> builder.append("false")
                    SpecialValue.NULL -> builder.append("null")
                    SpecialValue.THIS -> builder.append("this")
                    SpecialValue.SUPER -> throw IllegalStateException("Super cannot be standalone")
                }
            }
            is SimpleCall -> {
                val methodName = expr.methodName
                val done = when (expr.valueParameters.size) {
                    0 -> {
                        if (expr.self.type == BooleanType && methodName == "not") {
                            builder.append('!').append1(expr.self)
                            true
                        } else false
                    }
                    1 -> {
                        // todo compareTo is a problem for the numbers:
                        //  we must call their static function
                        val supportsType = when (expr.self.type) {
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
                            builder.append1(expr.self).append(symbol)
                            builder.append1(expr.valueParameters[0])
                            true
                        } else false
                    }
                    else -> false
                }
                if (!done) {
                    builder.append1(expr.self).append('.')
                    builder.append(expr.methodName)
                    appendValueParams(expr.valueParameters)
                }
            }
            is SimpleConstructor -> {
                builder.append("new ")
                appendType(expr.method.selfType, expr.scope, false)
                appendValueParams(expr.valueParameters)
            }
            is SimpleSelfConstructor -> {
                when (expr.isThis) {
                    true -> builder.append("this")
                    false -> builder.append("super")
                }
                appendValueParams(expr.valueParameters)
                builder.append(';')
            }
            is SimpleReturn -> {
                builder.append("return ").append1(expr.field).append(';')
            }
            else -> {
                builder.append("/* ${expr.javaClass.simpleName}: $expr */")
            }
        }
        if (expr is SimpleAssignmentExpression) builder.append(';')
        if (expr !is SimpleBlock && expr !is SimpleBranch) JavaSourceGenerator.nextLine()
    }

}