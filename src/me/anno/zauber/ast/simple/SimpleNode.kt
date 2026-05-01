package me.anno.zauber.ast.simple

import me.anno.generation.c.CSourceGenerator.isValueType
import me.anno.zauber.SpecialFieldNames.OUTER_FIELD_NAME
import me.anno.zauber.ast.rich.Field
import me.anno.zauber.ast.rich.controlflow.ReturnExpression
import me.anno.zauber.ast.rich.expression.Expression
import me.anno.zauber.ast.rich.expression.resolved.ResolvedCallExpression
import me.anno.zauber.ast.rich.expression.resolved.ResolvedGetFieldExpression
import me.anno.zauber.ast.simple.expression.SimpleGetField
import me.anno.zauber.ast.simple.expression.SimpleGetObject
import me.anno.zauber.scope.Scope
import me.anno.zauber.scope.ScopeInitType
import me.anno.zauber.types.Type
import me.anno.zauber.types.specialization.Specialization

class SimpleNode(val graph: SimpleGraph) {

    var isEntryPoint = false

    var branchCondition: SimpleField? = null
        set(value) {
            check(field == null || value == null)
            field = value
        }

    var ifBranch: SimpleNode? = null
        set(value) {
            unlinkTo(field)
            field = value
            linkTo(value)
        }

    var elseBranch: SimpleNode? = null
        set(value) {
            unlinkTo(field)
            field = value
            linkTo(value)
        }

    val isBranch get() = branchCondition != null && ifBranch != elseBranch

    private fun linkTo(value: SimpleNode?) {
        value ?: return
        value.inputNodes.add(this)
        outputNodes.add(value)
    }

    private fun unlinkTo(value: SimpleNode?) {
        value ?: return
        value.inputNodes.remove(this)
        outputNodes.remove(value)
    }

    val inputNodes = ArrayList<SimpleNode>(4)
    val outputNodes = ArrayList<SimpleNode>(2)

    var nextBranch: SimpleNode?
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
        inputNodes.clear()
        outputNodes.clear()
    }

    fun isOnlyInput(input: SimpleNode): Boolean {
        return !isEntryPoint && inputNodes.all { it == input }
    }

    fun isOnlyOutput(output: SimpleNode?): Boolean {
        return (output == ifBranch || ifBranch == null) && (output == elseBranch || elseBranch == null)
    }

    val blockId = graph.nodes.size
    val instructions = ArrayList<SimpleInstruction>()

    fun add(expr: SimpleInstruction) {
        instructions.add(expr)
    }

    fun add0(expr: SimpleInstruction) {
        instructions.add(0, expr)
    }

    fun field(type: Type, ownership: Ownership = getOwnership(type)): SimpleField =
        graph.field(type, ownership)

    fun thisField(
        type: Type, thisScope: Scope, scope: Scope, origin: Int,
        specialization: Specialization,
        contextExpr: Expression?
    ): SimpleField {
        thisScope[ScopeInitType.AFTER_DISCOVERY]
        if (thisScope.isObjectLike()) {
            // are objects comptime? yes
            val dst = field(thisScope.typeWithArgs, Ownership.COMPTIME)
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
        scope: Scope, origin: Int,
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

            add(SimpleGetField(dst, currField, field1, specialization, scope, origin))
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

    fun nextOrSelfIfEmpty(): SimpleNode {
        if (isEmpty()) return this
        val next = graph.addNode()
        nextBranch = next
        return next
    }

    companion object {
        fun getOwnership(type: Type): Ownership {
            return if (type.isValueType()) Ownership.VALUE
            else Ownership.SHARED
        }
    }
}