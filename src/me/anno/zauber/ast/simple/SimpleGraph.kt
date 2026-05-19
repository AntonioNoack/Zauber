package me.anno.zauber.ast.simple

import me.anno.zauber.ast.rich.Flags
import me.anno.zauber.ast.rich.expression.Expression
import me.anno.zauber.ast.rich.member.Field
import me.anno.zauber.ast.rich.member.MethodLike
import me.anno.zauber.ast.simple.controlflow.FlowResult
import me.anno.zauber.ast.simple.expression.SimpleAssignment
import me.anno.zauber.ast.simple.expression.SimpleConstructorCall
import me.anno.zauber.ast.simple.expression.SimpleGetOrSetField
import me.anno.zauber.scope.Scope
import me.anno.zauber.types.Specialization
import me.anno.zauber.types.Type
import me.anno.zauber.types.impl.ClassType

class SimpleGraph(val method0: Specialization) {

    val method = method0.method

    private var numFields = 0
    val blocks = ArrayList<SimpleBlock>()
    val startBlock = SimpleBlock(this)
    val fields = ArrayList<SimpleField>()

    // todo in methods within classes, always request self at the start,
    //  so we can use it to call self-based methods with explicitSelf
    val thisFields = HashMap<SimpleThis, SimpleField>()

    val capturedFields = HashMap<Capture, SimpleField>()

    val continueLabels = HashMap<Scope, SimpleBlock>()
    val breakLabels = HashMap<Scope, SimpleBlock>()

    var unitField: SimpleField? = null

    lateinit var endFlow: FlowResult

    init {
        startBlock.isEntryPoint = true
        blocks.add(startBlock)
    }

    fun addBlock(): SimpleBlock {
        val node = SimpleBlock(this)
        blocks.add(node)
        return node
    }

    fun field(type: Type, constantRef: Expression? = null): SimpleField {
        val field = SimpleField(type, numFields++, constantRef)
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
        return "Graph[${blocks.size} nodes, $numFields fields]\n" +
                "unit: $unitField\n" +
                "this: ${
                    thisFields.entries
                        .sortedBy { it.value.id }
                        .map { "\n  %${it.value.id} = ${it.key.scope}" }
                }\n" +
                blocks.joinToString("\n") { it.toString() }
    }

    fun removeSuperCalls() {
        for (block in blocks) {
            block.instructions.removeIf {
                it is SimpleConstructorCall
            }
        }
    }

    fun removeConstantFields() {
        removeFieldIf { it.constantRef != null }
        for (block in blocks) {
            block.instructions.removeIf {
                it is SimpleAssignment &&
                        it.dst.constantRef != null
            }
        }
    }

    fun removeThisFields() {
        removeFieldIf { it in thisFields.values }
    }

    fun removeObjectFields() {
        removeFieldIf {
            val type = it.type
            type is ClassType && type.clazz.isObjectLike()
        }
    }

    fun removeWriteOnlyFields() {
        removeFieldIf { it.numReads == 0 }
    }

    fun replaceYieldsByInnerClass() {
        // todo if inner classes/methods reference a mutable field,
        //  create an inner class, too
        TODO()
    }

    fun inlineValueClasses() {
        TODO()
    }

    fun removeFieldIf(condition: (SimpleField) -> Boolean) {
        fields.removeIf {
            if (condition(it)) {
                it.id = -100_000 - it.id
                check(it.id < 0)
                true
            } else false
        }
    }

    fun renumberFields() {
        for (i in fields.indices) {
            fields[i].id = i
        }
    }

    fun giveMethodFieldsUniqueNames() {
        val foundNames = HashMap<String, Field>()
        for (param in method.valueParameters) {
            // check that parameters are registered first...
            val field = param.getOrCreateField(null, Flags.NONE)
            while (field.newName.startsWith('$')) field.newName = field.newName.substring(1)
            foundNames.getOrPut(field.newName) { field }
        }
        for (block in blocks) {
            for (expr in block.instructions) {
                if (expr is SimpleGetOrSetField && expr.isLocalField()) {
                    foundNames.getOrPut(expr.field.newName) { expr.field }
                }
            }
        }

        for (block in blocks) {
            for (expr in block.instructions) {
                if (expr is SimpleGetOrSetField && expr.isLocalField()) {
                    val field = expr.field
                    val prevField = foundNames[field.newName]
                    if (prevField != field) findNewName(foundNames, field)
                }
            }
        }
    }

    private fun findNewName(allNames: HashMap<String, Field>, field: Field) {
        val oldName = field.newName
        var id = 0
        while (true) {
            val newName = "${oldName}_${id++}"
            if (allNames[newName] == null) {
                allNames[newName] = field
                field.newName = newName
                return
            }
        }
    }


}