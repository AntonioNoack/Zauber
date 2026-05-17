package me.anno.zauber.ast.simple

import me.anno.generation.cpp.CppSourceGenerator.Companion.nativeCppTypes
import me.anno.zauber.SpecialFieldNames.OUTER_FIELD_NAME
import me.anno.zauber.ast.rich.member.Field
import me.anno.zauber.ast.rich.controlflow.ReturnExpression
import me.anno.zauber.ast.rich.expression.Expression
import me.anno.zauber.ast.rich.expression.resolved.ResolvedCallExpression
import me.anno.zauber.ast.rich.expression.resolved.ResolvedGetFieldExpression
import me.anno.zauber.ast.simple.expression.SimpleGetField
import me.anno.zauber.ast.simple.expression.SimpleGetObject
import me.anno.zauber.scope.Scope
import me.anno.zauber.scope.ScopeInitType
import me.anno.zauber.types.Specialization
import me.anno.zauber.types.Type
import me.anno.zauber.types.impl.ClassType
import me.anno.zauber.types.impl.GenericType
import me.anno.zauber.types.impl.arithmetic.AndType
import me.anno.zauber.types.impl.arithmetic.NullType
import me.anno.zauber.types.impl.arithmetic.UnionType
import me.anno.zauber.types.impl.unresolved.UnresolvedType

class SimpleBlock(val graph: SimpleGraph) {

    var isEntryPoint = false

    var branchCondition: SimpleField? = null
        set(value) {
            check(field == null || value == null)
            field = value
        }

    var ifBranch: SimpleBlock? = null
        set(value) {
            unlinkTo(field)
            field = value
            linkTo(value)
        }

    var elseBranch: SimpleBlock? = null
        set(value) {
            unlinkTo(field)
            field = value
            linkTo(value)
        }

    val isBranch get() = branchCondition != null && ifBranch != elseBranch

    private fun linkTo(value: SimpleBlock?) {
        value ?: return
        value.inputBlocks.add(this)
        outputNodes.add(value)
    }

    private fun unlinkTo(value: SimpleBlock?) {
        value ?: return
        value.inputBlocks.remove(this)
        outputNodes.remove(value)
    }

    val inputBlocks = ArrayList<SimpleBlock>(4)
    val outputNodes = ArrayList<SimpleBlock>(2)

    var nextBranch: SimpleBlock?
        get() = ifBranch
        set(value) {
            ifBranch = value
        }


    fun clear() {
        check(!isEntryPoint)
        removeLinks()
        instructions.clear()
    }

    fun removeLinks() {
        ifBranch = null
        elseBranch = null
        branchCondition = null
        inputBlocks.clear()
        outputNodes.clear()
    }

    fun isOnlyInput(input: SimpleBlock): Boolean {
        return !isEntryPoint && inputBlocks.all { it == input }
    }

    fun isOnlyOutput(output: SimpleBlock?): Boolean {
        val ifBranch = ifBranch
        val elseBranch = elseBranch
        return (ifBranch == output || ifBranch == null) && (elseBranch == output || elseBranch == null)
    }

    val blockId = graph.blocks.size
    val instructions = ArrayList<SimpleInstruction>()

    fun add(expr: SimpleInstruction) {
        instructions.add(expr)
    }

    fun add0(expr: SimpleInstruction) {
        instructions.add(0, expr)
    }

    fun field(type: Type, constantRef: Expression? = null): SimpleField =
        graph.field(type, constantRef)

    fun thisField(
        type: Type, thisScope: Scope, scope: Scope, origin: Long,
        specialization: Specialization,
        contextExpr: Expression?
    ): SimpleField {
        thisScope[ScopeInitType.AFTER_DISCOVERY]
        if (thisScope.isObjectLike()) {
            // are objects comptime? yes
            val dst = field(thisScope.typeWithArgs)
            add(SimpleGetObject(dst, thisScope, scope, origin))
            return dst
        } else {
            val isAmbiguous = thisScope.selfAsMethod?.explicitSelfType == true
            val isExplicitSelf = if (isAmbiguous) {
                when (contextExpr) {
                    is ReturnExpression -> true
                    is ResolvedCallExpression -> {
                        val methodOrField = contextExpr.callable.resolved
                        val methodOwner = (methodOrField as? Field)?.ownerScope ?: (methodOrField.scope.parent!!)
                        !methodOwner.isInsideExpression() && !methodOwner.isMethodLike()
                    }
                    is ResolvedGetFieldExpression -> {
                        val methodOrField = contextExpr.field.resolved
                        val fieldOwner = methodOrField.ownerScope
                        !fieldOwner.isInsideExpression() && !fieldOwner.isMethodLike()
                    }
                    else -> TODO("$thisScope is ambiguous, what does $contextExpr ${contextExpr?.javaClass?.simpleName} indicate?")
                }.apply {
                    println("isExplicitSelf? $contextExpr -> $this")
                }
            } else false

            if (thisScope.isClassLike()) {
                val ownerScope = graph.method.ownerScope
                if (ownerScope.inheritsFrom(thisScope)) {
                    return thisField(type, ownerScope, scope, origin, specialization, contextExpr)
                }

                if (ownerScope.isInnerClassOf(thisScope)) {
                    return createOuterFieldChain(ownerScope, thisScope, scope, origin, specialization, contextExpr)
                }
            }

            // println("Creating simple-this: $thisScope, $isExplicitSelf, type: $type")
            return graph.thisFields.getOrPut(SimpleThis(thisScope, isExplicitSelf)) { field(type) }
        }
    }

    private fun createOuterFieldChain(
        innerScope: Scope, outerScope: Scope,
        scope: Scope, origin: Long,
        specialization: Specialization,
        contextExpr: Expression?
    ): SimpleField {

        check(innerScope.isInnerClassOf(outerScope))

        var currField = thisField(innerScope.typeWithArgs, innerScope, scope, origin, specialization, contextExpr)

        var innerScopeI = innerScope
        while (innerScopeI != outerScope) {
            val outerScopeI = innerScopeI.parent!!

            val dst = field(outerScopeI.typeWithArgs) // todo specialize
            val field1 = innerScopeI.fields.first { it.name == OUTER_FIELD_NAME }

            println("Step: ${innerScopeI}.$field1 -> $dst, calling on $currField")

            val spec = Specialization(field1.fieldScope, specialization.typeParameters)
            add(SimpleGetField(dst, currField, field1, spec, scope, origin))
            currField = dst
            innerScopeI = outerScopeI
        }
        return currField
    }

    // todo allow A<B: C|D>: B()? could be nice to use...

    private fun Scope.inheritsFrom(superScope: Scope): Boolean {
        if (this == superScope) return false
        for (superCall in superCalls) {
            if (superCall.type.clazz == superScope) return true
            val isGrandChild = superCall.type.clazz.inheritsFrom(superScope)
            if (isGrandChild) return true
        }
        return false
    }

    override fun toString(): String {
        val builder = StringBuilder()
        builder.append('b').append(blockId)
            .append('[')

        if (isEntryPoint) builder.append("->|")

        if (nextBranch == null) {
            builder.append("end")
        } else if (branchCondition != null) {
            builder.append(branchCondition).append(" ? ")
                .append(ifBranch?.blockId).append(" : ")
                .append(elseBranch?.blockId)
        } else {
            builder.append(nextBranch?.blockId)
        }

        builder.append(']')
        /*val or = onReturn
        if (or != null) builder.append('r').append(or.blockId)
        val ot = onThrow
        if (ot != null) builder.append('t').append(ot.handler.blockId)*/
        builder.append(':')
        for (instr in instructions) {
            builder.append("\n  ").append(instr)
        }
        return builder.toString()
    }

    fun isEmpty(): Boolean {
        return branchCondition == null && ifBranch == null && elseBranch == null &&
                instructions.isEmpty()
    }

    fun nextOrSelfIfEmpty(): SimpleBlock {
        if (isEmpty()) return this
        val next = graph.addBlock()
        nextBranch = next
        return next
    }

    companion object {

        fun Type.isValue(): Boolean {
            return needsCopy() || isNative()
        }

        fun Type.isNative(): Boolean {
            return this in nativeCppTypes
        }

        fun Type.needsCopy(): Boolean {
            return this is ClassType && clazz.isValueType()
        }

        fun Type.isNullable(): Boolean {
            return when (this) {
                NullType -> true
                is ClassType -> false
                is UnionType -> types.any { it.isNullable() }
                is AndType -> types.all { it.isNullable() }
                is GenericType -> superBounds.isNullable()
                is UnresolvedType -> resolvedName.isNullable()
                else -> throw NotImplementedError("Can a $this be null?")
            }
        }
    }
}