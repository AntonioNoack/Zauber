package me.anno.generation.java

import me.anno.generation.Generator
import me.anno.generation.Generator.Companion.comment
import me.anno.generation.Generator.Companion.nextLine
import me.anno.generation.Generator.Companion.trimWhitespaceAtEnd
import me.anno.generation.Generator.Companion.writeBlock
import me.anno.generation.java.JavaBuilder.appendFieldName
import me.anno.generation.java.JavaBuilder.appendType
import me.anno.generation.java.JavaSourceGenerator.nativeNumbers
import me.anno.generation.java.JavaSourceGenerator.nativeTypes
import me.anno.zauber.SpecialFieldNames.OBJECT_FIELD_NAME
import me.anno.zauber.ast.reverse.SimpleBranch
import me.anno.zauber.ast.reverse.SimpleLoop
import me.anno.zauber.ast.rich.Constructor
import me.anno.zauber.ast.rich.Field
import me.anno.zauber.ast.rich.expression.constants.SpecialValue
import me.anno.zauber.ast.simple.*
import me.anno.zauber.ast.simple.controlflow.SimpleExit
import me.anno.zauber.ast.simple.controlflow.SimpleReturn
import me.anno.zauber.ast.simple.controlflow.SimpleThrow
import me.anno.zauber.ast.simple.expression.*
import me.anno.zauber.scope.Scope
import me.anno.zauber.types.Type
import me.anno.zauber.types.Types
import me.anno.zauber.types.impl.ClassType
import me.anno.zauber.types.impl.GenericType
import me.anno.zauber.types.impl.arithmetic.AndType
import me.anno.zauber.types.impl.arithmetic.NullType
import me.anno.zauber.types.impl.arithmetic.UnionType

object JavaSimplifiedASTWriter {

    private val builder = Generator.builder
    val imports = HashMap<String, List<String>>()

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

    fun appendAssign(graph: SimpleGraph, expression: SimpleAssignment) {
        val dst = expression.dst
        if (dst.mergeInfo != null) {
            builder.append1(graph, dst).append(" = ")
        } else {
            builder.append("final ")
            appendType(dst.type, expression.scope, false)
            builder.append(' ').append1(graph, dst).append(" = ")
        }
    }

    fun SimpleField.isObjectLike() = type is ClassType && type.clazz.isObjectLike()

    fun SimpleField.isOwnerThis(graph: SimpleGraph): Boolean {
        return type is ClassType && type.clazz == graph.method.ownerScope &&
                graph.thisFields.any { !it.key.isExplicitSelf && it.value === this }
    }

    fun StringBuilder.append1(graph: SimpleGraph, field: SimpleField): StringBuilder {
        if (field.isObjectLike()) {
            appendType(field.type, (field.type as ClassType).clazz, false)
            append('.').append(OBJECT_FIELD_NAME)
        } else if (field.isOwnerThis(graph)) {
            append("this")
        } else {
            var field = field
            while (true) {
                field = field.mergeInfo?.dst ?: break
            }
            append("tmp").append(field.id)
        }
        return this
    }

    fun appendValueParams(graph: SimpleGraph, valueParameters: List<SimpleField>, withOpen: Boolean = true) {
        if (withOpen) builder.append('(')
        for (i in valueParameters.indices) {
            if (i > 0) builder.append(", ")
            val parameter = valueParameters[i]
            builder.append1(graph, parameter)
        }
        if (withOpen) builder.append(')')
    }

    // todo we have converted SimpleBlock into a complex graph,
    //  before we can use it, we must convert it back
    fun appendSimplifiedAST(
        graph: SimpleGraph, expr: SimpleNode,
        // loop: SimpleLoop? = null
    ) {
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

    fun appendSimplifiedAST(
        graph: SimpleGraph, expr: SimpleInstruction,
        // loop: SimpleLoop? = null
    ) {
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
            /*is SimpleGoto -> {
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

    fun outsideClassLike(scope: Scope): Scope? {
        var scope = scope
        while (true) {
            if (scope.isClassLike()) return scope
            scope = scope.parentIfSameFile ?: return null
        }
    }

    fun appendObjectInstance(field: Field, exprScope: Scope) {
        // todo if there is nothing dangerous in-between, we could use this.
        if (field.ownerScope == outsideClassLike(exprScope)) {
            builder.append("this.")
        } else {
            appendType(field.ownerScope.typeWithArgs, exprScope, true)
            builder.append('.').append(OBJECT_FIELD_NAME).append('.')
        }
    }

    fun appendSelfForFieldAccess(graph: SimpleGraph, self: SimpleField, field: Field, exprScope: Scope) {
        if (self.type is ClassType && self.type.clazz.isObjectLike()) {
            appendObjectInstance(field, exprScope)
        } else if (self.type is ClassType && !self.type.clazz.isClassLike()) {
            builder.append("/* ${field.ownerScope.pathStr} */ ")
        } else {
            val fieldSelfType = field.selfType
            val needsCast = self.type != fieldSelfType
            if (needsCast && fieldSelfType != null) {
                builder.append("((")
                appendType(fieldSelfType, exprScope, true)
                builder.append(')')
                builder.append1(graph, self).append(").")
            } else {
                builder.append1(graph, self).append('.')
            }
        }
    }

}