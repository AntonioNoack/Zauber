package me.anno.zauber.generation.java

import me.anno.zauber.ast.rich.Field
import me.anno.zauber.ast.rich.MethodLike
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
import me.anno.zauber.generation.java.JavaSourceGenerator.nativeTypes
import me.anno.zauber.generation.java.JavaSourceGenerator.writeBlock
import me.anno.zauber.types.Scope
import me.anno.zauber.types.Type
import me.anno.zauber.types.Types.BooleanType
import me.anno.zauber.types.Types.ByteType
import me.anno.zauber.types.Types.CharType
import me.anno.zauber.types.Types.DoubleType
import me.anno.zauber.types.Types.FloatType
import me.anno.zauber.types.Types.IntType
import me.anno.zauber.types.Types.LongType
import me.anno.zauber.types.Types.ShortType
import me.anno.zauber.types.Types.StringType
import me.anno.zauber.types.impl.ClassType
import me.anno.zauber.types.impl.GenericType
import me.anno.zauber.types.impl.NullType
import me.anno.zauber.types.impl.UnionType

object JavaSimplifiedASTWriter {

    private val builder = JavaSourceGenerator.builder

    fun canBeNull(type: Type): Boolean {
        return when (type) {
            NullType -> true
            is ClassType -> false
            is UnionType -> type.types.any { canBeNull(it) }
            is GenericType -> canBeNull(type.superBounds)
            else -> throw NotImplementedError()
        }
    }

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

    fun appendValueParams(valueParameters: List<SimpleField>, withOpen: Boolean = true) {
        if (withOpen) builder.append('(')
        for (i in valueParameters.indices) {
            if (i > 0) builder.append(", ")
            val parameter = valueParameters[i]
            builder.append1(parameter)
        }
        if (withOpen) builder.append(')')
    }

    fun appendSimplifiedAST(method: MethodLike, expr: SimpleExpression, loop: SimpleLoop? = null) {
        if (expr is SimpleMerge) return
        if (expr is SimpleAssignmentExpression) {
            if (expr.dst.numReads == 0) builder.append("/* ")
            appendAssign(expr)
            if (expr.dst.numReads == 0) builder.append("*/ ")
        }
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
                    appendSimplifiedAST(method, instr, loop)
                }
            }
            is SimpleBranch -> {
                builder.append("if (").append1(expr.condition).append(')')
                writeBlock {
                    appendSimplifiedAST(method, expr.ifTrue, loop)
                }
                builder.append(" else ")
                writeBlock {
                    appendSimplifiedAST(method, expr.ifFalse, loop)
                }
            }
            is SimpleLoop -> {
                builder.append("b").append(expr.body.blockId)
                builder.append(": while (true)")
                writeBlock {
                    appendSimplifiedAST(method, expr.body, expr)
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
                builder.append(' ').append(expr.name)
                if (false) {
                    builder.append(" = ")
                    when (expr.type) {
                        IntType, LongType,
                        FloatType, DoubleType,
                        ByteType, ShortType -> builder.append("0")
                        CharType -> builder.append("(char) 0")
                        BooleanType -> builder.append("false")
                        else -> builder.append("null")
                    }
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
                appendSelfForFieldAccess(method, expr.self, expr.field, expr.scope)
                val field = expr.field
                val getter = field.getter
                if (getter != null) {
                    builder.append(getter.name).append("()")
                } else {
                    builder.append(field.name)
                }
            }
            is SimpleSetField -> {
                appendSelfForFieldAccess(method, expr.self, expr.field, expr.scope)
                val field = expr.field
                val setter = field.setter
                if (setter != null) {
                    builder.append(setter.name).append('(')
                    builder.append1(expr.src)
                    builder.append(')')
                } else {
                    builder.append(field.name)
                        .append(" = ").append1(expr.src)
                }
                builder.append(';')
            }
            is SimpleCompare -> {
                builder.append1(expr.left).append(' ')
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

                // todo if left cannot be null, skip null check
                // todo if left side is a native field, use the static class for comparison

                val leftCanBeNull = canBeNull(expr.left.type)
                val rightCanBeNull = canBeNull(expr.right.type)

                val leftNative = nativeTypes[expr.left.type]
                val rightNative = nativeTypes[expr.right.type]
                if (leftNative != null && rightNative != null) {
                    builder.append1(expr.left).append(" == ")
                        .append1(expr.right)
                } else if (leftCanBeNull || rightCanBeNull) {
                    builder.append1(expr.left).append(" == null ? ")
                        .append1(expr.right).append(" == null : ")
                        .append1(expr.left).append(".equals(")
                        .append1(expr.right).append(")")
                } else {
                    builder.append1(expr.left).append(".equals(")
                        .append1(expr.right).append(")")
                }
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
                    SpecialValue.THIS -> {
                        builder.append(if (method.selfTypeIfNecessary != null) "__self" else "this")
                    }
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
                            StringType, in nativeTypes -> true
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
                    val needsCastForFirstValue = nativeTypes[expr.self.type]
                    if (needsCastForFirstValue != null) {
                        builder.append(needsCastForFirstValue.boxed).append('.')
                        builder.append(expr.methodName).append('(')
                        builder.append1(expr.self)
                        if (expr.valueParameters.isNotEmpty()) {
                            builder.append(", ")
                            appendValueParams(expr.valueParameters, false)
                        }
                        builder.append(')')
                    } else {
                        builder.append1(expr.self).append('.')
                        builder.append(expr.methodName)
                        appendValueParams(expr.valueParameters)
                    }
                }
            }
            is SimpleConstructor -> {
                builder.append("new ")
                appendType(expr.method.selfType, expr.scope, true)
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

    fun appendSelfForFieldAccess(method: MethodLike, self: SimpleField?, field: Field, exprScope: Scope) {
        if (self != null) {
            val needsCast = self.type != field.selfType
            if (needsCast) {
                builder.append("((")
                appendType(field.selfType!!, exprScope, true)
                builder.append(')')
                builder.append1(self).append(").")
            } else {
                builder.append1(self).append('.')
            }
        } else if (field.codeScope == method.scope.parent) {
            builder.append(if (method.selfTypeIfNecessary != null) "__self" else "this").append('.')
        }
    }

}