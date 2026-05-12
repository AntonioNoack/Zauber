package me.anno.zauber.ast.simple

import me.anno.zauber.ast.rich.Field
import me.anno.zauber.ast.rich.MethodLike
import me.anno.zauber.ast.simple.SimpleNode.Companion.getOwnership
import me.anno.zauber.ast.simple.expression.SimpleSelfConstructor
import me.anno.zauber.scope.Scope
import me.anno.zauber.types.Type

class SimpleGraph(val method: MethodLike) {

    private var numFields = 0
    val nodes = ArrayList<SimpleNode>()
    val startBlock = SimpleNode(this)
    val fields = ArrayList<SimpleField>()

    // todo in methods within classes, always request self at the start,
    //  so we can use it to call self-based methods with explicitSelf
    val thisFields = HashMap<SimpleThis, SimpleField>()

    val capturedFields = HashMap<Capture, SimpleField>()

    val continueLabels = HashMap<Scope, SimpleNode>()
    val breakLabels = HashMap<Scope, SimpleNode>()

    var unitField: SimpleField? = null

    lateinit var endFlow: FlowResult

    init {
        startBlock.isEntryPoint = true
        nodes.add(startBlock)
    }

    fun addNode(): SimpleNode {
        val node = SimpleNode(this)
        nodes.add(node)
        return node
    }

    fun field(type: Type, ownership: Ownership = getOwnership(type)): SimpleField {
        val field = SimpleField(type, ownership, numFields++)
        fields.add(field)
        return field
    }

    fun onCapturedField(field: Field) {
        field.isCaptured = true
        method.capturedFields += field // todo may be specialization dependant...
    }

    fun readCapturedField(owner: MethodLike, field: Field, valueType: Type): SimpleField {
        onCapturedField(field)
        return capturedFields.getOrPut(Capture(owner, field)) { field(valueType) }
    }

    override fun toString(): String {
        return "Graph[${nodes.size} nodes, $numFields fields]\n" +
                "unit: $unitField\n" +
                "this: ${thisFields.map { "\n  %${it.value.id} = ${it.key.scope}" }}\n" +
                nodes.joinToString("\n") { it.toString() }
    }

    fun removeSuperCalls() {
        for (block in nodes) {
            block.instructions.removeIf {
                it is SimpleSelfConstructor
            }
        }
    }
}