package me.anno.zauber.generation.java

import me.anno.zauber.ast.rich.Constructor
import me.anno.zauber.ast.rich.Field
import me.anno.zauber.ast.rich.MethodLike
import me.anno.zauber.ast.rich.expression.constants.SpecialValue
import me.anno.zauber.ast.simple.SimpleBlock
import me.anno.zauber.ast.simple.SimpleDeclaration
import me.anno.zauber.ast.simple.SimpleExpression
import me.anno.zauber.ast.simple.SimpleField
import me.anno.zauber.ast.simple.controlflow.SimpleReturn
import me.anno.zauber.ast.simple.controlflow.SimpleThrow
import me.anno.zauber.ast.simple.expression.*
import me.anno.zauber.generation.java.JavaSourceGenerator.appendType
import me.anno.zauber.generation.java.JavaSourceGenerator.comment
import me.anno.zauber.types.Scope
import me.anno.zauber.types.ScopeType
import me.anno.zauber.types.Type
import me.anno.zauber.types.Types.BooleanType
import me.anno.zauber.types.Types.ByteType
import me.anno.zauber.types.Types.CharType
import me.anno.zauber.types.Types.DoubleType
import me.anno.zauber.types.Types.FloatType
import me.anno.zauber.types.Types.IntType
import me.anno.zauber.types.Types.LongType
import me.anno.zauber.types.Types.NothingType
import me.anno.zauber.types.Types.ShortType
import me.anno.zauber.types.Types.StringType
import me.anno.zauber.types.impl.*

object JavaSimplifiedASTWriter {

    private val builder = JavaSourceGenerator.builder

    fun canBeNull(type: Type): Boolean {
        return when (type) {
            NullType -> true
            is ClassType -> false
            is UnionType -> type.types.any { canBeNull(it) }
            is AndType -> type.types.all { canBeNull(it) }
            is GenericType -> canBeNull(type.superBounds)
            else -> throw NotImplementedError("Can a $type be null?")
        }
    }

    fun appendAssign(expression: SimpleAssignmentExpression) {
        val dst = expression.dst
        if (dst.mergeInfo != null) {
            builder.append1(dst).append(" = ")
        } else {
            builder.append("final ")
            appendType(dst.type, expression.scope, false)
            builder.append(' ').append1(dst).append(" = ")
        }
    }

    fun noThis(field: SimpleField): Boolean {
        var field = field
        while (true) {
            field = field.mergeInfo?.dst ?: break
        }
        val thisScope = field.scopeIfIsThis
        if (thisScope != null) {
            val scopeType = thisScope.scopeType
            if (scopeType == ScopeType.METHOD || scopeType == ScopeType.CONSTRUCTOR ||
                (scopeType != null && scopeType.isInsideExpression())
            ) {
                return true
            }
        }
        return false
    }

    fun StringBuilder.append1(field: SimpleField): StringBuilder {
        var field = field
        while (true) {
            field = field.mergeInfo?.dst ?: break
        }
        val thisScope = field.scopeIfIsThis
        if (thisScope != null) {
            val scopeType = thisScope.scopeType
            if (scopeType == ScopeType.METHOD || scopeType == ScopeType.CONSTRUCTOR ||
                (scopeType != null && scopeType.isInsideExpression())
            ) {
                // parameter level -> no prefix needed
                comment { append(thisScope.pathStr) }
            } else {
                val isUnique = scopeType == null || scopeType == ScopeType.PACKAGE || scopeType.isObject()
                if (isUnique) {
                    // todo get object/package name with specialization...
                    appendType(field.type, thisScope /* not ideal */, true)
                } else {
                    // todo find 'this' of correct level in scope
                    append("this")
                    comment { append(thisScope.pathStr) }
                }
            }
        } else {
            append("tmp").append(field.id)
        }
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

    // todo we have converted SimpleBlock into a complex graph,
    //  before we can use it, we must convert it back
    fun JavaSourceGenerator.appendSimplifiedAST(
        method: MethodLike, expr: SimpleBlock,
        // loop: SimpleLoop? = null
    ) {
        val instructions = expr.instructions
        for (i in instructions.indices) {
            val instr = instructions[i]
            if (instr is SimpleMerge) {
                appendAssign(instr)
                builder.setLength(builder.length - 3) // remove " = "
                builder.append(';')
                nextLine()
            }
        }
        for (i in instructions.indices) {
            val instr = instructions[i]
            appendSimplifiedAST(method, instr /*loop*/)
            if (instr is SimpleAssignmentExpression &&
                instr.dst.type == NothingType
            ) break
        }
        if (expr.branchCondition == null) {
            val next = expr.nextBranch
            if (next != null) {
                appendSimplifiedAST(method, next)
            }
        } else {
            // todo this may or may not be simply be possible...
            // todo this may be a loop, branch, or similar...
            TODO("Jump to either branch")
        }
    }

    fun JavaSourceGenerator.appendSimplifiedAST(
        method: MethodLike, expr: SimpleExpression,
        // loop: SimpleLoop? = null
    ) {
        if (expr is SimpleMerge) return
        if (expr is SimpleAssignmentExpression && expr.dst.type != NothingType) {
            val notNeeded = expr.dst.numReads == 0
            if (notNeeded) comment { appendAssign(expr) }
            else appendAssign(expr)
        }
        when (expr) {
            /*is SimpleBranch -> {
                builder.append("if (").append1(expr.condition).append(')')
                writeBlock {
                    appendSimplifiedAST(method, expr.ifTrue, loop)
                }
                trimWhitespaceAtEnd()
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
            }*/
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
                when {
                    leftNative != null && rightNative != null -> {
                        builder.append1(expr.left).append(" == ")
                            .append1(expr.right)
                    }
                    leftCanBeNull && rightCanBeNull -> {
                        builder.append1(expr.left).append(" == null ? ")
                            .append1(expr.right).append(" == null : ")
                            .append1(expr.left).append(".equals(")
                            .append1(expr.right).append(")")
                    }
                    leftCanBeNull -> {
                        builder.append1(expr.left).append(" != null && ")
                            .append1(expr.left).append(".equals(")
                            .append1(expr.right).append(")")
                    }
                    rightCanBeNull -> {
                        builder.append1(expr.right).append(" != null && ")
                            .append1(expr.left).append(".equals(")
                            .append1(expr.right).append(")")
                    }
                    else -> {
                        builder.append1(expr.left).append(".equals(")
                            .append1(expr.right).append(")")
                    }
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
                    SpecialValue.SUPER -> throw IllegalStateException("Super cannot be standalone")
                }
            }
            is SimpleCall -> {
                if (expr.sample is Constructor) {
                    builder.append("new ")
                    appendType(expr.dst.type, expr.scope, true)
                    appendValueParams(expr.valueParameters)
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
                                builder.append(castSymbol).append1(expr.self)
                                true
                            } else if (expr.self.type == BooleanType && methodName == "not") {
                                builder.append('!').append1(expr.self)
                                true
                            } else false
                        }
                        1 -> {
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
                                // compareTo is a problem for numbers:
                                //  we must call their static compare() function
                                "compareTo" -> "compare"
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
            }
            is SimpleAllocateInstance -> {
                // handled in SimpleCall, because only there do we have the value parameters
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
                // todo cast if necessary
                builder.append("return ").append1(expr.field).append(';')
            }
            is SimpleThrow -> {
                builder.append("throw ").append1(expr.field).append(';')
            }
            else -> {
                comment {
                    builder.append(expr.javaClass.simpleName).append(": ")
                        .append(expr)
                }
            }
        }
        if (expr is SimpleAssignmentExpression) builder.append(';')
        /*if (expr !is SimpleBlock && expr !is SimpleBranch)*/ nextLine()
        if (expr is SimpleAssignmentExpression && expr.dst.type == NothingType) {
            builder.append("throw new AssertionError(\"Unreachable\");")
            nextLine()
        }
    }

    fun appendSelfForFieldAccess(method: MethodLike, self: SimpleField?, field: Field, exprScope: Scope) {
        if (self != null) {
            val needsCast = self.type != field.selfType && self.scopeIfIsThis == null
            if (needsCast && field.selfType != null) {
                builder.append("((")
                appendType(field.selfType, exprScope, true)
                builder.append(')')
                builder.append1(self).append(").")
            } else if (noThis(self)) {
                // just comment the type
                builder.append1(self)
            } else {
                builder.append1(self).append('.')
            }
        } else if (field.codeScope == method.scope.parent) {
            builder.append(if (method.selfTypeIfNecessary != null) "__self" else "this").append('.')
        } else if (field.codeScope.parent?.isObject() == true) {
            appendType(field.codeScope.parent!!.typeWithoutArgs, exprScope, true)
            builder.append(".__instance__.")
        }
    }

}